package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.remote.CvmClient
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.dto.CvmCompanyRaw
import com.lynchscreener.data.remote.dto.EarningsBasis
import com.lynchscreener.data.repository.AssetRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Ingests the CVM open-data packages to obtain each B3 company's 5-year net
 * income history, used to compute a true earnings CAGR (Peter Lynch's PEGY) and
 * the earnings-consistency rule.
 *
 * Each DFP package is ~60–80 MB, so a single run cannot read all of them inside
 * WorkManager's 10-minute window on a phone. The worker therefore processes
 * ONE fiscal year per invocation and self-chains ([SyncScheduler.continueCvm])
 * until the list is exhausted; the partial result lives in a small JSON file in
 * the app's private storage, so an interruption resumes from where it stopped
 * instead of restarting every download.
 */
class CvmEarningsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val client: CvmClient,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    private val partialFile: File get() = File(applicationContext.filesDir, PARTIAL_FILE)
    private val pendingFile: File get() = File(applicationContext.filesDir, PENDING_FILE)
    private val cnpjFile: File get() = File(applicationContext.filesDir, CNPJ_FILE)

    override suspend fun doWork(): Result {
        val tickers = repository.pendingEarningsTickers().toSet()
        if (tickers.isEmpty()) {
            SyncEventLog.log("CVM: nenhum ativo pendente de histórico")
            SyncProgress.finish()
            cleanup()
            return Result.success()
        }

        return try {
            val pending = readPendingPackages()
            if (pending.isEmpty()) {
                // Every package was already downloaded: just apply and finish.
                return applyToDatabase(tickers)
            }

            val packageId = pending.first()
            val isItr = packageId.startsWith("I")
            val year = packageId.drop(1).toIntOrNull() ?: return Result.success()
            SyncEventLog.log(
                "CVM: pacote ${if (isItr) "ITR" else "DFP"} $year " +
                    "(${pending.size} restante(s), ${tickers.size} ativos)",
            )
            SyncProgress.begin(
                label = "CVM · ${if (isItr) "ITR" else "DFP"} $year",
                pendingPackages = pending.size,
            )

            val tickerToCnpj = readCachedCnpj()
                ?: client.fetchTickerToCnpj(onBytes = SyncProgress::onBytes)
                    .also { writeCachedCnpj(it) }
            val cnpjs = tickers.mapNotNull { tickerToCnpj[it] }.toSet()
            SyncEventLog.log("CVM: ${cnpjs.size}/${tickers.size} tickers mapeados para CNPJ")

            if (cnpjs.isEmpty()) {
                // Nothing to look up — record "no data" so the loop stops.
                repository.applyEarningsBatch(
                    tickers.associateWith { CvmCompanyRaw() },
                )
                SyncProgress.finish()
                cleanup()
                return Result.success()
            }

            val partial = readPartial()
            val packageData = client.fetchCompanyData(
                cnpjs = cnpjs,
                years = if (isItr) emptyList() else listOf(year),
                itrYears = if (isItr) listOf(year) else emptyList(),
                onProgress = { SyncEventLog.log("CVM: $it") },
                onBytes = SyncProgress::onBytes,
            )
            for ((cnpj, company) in packageData) {
                val target = partial[cnpj] ?: PartialCompany()
                val mergedAnnual = if (isItr) {
                    target.netIncome
                } else {
                    target.netIncome + company.netIncomeByYear.mapKeys { it.key.toString() }
                }
                val mergedQuarters = target.quarters + company.quarterlyNetIncome
                partial[cnpj] = target.copy(
                    netIncome = mergedAnnual,
                    quarters = mergedQuarters,
                    basis = company.basis.name,
                    equity = company.equityControllers ?: target.equity,
                    latestYear = company.latestYear ?: target.latestYear,
                )
            }

            val remaining = pending.drop(1)
            writePartial(partial)
            writePendingPackages(remaining)

            if (remaining.isEmpty()) {
                applyToDatabase(tickers)
            } else {
                SyncProgress.begin(
                    label = "CVM · ${if (isItr) "ITR" else "DFP"} $year concluído",
                    pendingPackages = remaining.size,
                )
                SyncScheduler.continueCvm(applicationContext)
                Result.success()
            }
        } catch (e: Exception) {
            SyncProgress.finish()
            SyncEventLog.log("CVM: falha — ${e.message ?: e.javaClass.simpleName}")
            Result.retry()
        }
    }

    /** Writes the aggregated history to Room, then clears the scratch files. */
    private suspend fun applyToDatabase(tickers: Set<String>): Result {
        val partial = readPartial()
        val tickerToCnpj = readCachedCnpj().orEmpty()
        val tickerToData = tickers.associateWith { ticker ->
            val cnpj = tickerToCnpj[ticker]
            val stored = cnpj?.let { partial[it] }
            stored?.toRaw() ?: CvmCompanyRaw()
        }
        val updated = repository.applyEarningsBatch(tickerToData)
        val withData = tickerToData.count { it.value.netIncomeByYear.isNotEmpty() }
        val controllers = tickerToData.count { it.value.basis == EarningsBasis.CONTROLLERS }
        val withEquity = tickerToData.count { it.value.equityControllers != null }
        val withQuarters = tickerToData.count { it.value.quarterlyNetIncome.size >= 8 }
        SyncEventLog.log(
            "CVM: $updated ativos com histórico ($withData com série, " +
                "$controllers na base controladores, $withEquity com PL, " +
                "$withQuarters com 8+ trimestres)",
        )
        SyncProgress.finish()
        cleanup()
        return Result.success()
    }

    // --- scratch state -------------------------------------------------------

    /**
     * Packages still to download, in order: all DFPs first (they carry the annual
     * history and the equity), then the ITRs (the quarterly series).
     */
    private fun readPendingPackages(): List<String> {
        val stored = runCatching {
            json.decodeFromString<List<String>>(pendingFile.readText())
        }.getOrNull() ?: return defaultPackages()
        val allowed = defaultPackages().toSet()
        val filtered = stored.filter { it in allowed }
        // A file written by an older version may hold bare year numbers.
        return filtered.ifEmpty { defaultPackages() }
    }

    private fun defaultPackages(): List<String> =
        HttpConfig.CVM_DFP_YEARS.map { "D$it" } + HttpConfig.CVM_ITR_YEARS.map { "I$it" }

    private fun writePendingPackages(packages: List<String>) {
        runCatching { pendingFile.writeText(json.encodeToString(packages)) }
    }

    private fun readPartial(): MutableMap<String, PartialCompany> = runCatching {
        json.decodeFromString<Map<String, PartialCompany>>(partialFile.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun writePartial(partial: Map<String, PartialCompany>) {
        runCatching { partialFile.writeText(json.encodeToString(partial)) }
    }

    private fun readCachedCnpj(): Map<String, String>? = runCatching {
        json.decodeFromString<Map<String, String>>(cnpjFile.readText())
    }.getOrNull()

    private fun writeCachedCnpj(map: Map<String, String>) {
        runCatching { cnpjFile.writeText(json.encodeToString(map)) }
    }

    private fun cleanup() {
        runCatching {
            partialFile.delete()
            pendingFile.delete()
            cnpjFile.delete()
            applicationContext.cacheDir.listFiles()
                ?.filter { it.name.startsWith("dfp_") || it.name.startsWith("fca_") }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        const val PARTIAL_FILE = "cvm_earnings_partial.json"
        const val PENDING_FILE = "cvm_earnings_pending.json"
        const val CNPJ_FILE = "cvm_ticker_cnpj.json"
    }
}

/**
 * Scratch representation of one company between worker runs. Fiscal years are
 * string keys because JSON object keys are strings.
 */
@Serializable
private data class PartialCompany(
    val netIncome: Map<String, Double> = emptyMap(),
    val quarters: Map<String, Double> = emptyMap(),
    val basis: String = EarningsBasis.CONSOLIDATED.name,
    val equity: Double? = null,
    val latestYear: Int? = null,
) {
    fun toRaw(): CvmCompanyRaw {
        val byYear = netIncome.mapNotNull { (year, value) ->
            year.toIntOrNull()?.let { it to value }
        }.toMap()
        return CvmCompanyRaw(
            netIncomeByYear = byYear.toSortedMap(),
            basis = runCatching { EarningsBasis.valueOf(basis) }
                .getOrDefault(EarningsBasis.CONSOLIDATED),
            equityControllers = equity,
            latestYear = latestYear ?: byYear.keys.maxOrNull(),
            quarterlyNetIncome = quarters,
        )
    }
}

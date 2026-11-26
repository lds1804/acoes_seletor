package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.remote.CvmClient
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.dto.CvmEarnings
import com.lynchscreener.data.repository.AssetRepository
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
            cleanup()
            return Result.success()
        }

        return try {
            val pendingYears = readPendingYears()
            if (pendingYears.isEmpty()) {
                // Every package was already downloaded: just apply and finish.
                return applyToDatabase(tickers)
            }

            val year = pendingYears.first()
            SyncEventLog.log(
                "CVM: pacote DFP $year (${pendingYears.size} restante(s), ${tickers.size} ativos)",
            )

            val tickerToCnpj = readCachedCnpj()
                ?: client.fetchTickerToCnpj().also { writeCachedCnpj(it) }
            val cnpjs = tickers.mapNotNull { tickerToCnpj[it] }.toSet()
            SyncEventLog.log("CVM: ${cnpjs.size}/${tickers.size} tickers mapeados para CNPJ")

            if (cnpjs.isEmpty()) {
                // Nothing to look up — record "no data" so the loop stops.
                repository.applyEarningsBatch(tickers.associateWith { CvmEarnings.from(emptyMap()) })
                cleanup()
                return Result.success()
            }

            val partial = readPartial()
            val yearData = client.fetchNetIncomeByCnpj(cnpjs, years = listOf(year)) {
                SyncEventLog.log("CVM: $it")
            }
            for ((cnpj, byYear) in yearData) {
                val target = partial.getOrPut(cnpj) { mutableMapOf() }
                for ((fy, value) in byYear) target.putIfAbsent(fy, value)
            }

            val remaining = pendingYears.drop(1)
            writePartial(partial)
            writePendingYears(remaining)

            if (remaining.isEmpty()) {
                applyToDatabase(tickers)
            } else {
                SyncScheduler.continueCvm(applicationContext)
                Result.success()
            }
        } catch (e: Exception) {
            SyncEventLog.log("CVM: falha — ${e.message ?: e.javaClass.simpleName}")
            Result.retry()
        }
    }

    /** Writes the aggregated history to Room, then clears the scratch files. */
    private suspend fun applyToDatabase(tickers: Set<String>): Result {
        val partial = readPartial()
        val tickerToCnpj = readCachedCnpj().orEmpty()
        val tickerToEarnings = tickers.associateWith { ticker ->
            val cnpj = tickerToCnpj[ticker]
            val raw = cnpj?.let { partial[it] } ?: emptyMap()
            CvmEarnings.from(raw)
        }
        val updated = repository.applyEarningsBatch(tickerToEarnings)
        val withData = tickerToEarnings.count { it.value.netIncomeByYear.isNotEmpty() }
        val withLoss = tickerToEarnings.count { it.value.consistent == false }
        SyncEventLog.log("CVM: $updated ativos com histórico ($withData com série, $withLoss com prejuízo)")
        cleanup()
        return Result.success()
    }

    // --- scratch state -------------------------------------------------------

    private fun readPendingYears(): List<Int> {
        val stored = runCatching {
            json.decodeFromString<List<Int>>(pendingFile.readText())
        }.getOrNull() ?: return HttpConfig.CVM_DFP_YEARS
        // Keep only years still offered by the configured list (drops stale ones).
        return stored.filter { it in HttpConfig.CVM_DFP_YEARS }
    }

    private fun writePendingYears(years: List<Int>) {
        runCatching { pendingFile.writeText(json.encodeToString(years)) }
    }

    private fun readPartial(): MutableMap<String, MutableMap<Int, Double>> {
        val decoded = runCatching {
            json.decodeFromString<Map<String, Map<Int, Double>>>(partialFile.readText())
        }.getOrNull() ?: return mutableMapOf()
        return decoded.mapValues { it.value.toMutableMap() }.toMutableMap()
    }

    private fun writePartial(partial: Map<String, Map<Int, Double>>) {
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

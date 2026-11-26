package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.SecClient
import com.lynchscreener.data.remote.parser.SecCompanyFactsParser
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.domain.model.Market
import kotlinx.coroutines.delay

/**
 * Slow queue: consumes up to 20 stale US companies (balances older than
 * ~75 days), fetches SEC company facts at ~3 req/s, and discards the raw JSON
 * immediately (parser extracts only the fields we keep).
 */
class SecBalancesWorker(
    appContext: Context,
    params: WorkerParameters,
    private val client: SecClient,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val batch = repository.nextSecBalanceBatch()
        SyncEventLog.log("SEC: lote de ${batch.size} empresas (CIK)")
        var succeeded = 0

        for (asset in batch) {
            val cik = asset.cik ?: continue
            repository.setSyncing(asset.ticker)
            try {
                val raw = client.fetchCompanyFacts(cik)
                val sec = SecCompanyFactsParser.parse(raw)
                repository.applySecFundamentals(asset.ticker, sec)
                repository.setSynced(asset.ticker)
                succeeded++
            } catch (e: Exception) {
                repository.markError(asset.ticker, e.message ?: "Erro SEC")
            }
            delay(HttpConfig.SEC_MIN_INTERVAL_MS)
        }

        // Self-chain: catch up the whole US universe without waiting 6h.
        val pending = repository.countPendingFundamentals(Market.US)
        if (succeeded > 0 && pending > 0) {
            SyncEventLog.log("SEC: $pending empresas restantes — enfileirando próximo lote")
            SyncScheduler.continueSec(applicationContext)
        } else if (pending == 0 && batch.isNotEmpty()) {
            SyncEventLog.log("SEC: balanços concluídos para todo o S&P 500")
        }

        Result.success()
    } catch (e: Exception) {
        SyncEventLog.log("SEC: falha — ${e.message ?: e.javaClass.simpleName}")
        Result.retry()
    }
}

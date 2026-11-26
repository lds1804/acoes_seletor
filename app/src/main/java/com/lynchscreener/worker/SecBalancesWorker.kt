package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.SecClient
import com.lynchscreener.data.remote.parser.SecCompanyFactsParser
import com.lynchscreener.data.repository.AssetRepository
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

        for (asset in batch) {
            val cik = asset.cik ?: continue
            repository.setSyncing(asset.ticker)
            try {
                val raw = client.fetchCompanyFacts(cik)
                val sec = SecCompanyFactsParser.parse(raw)
                repository.applySecFundamentals(asset.ticker, sec)
                repository.setSynced(asset.ticker)
            } catch (e: Exception) {
                repository.markError(asset.ticker, e.message ?: "Erro SEC")
            }
            delay(HttpConfig.SEC_MIN_INTERVAL_MS)
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

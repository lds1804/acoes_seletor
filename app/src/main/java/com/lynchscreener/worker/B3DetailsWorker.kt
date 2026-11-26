package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lynchscreener.data.remote.FundamentusClient
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.parser.FundamentusParser
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.domain.model.Market
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Slow queue: consumes the 15 least-recently-updated B3 tickers, enriches each
 * via detalhes.php with a 1.5–2.0s delay, checkpointing `syncStatus` after
 * every ticker so progress survives process death.
 */
class B3DetailsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val client: FundamentusClient,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val batch = repository.nextB3DetailsBatch()
        val already = repository.countWithFundamentals(Market.BR)
        SyncEventLog.log("B3 Detalhes: lote de ${batch.size} (já com detalhes: $already)")
        var done = 0

        for (asset in batch) {
            done++
            setProgress(workDataOf("done" to done, "total" to (already + batch.size)))
            repository.setSyncing(asset.ticker)
            try {
                val html = client.fetchDetails(asset.ticker)
                val details = FundamentusParser.parseDetails(html)
                repository.applyB3Details(asset.ticker, details)
                repository.setSynced(asset.ticker)
            } catch (e: Exception) {
                repository.markError(asset.ticker, e.message ?: "Erro ao detalhar")
            }
            delay(Random.nextLong(HttpConfig.FUNDAMENTUS_DETAIL_MIN_MS, HttpConfig.FUNDAMENTUS_DETAIL_MAX_MS))
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

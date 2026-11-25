package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.PriceClient
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.domain.model.Market
import kotlinx.coroutines.delay

/**
 * Fast queue: refresh the most stale quotes across both markets via
 * Yahoo (Stooq fallback) and recompute P/L, P/VP, Market Cap, DY and PEGY
 * locally whenever the price moves.
 */
class PriceRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
    private val client: PriceClient,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        for (market in listOf(Market.BR, Market.US)) {
            val batch = repository.nextPriceBatch(market)
            for (asset in batch) {
                repository.setSyncing(asset.ticker)
                try {
                    val quote = client.fetchPrice(asset.ticker, market)
                    if (quote != null) {
                        repository.applyPrice(asset.ticker, market, quote.price, quote.timestampSec)
                        repository.setSynced(asset.ticker)
                    } else {
                        repository.markError(asset.ticker, "Sem cotação disponível")
                    }
                } catch (e: Exception) {
                    repository.markError(asset.ticker, e.message ?: "Erro de cotação")
                }
                delay(HttpConfig.PRICE_MIN_INTERVAL_MS)
            }
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

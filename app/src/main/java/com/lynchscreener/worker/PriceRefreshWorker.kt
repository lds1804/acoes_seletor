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
        var succeeded = 0
        for (market in listOf(Market.BR, Market.US)) {
            val batch = repository.nextPriceBatch(market)
            if (batch.isNotEmpty()) SyncEventLog.log("Cotações ${market.code}: lote de ${batch.size}")
            for (asset in batch) {
                repository.setSyncing(asset.ticker)
                try {
                    val quote = client.fetchPrice(asset.ticker, market)
                    if (quote != null) {
                        repository.applyPrice(asset.ticker, market, quote.price, quote.timestampSec)
                        repository.setSynced(asset.ticker)
                        succeeded++
                    } else {
                        repository.markError(asset.ticker, "Sem cotação disponível")
                    }
                } catch (e: Exception) {
                    repository.markError(asset.ticker, e.message ?: "Erro de cotação")
                }
                delay(HttpConfig.PRICE_MIN_INTERVAL_MS)
            }
        }

        // Self-chain while some tickers still lack a price.
        val pending = repository.countPendingPrices()
        if (succeeded > 0 && pending > 0) {
            SyncEventLog.log("Cotações: $pending restantes — enfileirando próximo lote")
            SyncScheduler.continuePrices(applicationContext)
        }

        Result.success()
    } catch (e: Exception) {
        SyncEventLog.log("Cotações: falha — ${e.message ?: e.javaClass.simpleName}")
        Result.retry()
    }
}

package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.remote.PriceClient
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.domain.model.Market

/**
 * On-demand refresh of a single ticker (used by the "Atualizar esta ação agora"
 * button). Reads the ticker/market from input data.
 */
class RefreshSingleWorker(
    appContext: Context,
    params: WorkerParameters,
    private val client: PriceClient,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ticker = inputData.getString(KEY_TICKER) ?: return Result.failure()
        val market = Market.fromCode(inputData.getString(KEY_MARKET) ?: Market.BR.code)

        repository.setSyncing(ticker)
        return try {
            val quote = client.fetchPrice(ticker, market)
            if (quote != null) {
                repository.applyPrice(ticker, market, quote.price, quote.timestampSec)
                repository.setSynced(ticker)
                Result.success()
            } else {
                repository.markError(ticker, "Sem cotação disponível")
                Result.failure()
            }
        } catch (e: Exception) {
            repository.markError(ticker, e.message ?: "Erro de cotação")
            Result.failure()
        }
    }

    companion object {
        const val KEY_TICKER = "ticker"
        const val KEY_MARKET = "market"
    }
}

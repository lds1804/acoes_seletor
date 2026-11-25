package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lynchscreener.data.repository.AssetRepository

/**
 * One-time seed: loads `sp500_universe.json` from assets and upserts the
 * US universe (ticker, name, cik, sector). Idempotent.
 */
class SeedUniverseWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        repository.seedUsUniverse(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

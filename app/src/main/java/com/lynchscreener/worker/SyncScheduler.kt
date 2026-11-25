package com.lynchscreener.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lynchscreener.data.repository.SyncSettings
import com.lynchscreener.domain.model.Market
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic + on-demand WorkManager chains with exponential
 * backoff and the Wi-Fi / battery constraints from [SyncSettings].
 */
object SyncScheduler {

    fun scheduleAll(context: Context, settings: SyncSettings) {
        val wm = WorkManager.getInstance(context)

        // US universe seed (one-time, idempotent).
        wm.enqueueUniqueWork(
            "seed_us",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SeedUniverseWorker>()
                .setConstraints(SyncConstraints.default())
                .build()
        )

        // B3 full table — daily.
        wm.enqueueUniquePeriodicWork(
            "b3_table",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<B3TableWorker>(24, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.default())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
        )

        // B3 details — slow queue, every 3h.
        wm.enqueueUniquePeriodicWork(
            "b3_details",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<B3DetailsWorker>(3, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.slowQueue(settings.wifiOnly, settings.chargingOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
        )

        // SEC balances — slow queue, every 6h.
        wm.enqueueUniquePeriodicWork(
            "sec_balances",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SecBalancesWorker>(6, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.slowQueue(settings.wifiOnly, settings.chargingOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
        )

        // Prices — every 6h.
        wm.enqueueUniquePeriodicWork(
            "prices",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.priceQueue(settings.wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
        )
    }

    /** On-demand chain: table -> details -> balances -> prices. */
    fun triggerNow(context: Context) {
        val constraints = SyncConstraints.default()
        val table = OneTimeWorkRequestBuilder<B3TableWorker>().setConstraints(constraints).build()
        val details = OneTimeWorkRequestBuilder<B3DetailsWorker>().setConstraints(constraints).build()
        val sec = OneTimeWorkRequestBuilder<SecBalancesWorker>().setConstraints(constraints).build()
        val price = OneTimeWorkRequestBuilder<PriceRefreshWorker>().setConstraints(constraints).build()

        WorkManager.getInstance(context)
            .beginUniqueWork("manual_sync", ExistingWorkPolicy.REPLACE, table)
            .then(details)
            .then(sec)
            .then(price)
            .enqueue()
    }

    /** On-demand refresh of a single ticker ("Atualizar esta ação agora"). */
    fun triggerSingle(context: Context, ticker: String, market: Market) {
        val input = Data.Builder()
            .putString(RefreshSingleWorker.KEY_TICKER, ticker)
            .putString(RefreshSingleWorker.KEY_MARKET, market.code)
            .build()

        val request = OneTimeWorkRequestBuilder<RefreshSingleWorker>()
            .setConstraints(SyncConstraints.default())
            .setInputData(input)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("refresh_$ticker", ExistingWorkPolicy.REPLACE, request)
    }
}

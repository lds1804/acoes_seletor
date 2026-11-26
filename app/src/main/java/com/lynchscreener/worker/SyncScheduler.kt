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
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )

        // B3 full table — daily.
        wm.enqueueUniquePeriodicWork(
            "b3_table",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<B3TableWorker>(24, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.default())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )

        // B3 details — slow queue, every 3h.
        wm.enqueueUniquePeriodicWork(
            "b3_details",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<B3DetailsWorker>(3, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.slowQueue(settings.wifiOnly, settings.chargingOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )

        // SEC balances — slow queue, every 6h.
        wm.enqueueUniquePeriodicWork(
            "sec_balances",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SecBalancesWorker>(6, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.slowQueue(settings.wifiOnly, settings.chargingOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )

        // Prices — every 6h.
        wm.enqueueUniquePeriodicWork(
            "prices",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.priceQueue(settings.wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )

        // CVM earnings history — weekly (only re-downloads when new tickers appear).
        wm.enqueueUniquePeriodicWork(
            "cvm_earnings",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CvmEarningsWorker>(7, TimeUnit.DAYS)
                .setConstraints(SyncConstraints.slowQueue(settings.wifiOnly, settings.chargingOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )
    }

    /** On-demand chain: table -> details -> balances -> prices. */
    fun triggerNow(context: Context) {
        SyncEventLog.log("Sincronização manual disparada")
        val constraints = SyncConstraints.default()
        val table = OneTimeWorkRequestBuilder<B3TableWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val details = OneTimeWorkRequestBuilder<B3DetailsWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val sec = OneTimeWorkRequestBuilder<SecBalancesWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val price = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val cvm = OneTimeWorkRequestBuilder<CvmEarningsWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()

        WorkManager.getInstance(context)
            .beginUniqueWork("manual_sync", ExistingWorkPolicy.REPLACE, table)
            .then(details)
            .then(sec)
            .then(cvm)
            .then(price)
            .enqueue()
    }

    /** On-demand refresh of a single ticker ("Atualizar esta ação agora"). */
    fun triggerSingle(context: Context, ticker: String, market: Market) {
        SyncEventLog.log("Atualização individual de $ticker")
        val input = Data.Builder()
            .putString(RefreshSingleWorker.KEY_TICKER, ticker)
            .putString(RefreshSingleWorker.KEY_MARKET, market.code)
            .build()

        val request = OneTimeWorkRequestBuilder<RefreshSingleWorker>()
            .setConstraints(SyncConstraints.default())
            .setInputData(input)
            .addTag(SyncMonitor.SYNC_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("refresh_$ticker", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Dispara a primeira sincronização assim que o app é instalado/aberto pela
     * primeira vez, para que o banco não fique vazio à espera dos workers
     * periódicos (24h/3h/6h). Idempotente via SharedPreferences.
     */
    fun triggerInitialIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
        if (prefs.getBoolean(INITIAL_SYNC_KEY, false)) return
        prefs.edit().putBoolean(INITIAL_SYNC_KEY, true).apply()

        SyncEventLog.log("Primeira execução: disparando sincronização inicial")
        val constraints = SyncConstraints.default()
        val seed = OneTimeWorkRequestBuilder<SeedUniverseWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val table = OneTimeWorkRequestBuilder<B3TableWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val details = OneTimeWorkRequestBuilder<B3DetailsWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val sec = OneTimeWorkRequestBuilder<SecBalancesWorker>()
        val cvm = OneTimeWorkRequestBuilder<CvmEarningsWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()

        WorkManager.getInstance(context)
            .beginUniqueWork("initial_sync", ExistingWorkPolicy.KEEP, seed)
            .then(table)
            .then(details)   // sector first (CVM/PEGY need it)
            .then(price)     // prices so SEC/CVM can compute ratios
            .then(sec)
            .then(cvm)       // earnings history last (heaviest downloadle)
            .then(details)
            .then(price)   // prices first so SEC can compute P/L, P/VP, DY
            .then(sec)
            .enqueue()
    }

    private const val INITIAL_SYNC_KEY = "initial_sync_done_v2"

    // -----------------------------------------------------------------
    // Self-chaining continuations: each worker appends the next batch of the
    // same queue when there is still pending data, so a fresh install catches
    // up in minutes instead of waiting for the next periodic window (3h/6h).
    // -----------------------------------------------------------------

    fun continueB3Details(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAIN_B3_DETAILS,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<B3DetailsWorker>()
                .setConstraints(SyncConstraints.default())
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )
    }

    fun continueSec(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAIN_SEC,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SecBalancesWorker>()
                .setConstraints(SyncConstraints.default())
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )
    }

    fun continuePrices(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAIN_PRICES,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(SyncConstraints.default())
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )
    }

    /** Next DFP package of the CVM earnings history (one year per run). */
    fun continueCvm(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHAIN_CVM,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<CvmEarningsWorker>()
                .setConstraints(SyncConstraints.default())
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )
    }

    private const val CHAIN_B3_DETAILS = "chain_b3_details"
    private const val CHAIN_SEC = "chain_sec"
    private const val CHAIN_PRICES = "chain_prices"
    private const val CHAIN_CVM = "chain_cvm"
}

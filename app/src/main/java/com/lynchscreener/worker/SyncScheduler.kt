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

        // B3 details — light enrichment, every 3h, any connection.
        wm.enqueueUniquePeriodicWork(
            "b3_details",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<B3DetailsWorker>(3, TimeUnit.HOURS)
                .setConstraints(SyncConstraints.lightQueue())
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

    /**
     * On-demand chain, triggered by the user tapping "Sincronizar agora".
     *
     * Runs with [SyncConstraints.userInitiated] instead of the background profile:
     * a tap is explicit consent, and silently deferring it behind wifi/charging
     * rules is what made the button look broken. The CVM download is the one
     * exception — it stays Wi-Fi-only because of the volume.
     */
    fun triggerNow(context: Context) {
        SyncEventLog.log("Sincronização manual disparada")
        val constraints = SyncConstraints.userInitiated()
        val table = OneTimeWorkRequestBuilder<B3TableWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val details = OneTimeWorkRequestBuilder<B3DetailsWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val sec = OneTimeWorkRequestBuilder<SecBalancesWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val price = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val cvm = OneTimeWorkRequestBuilder<CvmEarningsWorker>()
            .setConstraints(SyncConstraints.heavyDownload()).addTag(SyncMonitor.SYNC_TAG).build()

        WorkManager.getInstance(context)
            .beginUniqueWork("manual_sync", ExistingWorkPolicy.REPLACE, table)
            .then(details)
            .then(price)   // SEC precisa do preço para P/L, P/VP e DY
            .then(sec)
            .then(cvm)
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
     * periódicos (24h/3h/6h).
     *
     * Self-healing: the "done" flag alone is not trusted. If it says done but the
     * database has no prices, the previous chain was cancelled mid-way (battery
     * died, Wi-Fi lost, job killed) and the app would stay half-populated forever
     * — which is exactly what happened on device, where every job had been
     * cancelled by `BATTERY_NOT_LOW` after the flag was already set.
     *
     * @param dataPresent true when the B3 universe already has prices.
     */
    fun triggerInitialIfNeeded(context: Context, dataPresent: Boolean = false) {
        val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
        val flagged = prefs.getBoolean(INITIAL_SYNC_KEY, false)
        if (flagged && dataPresent) return

        val retrying = flagged
        if (retrying) {
            SyncEventLog.log("Sincronização inicial incompleta — retomando")
        } else {
            SyncEventLog.log("Primeira execução: disparando sincronização inicial")
        }
        prefs.edit().putBoolean(INITIAL_SYNC_KEY, true).apply()

        val constraints = SyncConstraints.default()
        val seed = OneTimeWorkRequestBuilder<SeedUniverseWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val table = OneTimeWorkRequestBuilder<B3TableWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val details = OneTimeWorkRequestBuilder<B3DetailsWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val price = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val sec = OneTimeWorkRequestBuilder<SecBalancesWorker>()
            .setConstraints(constraints).addTag(SyncMonitor.SYNC_TAG).build()
        val cvm = OneTimeWorkRequestBuilder<CvmEarningsWorker>()
            .setConstraints(SyncConstraints.heavyDownload()).addTag(SyncMonitor.SYNC_TAG).build()

        WorkManager.getInstance(context)
            .beginUniqueWork(
                "initial_sync",
                // A retry must replace the dead chain: the old one is still holding
                // the unique name while its last step waits for Wi-Fi forever.
                if (retrying) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                seed,
            )
            .then(table)      // universo da B3 (tickers + múltiplos)
            .then(details)    // nome/setor/caixa (necessário para o score)
            .then(price)      // cotações para SEC/CVM calcularem ratios
            .then(sec)        // fundamentos dos EUA (SEC EDGAR)
            .then(cvm)        // histórico de lucro da CVM (download mais pesado)
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
                .setConstraints(SyncConstraints.lightQueue())
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
                .setConstraints(SyncConstraints.heavyDownload())
                .addTag(SyncMonitor.SYNC_TAG)
                .build()
        )
    }

    private const val CHAIN_B3_DETAILS = "chain_b3_details"
    private const val CHAIN_SEC = "chain_sec"
    private const val CHAIN_PRICES = "chain_prices"
    private const val CHAIN_CVM = "chain_cvm"
}

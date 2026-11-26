package com.lynchscreener

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.lynchscreener.di.AppContainer
import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Market
import com.lynchscreener.worker.LynchWorkerFactory
import com.lynchscreener.worker.SyncEventLog
import com.lynchscreener.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Builds the manual DI container ([AppContainer]) and initializes WorkManager
 * with a custom [LynchWorkerFactory] so workers receive their dependencies.
 * The default initializer is disabled in AndroidManifest.xml.
 */
class LynchScreenerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(LynchWorkerFactory(container))
                .build()
        )

        SyncScheduler.scheduleAll(this, container.syncSettings)
        startInitialSyncIfNeeded()
        migrateScoringIfNeeded()
    }

    /**
     * Triggers the first sync only when the database is actually empty.
     *
     * The check is what makes the recovery work: a chain whose jobs were all
     * cancelled (battery, no Wi-Fi, Doze) leaves the "done" flag set with an empty
     * table, and the app would never try again.
     */
    private fun startInitialSyncIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            val brWithPrice = runCatching {
                container.repository.countWithPrice(Market.BR)
            }.getOrDefault(0)
            SyncScheduler.triggerInitialIfNeeded(this@LynchScreenerApp, dataPresent = brWithPrice > 0)
        }
    }

    /**
     * Re-scores everything persisted by an older engine version.
     *
     * Rule changes (PEGY cap, score ceilings, bank/insurer exception) only reach
     * new data otherwise, leaving the ranking with numbers produced by rules that
     * no longer exist. Runs in the background; the portfolio table is untouched.
     */
    private fun migrateScoringIfNeeded() {
        val prefs = getSharedPreferences("sync_state", MODE_PRIVATE)
        val stored = prefs.getInt(KEY_SCORING_VERSION, 0)
        val current = LynchScoringEngine.SCORING_ENGINE_VERSION
        if (stored >= current) return

        CoroutineScope(Dispatchers.IO).launch {
            val rescored = runCatching { container.repository.rescoreAll() }.getOrNull()
            if (rescored == null) return@launch
            SyncEventLog.log("Motor de score v$current: $rescored ativos recalculados")
            prefs.edit().putInt(KEY_SCORING_VERSION, current).apply()
        }
    }

    private companion object {
        const val KEY_SCORING_VERSION = "scoring_engine_version"
    }
}

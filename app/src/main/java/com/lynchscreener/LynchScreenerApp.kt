package com.lynchscreener

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.lynchscreener.di.AppContainer
import com.lynchscreener.worker.LynchWorkerFactory
import com.lynchscreener.worker.SyncScheduler

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
    }
}

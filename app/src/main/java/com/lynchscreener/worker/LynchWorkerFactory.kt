package com.lynchscreener.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.lynchscreener.di.AppContainer

/**
 * Constructs workers with their dependencies from [AppContainer], so workers
 * never need a ServiceLocator or reflection-based DI.
 */
class LynchWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        B3TableWorker::class.java.name ->
            B3TableWorker(appContext, workerParameters, container.fundamentusClient, container.repository)

        B3DetailsWorker::class.java.name ->
            B3DetailsWorker(appContext, workerParameters, container.fundamentusClient, container.repository)

        SecBalancesWorker::class.java.name ->
            SecBalancesWorker(appContext, workerParameters, container.secClient, container.repository)

        PriceRefreshWorker::class.java.name ->
            PriceRefreshWorker(appContext, workerParameters, container.priceClient, container.repository)

        SeedUniverseWorker::class.java.name ->
            SeedUniverseWorker(appContext, workerParameters, container.repository)

        RefreshSingleWorker::class.java.name ->
            RefreshSingleWorker(appContext, workerParameters, container.priceClient, container.repository)

        CvmEarningsWorker::class.java.name ->
            CvmEarningsWorker(appContext, workerParameters, container.cvmClient, container.repository)

        else -> null
    }
}

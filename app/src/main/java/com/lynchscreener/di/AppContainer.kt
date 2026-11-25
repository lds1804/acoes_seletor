package com.lynchscreener.di

import android.content.Context
import androidx.room.Room
import com.lynchscreener.data.local.AppDatabase
import com.lynchscreener.data.remote.FundamentusClient
import com.lynchscreener.data.remote.PriceClient
import com.lynchscreener.data.remote.SecClient
import com.lynchscreener.data.repository.AssetRepository
import com.lynchscreener.data.repository.SyncSettings
import com.lynchscreener.domain.LynchScoringEngine
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Lightweight manual dependency graph. No framework, no codegen — plain
 * `by lazy` singletons. Exposes everything the workers and (in Etapa 3) the
 * ViewModels need.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val scoringEngine: LynchScoringEngine by lazy { LynchScoringEngine() }

    val fundamentusClient: FundamentusClient by lazy { FundamentusClient(okHttp) }
    val secClient: SecClient by lazy { SecClient(okHttp) }
    val priceClient: PriceClient by lazy { PriceClient(okHttp) }

    val repository: AssetRepository by lazy {
        AssetRepository(database.assetDao(), database.fundamentalsDao(), scoringEngine)
    }

    val syncSettings: SyncSettings by lazy { SyncSettings(appContext) }
}

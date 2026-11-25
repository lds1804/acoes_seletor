package com.lynchscreener.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Universe of tracked companies (B3 and S&P 500).
 *
 * [ticker] is the raw exchange symbol (e.g. "PETR4", "AAPL") and is the primary key.
 * [market] is "BR" or "US" — see [com.lynchscreener.domain.model.Market].
 */
@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["market"]),
        Index(value = ["syncStatus"]),
    ]
)
data class AssetEntity(
    @PrimaryKey val ticker: String,
    val market: String,
    val name: String?,
    val sector: String?,
    val subsector: String?,
    /** SEC Central Index Key (10 digits). Null for Brazilian tickers. */
    val cik: String?,
    val price: Double?,
    val marketCap: Double?,
    /** Epoch millis of the last successful price refresh. */
    val priceUpdatedAt: Long?,
    /** Epoch millis of the last successful fundamentals refresh. */
    val fundamentalsUpdatedAt: Long?,
    /** One of [SyncStatus]. */
    val syncStatus: String = SyncStatus.PENDING,
    val lastError: String?,
) {
    object SyncStatus {
        const val PENDING = "PENDING"
        const val SYNCING = "SYNCING"
        const val SYNCED = "SYNCED"
        const val ERROR = "ERROR"
    }
}

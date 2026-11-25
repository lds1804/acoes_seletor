package com.lynchscreener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lynchscreener.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assets: List<AssetEntity>)

    @Query("SELECT * FROM assets WHERE ticker = :ticker")
    suspend fun getByTicker(ticker: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE market = :market ORDER BY ticker")
    fun observeByMarket(market: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE market = :market")
    suspend fun getByMarket(market: String): List<AssetEntity>

    /**
     * Next batch for the slow fundamentals queue: least-recently-updated first,
     * excluding the ones currently being synced. This is the checkpoint cursor
     * used by the incremental [androidx.work.CoroutineWorker]s (Etapa 2).
     */
    @Query(
        """
        SELECT * FROM assets
        WHERE market = :market
          AND syncStatus != 'SYNCING'
        ORDER BY fundamentalsUpdatedAt IS NULL DESC, fundamentalsUpdatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun nextFundamentalsBatch(market: String, limit: Int): List<AssetEntity>

    /** Next batch for the fast price queue. */
    @Query(
        """
        SELECT * FROM assets
        WHERE market = :market
          AND syncStatus != 'SYNCING'
        ORDER BY priceUpdatedAt IS NULL DESC, priceUpdatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun nextPriceBatch(market: String, limit: Int): List<AssetEntity>

    @Query("UPDATE assets SET syncStatus = :status WHERE ticker = :ticker")
    suspend fun setSyncStatus(ticker: String, status: String)

    @Query("UPDATE assets SET lastError = :error, syncStatus = 'ERROR' WHERE ticker = :ticker")
    suspend fun markError(ticker: String, error: String)

    @Query(
        """
        UPDATE assets
        SET price = :price, marketCap = :marketCap, priceUpdatedAt = :now,
            syncStatus = 'SYNCED', lastError = NULL
        WHERE ticker = :ticker
        """
    )
    suspend fun updatePrice(ticker: String, price: Double, marketCap: Double?, now: Long)

    @Query("DELETE FROM assets WHERE market = :market")
    suspend fun deleteByMarket(market: String)

    @Query("SELECT COUNT(*) FROM assets WHERE market = :market")
    suspend fun countByMarket(market: String): Int
}

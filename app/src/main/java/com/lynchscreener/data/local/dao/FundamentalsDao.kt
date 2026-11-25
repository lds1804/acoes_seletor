package com.lynchscreener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lynchscreener.data.local.entity.FundamentalsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FundamentalsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fundamentals: FundamentalsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(fundamentals: List<FundamentalsEntity>)

    @Query("SELECT * FROM fundamentals WHERE ticker = :ticker")
    suspend fun getByTicker(ticker: String): FundamentalsEntity?

    @Query("SELECT * FROM fundamentals WHERE ticker = :ticker")
    fun observeByTicker(ticker: String): Flow<FundamentalsEntity?>

    @Query("SELECT * FROM fundamentals WHERE ticker IN (:tickers)")
    suspend fun getByTickers(tickers: List<String>): List<FundamentalsEntity>

    /** Invalidate existing ranks before recomputing (Etapa 3). */
    @Query("UPDATE fundamentals SET overallRank = NULL, sectorRank = NULL")
    suspend fun clearRanks()

    @Query("DELETE FROM fundamentals WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)
}

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

    /**
     * Inserts only the rows that do not exist yet (IGNORE on conflict), so a
     * re-seed never overwrites already-collected fundamentals.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(fundamentals: List<FundamentalsEntity>)

    @Query("SELECT * FROM fundamentals WHERE ticker = :ticker")
    suspend fun getByTicker(ticker: String): FundamentalsEntity?

    @Query("SELECT * FROM fundamentals WHERE ticker = :ticker")
    fun observeByTicker(ticker: String): Flow<FundamentalsEntity?>

    @Query("SELECT * FROM fundamentals WHERE ticker IN (:tickers)")
    suspend fun getByTickers(tickers: List<String>): List<FundamentalsEntity>

    /** Every fundamentals row, used by the scoring-engine migration (re-score all). */
    @Query("SELECT * FROM fundamentals")
    suspend fun getAll(): List<FundamentalsEntity>

    /**
     * Clears a CVM history that predates the parent-company basis, forcing the
     * CVM worker to re-ingest it (the JSON has no way to tell which shareholder
     * group the numbers belong to).
     */
    @Query(
        """
        UPDATE fundamentals
        SET earningsHistoryJson = NULL
        WHERE earningsHistoryJson IS NOT NULL AND earningsBasis IS NULL
          AND ticker IN (SELECT ticker FROM assets WHERE market = 'BR')
        """
    )
    suspend fun resetPreBasisEarnings(): Int

    /** Invalidate existing ranks before recomputing (Etapa 3). */
    @Query("UPDATE fundamentals SET overallRank = NULL, sectorRank = NULL")
    suspend fun clearRanks()

    @Query("DELETE FROM fundamentals WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)
}

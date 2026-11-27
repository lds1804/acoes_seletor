package com.lynchscreener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lynchscreener.data.local.entity.AssetEntity
import com.lynchscreener.domain.model.RankedCompany
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

    /** Every asset, used by the scoring-engine migration (re-score all). */
    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<AssetEntity>

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

    /**
     * Next US batch for the SEC balances queue. Only returns companies whose
     * fundamentals are stale (older than the [cutoff] timestamp), so we skip
     * the ones refreshed recently (balances change only quarterly).
     */
    @Query(
        """
        SELECT * FROM assets
        WHERE market = 'US'
          AND (fundamentalsUpdatedAt IS NULL OR fundamentalsUpdatedAt < :cutoff)
          AND syncStatus != 'SYNCING'
        ORDER BY fundamentalsUpdatedAt IS NULL DESC, fundamentalsUpdatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun nextSecBalanceBatch(cutoff: Long, limit: Int): List<AssetEntity>

    @Query("SELECT COUNT(*) FROM assets WHERE market = :market AND fundamentalsUpdatedAt IS NOT NULL")
    suspend fun countWithFundamentals(market: String): Int

    /** Assets still missing fundamentals (drives the self-chaining workers). */
    @Query("SELECT COUNT(*) FROM assets WHERE market = :market AND fundamentalsUpdatedAt IS NULL")
    suspend fun countPendingFundamentals(market: String): Int

    /** Assets still missing a price. */
    @Query("SELECT COUNT(*) FROM assets WHERE priceUpdatedAt IS NULL")
    suspend fun countPendingPrices(): Int

    /**
     * B3 tickers whose CVM earnings history was not ingested yet.
     *
     * The INNER JOIN is essential: the history is written into `fundamentals`,
     * so an asset without that row could never be marked as processed and would
     * stay pending forever (the worker self-chains, so that would loop).
     *
     * `quarterlyJson IS NULL` means the ITR packages were never read for this
     * company — either because the install predates the quarterly series or
     * because the run stopped halfway. `"{}"` is written once the attempt is
     * made, so a company without ITR filings stops being pending instead of
     * looping forever.
     */
    @Query(
        """
        SELECT a.ticker FROM assets a
        INNER JOIN fundamentals f ON a.ticker = f.ticker
        WHERE a.market = 'BR'
          AND (f.earningsHistoryJson IS NULL OR f.quarterlyJson IS NULL)
        """
    )
    suspend fun pendingEarningsTickers(): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM assets a
        INNER JOIN fundamentals f ON a.ticker = f.ticker
        WHERE a.market = 'BR'
          AND (f.earningsHistoryJson IS NULL OR f.quarterlyJson IS NULL)
        """
    )
    suspend fun countPendingEarnings(): Int

    @Query("SELECT COUNT(*) FROM assets WHERE market = :market AND priceUpdatedAt IS NOT NULL")
    suspend fun countWithPrice(market: String): Int

    /**
     * Companies with fundamentals, ordered by Lynch score descending.
     * The [RankedCompany] projection feeds the ranking screen; overall and
     * sector positions are computed in [com.lynchscreener.domain.Ranking].
     */
    @Query(
        """
        SELECT a.ticker AS ticker, a.market AS market, a.name AS name,
               a.sector AS sector, a.subsector AS subsector,
               a.price AS price, a.marketCap AS marketCap,
               f.pe AS pe, f.pb AS pb, f.roe AS roe, f.roic AS roic,
               f.dividendYield AS dividendYield, f.cagr5y AS cagr5y,
               f.pegy AS pegy, f.netDebt AS netDebt,
               f.netCashToMarketCap AS netCashToMarketCap,
               f.equity AS equity, f.netIncome12m AS netIncome12m,
               f.sharesOutstanding AS sharesOutstanding,
               f.dividendPerShare AS dividendPerShare,
               f.liquidity AS liquidity,
               f.lynchScore AS lynchScore, f.lynchCategory AS lynchCategory,
               f.breakdownJson AS breakdownJson,
               f.earningsCagr5y AS earningsCagr5y,
               f.earningsConsistent AS earningsConsistent,
               f.earningsHistoryJson AS earningsHistoryJson,
               f.earningsBasis AS earningsBasis,
               f.earningsYoy AS earningsYoy,
               f.ttmNetIncome AS ttmNetIncome,
               f.ttmPrevious AS ttmPrevious,
               f.ttmQuarter AS ttmQuarter,
               f.alertFlags AS alertFlags,
               a.priceUpdatedAt AS priceUpdatedAt,
               a.fundamentalsUpdatedAt AS fundamentalsUpdatedAt
        FROM assets a
        INNER JOIN fundamentals f ON a.ticker = f.ticker
        WHERE a.market = :market
        ORDER BY f.lynchScore DESC, a.ticker ASC
        """
    )
    fun observeRanked(market: String): Flow<List<RankedCompany>>

    @Query(
        """
        SELECT a.ticker AS ticker, a.market AS market, a.name AS name,
               a.sector AS sector, a.subsector AS subsector,
               a.price AS price, a.marketCap AS marketCap,
               f.pe AS pe, f.pb AS pb, f.roe AS roe, f.roic AS roic,
               f.dividendYield AS dividendYield, f.cagr5y AS cagr5y,
               f.pegy AS pegy, f.netDebt AS netDebt,
               f.netCashToMarketCap AS netCashToMarketCap,
               f.equity AS equity, f.netIncome12m AS netIncome12m,
               f.sharesOutstanding AS sharesOutstanding,
               f.dividendPerShare AS dividendPerShare,
               f.liquidity AS liquidity,
               f.lynchScore AS lynchScore, f.lynchCategory AS lynchCategory,
               f.breakdownJson AS breakdownJson,
               f.earningsCagr5y AS earningsCagr5y,
               f.earningsConsistent AS earningsConsistent,
               f.earningsHistoryJson AS earningsHistoryJson,
               f.earningsBasis AS earningsBasis,
               f.earningsYoy AS earningsYoy,
               f.ttmNetIncome AS ttmNetIncome,
               f.ttmPrevious AS ttmPrevious,
               f.ttmQuarter AS ttmQuarter,
               f.alertFlags AS alertFlags,
               a.priceUpdatedAt AS priceUpdatedAt,
               a.fundamentalsUpdatedAt AS fundamentalsUpdatedAt
        FROM assets a
        INNER JOIN fundamentals f ON a.ticker = f.ticker
        WHERE a.ticker = :ticker
        LIMIT 1
        """
    )
    fun observeRankedByTicker(ticker: String): Flow<RankedCompany?>

    /** Tickers currently in ERROR state, for the sync screen error log. */
    @Query("SELECT * FROM assets WHERE syncStatus = 'ERROR' ORDER BY ticker LIMIT 50")
    fun observeErrors(): Flow<List<AssetEntity>>
}

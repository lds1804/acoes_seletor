package com.lynchscreener.data.repository

import android.content.Context
import com.lynchscreener.data.local.dao.AssetDao
import com.lynchscreener.data.local.dao.FundamentalsDao
import com.lynchscreener.data.local.entity.AssetEntity
import com.lynchscreener.data.local.entity.FundamentalsEntity
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.dto.B3Details
import com.lynchscreener.data.remote.dto.B3TableRow
import com.lynchscreener.data.remote.dto.SecFundamentals
import com.lynchscreener.data.remote.dto.Sp500Entry
import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.ScoreComponent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single entry point for merging scraped data into Room, recomputing the
 * Lynch score, and driving the incremental queues used by the workers.
 */
class AssetRepository(
    private val assetDao: AssetDao,
    private val fundamentalsDao: FundamentalsDao,
    private val engine: LynchScoringEngine,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // -----------------------------------------------------------------
    // Queue cursors (checkpointing by timestamp)
    // -----------------------------------------------------------------

    suspend fun nextB3DetailsBatch(): List<AssetEntity> =
        assetDao.nextFundamentalsBatch(Market.BR.code, HttpConfig.B3_DETAILS_BATCH)

    suspend fun nextSecBalanceBatch(): List<AssetEntity> =
        assetDao.nextSecBalanceBatch(HttpConfig.SEC_STALENESS_MS, HttpConfig.SEC_BALANCE_BATCH)

    suspend fun nextPriceBatch(market: Market): List<AssetEntity> =
        assetDao.nextPriceBatch(market.code, HttpConfig.PRICE_BATCH)

    suspend fun setSyncing(ticker: String) =
        assetDao.setSyncStatus(ticker, AssetEntity.SyncStatus.SYNCING)

    suspend fun setSynced(ticker: String) =
        assetDao.setSyncStatus(ticker, AssetEntity.SyncStatus.SYNCED)

    suspend fun markError(ticker: String, message: String) =
        assetDao.markError(ticker, message)

    /** Number of tickers in [market] that already have fundamentals (for progress). */
    suspend fun countWithFundamentals(market: Market): Int =
        assetDao.countWithFundamentals(market.code)

    /** Number of tickers in [market] that already have a price (for progress). */
    suspend fun countWithPrice(market: Market): Int =
        assetDao.countWithPrice(market.code)

    // -----------------------------------------------------------------
    // Seed US universe from bundled assets
    // -----------------------------------------------------------------

    suspend fun seedUsUniverse(context: Context) {
        val text = runCatching {
            context.assets.open("sp500_universe.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

        val entries = runCatching { json.decodeFromString<List<Sp500Entry>>(text) }.getOrNull() ?: return
        if (entries.isEmpty()) return

        assetDao.upsertAll(
            entries.map { entry ->
                AssetEntity(
                    ticker = entry.ticker,
                    market = Market.US.code,
                    name = entry.name,
                    sector = entry.sector,
                    subsector = null,
                    cik = entry.cik,
                    price = null,
                    marketCap = null,
                    priceUpdatedAt = null,
                    fundamentalsUpdatedAt = null,
                    syncStatus = AssetEntity.SyncStatus.PENDING,
                    lastError = null,
                )
            }
        )
    }

    // -----------------------------------------------------------------
    // B3: full table (fast queue)
    // -----------------------------------------------------------------

    suspend fun replaceB3Table(rows: List<B3TableRow>): Int {
        val valid = rows.filter { it.liquidity != null && it.liquidity >= HttpConfig.MIN_B3_LIQUIDITY }
        if (valid.isEmpty()) return 0

        val tickers = valid.map { it.ticker }
        val prevAssets = assetDao.getByMarket(Market.BR.code).associateBy { it.ticker }
        val prevFunds = fundamentalsDao.getByTickers(tickers).associateBy { it.ticker }
        val now = System.currentTimeMillis()

        assetDao.upsertAll(
            valid.map { row ->
                val prev = prevAssets[row.ticker]
                AssetEntity(
                    ticker = row.ticker,
                    market = Market.BR.code,
                    name = prev?.name,
                    sector = prev?.sector,
                    subsector = prev?.subsector,
                    cik = null,
                    price = row.price,
                    marketCap = prev?.marketCap,
                    priceUpdatedAt = now,
                    fundamentalsUpdatedAt = prev?.fundamentalsUpdatedAt,
                    syncStatus = AssetEntity.SyncStatus.SYNCED,
                    lastError = null,
                )
            }
        )

        fundamentalsDao.upsertAll(
            valid.mapNotNull { row ->
                val prev = prevFunds[row.ticker]
                val financials = Financials(
                    ticker = row.ticker,
                    market = Market.BR,
                    sector = prevAssets[row.ticker]?.sector,
                    price = row.price ?: 0.0,
                    pe = row.pe,
                    pb = row.pb,
                    roe = row.roe,
                    roic = row.roic,
                    dividendYield = row.dividendYield,
                    cagr5y = row.cagr5y,
                    netDebt = prev?.netDebt,
                    totalDebt = null,
                    cashAndEquivalents = null,
                    equity = prev?.equity,
                    netIncome12m = prev?.netIncome12m,
                    sharesOutstanding = prev?.sharesOutstanding,
                    marketCap = prevAssets[row.ticker]?.marketCap,
                    liquidity = row.liquidity,
                )
                val score = engine.score(financials)
                FundamentalsEntity(
                    ticker = row.ticker,
                    pe = row.pe,
                    pb = row.pb,
                    roe = row.roe,
                    roic = row.roic,
                    dividendYield = row.dividendYield,
                    cagr5y = row.cagr5y,
                    pegy = score.pegy,
                    netDebt = prev?.netDebt,
                    netCashToMarketCap = score.netCashToMarketCap,
                    equity = prev?.equity,
                    netIncome12m = prev?.netIncome12m,
                    sharesOutstanding = prev?.sharesOutstanding,
                    dividendPerShare = prev?.dividendPerShare,
                    liquidity = row.liquidity,
                    lynchScore = score.totalScore,
                    lynchCategory = score.category.label,
                    overallRank = null,
                    sectorRank = null,
                    breakdownJson = encodeBreakdown(score.components),
                )
            }
        )
        return valid.size
    }

    // -----------------------------------------------------------------
    // B3: details enrichment (slow queue)
    // -----------------------------------------------------------------

    suspend fun applyB3Details(ticker: String, details: B3Details): Boolean {
        val asset = assetDao.getByTicker(ticker) ?: return false
        val prev = fundamentalsDao.getByTicker(ticker)

        val price = asset.price
        val marketCap = details.marketCap ?: asset.marketCap
        val shares = when {
            price != null && price > 0.0 && marketCap != null -> marketCap / price
            else -> prev?.sharesOutstanding
        }

        val financials = Financials(
            ticker = ticker,
            market = Market.BR,
            sector = details.sector ?: asset.sector,
            price = price ?: 0.0,
            pe = prev?.pe,
            pb = prev?.pb,
            roe = prev?.roe,
            roic = prev?.roic,
            dividendYield = prev?.dividendYield,
            cagr5y = prev?.cagr5y,
            netDebt = details.netDebt ?: prev?.netDebt,
            totalDebt = null,
            cashAndEquivalents = details.cash,
            equity = details.equity ?: prev?.equity,
            netIncome12m = details.netIncome12m ?: prev?.netIncome12m,
            sharesOutstanding = shares,
            marketCap = marketCap,
            liquidity = prev?.liquidity,
        )
        val score = engine.score(financials)

        assetDao.upsert(
            asset.copy(
                sector = details.sector ?: asset.sector,
                subsector = details.subsector ?: asset.subsector,
                marketCap = marketCap,
                fundamentalsUpdatedAt = System.currentTimeMillis(),
            )
        )

        fundamentalsDao.upsert(
            FundamentalsEntity(
                ticker = ticker,
                pe = prev?.pe,
                pb = prev?.pb,
                roe = prev?.roe,
                roic = prev?.roic,
                dividendYield = prev?.dividendYield,
                cagr5y = prev?.cagr5y,
                pegy = score.pegy,
                netDebt = details.netDebt ?: prev?.netDebt,
                netCashToMarketCap = score.netCashToMarketCap,
                equity = details.equity ?: prev?.equity,
                netIncome12m = details.netIncome12m ?: prev?.netIncome12m,
                sharesOutstanding = shares,
                dividendPerShare = prev?.dividendPerShare,
                liquidity = prev?.liquidity,
                lynchScore = score.totalScore,
                lynchCategory = score.category.label,
                overallRank = null,
                sectorRank = null,
                breakdownJson = encodeBreakdown(score.components),
            )
        )
        return true
    }

    // -----------------------------------------------------------------
    // US: SEC balances (slow queue)
    // -----------------------------------------------------------------

    suspend fun applySecFundamentals(ticker: String, sec: SecFundamentals): Boolean {
        val asset = assetDao.getByTicker(ticker) ?: return false
        val prev = fundamentalsDao.getByTicker(ticker)

        val price = asset.price
        val shares = sec.sharesOutstanding ?: prev?.sharesOutstanding
        val marketCap = when {
            price != null && shares != null -> price * shares
            else -> asset.marketCap
        }
        val netIncome = sec.netIncomeHistory.lastOrNull() ?: prev?.netIncome12m
        val equity = sec.equity ?: prev?.equity
        val cash = sec.cash
        val totalDebt = sec.totalDebt
        val netCash = if (cash != null && totalDebt != null) cash - totalDebt else null
        val netDebt = netCash?.let { -it }

        val cagr = cagrFromNetIncome(sec.netIncomeHistory) ?: prev?.cagr5y
        val roe = if (netIncome != null && equity != null && equity != 0.0) netIncome / equity else prev?.roe
        val pe = if (netIncome != null && netIncome > 0.0 && marketCap != null) marketCap / netIncome else prev?.pe
        val pb = if (marketCap != null && equity != null && equity != 0.0) marketCap / equity else prev?.pb
        val dy = if (sec.dividendPerShare != null && price != null && price > 0.0) sec.dividendPerShare / price else prev?.dividendYield

        val financials = Financials(
            ticker = ticker,
            market = Market.US,
            sector = asset.sector,
            price = price ?: 0.0,
            pe = pe,
            pb = pb,
            roe = roe,
            roic = prev?.roic,
            dividendYield = dy,
            cagr5y = cagr,
            netDebt = netDebt,
            totalDebt = totalDebt,
            cashAndEquivalents = cash,
            equity = equity,
            netIncome12m = netIncome,
            sharesOutstanding = shares,
            marketCap = marketCap,
            liquidity = prev?.liquidity,
        )
        val score = engine.score(financials)

        assetDao.upsert(
            asset.copy(
                name = sec.entityName ?: asset.name,
                marketCap = marketCap,
                fundamentalsUpdatedAt = System.currentTimeMillis(),
            )
        )

        fundamentalsDao.upsert(
            FundamentalsEntity(
                ticker = ticker,
                pe = pe,
                pb = pb,
                roe = roe,
                roic = prev?.roic,
                dividendYield = dy,
                cagr5y = cagr,
                pegy = score.pegy,
                netDebt = netDebt,
                netCashToMarketCap = score.netCashToMarketCap,
                equity = equity,
                netIncome12m = netIncome,
                sharesOutstanding = shares,
                dividendPerShare = sec.dividendPerShare ?: prev?.dividendPerShare,
                liquidity = prev?.liquidity,
                lynchScore = score.totalScore,
                lynchCategory = score.category.label,
                overallRank = null,
                sectorRank = null,
                breakdownJson = encodeBreakdown(score.components),
            )
        )
        return true
    }

    // -----------------------------------------------------------------
    // Prices (fast queue, both markets)
    // -----------------------------------------------------------------

    suspend fun applyPrice(ticker: String, market: Market, price: Double, timestampSec: Long?) {
        val asset = assetDao.getByTicker(ticker) ?: return
        val prev = fundamentalsDao.getByTicker(ticker)

        val shares = prev?.sharesOutstanding
        val marketCap = if (shares != null) price * shares else asset.marketCap
        val netIncome = prev?.netIncome12m
        val equity = prev?.equity

        val pe = if (netIncome != null && netIncome > 0.0 && marketCap != null) marketCap / netIncome else prev?.pe
        val pb = if (marketCap != null && equity != null && equity != 0.0) marketCap / equity else prev?.pb
        val dy = if (prev?.dividendPerShare != null && price > 0.0) prev.dividendPerShare / price else prev?.dividendYield

        assetDao.updatePrice(ticker, price, marketCap, System.currentTimeMillis())

        if (prev != null) {
            val financials = Financials(
                ticker = ticker,
                market = market,
                sector = asset.sector,
                price = price,
                pe = pe,
                pb = pb,
                roe = prev.roe,
                roic = prev.roic,
                dividendYield = dy,
                cagr5y = prev.cagr5y,
                netDebt = prev.netDebt,
                totalDebt = null,
                cashAndEquivalents = null,
                equity = equity,
                netIncome12m = netIncome,
                sharesOutstanding = shares,
                marketCap = marketCap,
                liquidity = prev.liquidity,
            )
            val score = engine.score(financials)
            fundamentalsDao.upsert(
                prev.copy(
                    pe = pe,
                    pb = pb,
                    dividendYield = dy,
                    pegy = score.pegy,
                    netCashToMarketCap = score.netCashToMarketCap,
                    lynchScore = score.totalScore,
                    lynchCategory = score.category.label,
                    breakdownJson = encodeBreakdown(score.components),
                )
            )
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private fun cagrFromNetIncome(history: List<Double>): Double? {
        if (history.size < 2) return null
        val first = history.first()
        val last = history.last()
        if (first <= 0.0 || last <= 0.0) return null
        val years = (history.size - 1).toDouble()
        return Math.pow(last / first, 1.0 / years) - 1.0
    }

    private fun encodeBreakdown(components: List<ScoreComponent>): String? =
        runCatching { json.encodeToString(components) }.getOrNull()
}

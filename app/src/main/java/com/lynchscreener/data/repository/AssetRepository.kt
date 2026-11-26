package com.lynchscreener.data.repository

import android.content.Context
import com.lynchscreener.data.local.dao.AssetDao
import com.lynchscreener.data.local.dao.FundamentalsDao
import com.lynchscreener.data.local.entity.AssetEntity
import com.lynchscreener.data.local.entity.FundamentalsEntity
import com.lynchscreener.data.remote.HttpConfig
import com.lynchscreener.data.remote.dto.B3Details
import com.lynchscreener.data.remote.dto.B3TableRow
import com.lynchscreener.data.remote.dto.CvmCompanyRaw
import com.lynchscreener.data.remote.dto.CvmEarnings
import com.lynchscreener.data.remote.dto.EarningsBasis
import com.lynchscreener.data.remote.dto.SecFundamentals
import com.lynchscreener.data.remote.dto.Sp500Entry
import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.LynchFlag
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.ScoreComponent
import com.lynchscreener.worker.SyncEventLog
import kotlinx.coroutines.flow.Flow
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

    /**
     * Year-over-year variation of net income, derived from the annual history
     * already stored in Room.
     *
     * This is what makes the momentum check free: no extra CVM/SEC download is
     * needed to notice that a company's profit just fell by 20%.
     * Returns null when there are fewer than two comparable years or when the
     * base year was a loss (percentage growth from a loss is meaningless).
     */
    private fun yoyFromHistory(historyJson: String?): Double? {
        if (historyJson.isNullOrBlank() || historyJson == "{}") return null
        val map = runCatching {
            json.decodeFromString<Map<String, Double>>(historyJson)
        }.getOrNull() ?: return null
        val years = map.keys.mapNotNull { it.toIntOrNull() }.sorted()
        if (years.size < 2) return null
        val last = map[years.last().toString()] ?: return null
        val previous = map[years[years.size - 2].toString()] ?: return null
        if (previous <= 0.0) return null
        return last / previous - 1.0
    }

    // -----------------------------------------------------------------
    // Queue cursors (checkpointing by timestamp)
    // -----------------------------------------------------------------

    suspend fun nextB3DetailsBatch(): List<AssetEntity> =
        assetDao.nextFundamentalsBatch(Market.BR.code, HttpConfig.B3_DETAILS_BATCH)

    suspend fun nextSecBalanceBatch(): List<AssetEntity> =
        assetDao.nextSecBalanceBatch(
            cutoff = System.currentTimeMillis() - HttpConfig.SEC_STALENESS_MS,
            limit = HttpConfig.SEC_BALANCE_BATCH,
        )

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

    /** Tickers still missing fundamentals — used by the self-chaining workers. */
    suspend fun countPendingFundamentals(market: Market): Int =
        assetDao.countPendingFundamentals(market.code)

    /** Tickers still missing a price. */
    suspend fun countPendingPrices(): Int = assetDao.countPendingPrices()

    // -----------------------------------------------------------------
    // B3: CVM earnings history (5-year net-income CAGR + consistency)
    // -----------------------------------------------------------------

    /** B3 tickers whose CVM earnings history has not been ingested yet. */
    suspend fun pendingEarningsTickers(): List<String> = assetDao.pendingEarningsTickers()

    suspend fun countPendingEarnings(): Int = assetDao.countPendingEarnings()

    /**
     * Applies the CVM data to the fundamentals of each ticker and recomputes the
     * Lynch score.
     *
     * Besides the earnings history (PEGY + consistency), this rebases P/L, P/VP
     * and ROE on the PARENT-COMPANY slice when the balance sheet is available:
     * all three are then derived from the same figures, so the identity
     * `P/L = P/VP / ROE` holds by construction instead of depending on three
     * independently scraped numbers.
     */
    suspend fun applyEarningsBatch(tickerToData: Map<String, CvmCompanyRaw>): Int {
        var updated = 0
        for ((ticker, raw) in tickerToData) {
            val asset = assetDao.getByTicker(ticker) ?: continue
            val prev = fundamentalsDao.getByTicker(ticker) ?: continue

            // No earnings found (e.g. no CNPJ match): still mark as attempted
            // ("{}") so the costly CVM download is not retried forever.
            if (raw.isEmpty) {
                fundamentalsDao.upsert(prev.copy(earningsHistoryJson = "{}"))
                continue
            }

            val earnings = CvmEarnings.from(raw.netIncomeByYear)
            val statement = parentBasis(prev, asset.marketCap, raw, earnings)
            val historyJson = encodeEarnings(earnings.netIncomeByYear)

            val financials = Financials(
                ticker = ticker,
                market = Market.BR,
                sector = asset.sector,
                subsector = asset.subsector,
                price = asset.price ?: 0.0,
                pe = statement.pe,
                pb = statement.pb,
                roe = statement.roe,
                roic = prev.roic,
                dividendYield = prev.dividendYield,
                cagr5y = prev.cagr5y,
                earningsCagr5y = earnings.cagr5y,
                earningsConsistent = earnings.consistent,
                earningsYoy = yoyFromHistory(historyJson),
                netDebt = prev.netDebt,
                totalDebt = null,
                cashAndEquivalents = null,
                equity = statement.equity,
                netIncome12m = statement.netIncome,
                sharesOutstanding = prev.sharesOutstanding,
                marketCap = asset.marketCap,
                liquidity = prev.liquidity,
            )
            val score = engine.score(financials)

            fundamentalsDao.upsert(
                prev.copy(
                    pe = statement.pe,
                    pb = statement.pb,
                    roe = statement.roe,
                    equity = statement.equity,
                    netIncome12m = statement.netIncome,
                    equityControllers = raw.equityControllers,
                    earningsBasis = raw.basis.name,
                    earningsCagr5y = earnings.cagr5y,
                    earningsConsistent = earnings.consistent,
                    earningsYoy = yoyFromHistory(historyJson),
                    alertFlags = LynchFlag.toCodes(score.flags),
                    earningsHistoryJson = historyJson,
                    pegy = score.pegy,
                    netCashToMarketCap = score.netCashToMarketCap,
                    lynchScore = score.totalScore,
                    lynchCategory = score.category.label,
                    breakdownJson = encodeBreakdown(score.components),
                )
            )
            updated++
        }
        return updated
    }

    /**
     * Ratios derived from the parent-company slice of the latest fiscal year.
     *
     * Fallback chain per field keeps previously scraped values when the CVM
     * statement is missing or the company reports a loss (a loss makes P/L
     * meaningless, and the engine disqualifies it anyway).
     */
    private data class StatementBasis(
        val pe: Double?,
        val pb: Double?,
        val roe: Double?,
        val equity: Double?,
        val netIncome: Double?,
    )

    private fun parentBasis(
        prev: FundamentalsEntity,
        marketCap: Double?,
        raw: CvmCompanyRaw,
        earnings: CvmEarnings,
    ): StatementBasis {
        val income = raw.latestNetIncome ?: earnings.netIncomeByYear.maxByOrNull { it.key }?.value
        val equity = raw.equityControllers
        val cap = marketCap

        if (income == null || income <= 0.0 || equity == null || equity <= 0.0 ||
            cap == null || cap <= 0.0
        ) {
            // Keep what we had, but still repair a leaked quarterly income.
            val repairedIncome = impliedIncome(prev.pe, cap) ?: prev.netIncome12m
            return StatementBasis(prev.pe, prev.pb, prev.roe, prev.equity, repairedIncome)
        }

        val pe = cap / income
        val pb = cap / equity
        val roe = income / equity

        // The scraped multiples came from a different basis (consolidated, other
        // date). Log when the rebase is material so a wrong CNPJ match is visible.
        val previousPe = prev.pe
        if (previousPe != null && previousPe > 0.0) {
            val shift = kotlin.math.abs(pe / previousPe - 1.0)
            if (shift > 0.40) {
                SyncEventLog.log(
                    "B3 ${prev.ticker}: P/L rebaseado ${fmt(previousPe)} → ${fmt(pe)} " +
                        "(${raw.basis.label}, lucro ${raw.latestYear ?: "?"})",
                )
            }
        }

        return StatementBasis(pe = pe, pb = pb, roe = roe, equity = equity, netIncome = income)
    }

    private fun fmt(value: Double): String = "%.2f".format(value)

    // -----------------------------------------------------------------
    // Sanity checks shared by B3 and US
    // -----------------------------------------------------------------

    /**
     * Net income implied by the P/L multiple. Fundamentus' "Lucro Líquido" cell is
     * a PERIOD value for some companies (a single quarter), which produced
     * P/L = 25.9 next to ROE = 65% — the income must match the multiple.
     */
    private fun impliedIncome(pe: Double?, marketCap: Double?): Double? {
        if (pe == null || pe <= 0.0) return null
        if (marketCap == null || marketCap <= 0.0) return null
        return marketCap / pe
    }

    /**
     * Logs when `P/L ≈ P/VP / ROE` does not hold. The three multiples describe the
     * same balance sheet, so a >15% deviation means they came from different
     * periods or different shareholder bases.
     */
    private fun coherenceWarning(
        ticker: String,
        market: String,
        pe: Double?,
        pb: Double?,
        roe: Double?,
        expectedPe: Double?,
    ) {
        if (pe == null || pb == null || roe == null) return
        if (pe <= 0.0 || pb <= 0.0 || roe <= 0.0) return
        val expected = expectedPe ?: (pb / roe)
        if (expected <= 0.0) return
        val deviation = kotlin.math.abs(pe / expected - 1.0)
        if (deviation <= 0.15) return
        SyncEventLog.log(
            "Aviso $market $ticker: P/L ${fmt(pe)} vs P/VP ${fmt(pb)} / ROE " +
                "${fmt(roe * 100)}% => esperado ${fmt(expected)} " +
                "(desvio ${(deviation * 100).toInt()}%)",
        )
    }

    // -----------------------------------------------------------------
    // Scoring-engine migration
    // -----------------------------------------------------------------

    /**
     * Recomputes every persisted score with the current engine rules.
     *
     * Called once after the app is upgraded, so a rule change (PEGY cap, score
     * ceilings, sector exception) reaches rows collected by older versions. The
     * user portfolio table is untouched.
     *
     * Also invalidates CVM histories that predate the parent-company basis: their
     * JSON only holds numbers, with no way to know which shareholder group they
     * belong to, so they must be re-ingested.
     *
     * @return number of fundamentals re-scored.
     */
    suspend fun rescoreAll(): Int {
        val assets = assetDao.getAll().associateBy { it.ticker }
        val all = fundamentalsDao.getAll()
        var rescored = 0

        for (prev in all) {
            val asset = assets[prev.ticker] ?: continue
            // Momentum is derived from data already on disk, so a rule change is
            // picked up without downloading anything again.
            val yoy = yoyFromHistory(prev.earningsHistoryJson)
            val financials = Financials(
                ticker = prev.ticker,
                market = Market.fromCode(asset.market),
                sector = asset.sector,
                subsector = asset.subsector,
                price = asset.price ?: 0.0,
                pe = prev.pe,
                pb = prev.pb,
                roe = prev.roe,
                roic = prev.roic,
                dividendYield = prev.dividendYield,
                cagr5y = prev.cagr5y,
                earningsCagr5y = prev.earningsCagr5y,
                earningsConsistent = prev.earningsConsistent,
                earningsYoy = yoy,
                netDebt = prev.netDebt,
                totalDebt = null,
                cashAndEquivalents = null,
                equity = prev.equity,
                netIncome12m = prev.netIncome12m,
                sharesOutstanding = prev.sharesOutstanding,
                marketCap = asset.marketCap,
                liquidity = prev.liquidity,
            )
            val score = engine.score(financials)
            fundamentalsDao.upsert(
                prev.copy(
                    earningsYoy = yoy,
                    alertFlags = LynchFlag.toCodes(score.flags),
                    pegy = score.pegy,
                    netCashToMarketCap = score.netCashToMarketCap,
                    lynchScore = score.totalScore,
                    lynchCategory = score.category.label,
                    breakdownJson = encodeBreakdown(score.components),
                )
            )
            rescored++
        }

        fundamentalsDao.resetPreBasisEarnings()
        return rescored
    }

    // -----------------------------------------------------------------
    // Read models for the UI (Etapa 3)
    // -----------------------------------------------------------------

    /** Companies (with fundamentals) of a market, sorted by Lynch score desc. */
    fun observeRanked(market: Market): Flow<List<RankedCompany>> =
        assetDao.observeRanked(market.code)

    /** A single company projection (with fundamentals), or null. */
    fun observeCompany(ticker: String): Flow<RankedCompany?> =
        assetDao.observeRankedByTicker(ticker)

    /** Tickers currently failing sync (for the error log). */
    fun observeErrors(): Flow<List<AssetEntity>> =
        assetDao.observeErrors()

    /** Snapshot of sync progress (totals vs. processed) for the sync screen. */
    suspend fun syncSnapshot(): SyncSnapshot = SyncSnapshot(
        brTotal = assetDao.countByMarket(Market.BR.code),
        brWithFundamentals = assetDao.countWithFundamentals(Market.BR.code),
        brWithPrice = assetDao.countWithPrice(Market.BR.code),
        usTotal = assetDao.countByMarket(Market.US.code),
        usWithFundamentals = assetDao.countWithFundamentals(Market.US.code),
        usWithPrice = assetDao.countWithPrice(Market.US.code),
    )

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

        // Placeholder fundamentals so the US list is visible immediately after
        // the seed. IGNORE keeps any fundamentals already collected from SEC.
        fundamentalsDao.insertIgnoreAll(
            entries.map { entry ->
                FundamentalsEntity(
                    ticker = entry.ticker,
                    pe = null,
                    pb = null,
                    roe = null,
                    roic = null,
                    dividendYield = null,
                    cagr5y = null,
                    pegy = null,
                    netDebt = null,
                    netCashToMarketCap = null,
                    equity = null,
                    netIncome12m = null,
                    sharesOutstanding = null,
                    dividendPerShare = null,
                    liquidity = null,
                    lynchScore = 0,
                    lynchCategory = LynchCategory.UNRATED.label,
                    overallRank = null,
                    sectorRank = null,
                    breakdownJson = null,
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
                    subsector = prevAssets[row.ticker]?.subsector,
                    price = row.price ?: 0.0,
                    pe = row.pe,
                    pb = row.pb,
                    roe = row.roe,
                    roic = row.roic,
                    dividendYield = row.dividendYield,
                    cagr5y = row.cagr5y,
                    earningsCagr5y = prev?.earningsCagr5y,
                    earningsConsistent = prev?.earningsConsistent,
                    earningsYoy = yoyFromHistory(prev?.earningsHistoryJson),
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
                    earningsCagr5y = prev?.earningsCagr5y,
                    earningsConsistent = prev?.earningsConsistent,
                    earningsHistoryJson = prev?.earningsHistoryJson,
                    equityControllers = prev?.equityControllers,
                    earningsBasis = prev?.earningsBasis,
                    earningsYoy = yoyFromHistory(prev?.earningsHistoryJson),
                    alertFlags = LynchFlag.toCodes(score.flags),
                    pegy = score.pegy,
                    netDebt = prev?.netDebt,
                    netCashToMarketCap = score.netCashToMarketCap,
                    equity = prev?.equity,
                    netIncome12m = prev?.netIncome12m,
                    sharesOutstanding = prev?.sharesOutstanding,
                    // Fundamentus only gives the DY ratio; derive trailing DPS so
                    // DY can be recalculated when the price moves.
                    dividendPerShare = deriveDps(row.dividendYield, row.price, prev?.dividendPerShare),
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
        val shares = details.shares ?: when {
            price != null && price > 0.0 && marketCap != null -> marketCap / price
            else -> prev?.sharesOutstanding
        }

        // Fundamentus' "Lucro Líquido" cell is a period figure for some companies
        // (a single quarter), which made P/L and the income disagree by 3–4x.
        // Trust the P/L multiple and derive the 12-month figure when they diverge.
        val implied = impliedIncome(prev?.pe, marketCap)
        val reportedIncome = details.netIncome12m ?: prev?.netIncome12m
        val netIncome = when {
            implied == null || implied <= 0.0 -> reportedIncome
            reportedIncome == null || reportedIncome <= 0.0 -> implied
            kotlin.math.abs(reportedIncome / implied - 1.0) > 0.15 -> implied
            else -> reportedIncome
        }

        val equity = details.equity ?: prev?.equity
        val financials = Financials(
            ticker = ticker,
            market = Market.BR,
            sector = details.sector ?: asset.sector,
            subsector = details.subsector ?: asset.subsector,
            price = price ?: 0.0,
            pe = prev?.pe,
            pb = prev?.pb,
            roe = prev?.roe,
            roic = prev?.roic,
            dividendYield = prev?.dividendYield,
            cagr5y = prev?.cagr5y,
            earningsCagr5y = prev?.earningsCagr5y,
            earningsConsistent = prev?.earningsConsistent,
            earningsYoy = yoyFromHistory(prev?.earningsHistoryJson),
            netDebt = details.netDebt ?: prev?.netDebt,
            totalDebt = null,
            cashAndEquivalents = details.cash,
            equity = equity,
            netIncome12m = netIncome,
            sharesOutstanding = shares,
            marketCap = marketCap,
            liquidity = prev?.liquidity,
        )
        val score = engine.score(financials)
        coherenceWarning(ticker, "B3", prev?.pe, prev?.pb, prev?.roe, expectedPe = null)

        assetDao.upsert(
            asset.copy(
                name = details.name ?: asset.name,
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
                earningsCagr5y = prev?.earningsCagr5y,
                earningsConsistent = prev?.earningsConsistent,
                earningsHistoryJson = prev?.earningsHistoryJson,
                equityControllers = prev?.equityControllers,
                earningsBasis = prev?.earningsBasis,
                earningsYoy = yoyFromHistory(prev?.earningsHistoryJson),
                alertFlags = LynchFlag.toCodes(score.flags),
                dividendYield = prev?.dividendYield,
                cagr5y = prev?.cagr5y,
                pegy = score.pegy,
                netDebt = details.netDebt ?: prev?.netDebt,
                netCashToMarketCap = score.netCashToMarketCap,
                equity = equity,
                netIncome12m = netIncome,
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
        val consistent = if (sec.netIncomeHistory.size >= 2) {
            sec.netIncomeHistory.all { it > 0.0 }
        } else {
            prev?.earningsConsistent
        }
        // SEC already returns the annual series, so momentum is free here too.
        val yoy = run {
            val history = sec.netIncomeHistory
            if (history.size < 2) return@run null
            val previous = history[history.size - 2]
            if (previous <= 0.0) null else history.last() / previous - 1.0
        }
        val roe = if (netIncome != null && equity != null && equity != 0.0) netIncome / equity else prev?.roe
        val pe = if (netIncome != null && netIncome > 0.0 && marketCap != null) marketCap / netIncome else prev?.pe
        val pb = if (marketCap != null && equity != null && equity != 0.0) marketCap / equity else prev?.pb
        val dy = if (sec.dividendPerShare != null && price != null && price > 0.0) sec.dividendPerShare / price else prev?.dividendYield

        val financials = Financials(
            ticker = ticker,
            market = Market.US,
            sector = asset.sector,
            subsector = asset.subsector,
            price = price ?: 0.0,
            pe = pe,
            pb = pb,
            roe = roe,
            roic = prev?.roic,
            dividendYield = dy,
            cagr5y = cagr,
            earningsCagr5y = cagr,
            earningsConsistent = consistent,
            earningsYoy = yoy,
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
                earningsCagr5y = cagr,
                earningsConsistent = consistent,
                earningsHistoryJson = prev?.earningsHistoryJson,
                equityControllers = prev?.equityControllers,
                earningsBasis = prev?.earningsBasis,
                earningsYoy = yoy,
                alertFlags = LynchFlag.toCodes(score.flags),
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

        // Placeholder rows (seeded US universe before SEC runs) have no
        // fundamentals at all — skip scoring so we don't persist a bogus
        // "score 0 / Stalwart" that would pollute the ranking.
        val hasFundamentals = prev != null && (
            prev.pe != null || prev.roe != null || prev.netIncome12m != null ||
                prev.equity != null || prev.pb != null
            )

        if (prev != null && hasFundamentals) {
            val financials = Financials(
                ticker = ticker,
                market = market,
                sector = asset.sector,
                subsector = asset.subsector,
                price = price,
                pe = pe,
                pb = pb,
                roe = prev.roe,
                earningsCagr5y = prev.earningsCagr5y,
                earningsConsistent = prev.earningsConsistent,
                earningsYoy = yoyFromHistory(prev.earningsHistoryJson),
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
                    alertFlags = LynchFlag.toCodes(score.flags),
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

    /** DPS ≈ DY × price (both from the same snapshot), falling back to the previous value. */
    private fun deriveDps(dividendYield: Double?, price: Double?, previous: Double?): Double? {
        if (dividendYield != null && price != null && price > 0.0) {
            val dps = dividendYield * price
            if (dps > 0.0) return dps
        }
        return previous
    }

    /** Year→net-income map serialized with string keys (JSON object keys). */
    private fun encodeEarnings(byYear: Map<Int, Double>): String? =
        runCatching {
            json.encodeToString(byYear.entries.associate { it.key.toString() to it.value })
        }.getOrNull()

    private fun encodeBreakdown(components: List<ScoreComponent>): String? =
        runCatching { json.encodeToString(components) }.getOrNull()
}

package com.lynchscreener.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Derived fundamentals + Peter Lynch score for each ticker.
 *
 * Ratio fields ([roe], [roic], [dividendYield], [cagr5y], [netCashToMarketCap])
 * are stored as FRACTIONS (0.15 == 15%) — see [com.lynchscreener.domain.model.Financials].
 */
@Entity(
    tableName = "fundamentals",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["ticker"],
            childColumns = ["ticker"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class FundamentalsEntity(
    @PrimaryKey val ticker: String,
    val pe: Double?,
    val pb: Double?,
    val roe: Double?,
    val roic: Double?,
    val dividendYield: Double?,
    val cagr5y: Double?,
    /** 5-year compound annual growth rate of NET INCOME (CVM DFP), a fraction. */
    val earningsCagr5y: Double? = null,
    /** True when no fiscal year in the 5-year window reported a loss. */
    val earningsConsistent: Boolean? = null,
    /** Annual net income history as JSON {"2021":123.4,...} for the detail screen. */
    val earningsHistoryJson: String? = null,
    /**
     * Equity attributable to the controlling shareholders (CVM BPP), in BRL.
     * Kept apart from [equity] so the UI can explain the basis used.
     */
    val equityControllers: Double? = null,
    /**
     * `CONTROLLERS` or `CONSOLIDATED` — which shareholder group the reported
     * earnings/equity refer to (see [com.lynchscreener.data.remote.dto.EarningsBasis]).
     */
    val earningsBasis: String? = null,
    /** Variation of net income vs the previous 12 months (fraction). */
    val earningsYoy: Double? = null,
    /** Isolated quarterly net income as JSON {"2026Q3":123.4,...} (CVM ITR). */
    val quarterlyJson: String? = null,
    /** Sum of the last four reported quarters, in BRL. */
    val ttmNetIncome: Double? = null,
    /** Sum of the four quarters before those, in BRL. */
    val ttmPrevious: Double? = null,
    /** Last quarter used in the TTM window, e.g. "2026Q3" or "FY2025". */
    val ttmQuarter: String? = null,
    /** Comma-separated [com.lynchscreener.domain.model.LynchFlag] codes. */
    val alertFlags: String? = null,
    val pegy: Double?,
    /** Positive = net debt, negative = net cash. */
    val netDebt: Double?,
    /** Net cash (or negative net debt) divided by market cap. */
    val netCashToMarketCap: Double?,
    val equity: Double?,
    val netIncome12m: Double?,
    /** Shares used to recompute market cap / P/L / P/VP on price refresh. */
    val sharesOutstanding: Double?,
    /** Latest annual dividends per share, used to recompute DY on price refresh. */
    val dividendPerShare: Double?,
    /** Average daily traded volume (R$ for BR, USD for US). */
    val liquidity: Double?,
    val lynchScore: Int,
    val lynchCategory: String,
    /** Position in the overall market ranking (1 = best). Null until ranked. */
    val overallRank: Int?,
    /** Position within the company's sector (1 = best). Null until ranked. */
    val sectorRank: Int?,
    /** JSON payload explaining the score, for the "Raio-X" screen (Etapa 3). */
    val breakdownJson: String?,
)

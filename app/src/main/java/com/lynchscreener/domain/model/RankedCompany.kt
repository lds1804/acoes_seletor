package com.lynchscreener.domain.model

/**
 * Flat projection of `assets` JOIN `fundamentals`, returned by the Room query
 * that feeds the ranking screen. No Android dependencies — pure data.
 */
data class RankedCompany(
    val ticker: String,
    val market: String,
    val name: String?,
    val sector: String?,
    val subsector: String?,
    val price: Double?,
    val marketCap: Double?,
    val pe: Double?,
    val pb: Double?,
    val roe: Double?,
    val roic: Double?,
    val dividendYield: Double?,
    val cagr5y: Double?,
    val pegy: Double?,
    val netDebt: Double?,
    val netCashToMarketCap: Double?,
    val equity: Double?,
    val netIncome12m: Double?,
    val sharesOutstanding: Double?,
    val dividendPerShare: Double?,
    val liquidity: Double?,
    val lynchScore: Int,
    val lynchCategory: String,
    val breakdownJson: String?,
    /** CAGR of net income over the last ~5 fiscal years (CVM DFP, B3 only). */
    val earningsCagr5y: Double?,
    /** True when every year in the window was profitable. */
    val earningsConsistent: Boolean?,
    /** JSON object `{"2020":1234.5,…}` with net income per fiscal year. */
    val earningsHistoryJson: String?,
    /** `CONTROLLERS` or `CONSOLIDATED` — which shareholder group the series uses. */
    val earningsBasis: String?,
    val priceUpdatedAt: Long?,
    val fundamentalsUpdatedAt: Long?,
)

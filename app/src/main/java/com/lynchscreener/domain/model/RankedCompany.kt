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
    val priceUpdatedAt: Long?,
    val fundamentalsUpdatedAt: Long?,
)

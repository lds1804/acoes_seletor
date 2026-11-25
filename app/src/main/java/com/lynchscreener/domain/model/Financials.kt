package com.lynchscreener.domain.model

/**
 * Normalized, market-agnostic input for the scoring engine.
 *
 * Conventions (IMPORTANT — keep consistent across B3 and SEC parsers):
 *  - Ratio fields ([roe], [roic], [dividendYield], [cagr5y]) are FRACTIONS:
 *    0.15 means 15%. This keeps the math uniform regardless of source.
 *  - Monetary fields ([netDebt], [totalDebt], [cashAndEquivalents], [equity],
 *    [netIncome12m], [marketCap]) are in the company's reporting currency.
 *  - [netDebt] > 0 means net debt; [netDebt] < 0 means net cash.
 */
data class Financials(
    val ticker: String,
    val market: Market = Market.BR,
    val sector: String? = null,
    val price: Double = 0.0,
    /** Earnings per share (TTM). Used to derive P/E when [pe] is null. */
    val earningsPerShare: Double? = null,
    val pe: Double? = null,
    val pb: Double? = null,
    val roe: Double? = null,
    val roic: Double? = null,
    val dividendYield: Double? = null,
    val cagr5y: Double? = null,
    val netDebt: Double? = null,
    val totalDebt: Double? = null,
    val cashAndEquivalents: Double? = null,
    val equity: Double? = null,
    val netIncome12m: Double? = null,
    val sharesOutstanding: Double? = null,
    val marketCap: Double? = null,
    /** Average daily traded volume (R$ or USD). */
    val liquidity: Double? = null,
)

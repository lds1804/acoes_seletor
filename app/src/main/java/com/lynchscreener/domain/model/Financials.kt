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
    /**
     * B3 subsector (e.g. "Bancos", "Seguradoras", "Corretoras de Seguros").
     * Required to tell a bank/insurer — which legitimately runs on debt — from an
     * insurance broker, which does not. Null for the US universe (SEC only
     * publishes the GICS sector).
     */
    val subsector: String? = null,
    val price: Double = 0.0,
    /** Earnings per share (TTM). Used to derive P/E when [pe] is null. */
    val earningsPerShare: Double? = null,
    val pe: Double? = null,
    val pb: Double? = null,
    val roe: Double? = null,
    val roic: Double? = null,
    val dividendYield: Double? = null,
    val cagr5y: Double? = null,
    /**
     * 5-year CAGR of NET INCOME (fraction). Preferred over [cagr5y] by the
     * scoring engine because Peter Lynch defines PEGY on earnings growth.
     */
    val earningsCagr5y: Double? = null,
    /** False when the company posted a loss in any of the last 5 fiscal years. */
    val earningsConsistent: Boolean? = null,
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

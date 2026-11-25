package com.lynchscreener.data.remote.dto

/**
 * Normalized output of the SEC company-facts parser.
 *
 * [netIncomeHistory] holds annual (10-K) net income, oldest first, used to
 * derive a 5-year earnings CAGR. The remaining fields are the latest reported
 * values. [dividendPerShare] is the latest declared dividends per share.
 */
data class SecFundamentals(
    val entityName: String?,
    val netIncomeHistory: List<Double>,
    val equity: Double?,
    val cash: Double?,
    val totalDebt: Double?,
    val sharesOutstanding: Double?,
    val dividendPerShare: Double?,
)

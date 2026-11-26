package com.lynchscreener.data.remote.dto

/** One row of the Fundamentus `resultado.php` table. */
data class B3TableRow(
    val ticker: String,
    val price: Double?,
    val pe: Double?,
    val pb: Double?,
    val dividendYield: Double?,
    val roe: Double?,
    val roic: Double?,
    val evEbit: Double?,
    val debtToEquity: Double?,
    val cagr5y: Double?,
    val liquidity: Double?,
)

/** Enriched fields from the Fundamentus `detalhes.php` page. */
data class B3Details(
    /** Razão social / nome comercial (campo "Empresa"). */
    val name: String?,
    val sector: String?,
    val subsector: String?,
    val marketCap: Double?,
    val equity: Double?,
    val cash: Double?,
    val netDebt: Double?,
    val netIncome12m: Double?,
    /** Número de ações emitidas (campo "Nro. Ações"). */
    val shares: Double?,
)

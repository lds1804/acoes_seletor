package com.lynchscreener.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Which shareholder group the reported net income / equity belongs to.
 *
 * Lynch-based ratios must be computed on the PARENT company's slice: minority
 * interests sitting in joint ventures would otherwise inflate equity and
 * earnings, distorting P/L, ROE, P/VP and the earnings CAGR. WIZC3 is the
 * textbook case — half of its consolidated equity belongs to non-controlling
 * shareholders of the insurance partnership.
 */
enum class EarningsBasis(val label: String) {
    /** `Atribuído a Sócios da Empresa Controladora` — preferred. */
    CONTROLLERS("Controladores"),

    /** Consolidated total (includes non-controlling shareholders) — fallback. */
    CONSOLIDATED("Consolidado"),
}

/**
 * Raw figures extracted from the CVM DFP for one company (CNPJ).
 *
 * @property netIncomeByYear net income per fiscal year (BRL), already on the
 *   basis described by [basis].
 * @property equityControllers parent-company equity of the LATEST fiscal year
 *   (`2.03`/`2.08` minus the non-controlling participation), in BRL.
 * @property latestYear fiscal year of [equityControllers].
 */
@Serializable
data class CvmCompanyRaw(
    val netIncomeByYear: Map<Int, Double> = emptyMap(),
    val basis: EarningsBasis = EarningsBasis.CONSOLIDATED,
    val equityControllers: Double? = null,
    val latestYear: Int? = null,
    /**
     * Isolated quarterly net income keyed `YYYYQn` (e.g. `"2026Q3"`), from the ITR
     * filings. Used to build a real trailing-twelve-month window.
     */
    val quarterlyNetIncome: Map<String, Double> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = netIncomeByYear.isEmpty() && quarterlyNetIncome.isEmpty()

    /** Net income of the most recent fiscal year on file, if any. */
    val latestNetIncome: Double? get() = latestYear?.let { netIncomeByYear[it] }
        ?: netIncomeByYear.maxByOrNull { it.key }?.value
}

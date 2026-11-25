package com.lynchscreener.domain

import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.LynchScore
import com.lynchscreener.domain.model.ScoreComponent
import kotlin.math.round

/**
 * Pure, side-effect-free Peter Lynch scoring engine.
 *
 * Produces a 0..100 score from normalized [Financials]. No Android dependencies,
 * fully unit-testable.
 *
 * Sector sensitivity: financial companies (banks/insurers) are NOT scored on
 * debt leverage, because liabilities are their raw material. Their points are
 * redistributed to ROE, P/VP, P/L and dividend yield (see [isFinancialSector]).
 */
class LynchScoringEngine {

    // ---------------------------------------------------------------------
    // Weights (general / financial)
    // ---------------------------------------------------------------------

    companion object {
        // Non-financial sectors.
        private const val W_PEGY = 30.0
        private const val W_PROFITABILITY = 25.0
        private const val W_HEALTH = 20.0
        private const val W_VALUATION = 15.0
        private const val W_DIVIDEND = 10.0

        // Financial sectors (debt weight removed and redistributed).
        private const val WF_PEGY = 20.0
        private const val WF_PROFITABILITY = 30.0
        private const val WF_PB = 20.0
        private const val WF_PE = 15.0
        private const val WF_DIVIDEND = 15.0

        private val FINANCIAL_KEYWORDS = listOf(
            "bank", "banco", "bancário", "bancario", "financial", "financeiro", "financeira",
            "insurance", "segur", "securitizadora",
            "instituições financeiras", "instituicoes financeiras",
        )

        private val CYCLICAL_KEYWORDS = listOf(
            "materia", "materials", "energy", "energia", "oil", "óleo", "oleo", "gas",
            "mining", "miner", "steel", "aço", "aco", "siderurg", "metal", "commodit",
            "papel", "celulose", "petroquim", "petroq", "quim", "automobil", "montadora",
            "veículos", "veiculos", "airline", "aére", "aere", "avia", "agro",
        )

        fun isFinancialSector(sector: String?): Boolean =
            sector?.let { s -> FINANCIAL_KEYWORDS.any { s.lowercase().contains(it) } } ?: false

        fun isCyclicalSector(sector: String?): Boolean =
            sector?.let { s -> CYCLICAL_KEYWORDS.any { s.lowercase().contains(it) } } ?: false
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun score(financials: Financials): LynchScore {
        val pe = effectivePe(financials)
        val pegy = computePegy(financials, pe)
        val netCashToMc = computeNetCashToMarketCap(financials)

        val disqualifiers = findDisqualifiers(financials, pe)
        if (disqualifiers.isNotEmpty()) {
            val netIncome = financials.netIncome12m
            val category = if (netIncome != null && netIncome <= 0.0) {
                LynchCategory.VALUE_TRAP
            } else {
                LynchCategory.UNRATED
            }
            return LynchScore(
                totalScore = 0,
                category = category,
                pegy = pegy,
                netCashToMarketCap = netCashToMc,
                components = disqualifiers.map { reason ->
                    ScoreComponent(
                        key = "DISQUALIFIED",
                        label = "Desqualificado",
                        points = 0.0,
                        maxPoints = 0.0,
                        rationale = reason,
                    )
                },
                disqualifiers = disqualifiers,
            )
        }

        val components = if (isFinancialSector(financials.sector)) {
            scoreFinancial(financials, pe, pegy)
        } else {
            scoreGeneral(financials, pe, pegy, netCashToMc)
        }

        val raw = components.sumOf { it.points }.coerceIn(0.0, 100.0)
        val totalScore = raw.roundToInt()
        val category = classify(financials, pe, pegy)

        return LynchScore(
            totalScore = totalScore,
            category = category,
            pegy = pegy,
            netCashToMarketCap = netCashToMc,
            components = components,
            disqualifiers = emptyList(),
        )
    }

    // ---------------------------------------------------------------------
    // Derived metrics
    // ---------------------------------------------------------------------

    /** P/E, falling back to price / EPS when not provided directly. */
    private fun effectivePe(f: Financials): Double? {
        f.pe?.let { return it }
        val eps = f.earningsPerShare ?: return null
        if (eps <= 0.0 || f.price <= 0.0) return null
        return f.price / eps
    }

    /**
     * PEGY = P/L / (growth% + yield%).
     *
     * The denominator is expressed in percentage POINTS, so a stock with
     * P/L = 20, CAGR = 15% and DY = 5% yields PEGY = 20 / 20 = 1.0.
     * Returns null when undefined (negative earnings or non-positive growth+yield).
     */
    private fun computePegy(f: Financials, pe: Double?): Double? {
        if (pe == null || pe <= 0.0) return null
        val growth = f.cagr5y ?: return null
        val yield = f.dividendYield ?: 0.0
        val denominator = (growth + yield) * 100.0
        if (denominator <= 0.0) return null
        return pe / denominator
    }

    /** Net cash = cash - debt. Returns fraction of market cap. */
    private fun computeNetCashToMarketCap(f: Financials): Double? {
        val netCash = netCash(f) ?: return null
        val marketCap = f.marketCap
        if (marketCap == null || marketCap <= 0.0) return null
        return netCash / marketCap
    }

    private fun netCash(f: Financials): Double? {
        val cash = f.cashAndEquivalents
        val debt = f.totalDebt
        if (cash != null && debt != null) return cash - debt
        // netDebt > 0 means net debt => net cash = -netDebt.
        return f.netDebt?.let { -it }
    }

    private fun findDisqualifiers(f: Financials, pe: Double?): List<String> {
        val reasons = mutableListOf<String>()
        if (pe != null && pe < 0.0) {
            reasons += "P/L negativo (prejuízo nos últimos 12 meses)"
        }
        if (f.equity != null && f.equity <= 0.0) {
            reasons += "Patrimônio líquido negativo"
        }
        if (f.netIncome12m != null && f.netIncome12m <= 0.0) {
            reasons += "Lucro líquido inconsistente ou negativo"
        }
        if (f.roe != null && f.roe < 0.0) {
            reasons += "ROE negativo"
        }
        return reasons
    }

    // ---------------------------------------------------------------------
    // Scoring — general (non-financial)
    // ---------------------------------------------------------------------

    private fun scoreGeneral(
        f: Financials,
        pe: Double?,
        pegy: Double?,
        netCashToMc: Double?,
    ): List<ScoreComponent> {
        val profitability = profitabilityMetric(f)
        return listOf(
            ScoreComponent(
                key = "pegy", label = "PEGY",
                points = scorePegy(pegy, W_PEGY), maxPoints = W_PEGY,
                rationale = pegyRationale(pegy),
            ),
            ScoreComponent(
                key = "profitability", label = "Rentabilidade (ROE/ROIC)",
                points = scoreProfitability(profitability, W_PROFITABILITY), maxPoints = W_PROFITABILITY,
                rationale = profitabilityRationale(profitability),
            ),
            ScoreComponent(
                key = "health", label = "Saúde financeira (Caixa/Dívida)",
                points = scoreHealth(netCashToMc), maxPoints = W_HEALTH,
                rationale = healthRationale(netCashToMc),
            ),
            ScoreComponent(
                key = "valuation", label = "Desconto patrimonial/lucro (P/L + P/VP)",
                points = scoreValuation(pe, f.pb), maxPoints = W_VALUATION,
                rationale = valuationRationale(pe, f.pb),
            ),
            ScoreComponent(
                key = "dividend", label = "Dividend Yield",
                points = scoreDividend(f.dividendYield, W_DIVIDEND), maxPoints = W_DIVIDEND,
                rationale = dividendRationale(f.dividendYield),
            ),
        )
    }

    // ---------------------------------------------------------------------
    // Scoring — financial (banks / insurers): debt NOT penalized
    // ---------------------------------------------------------------------

    private fun scoreFinancial(f: Financials, pe: Double?, pegy: Double?): List<ScoreComponent> {
        val profitability = profitabilityMetric(f)
        return listOf(
            ScoreComponent(
                key = "pegy", label = "PEGY",
                points = scorePegy(pegy, WF_PEGY), maxPoints = WF_PEGY,
                rationale = pegyRationale(pegy),
            ),
            ScoreComponent(
                key = "profitability", label = "Rentabilidade (ROE/ROIC)",
                points = scoreProfitability(profitability, WF_PROFITABILITY), maxPoints = WF_PROFITABILITY,
                rationale = profitabilityRationale(profitability),
            ),
            ScoreComponent(
                key = "pb", label = "P/VP (múltiplo patrimonial)",
                points = scorePb(f.pb), maxPoints = WF_PB,
                rationale = pbRationale(f.pb),
            ),
            ScoreComponent(
                key = "pe", label = "P/L",
                points = scorePe(pe), maxPoints = WF_PE,
                rationale = peRationale(pe),
            ),
            ScoreComponent(
                key = "dividend", label = "Dividend Yield",
                points = scoreDividend(f.dividendYield, WF_DIVIDEND), maxPoints = WF_DIVIDEND,
                rationale = dividendRationale(f.dividendYield),
            ),
        )
    }

    // ---------------------------------------------------------------------
    // Component scorers (thresholds → points)
    // ---------------------------------------------------------------------

    private fun profitabilityMetric(f: Financials): Double? = when {
        f.roe != null && f.roic != null -> (f.roe + f.roic) / 2.0
        f.roe != null -> f.roe
        f.roic != null -> f.roic
        else -> null
    }

    private fun scorePegy(pegy: Double?, max: Double): Double {
        if (pegy == null) return 0.0
        val fraction = when {
            pegy < 0.5 -> 1.00
            pegy < 1.0 -> 0.80
            pegy < 1.5 -> 0.50
            pegy < 2.0 -> 0.20
            else -> 0.0
        }
        return max * fraction
    }

    private fun scoreProfitability(profitability: Double?, max: Double): Double {
        if (profitability == null) return 0.0
        val fraction = when {
            profitability >= 0.20 -> 1.00
            profitability >= 0.15 -> 0.80
            profitability >= 0.10 -> 0.50
            profitability >= 0.05 -> 0.20
            else -> 0.0
        }
        return max * fraction
    }

    private fun scoreHealth(netCashToMc: Double?): Double {
        if (netCashToMc == null) return W_HEALTH * 0.5 // neutral when unknown
        val fraction = when {
            netCashToMc >= 0.20 -> 1.00
            netCashToMc >= 0.10 -> 0.85
            netCashToMc > 0.0 -> 0.70
            netCashToMc >= -0.10 -> 0.45
            netCashToMc >= -0.30 -> 0.20
            else -> 0.0
        }
        return W_HEALTH * fraction
    }

    private fun scoreValuation(pe: Double?, pb: Double?): Double {
        var points = 0.0
        if (pe != null && pe > 0.0) {
            points += when {
                pe < 5 -> 7.5
                pe < 10 -> 6.0
                pe < 15 -> 4.5
                pe < 20 -> 3.0
                else -> 0.0
            }
        }
        if (pb != null && pb > 0.0) {
            points += when {
                pb < 0.7 -> 7.5
                pb < 1.0 -> 6.0
                pb < 1.5 -> 4.0
                pb < 2.5 -> 2.0
                else -> 0.0
            }
        }
        return points
    }

    private fun scorePb(pb: Double?): Double {
        if (pb == null || pb <= 0.0) return 0.0
        val fraction = when {
            pb < 0.7 -> 1.00
            pb < 1.0 -> 0.85
            pb < 1.5 -> 0.60
            pb < 2.0 -> 0.40
            pb < 3.0 -> 0.20
            else -> 0.0
        }
        return WF_PB * fraction
    }

    private fun scorePe(pe: Double?): Double {
        if (pe == null || pe <= 0.0) return 0.0
        val fraction = when {
            pe < 5 -> 1.00
            pe < 8 -> 0.85
            pe < 12 -> 0.65
            pe < 18 -> 0.45
            pe < 25 -> 0.25
            else -> 0.0
        }
        return WF_PE * fraction
    }

    private fun scoreDividend(dy: Double?, max: Double): Double {
        if (dy == null) return 0.0
        val fraction = when {
            dy >= 0.06 -> 1.00
            dy >= 0.04 -> 0.80
            dy >= 0.02 -> 0.50
            dy > 0.0 -> 0.20
            else -> 0.0
        }
        return max * fraction
    }

    // ---------------------------------------------------------------------
    // Classification
    // ---------------------------------------------------------------------

    private fun classify(f: Financials, pe: Double?, pegy: Double?): LynchCategory {
        val growth = f.cagr5y
        val roe = f.roe
        val pb = f.pb

        return when {
            // Value trap: looks cheap but deteriorating or unprofitable.
            pe != null && pe in 0.0..10.0 &&
                ((growth != null && growth < 0.0) || (roe != null && roe < 0.05)) ->
                LynchCategory.VALUE_TRAP

            // Asset play: trading below book value with a healthy ROE.
            pb != null && pb < 1.0 && (roe ?: 0.0) >= 0.05 ->
                LynchCategory.ASSET_PLAY

            // Fast grower: strong growth at a still-reasonable PEGY.
            growth != null && growth >= 0.15 && (pegy == null || pegy <= 1.5) ->
                LynchCategory.FAST_GROWER

            // Stalwart: steady profitability and moderate growth.
            growth != null && growth >= 0.05 && (roe ?: 0.0) >= 0.10 ->
                LynchCategory.STALWART

            // Cyclical: known cyclical sectors fall back here.
            isCyclicalSector(f.sector) ->
                LynchCategory.CYCLICAL

            else -> LynchCategory.STALWART
        }
    }

    // ---------------------------------------------------------------------
    // Rationale strings (for the "Raio-X" screen)
    // ---------------------------------------------------------------------

    private fun pegyRationale(pegy: Double?): String = when {
        pegy == null -> "PEGY indisponível (crescimento ou lucro ausente/negativo)."
        pegy < 0.5 -> "PEGY de ${pegy.round2()} — subvalorizado em relação ao crescimento."
        pegy < 1.0 -> "PEGY de ${pegy.round2()} — preço justo frente ao crescimento."
        pegy < 1.5 -> "PEGY de ${pegy.round2()} — levemente acima do ideal."
        pegy < 2.0 -> "PEGY de ${pegy.round2()} — caro para o crescimento entregue."
        else -> "PEGY de ${pegy.round2()} — muito caro; penalizado."
    }

    private fun profitabilityRationale(p: Double?): String = when {
        p == null -> "Rentabilidade indisponível."
        p >= 0.20 -> "ROE/ROIC de ${p.pct()} — excelente."
        p >= 0.15 -> "ROE/ROIC de ${p.pct()} — bom."
        p >= 0.10 -> "ROE/ROIC de ${p.pct()} — mediano."
        p >= 0.05 -> "ROE/ROIC de ${p.pct()} — fraco."
        else -> "ROE/ROIC de ${p.pct()} — muito fraco; penalizado."
    }

    private fun healthRationale(netCashToMc: Double?): String = when {
        netCashToMc == null -> "Caixa/dívida indisponível; nota neutra."
        netCashToMc >= 0.20 -> "Caixa líquido de ${netCashToMc.pct()} do valor de mercado — fortaleza de caixa."
        netCashToMc >= 0.10 -> "Caixa líquido positivo (${netCashToMc.pct()} do valor de mercado)."
        netCashToMc > 0.0 -> "Caixa líquido levemente positivo (${netCashToMc.pct()})."
        netCashToMc >= -0.10 -> "Dívida líquida moderada (${netCashToMc.pct()})."
        netCashToMc >= -0.30 -> "Dívida líquida relevante (${netCashToMc.pct()})."
        else -> "Alavancagem excessiva (${netCashToMc.pct()}) — penalizado."
    }

    private fun valuationRationale(pe: Double?, pb: Double?): String =
        "P/L ${pe?.round2() ?: "—"} e P/VP ${pb?.round2() ?: "—"} — múltiplos " +
            (if ((pe != null && pe < 15.0) || (pb != null && pb < 1.5)) "descontados." else "sem desconto relevante.")

    private fun pbRationale(pb: Double?): String = when {
        pb == null || pb <= 0.0 -> "P/VP indisponível."
        pb < 1.0 -> "P/VP de ${pb.round2()} — abaixo do valor patrimonial."
        pb < 2.0 -> "P/VP de ${pb.round2()} — razoável."
        else -> "P/VP de ${pb.round2()} — caro em relação ao patrimônio."
    }

    private fun peRationale(pe: Double?): String = when {
        pe == null || pe <= 0.0 -> "P/L indisponível."
        pe < 10 -> "P/L de ${pe.round2()} — barato."
        pe < 18 -> "P/L de ${pe.round2()} — razoável."
        pe < 25 -> "P/L de ${pe.round2()} — caro."
        else -> "P/L de ${pe.round2()} — muito caro."
    }

    private fun dividendRationale(dy: Double?): String = when {
        dy == null -> "Dividend Yield indisponível."
        dy <= 0.0 -> "Não paga dividendos."
        else -> "Dividend Yield de ${dy.pct()}."
    }

    // ---------------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------------

    private fun Double.round2(): Double = round(this * 100.0) / 100.0
    private fun Double.pct(): String = "${round2() * 100}%"
}

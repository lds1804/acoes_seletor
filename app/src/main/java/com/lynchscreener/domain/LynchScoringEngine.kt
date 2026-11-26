package com.lynchscreener.domain

import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.LynchScore
import com.lynchscreener.domain.model.ScoreComponent
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Pure, side-effect-free Peter Lynch scoring engine.
 *
 * Produces a 0..100 score from normalized [Financials]. No Android dependencies,
 * fully unit-testable.
 *
 * Sector sensitivity: BANKS and INSURERS are not scored on debt leverage,
 * because liabilities are their raw material. Insurance brokers, payment
 * processors, asset managers and holdings keep the leverage rule — they carry
 * real corporate debt (debentures, earn-outs) even when their B3 sector is
 * placed under "Previdência e Seguros".
 */
class LynchScoringEngine {

    // ---------------------------------------------------------------------
    // Weights (general / financial)
    // ---------------------------------------------------------------------

    companion object {
        /**
         * Bump whenever the scoring rules change, so already persisted scores are
         * recomputed on the next launch ([com.lynchscreener.data.repository.AssetRepository.rescoreAll]).
         */
        const val SCORING_ENGINE_VERSION = 3

        // Non-financial sectors. Weights follow Lynch's GARP priorities: earnings
        // growth and the price paid for it first, then return on capital. P/VP and
        // dividend yield are deliberately small — they only confirm a thesis, they
        // do not create one.
        private const val W_PEGY = 24.0
        private const val W_PROFITABILITY = 26.0
        private const val W_GROWTH = 20.0
        private const val W_HEALTH = 12.0
        private const val W_VALUATION = 10.0
        private const val W_DIVIDEND = 8.0

        // Financial sectors: no leverage criterion (liabilities are the raw
        // material), so the free weight goes back to profitability and growth.
        private const val WF_PEGY = 22.0
        private const val WF_PROFITABILITY = 30.0
        private const val WF_GROWTH = 20.0
        private const val WF_PB = 10.0
        private const val WF_PE = 10.0
        private const val WF_DIVIDEND = 8.0

        /** Valuation split (non-financial): P/L carries more weight than P/VP. */
        private const val VALUATION_PE_POINTS = 6.0
        private const val VALUATION_PB_POINTS = 4.0

        /**
         * Dividend yield used in the PEGY denominator is capped here.
         *
         * Without the cap a 14–15% yield alone pushes PEGY to 0.2, awarding full
         * marks to a company whose earnings do not grow at all.
         */
        const val MAX_DY_FOR_PEGY = 0.08

        /** Above this the dividend earns no extra points (and raises a flag). */
        const val ATYPICAL_DY = 0.12

        /** ROE above this usually means artificially thin equity, not excellence. */
        const val ROE_OUTLIER = 0.40

        /** Max share of the PEGY weight when earnings growth is zero/negative. */
        private const val NO_GROWTH_PEGY_FRACTION = 0.34

        /** A Value Trap cannot present itself as an 80+ opportunity. */
        private const val VALUE_TRAP_CEILING = 65

        /** Explicit charge applied to a Value Trap (growth alone cannot reach it). */
        private const val VALUE_TRAP_PENALTY = 12

        /** Below this ROE the score is capped regardless of how cheap it looks. */
        const val MIN_ROE = 0.12
        private const val LOW_ROE_CEILING = 68

        /** YoY earnings drop that triggers the momentum penalty. */
        const val PROFIT_DROP = -0.15
        private const val PROFIT_DROP_PENALTY = -8.0
        private const val SEVERE_PROFIT_DROP = -0.30
        private const val SEVERE_PROFIT_DROP_PENALTY = -14.0

        private val FINANCIAL_EXCLUDED_KEYWORDS = listOf(
            "corretora de seguros", "corretoras de seguros", "insurance broker",
            "serviços financeiros diversos", "servicos financeiros diversos",
            "meios de pagamento", "pagamento", "gestão de recursos", "gestao de recursos",
            "asset management", "capital markets", "bolsa", "securitizadora",
            "exploração de imóveis", "exploracao de imoveis", "holding", "participações",
            "participacoes", "consumer finance", "crédito", "credito",
        )

        private val BANK_KEYWORDS = listOf(
            "banco", "bancos", "bancário", "bancario", "intermediários financeiros",
            "intermediarios financeiros", "bank", "banks",
        )

        private val INSURER_KEYWORDS = listOf(
            "seguradora", "seguradoras", "insurance", "resseguro", "previdência", "previdencia",
        )

        /**
         * Sectors that really hold hard assets (real estate, holdings with stakes,
         * timber, utilities with concession assets). Only these can be an Asset
         * Play on a low P/VP alone.
         */
        private val ASSET_HEAVY_KEYWORDS = listOf(
            "exploração de imóveis", "exploracao de imoveis", "incorporaç", "incorporac",
            "shopping", "imobiliári", "imobiliari", "real estate", "reit",
            // Builders carry a land bank, so book value is real inventory.
            "construç", "construc",
            "holding", "participaç", "participac", "diversos",
            "madeira", "papel", "celulose", "mineração", "mineracao", "mining",
            "energia elétrica", "energia eletrica", "água e saneamento", "agua e saneamento",
            "utilities", "concess", "logística", "logistica",
        )

        private val CYCLICAL_KEYWORDS = listOf(
            "materia", "materials", "energy", "energia", "oil", "óleo", "oleo", "gas",
            "mining", "miner", "steel", "aço", "aco", "siderurg", "metal", "commodit",
            "papel", "celulose", "petroquim", "petroq", "quim", "automobil", "montadora",
            "veículos", "veiculos", "airline", "aére", "aere", "avia", "agro",
        )

        /** Retail/industry/services: a low P/VP here is a warning, not a bargain. */
        private val NON_ASSET_HEAVY_KEYWORDS = listOf(
            "comércio", "comercio", "varejo", "eletrodoméstic", "eletrodomestic",
            "tecidos", "vestuário", "vestuario", "alimentos", "bebidas",
            "programas e serviços", "programas e servicos", "tecnologia",
            "serv.méd", "serv.med", "saúde", "saude", "educaç", "educac",
            "telecomunicaç", "telecomunicac", "transporte", "viagens e lazer",
            "máquinas e equipamentos", "maquinas e equipamentos",
            "computadores", "produtos de uso pessoal", "serviços diversos", "servicos diversos",
        )


        /**
         * True only for banks and insurers, where debt is operational.
         *
         * [subsector] is required to separate them from brokers: B3 files
         * "Corretoras de Seguros" under the same sector as "Seguradoras".
         */
        fun isFinancialSector(sector: String?, subsector: String? = null): Boolean {
            val s = sector?.lowercase().orEmpty()
            val sub = subsector?.lowercase().orEmpty()
            if (s.isBlank() && sub.isBlank()) return false

            // Exclusions win: these carry ordinary corporate debt.
            if (FINANCIAL_EXCLUDED_KEYWORDS.any { sub.contains(it) || s.contains(it) }) return false

            if (BANK_KEYWORDS.any { s.contains(it) || sub.contains(it) }) return true
            if (INSURER_KEYWORDS.any { s.contains(it) || sub.contains(it) }) return true

            // No subsector to disambiguate (US/GICS, or a generic B3 label):
            // fall back to the sector alone.
            val generic = setOf("financials", "financial", "financeiro", "financeira")
            return sub.isBlank() && s in generic
        }

        fun isCyclicalSector(sector: String?): Boolean =
            sector?.let { s -> CYCLICAL_KEYWORDS.any { s.lowercase().contains(it) } } ?: false

        /**
         * Sectors whose value is backed by real assets — the only place where a
         * P/VP below 1 is genuinely an "asset play".
         *
         * A retailer or a software company trading under book value is usually
         * destroying the book value, not hiding it.
         */
        fun isAssetHeavySector(sector: String?, subsector: String? = null): Boolean {
            val s = sector?.lowercase().orEmpty()
            val sub = subsector?.lowercase().orEmpty()
            if (s.isBlank() && sub.isBlank()) return false
            if (NON_ASSET_HEAVY_KEYWORDS.any { s.contains(it) || sub.contains(it) }) return false
            return ASSET_HEAVY_KEYWORDS.any { s.contains(it) || sub.contains(it) }
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun score(financials: Financials): LynchScore {
        val isFinancial = isFinancialSector(financials.sector, financials.subsector)
        val pe = effectivePe(financials)
        val pegy = computePegy(financials, pe)
        // Net cash is meaningless for banks/insurers: deposits, float and technical
        // provisions are raw material, not idle cash. Reporting it would also light
        // up the "Caixa Líq." seal in the ranking for the wrong companies.
        val netCashToMc = if (isFinancial) null else computeNetCashToMarketCap(financials)
        val flags = collectFlags(financials)

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
                flags = flags,
            )
        }

        val scored = buildList {
            if (isFinancial) {
                addAll(scoreFinancial(financials, pe, pegy))
            } else {
                addAll(scoreGeneral(financials, pe, pegy, netCashToMc))
            }
            momentumPenalty(financials)?.let { add(it) }
        }

        val category = classify(financials, pe, pegy)

        // Round each criterion FIRST, then sum: the breakdown shown in the UI must
        // add up to exactly the number inside the ring. Penalties are negative
        // components, so they participate in the same sum.
        val baseScore = scored.sumOf { it.points.roundToInt() }
        val (totalScore, adjustment) = applyCeilings(baseScore, category, financials)

        return LynchScore(
            totalScore = totalScore,
            category = category,
            pegy = pegy,
            netCashToMarketCap = netCashToMc,
            components = if (adjustment != null) scored + adjustment else scored,
            disqualifiers = emptyList(),
            baseScore = baseScore,
            flags = flags,
        )
    }

    /**
     * Data-quality / risk alerts shown next to the score.
     *
     * They never change the score on their own — the ones that should hurt do it
     * through a criterion or a ceiling — but they are persisted so the ranking and
     * the exported list can say *why* a number looks off.
     */
    private fun collectFlags(f: Financials): List<LynchFlag> {
        val flags = mutableListOf<LynchFlag>()
        val growth = f.earningsCagr5y ?: f.cagr5y

        if (f.earningsYoy != null && f.earningsYoy <= PROFIT_DROP) flags += LynchFlag.PROFIT_DROP
        if (growth != null && growth < 0.0) flags += LynchFlag.NEGATIVE_GROWTH
        if (f.earningsConsistent == false) flags += LynchFlag.INCONSISTENT_EARNINGS
        if ((f.dividendYield ?: 0.0) > ATYPICAL_DY) flags += LynchFlag.ATYPICAL_DY
        if ((f.roe ?: 0.0) > ROE_OUTLIER) flags += LynchFlag.HIGH_ROE
        if (f.equity != null && f.equity <= 0.0) flags += LynchFlag.THIN_EQUITY
        return flags
    }

    /**
     * Recent-earnings momentum: Lynch's warning light. A strong 5-year CAGR can
     * hide a business that deteriorated in the last twelve months, so the drop is
     * charged against the final score and flagged.
     */
    private fun momentumPenalty(f: Financials): ScoreComponent? {
        val yoy = f.earningsYoy ?: return null
        val points = when {
            yoy <= SEVERE_PROFIT_DROP -> SEVERE_PROFIT_DROP_PENALTY
            yoy <= PROFIT_DROP -> PROFIT_DROP_PENALTY
            else -> return null
        }
        return ScoreComponent(
            key = "momentum",
            label = "Momentum do lucro (YoY)",
            points = points,
            maxPoints = 0.0,
            rationale = "Lucro caiu ${yoy.pct()} contra o período anterior — possível " +
                "deterioração operacional (${points.toInt()} pts).",
        )
    }

    /**
     * Applies the penalties and hard ceilings that keep cheap-but-weak companies
     * out of the top of the ranking.
     *
     * Constraints are reported as one explicit negative component so the "Raio-X"
     * screen can show them and the visible sum still matches the ring.
     *
     * `Value Trap` gets a fixed penalty rather than only a ceiling: with no
     * earnings growth the criteria alone already cap a trap around 64 points, so a
     * ceiling at 65 would never bind. The penalty is what actually pushes it down,
     * and the ceiling stays as a safety net for edge cases.
     */
    private fun applyCeilings(
        baseScore: Int,
        category: LynchCategory,
        f: Financials,
    ): Pair<Int, ScoreComponent?> {
        var adjusted = baseScore
        var ceiling = 100
        val reasons = mutableListOf<String>()

        if (category == LynchCategory.VALUE_TRAP) {
            adjusted -= VALUE_TRAP_PENALTY
            ceiling = minOf(ceiling, VALUE_TRAP_CEILING)
            reasons += "Value Trap: −$VALUE_TRAP_PENALTY pts (teto de $VALUE_TRAP_CEILING)."
        }

        val roe = f.roe
        if (roe != null && roe < MIN_ROE) {
            ceiling = minOf(ceiling, LOW_ROE_CEILING)
            reasons += "ROE de ${roe.pct()} abaixo de ${MIN_ROE.pct()}: nota limitada a " +
                "$LOW_ROE_CEILING, pois desconto e dividendo não substituem rentabilidade."
        }

        val finalScore = minOf(adjusted, ceiling).coerceIn(0, 100)
        if (finalScore == baseScore) return baseScore to null

        return finalScore to ScoreComponent(
            key = "CEILING",
            label = "Teto / penalidade",
            points = (finalScore - baseScore).toDouble(),
            maxPoints = 0.0,
            rationale = reasons.joinToString(" "),
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
     *
     * Growth source: net-income CAGR (CVM/SEC) when available, falling back to
     * the revenue growth reported by Fundamentus. Lynch defines PEGY on
     * earnings, so earnings growth always wins.
     */
    private fun computePegy(f: Financials, pe: Double?): Double? {
        if (pe == null || pe <= 0.0) return null
        val growth = f.earningsCagr5y ?: f.cagr5y ?: return null
        // Yield is capped (see MAX_DY_FOR_PEGY) and growth is floored at zero: a
        // shrinking company must not convert a big dividend into a low PEGY.
        val effectiveDy = (f.dividendYield ?: 0.0).coerceIn(0.0, MAX_DY_FOR_PEGY)
        val effectiveGrowth = growth.coerceAtLeast(0.0)
        val denominator = (effectiveGrowth + effectiveDy) * 100.0
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
        val growth = f.earningsCagr5y ?: f.cagr5y
        return listOf(
            ScoreComponent(
                key = "pegy", label = "PEGY (preço pago pelo crescimento)",
                points = scorePegy(pegy, W_PEGY, growth), maxPoints = W_PEGY,
                rationale = pegyRationale(pegy, growth),
            ),
            ScoreComponent(
                key = "profitability", label = "Rentabilidade (ROE/ROIC)",
                points = scoreProfitability(profitability, f.roe, W_PROFITABILITY),
                maxPoints = W_PROFITABILITY,
                rationale = profitabilityRationale(profitability, f.roe),
            ),
            ScoreComponent(
                key = "growth", label = "Crescimento e consistência do lucro",
                points = scoreGrowth(growth, f.earningsConsistent),
                maxPoints = W_GROWTH,
                rationale = growthRationale(growth, f.earningsConsistent),
            ),
            ScoreComponent(
                key = "health", label = "Saúde financeira (Caixa/Dívida)",
                points = scoreHealth(netCashToMc), maxPoints = W_HEALTH,
                rationale = healthRationale(netCashToMc),
            ),
            ScoreComponent(
                key = "valuation", label = "Desconto de múltiplos (P/L + P/VP)",
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
        val growth = f.earningsCagr5y ?: f.cagr5y
        return listOf(
            ScoreComponent(
                key = "pegy", label = "PEGY (preço pago pelo crescimento)",
                points = scorePegy(pegy, WF_PEGY, growth), maxPoints = WF_PEGY,
                rationale = pegyRationale(pegy, growth),
            ),
            ScoreComponent(
                key = "profitability", label = "Rentabilidade (ROE/ROIC)",
                points = scoreProfitability(profitability, f.roe, WF_PROFITABILITY),
                maxPoints = WF_PROFITABILITY,
                rationale = profitabilityRationale(profitability, f.roe),
            ),
            ScoreComponent(
                key = "growth", label = "Crescimento e consistência do lucro",
                points = scoreGrowth(growth, f.earningsConsistent),
                maxPoints = WF_GROWTH,
                rationale = growthRationale(growth, f.earningsConsistent),
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

    private fun scorePegy(pegy: Double?, max: Double, growth: Double?): Double {
        if (pegy == null) return 0.0
        val fraction = when {
            pegy < 0.5 -> 1.00
            pegy < 1.0 -> 0.80
            pegy < 1.5 -> 0.50
            pegy < 2.0 -> 0.20
            else -> 0.0
        }
        val points = max * fraction
        // No earnings growth → dividend alone cannot buy full PEGY credit.
        return if (growth == null || growth <= 0.0) {
            points.coerceAtMost(max * NO_GROWTH_PEGY_FRACTION)
        } else {
            points
        }
    }

    private fun scoreProfitability(profitability: Double?, roe: Double?, max: Double): Double {
        if (profitability == null) return 0.0
        val fraction = when {
            profitability >= 0.25 -> 1.00
            profitability >= 0.20 -> 0.90
            profitability >= 0.15 -> 0.75
            profitability >= 0.10 -> 0.50
            profitability >= 0.05 -> 0.20
            else -> 0.0
        }
        // An extreme ROE is usually thin equity (buybacks, negative reserves), not
        // superior economics, so the top band is capped instead of rewarded.
        val capped = if (roe != null && roe > ROE_OUTLIER) minOf(fraction, 0.85) else fraction
        return max * capped
    }

    /**
     * Growth × durability. Lynch paid up for growth only when it repeated, so a
     * series with a loss year loses half of the credit.
     */
    private fun scoreGrowth(growth: Double?, consistent: Boolean?): Double {
        if (growth == null) return 0.0
        var fraction = when {
            growth >= 0.20 -> 1.00
            growth >= 0.15 -> 0.85
            growth >= 0.10 -> 0.65
            growth >= 0.05 -> 0.40
            growth >= 0.0 -> 0.15
            else -> 0.0
        }
        if (consistent == false) fraction *= 0.5
        return W_GROWTH * fraction
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

    /**
     * Valuation split: P/L carries more weight than P/VP.
     *
     * Buying under book value is a balance-sheet argument; Lynch's edge came from
     * earnings power, so the earnings multiple dominates the patrimonial one.
     */
    private fun scoreValuation(pe: Double?, pb: Double?): Double {
        var points = 0.0
        if (pe != null && pe > 0.0) {
            val fraction = when {
                pe < 5 -> 1.00
                pe < 8 -> 0.85
                pe < 12 -> 0.65
                pe < 18 -> 0.40
                pe < 25 -> 0.20
                else -> 0.0
            }
            points += VALUATION_PE_POINTS * fraction
        }
        if (pb != null && pb > 0.0) {
            val fraction = when {
                pb < 0.7 -> 1.00
                pb < 1.0 -> 0.80
                pb < 1.5 -> 0.55
                pb < 2.5 -> 0.30
                pb < 4.0 -> 0.15
                else -> 0.0
            }
            points += VALUATION_PB_POINTS * fraction
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

    /**
     * Dividend yield score, capped at [ATYPICAL_DY].
     *
     * A 14–15% yield is usually a falling price, a one-off payout or a company
     * paying out its own capital — it must not outscore a 6% yield earned with
     * growing profits.
     */
    private fun scoreDividend(dy: Double?, max: Double): Double {
        if (dy == null || dy <= 0.0) return 0.0
        val effective = dy.coerceAtMost(ATYPICAL_DY)
        val fraction = when {
            effective >= 0.08 -> 1.00
            effective >= 0.06 -> 0.85
            effective >= 0.04 -> 0.65
            effective >= 0.02 -> 0.40
            else -> 0.15
        }
        return max * fraction
    }

    // ---------------------------------------------------------------------
    // Classification
    // ---------------------------------------------------------------------

    /**
     * Lynch category. Order matters — the first rule that fires wins.
     *
     * | category    | test                                                      |
     * |-------------|-----------------------------------------------------------|
     * | Turnaround  | série histórica ruim, mas recuperação forte nos últimos 12m |
     * | Value Trap  | P/L baixo + lucro encolhendo, ROE fraco ou prejuízo na série |
     * | Fast Grower | CAGR ≥ 15% com PEGY ≤ 1.5 e série sem prejuízo             |
     * | Asset Play  | P/VP < 1 + setor intensivo em ativos (ou caixa ≥ 25% do cap) |
     * | Stalwart    | CAGR ≥ 5% com ROE ≥ 10%                                   |
     * | Cyclical    | setor cíclico conhecido                                   |
     * | Slow Grower | crescimento baixo, mas com dividendo sustentado            |
     */
    private fun classify(f: Financials, pe: Double?, pegy: Double?): LynchCategory {
        val growth = f.earningsCagr5y ?: f.cagr5y
        val roe = f.roe
        val pb = f.pb
        val dy = f.dividendYield ?: 0.0
        val cheap = pe != null && pe in 0.0..10.0
        val yoy = f.earningsYoy

        // Turnaround: the long record is still bad, but the last twelve months show
        // a real recovery. Checked BEFORE the value-trap rule — the two look alike
        // on the 5-year CAGR and only the recent trend separates them.
        if (growth != null && growth < 0.0 && yoy != null && yoy >= 0.20 &&
            (roe ?: 0.0) >= 0.05 && f.earningsConsistent != false
        ) {
            return LynchCategory.TURNAROUND
        }

        // Value trap: looks cheap but the business is shrinking or unprofitable.
        if (cheap) {
            val shrinking = (growth != null && growth < 0.0) ||
                (yoy != null && yoy <= PROFIT_DROP)
            if (shrinking || (roe != null && roe < 0.05) || f.earningsConsistent == false) {
                return LynchCategory.VALUE_TRAP
            }
        }

        // Fast grower.
        if (growth != null && growth >= 0.15 && (pegy == null || pegy <= 1.5) &&
            f.earningsConsistent != false
        ) {
            return LynchCategory.FAST_GROWER
        }

        // Asset play — only where hard assets are the thesis, or when cash alone
        // already covers a quarter of the market cap.
        if (pb != null && pb < 1.0 && (roe ?: 0.0) >= 0.05 && f.earningsConsistent != false) {
            val cashRich = (computeNetCashToMarketCap(f) ?: 0.0) >= 0.25
            if (cashRich || isAssetHeavySector(f.sector, f.subsector)) {
                return LynchCategory.ASSET_PLAY
            }
        }

        // Stalwart: steady compounder.
        if (growth != null && growth >= 0.05 && (roe ?: 0.0) >= 0.10) {
            return LynchCategory.STALWART
        }

        if (isCyclicalSector(f.sector)) return LynchCategory.CYCLICAL

        // Slow grower: little growth, but the dividend is being paid for real.
        if (dy >= 0.04 && (roe ?: 0.0) >= 0.08 && f.earningsConsistent != false) {
            return LynchCategory.SLOW_GROWER
        }

        return if (growth != null && growth >= 0.05) {
            LynchCategory.STALWART
        } else {
            LynchCategory.SLOW_GROWER
        }
    }

    // ---------------------------------------------------------------------
    // Rationale strings (for the "Raio-X" screen)
    // ---------------------------------------------------------------------

    private fun pegyRationale(pegy: Double?, growth: Double? = null): String {
        if (growth != null && growth <= 0.0) {
            return "PEGY de ${pegy?.round2() ?: "—"} sem crescimento de lucro — " +
                "dividendo não substitui crescimento; critério limitado."
        }
        return when {
            pegy == null -> "PEGY indisponível (crescimento ou lucro ausente/negativo)."
            pegy < 0.5 -> "PEGY de ${pegy.round2()} — subvalorizado em relação ao crescimento."
            pegy < 1.0 -> "PEGY de ${pegy.round2()} — preço justo frente ao crescimento."
            pegy < 1.5 -> "PEGY de ${pegy.round2()} — levemente acima do ideal."
            pegy < 2.0 -> "PEGY de ${pegy.round2()} — caro para o crescimento entregue."
            else -> "PEGY de ${pegy.round2()} — muito caro; penalizado."
        }
    }

    private fun profitabilityRationale(p: Double?, roe: Double? = null): String {
        val base = when {
            p == null -> "Rentabilidade indisponível."
            p >= 0.25 -> "ROE/ROIC de ${p.pct()} — excelente."
            p >= 0.20 -> "ROE/ROIC de ${p.pct()} — muito bom."
            p >= 0.15 -> "ROE/ROIC de ${p.pct()} — bom."
            p >= 0.10 -> "ROE/ROIC de ${p.pct()} — mediano."
            p >= 0.05 -> "ROE/ROIC de ${p.pct()} — fraco."
            else -> "ROE/ROIC de ${p.pct()} — muito fraco; penalizado."
        }
        return if (roe != null && roe > ROE_OUTLIER) {
            "$base ROE acima de ${ROE_OUTLIER.pct()} costuma indicar patrimônio " +
                "líquido muito baixo — nota limitada."
        } else {
            base
        }
    }

    private fun growthRationale(growth: Double?, consistent: Boolean?): String {
        if (growth == null) return "CAGR de lucro indisponível."
        val base = when {
            growth >= 0.20 -> "CAGR de ${growth.pct()} a.a. — crescimento alto."
            growth >= 0.15 -> "CAGR de ${growth.pct()} a.a. — forte."
            growth >= 0.10 -> "CAGR de ${growth.pct()} a.a. — saudável."
            growth >= 0.05 -> "CAGR de ${growth.pct()} a.a. — moderado."
            growth >= 0.0 -> "CAGR de ${growth.pct()} a.a. — estagnado."
            else -> "CAGR de ${growth.pct()} a.a. — lucro em queda."
        }
        return if (consistent == false) {
            "$base Série com prejuízo em algum ano — crédito reduzido."
        } else {
            base
        }
    }

    private fun dividendRationale(dy: Double?): String = when {
        dy == null -> "Dividendo indisponível."
        dy <= 0.0 -> "Não distribui dividendos."
        dy > ATYPICAL_DY -> "DY de ${dy.pct()} — atípico (payout ou preço em queda); " +
            "não gera pontos extras."
        dy >= 0.08 -> "DY de ${dy.pct()} — alto e consistente."
        dy >= 0.06 -> "DY de ${dy.pct()} — bom."
        dy >= 0.04 -> "DY de ${dy.pct()} — razoável."
        else -> "DY de ${dy.pct()} — baixo."
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

    // ---------------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------------

    private fun Double.round2(): Double = round(this * 100.0) / 100.0
    private fun Double.pct(): String = "${round2() * 100}%"
}

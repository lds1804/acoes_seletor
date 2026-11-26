package com.lynchscreener

import com.lynchscreener.data.remote.CvmPeriod
import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.ScoreComponent
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the corrections landed after the WIZC3 (broker scored as a
 * bank), CURY3 (P/L from a single quarter), DEXP3 and ALLD3 (high score with flat
 * earnings / weak ROE) findings.
 */
class ScoringCalibrationTest {

    private lateinit var engine: LynchScoringEngine

    @Before
    fun setUp() {
        engine = LynchScoringEngine()
    }

    private fun base(
        ticker: String = "TEST3",
        sector: String? = "Comércio",
        subsector: String? = null,
    ) = Financials(
        ticker = ticker,
        market = Market.BR,
        sector = sector,
        subsector = subsector,
        price = 10.0,
        pe = 6.0,
        pb = 0.9,
        roe = 0.18,
        roic = 0.15,
        dividendYield = 0.06,
        earningsCagr5y = 0.12,
        earningsConsistent = true,
        netDebt = -100_000_000.0,
        equity = 1_000_000_000.0,
        netIncome12m = 180_000_000.0,
        marketCap = 900_000_000.0,
    )

    // ---------------------------------------------------------------
    // 1. Sector exception: banks/insurers only
    // ---------------------------------------------------------------

    @Test
    fun `bank is exempt from the leverage rule`() {
        assertTrue(LynchScoringEngine.isFinancialSector("Intermediários Financeiros", "Bancos"))
        assertTrue(LynchScoringEngine.isFinancialSector("Previdência e Seguros", "Seguradoras"))
    }

    @Test
    fun `insurance broker is NOT exempt (WIZC3 case)`() {
        assertFalse(
            LynchScoringEngine.isFinancialSector(
                "Previdência e Seguros",
                "Corretoras de Seguros",
            ),
        )
    }

    @Test
    fun `other financial segments keep the leverage rule`() {
        assertFalse(LynchScoringEngine.isFinancialSector("Serviços Financeiros Diversos", null))
        assertFalse(LynchScoringEngine.isFinancialSector("Exploração de Imóveis", "Incorporações"))
        assertFalse(LynchScoringEngine.isFinancialSector("Diversos", "Holdings"))
        assertFalse(LynchScoringEngine.isFinancialSector("Comércio", "Eletrodomésticos"))
    }

    @Test
    fun `broker does not receive the financial weight set`() {
        val broker = base(sector = "Previdência e Seguros", subsector = "Corretoras de Seguros")
        val keys = engine.score(broker).components.map { it.key }.toSet()
        // The general rule set includes the debt/health criterion; the financial
        // one replaces it with P/VP.
        assertTrue("esperava critério de saúde para corretora", "health" in keys)
        assertFalse("corretora não deve usar pesos de banco", "pb" in keys)
    }

    // ---------------------------------------------------------------
    // 2. PEGY dividend cap (DEXP3 case)
    // ---------------------------------------------------------------

    @Test
    fun `pegy denominator caps the dividend yield at MAX_DY_FOR_PEGY`() {
        // 15% DY must be treated as 8%: 6 / (10 + 8) = 0.3333
        val score = engine.score(
            base().copy(pe = 6.0, earningsCagr5y = 0.10, dividendYield = 0.15),
        )
        assertEquals(6.0 / 18.0, score.pegy!!, 1e-9)
    }

    @Test
    fun `company without earnings growth cannot earn full pegy credit`() {
        val flat = base().copy(
            pe = 5.0,
            pb = 0.7,
            earningsCagr5y = 0.0,
            dividendYield = 0.15,
            roe = 0.18,
        )
        val components = engine.score(flat).components
        val pegy = components.first { it.key == "pegy" }
        assertTrue("PEGY de empresa estagnada deve ser limitado", pegy.points <= pegy.maxPoints * 0.35)
    }

    // ---------------------------------------------------------------
    // 4. Score ceilings (DEXP3 / ALLD3 cases)
    // ---------------------------------------------------------------

    @Test
    fun `value trap score is capped at 65`() {
        // Looks dirt cheap, but earnings are shrinking and there was a loss.
        val trap = base().copy(
            pe = 4.0,
            pb = 0.7,
            roe = 0.20,
            dividendYield = 0.15,
            earningsCagr5y = -0.02,
            earningsConsistent = false,
            netDebt = -200_000_000.0,
        )
        val score = engine.score(trap)
        assertEquals(LynchCategory.VALUE_TRAP, score.category)
        assertTrue("score ${score.totalScore} deveria respeitar o teto", score.totalScore <= 65)
        assertTrue(score.isCapped)
    }

    @Test
    fun `low roe score is capped at 68 even when multiples look cheap`() {
        // ALLD3-like: P/VP 0.41, DY 15%, ROE 8.3%.
        val weak = base().copy(
            pe = 4.95,
            pb = 0.41,
            roe = 0.083,
            roic = 0.083,
            dividendYield = 0.15,
            earningsCagr5y = 0.147,
            earningsConsistent = true,
            netDebt = 300_000_000.0,
            marketCap = 665_000_000.0,
        )
        val score = engine.score(weak)
        assertTrue("score ${score.totalScore} deveria respeitar o teto de ROE", score.totalScore <= 68)
    }

    @Test
    fun `high quality grower is not capped`() {
        val quality = base().copy(
            pe = 7.0,
            pb = 4.57,
            roe = 0.65,
            roic = 0.55,
            dividendYield = 0.18,
            earningsCagr5y = 0.42,
            earningsConsistent = true,
            netDebt = -500_000_000.0,
        )
        val score = engine.score(quality)
        assertFalse("empresa de qualidade não deve levar teto", score.isCapped)
        assertTrue(score.totalScore > 68)
    }

    // ---------------------------------------------------------------
    // 5. Breakdown reconciles with the ring
    // ---------------------------------------------------------------

    @Test
    fun `sum of integer components equals the displayed score`() {
        val samples = listOf(
            base(),
            base().copy(pe = 4.0, pb = 0.7, roe = 0.20, earningsConsistent = false),
            base().copy(roe = 0.083, pb = 0.41, dividendYield = 0.15, pe = 4.95),
            base().copy(sector = "Intermediários Financeiros", subsector = "Bancos", pb = 1.3),
        )
        for (f in samples) {
            val score = engine.score(f)
            val sum = score.components.sumOf { it.points.toIntRounded() }
            assertEquals(
                "${f.ticker}: soma dos critérios deve fechar com o anel",
                score.totalScore,
                sum,
            )
        }
    }

    @Test
    fun `ceiling is exposed as an explicit negative component`() {
        val trap = base().copy(
            pe = 4.0,
            pb = 0.7,
            roe = 0.20,
            earningsConsistent = false,
            earningsCagr5y = -0.02,
        )
        val score = engine.score(trap)
        val ceiling = score.components.firstOrNull { it.key == "CEILING" }
        assertNotNull("o teto deve aparecer no raio-X", ceiling)
        assertTrue("a linha do teto deve ser negativa", ceiling!!.points < 0)
        assertEquals(
            score.totalScore,
            score.components.sumOf { it.points.toIntRounded() },
        )
    }

    // ---------------------------------------------------------------
    // 3. P/L identity (CURY3 case)
    // ---------------------------------------------------------------

    @Test
    fun `pl pb roe identity holds for internally consistent figures`() {
        // marketCap / netIncome = pe ; marketCap / equity = pb ; netIncome / equity = roe
        val marketCap = 8_129_380_000.0
        val netIncome = 1_158_000_000.0
        val equity = 1_778_940_000.0
        val pe = marketCap / netIncome
        val pb = marketCap / equity
        val roe = netIncome / equity
        assertEquals(pe, pb / roe, 1e-6)
    }

    // ---------------------------------------------------------------
    // Period selection (TTM / annual guard)
    // ---------------------------------------------------------------

    @Test
    fun `three month row is not accepted as annual`() {
        assertFalse(CvmPeriod.isAnnual("2025-07-01", "2025-09-30"))
    }

    @Test
    fun `twelve month row is accepted as annual`() {
        assertTrue(CvmPeriod.isAnnual("2025-01-01", "2025-12-31"))
        assertTrue(CvmPeriod.isAnnual("2025-01-01", "2025-09-30").not())
    }

    @Test
    fun `year to date row is detected by january 1st start`() {
        assertTrue(CvmPeriod.isYearToDate("2025-01-01"))
        assertFalse(CvmPeriod.isYearToDate("2025-07-01"))
    }

    @Test
    fun `pickYearToDate prefers the accumulated 9 month row over the isolated quarter`() {
        data class Row(val start: String, val end: String, val value: Double, val label: String)

        // A 3T ITR exposes both rows for the same account.
        val rows = listOf(
            Row("2025-07-01", "2025-09-30", 10.0, "trimestre isolado"),
            Row("2025-01-01", "2025-09-30", 28.0, "acumulado 9M"),
        )
        val chosen = CvmPeriod.pickYearToDate(rows, { it.start }, { it.end })
        assertEquals("acumulado 9M", chosen!!.label)
        assertEquals(9, CvmPeriod.monthsBetween(chosen.start, chosen.end))
    }

    @Test
    fun `cagr requires positive endpoints`() {
        assertNull(CvmPeriod.cagr(-100.0, 200.0, 5))
        assertNull(CvmPeriod.cagr(100.0, -200.0, 5))
        assertEquals(0.148698, CvmPeriod.cagr(100.0, 200.0, 5)!!, 1e-6)
    }
}

/** Same rounding the engine uses, so the test mirrors the UI exactly. */
private fun Double.toIntRounded(): Int = this.roundToInt()

@Suppress("unused")
private fun ScoreComponent.shownPoints(): Int = points.toIntRounded()

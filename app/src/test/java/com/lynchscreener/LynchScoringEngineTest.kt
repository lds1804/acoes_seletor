package com.lynchscreener

import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LynchScoringEngineTest {

    private lateinit var engine: LynchScoringEngine

    @Before
    fun setUp() {
        engine = LynchScoringEngine()
    }

    // ---------------------------------------------------------------
    // PEGY math
    // ---------------------------------------------------------------

    @Test
    fun `pegy uses percentage-point denominator`() {
        // P/L 20, growth 15%, yield 5% => 20 / (15 + 5) = 1.0
        val score = engine.score(healthy().copy(pe = 20.0, cagr5y = 0.15, dividendYield = 0.05))
        assertEquals(1.0, score.pegy!!, 1e-9)
    }

    @Test
    fun `pegy is null when growth plus yield is not positive`() {
        val score = engine.score(healthy().copy(pe = 20.0, cagr5y = -0.05, dividendYield = 0.0))
        assertNull(score.pegy)
    }

    @Test
    fun `pegy is null when pe is negative`() {
        val score = engine.score(healthy().copy(pe = -5.0))
        assertNull(score.pegy)
    }

    @Test
    fun `pe is derived from price and eps when not provided`() {
        // P/E = 30 / 3 = 10 in both cases => identical scores.
        val derived = engine.score(healthy().copy(pe = null, price = 30.0, earningsPerShare = 3.0))
        val direct = engine.score(healthy().copy(pe = 10.0, price = 30.0, earningsPerShare = 3.0))
        assertEquals(direct.totalScore, derived.totalScore)
    }

    // ---------------------------------------------------------------
    // Disqualification
    // ---------------------------------------------------------------

    @Test
    fun `negative earnings disqualify to zero`() {
        val score = engine.score(healthy().copy(pe = -8.0, netIncome12m = -1_000_000.0))
        assertEquals(0, score.totalScore)
        assertTrue(score.isDisqualified)
        assertTrue(score.disqualifiers.isNotEmpty())
    }

    @Test
    fun `negative equity disqualifies to zero`() {
        val score = engine.score(healthy().copy(equity = -500_000_000.0))
        assertEquals(0, score.totalScore)
        assertTrue(score.isDisqualified)
    }

    // ---------------------------------------------------------------
    // Sector sensitivity — banks are NOT penalized for debt
    // ---------------------------------------------------------------

    @Test
    fun `financial sector ignores debt leverage`() {
        val lowDebtBank = bank().copy(totalDebt = 10_000_000_000.0, cashAndEquivalents = 8_000_000_000.0)
        val highDebtBank = bank().copy(totalDebt = 900_000_000_000.0, cashAndEquivalents = 100_000_000_000.0)

        val a = engine.score(lowDebtBank)
        val b = engine.score(highDebtBank)

        // Identical fundamentals except leverage => identical score.
        assertEquals(a.totalScore, b.totalScore)
        // The "health/debt" component must not exist for financials.
        assertFalse(a.components.any { it.key == "health" })
    }

    @Test
    fun `non financial sector penalizes high debt`() {
        val lowDebt = industrial().copy(totalDebt = 100_000_000.0, cashAndEquivalents = 90_000_000.0)
        val highDebt = industrial().copy(totalDebt = 2_000_000_000.0, cashAndEquivalents = 50_000_000.0)

        val a = engine.score(lowDebt)
        val b = engine.score(highDebt)

        assertTrue("High-debt industrial should score lower", b.totalScore < a.totalScore)
        assertTrue(a.components.any { it.key == "health" })
    }

    // ---------------------------------------------------------------
    // Classification
    // ---------------------------------------------------------------

    @Test
    fun `fast grower classification`() {
        val score = engine.score(healthy().copy(cagr5y = 0.25, pe = 20.0, dividendYield = 0.02))
        assertEquals(LynchCategory.FAST_GROWER, score.category)
    }

    @Test
    fun `asset play classification`() {
        val score = engine.score(industrial().copy(pb = 0.8, roe = 0.10))
        assertEquals(LynchCategory.ASSET_PLAY, score.category)
    }

    @Test
    fun `value trap classification for cheap but declining company`() {
        val score = engine.score(industrial().copy(pe = 8.0, cagr5y = -0.03, roe = 0.08))
        assertEquals(LynchCategory.VALUE_TRAP, score.category)
    }

    @Test
    fun `cyclical sector classification fallback`() {
        val score = engine.score(
            industrial().copy(sector = "Materiais Básicos", pe = 12.0, cagr5y = 0.02, roe = 0.12)
        )
        assertEquals(LynchCategory.CYCLICAL, score.category)
    }

    // ---------------------------------------------------------------
    // Score range & quality
    // ---------------------------------------------------------------

    @Test
    fun `high quality company scores in upper band`() {
        val score = engine.score(
            healthy().copy(
                pe = 8.0, pb = 0.9, roe = 0.25, roic = 0.22, cagr5y = 0.20, dividendYield = 0.05,
                totalDebt = 0.0, cashAndEquivalents = 500_000_000.0, marketCap = 1_000_000_000.0,
            )
        )
        assertTrue("Expected >= 70, got ${score.totalScore}", score.totalScore >= 70)
        assertTrue(score.totalScore <= 100)
    }

    @Test
    fun `every non disqualified score is within 0 and 100`() {
        val score = engine.score(
            industrial().copy(
                pe = 25.0, pb = 3.0, roe = 0.05, cagr5y = 0.0, dividendYield = 0.0,
                totalDebt = 5_000_000_000.0, cashAndEquivalents = 0.0, marketCap = 1_000_000_000.0,
            )
        )
        assertTrue(score.totalScore in 0..100)
    }

    @Test
    fun `net cash to market cap is computed`() {
        val score = engine.score(
            industrial().copy(totalDebt = 0.0, cashAndEquivalents = 200_000_000.0, marketCap = 1_000_000_000.0)
        )
        assertEquals(0.2, score.netCashToMarketCap!!, 1e-9)
    }

    @Test
    fun `component points sum to total score`() {
        val score = engine.score(healthy())
        assertEquals(score.totalScore, score.components.sumOf { it.points }.roundToInt())
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private fun healthy() = Financials(
        ticker = "GOOD", market = Market.BR, sector = "Tecnologia",
        pe = 15.0, pb = 2.0, roe = 0.20, roic = 0.18,
        dividendYield = 0.03, cagr5y = 0.12,
        totalDebt = 200_000_000.0, cashAndEquivalents = 100_000_000.0,
        equity = 1_000_000_000.0, netIncome12m = 250_000_000.0,
        marketCap = 2_000_000_000.0, sharesOutstanding = 100_000_000.0, price = 20.0,
    )

    private fun industrial() = Financials(
        ticker = "INDU", market = Market.BR, sector = "Construção Civil",
        pe = 10.0, pb = 1.5, roe = 0.18, roic = 0.15,
        dividendYield = 0.04, cagr5y = 0.10,
        totalDebt = 100_000_000.0, cashAndEquivalents = 90_000_000.0,
        equity = 500_000_000.0, netIncome12m = 120_000_000.0,
        marketCap = 1_000_000_000.0, sharesOutstanding = 50_000_000.0, price = 20.0,
    )

    private fun bank() = Financials(
        ticker = "BANK", market = Market.BR, sector = "Financeiro",
        pe = 8.0, pb = 0.9, roe = 0.22, roic = 0.19,
        dividendYield = 0.05, cagr5y = 0.10,
        totalDebt = 50_000_000_000.0, cashAndEquivalents = 45_000_000_000.0,
        equity = 20_000_000_000.0, netIncome12m = 5_000_000_000.0,
        marketCap = 40_000_000_000.0, sharesOutstanding = 1_000_000_000.0, price = 40.0,
    )
}

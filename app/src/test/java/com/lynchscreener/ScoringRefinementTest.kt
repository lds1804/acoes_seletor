package com.lynchscreener

import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.LynchFlag
import com.lynchscreener.domain.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rules added after the first calibration round: the PEGY ceiling on base-effect
 * growth, the sector gates that were letting every commodity upcycle through as a
 * "Fast Grower", and the atypical-profit alert.
 */
class ScoringRefinementTest {

    private lateinit var engine: LynchScoringEngine

    @Before
    fun setUp() {
        engine = LynchScoringEngine()
    }

    // ---------------------------------------------------------------
    // CAGR ceiling inside PEGY only
    // ---------------------------------------------------------------

    @Test
    fun `pegy caps growth at thirty percent`() {
        // P/L 30, growth 90%, no dividend: uncapped PEGY would be 0.33.
        // Capped at 30%: 30 / 30 = 1.00.
        val score = engine.score(
            base().copy(pe = 30.0, earningsCagr5y = 0.90, cagr5y = 0.90, dividendYield = 0.0),
        )
        assertEquals(1.0, score.pegy!!, 1e-9)
    }

    @Test
    fun `growth below the ceiling is untouched`() {
        val score = engine.score(
            base().copy(pe = 20.0, earningsCagr5y = 0.15, dividendYield = 0.05),
        )
        assertEquals(1.0, score.pegy!!, 1e-9)
    }

    // ---------------------------------------------------------------
    // Sector gates on the classification
    // ---------------------------------------------------------------

    @Test
    fun `commodity producer is cyclical even with high growth`() {
        val score = engine.score(
            base().copy(
                sector = "Materiais Básicos",
                subsector = "Siderurgia e Metalurgia",
                pe = 8.0,
                earningsCagr5y = 0.40,
                earningsYoy = 0.30,
            ),
        )
        assertEquals(LynchCategory.CYCLICAL, score.category)
    }

    @Test
    fun `bank with high growth is a stalwart not a fast grower`() {
        val score = engine.score(
            base().copy(
                sector = "Financeiro",
                subsector = "Bancos",
                pe = 9.0,
                roe = 0.22,
                earningsCagr5y = 0.25,
                earningsYoy = 0.20,
            ),
        )
        assertEquals(LynchCategory.STALWART, score.category)
    }

    @Test
    fun `fast grower needs the last twelve months to confirm`() {
        val growing = engine.score(
            base().copy(pe = 12.0, earningsCagr5y = 0.30, earningsYoy = 0.10),
        )
        assertEquals(LynchCategory.FAST_GROWER, growing.category)

        // Same five-year record, but the last four quarters are already shrinking:
        // that is the top of the cycle, not a growth story.
        val topped = engine.score(
            base().copy(pe = 12.0, earningsCagr5y = 0.30, earningsYoy = -0.05),
        )
        assertFalse(topped.category == LynchCategory.FAST_GROWER)
    }

    // ---------------------------------------------------------------
    // Atypical profit
    // ---------------------------------------------------------------

    @Test
    fun `yoy above eighty percent raises the flag`() {
        val score = engine.score(base().copy(pe = 14.0, earningsCagr5y = 0.20, earningsYoy = 0.95))
        assertTrue(score.flags.contains(LynchFlag.ATYPICAL_PROFIT))
    }

    @Test
    fun `five year cagr above fifty percent raises the flag`() {
        val score = engine.score(base().copy(pe = 25.0, earningsCagr5y = 0.65, earningsYoy = 0.10))
        assertTrue(score.flags.contains(LynchFlag.ATYPICAL_PROFIT))
    }

    @Test
    fun `ordinary growth does not raise the flag`() {
        val score = engine.score(base().copy(pe = 15.0, earningsCagr5y = 0.18, earningsYoy = 0.12))
        assertFalse(score.flags.contains(LynchFlag.ATYPICAL_PROFIT))
    }

    @Test
    fun `atypical profit is not rewarded with extra points`() {
        // 25% and 90% CAGR both max out the growth criterion — no bonus above it.
        val normal = engine.score(base().copy(pe = 20.0, earningsCagr5y = 0.25, earningsYoy = 0.10))
        val extreme = engine.score(base().copy(pe = 20.0, earningsCagr5y = 0.90, earningsYoy = 0.10))
        assertEquals(normal.totalScore, extreme.totalScore)
    }

    // ---------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------

    private fun base() = Financials(
        ticker = "TEST", market = Market.BR, sector = "Tecnologia",
        pe = 15.0, pb = 2.0, roe = 0.20, roic = 0.18,
        dividendYield = 0.03, cagr5y = 0.12,
        totalDebt = 200_000_000.0, cashAndEquivalents = 100_000_000.0,
        equity = 1_000_000_000.0, netIncome12m = 250_000_000.0,
        marketCap = 2_000_000_000.0, sharesOutstanding = 100_000_000.0, price = 20.0,
    )
}

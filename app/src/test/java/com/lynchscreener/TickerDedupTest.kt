package com.lynchscreener

import com.lynchscreener.domain.TickerDedup
import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.RankingItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ON, PN and Units of the same company must appear once, and the survivor must be
 * the class a real investor trades (the most liquid one).
 */
class TickerDedupTest {

    @Test
    fun `root groups the share classes`() {
        assertEquals("PETR", TickerDedup.root("PETR3"))
        assertEquals(TickerDedup.root("PETR3"), TickerDedup.root("PETR4"))
        assertEquals(TickerDedup.root("TAEE3"), TickerDedup.root("TAEE11"))
        assertEquals(TickerDedup.root("BBDC3"), TickerDedup.root("BBDC4"))
        // BRK.B -> BRKB, so the dot does not create a new group.
        assertEquals("BRKB", TickerDedup.root("BRK.B"))
    }

    @Test
    fun `Alphabet classes are merged`() {
        assertEquals(TickerDedup.root("GOOG"), TickerDedup.root("GOOGL"))
    }

    @Test
    fun `most liquid class survives`() {
        val pn = company("PETR4", liquidity = 900_000_000.0, score = 70)
        val on = company("PETR3", liquidity = 300_000_000.0, score = 72)

        val result = TickerDedup.collapse(listOf(on, pn))

        assertEquals(listOf("PETR4"), result.map { it.ticker })
    }

    @Test
    fun `market cap breaks a liquidity tie`() {
        val unit = company("TAEE11", liquidity = 1_000.0, marketCap = 9e9, score = 60)
        val on = company("TAEE3", liquidity = 1_000.0, marketCap = 3e9, score = 65)

        val result = TickerDedup.collapse(listOf(on, unit))

        assertEquals(listOf("TAEE11"), result.map { it.ticker })
    }

    @Test
    fun `score then ticker break the remaining ties`() {
        val low = company("ABCD3", liquidity = null, marketCap = null, score = 40)
        val high = company("ABCD4", liquidity = null, marketCap = null, score = 55)

        assertEquals(
            listOf("ABCD4"),
            TickerDedup.collapse(listOf(low, high)).map { it.ticker },
        )
        // Fully tied: deterministic (alphabetical) so the list does not flicker.
        val a = company("ABCD3", liquidity = null, marketCap = null, score = 50)
        val b = company("ABCD4", liquidity = null, marketCap = null, score = 50)
        assertEquals(listOf("ABCD3"), TickerDedup.collapse(listOf(b, a)).map { it.ticker })
    }

    @Test
    fun `distinct companies are untouched and order is preserved`() {
        val vale = company("VALE3", liquidity = 2e9, score = 80)
        val itub = company("ITUB4", liquidity = 1e9, score = 70)
        val itubOn = company("ITUB3", liquidity = 1e8, score = 70)

        val result = TickerDedup.collapse(listOf(vale, itubOn, itub))

        assertEquals(listOf("VALE3", "ITUB4"), result.map { it.ticker })
    }

    @Test
    fun `ranking items collapse with the same rule`() {
        val pn = RankingItem(company("GOOGL", liquidity = 5e9, score = 88), 1, 1)
        val on = RankingItem(company("GOOG", liquidity = 4e9, score = 88), 2, 2)

        val result = TickerDedup.collapseItems(listOf(pn, on))

        assertEquals(1, result.size)
        assertEquals("GOOGL", result.first().company.ticker)
    }

    private fun company(
        ticker: String,
        liquidity: Double? = null,
        marketCap: Double? = null,
        score: Int = 50,
    ) = RankedCompany(
        ticker = ticker,
        market = "BR",
        name = ticker,
        sector = "Petróleo",
        subsector = null,
        price = 10.0,
        marketCap = marketCap,
        pe = 8.0,
        pb = 1.2,
        roe = 0.18,
        roic = null,
        dividendYield = 0.05,
        cagr5y = 0.1,
        pegy = 0.8,
        netDebt = null,
        netCashToMarketCap = null,
        equity = null,
        netIncome12m = null,
        sharesOutstanding = null,
        dividendPerShare = null,
        liquidity = liquidity,
        lynchScore = score,
        lynchCategory = "Stalwart",
        breakdownJson = null,
        earningsCagr5y = null,
        earningsConsistent = true,
        earningsHistoryJson = null,
        earningsBasis = null,
        earningsYoy = null,
        alertFlags = null,
        priceUpdatedAt = null,
        fundamentalsUpdatedAt = null,
    )
}

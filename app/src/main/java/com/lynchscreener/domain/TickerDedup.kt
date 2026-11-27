package com.lynchscreener.domain

import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.RankingItem

/**
 * Collapses the several share classes of a single company into one line.
 *
 * A company lists ON, PN and Units — `PETR3`/`PETR4`, `ITUB3`/`ITUB4`,
 * `TAEE3`/`TAEE11` — and Alphabet trades as `GOOGL` and `GOOG`. All of them show
 * the same fundamentals, so the ranking would otherwise repeat the same business
 * two or three times and crowd out everything else, most visibly in the Top 30.
 *
 * The root is the first four characters of the ticker: B3 convention puts the
 * class digit in the fifth position (`VALE3`, `BBDC3`/`BBDC4`, `ALUP11`, `CCRO3`).
 * Fragments shorter than four characters, and tickers that share fewer than four
 * characters, are never merged — `GOOGL`/`GOOG` are special-cased because they
 * differ inside the four-character window.
 */
object TickerDedup {

    /** Tickers whose four-character root is not shared with their sibling. */
    private val EXTRA_ROOTS = mapOf(
        "GOOG" to "GOOGL",
        "GOOGL" to "GOOGL",
    )

    /**
     * Root key used to group the share classes. Handles `BRK.B` (dot stripped to
     * `BRKB`) so the class suffix does not create a new group.
     */
    fun root(ticker: String): String {
        val clean = ticker.uppercase().replace(".", "").replace("-", "").trim()
        if (clean.isEmpty()) return clean
        EXTRA_ROOTS[clean]?.let { return it }
        return if (clean.length <= 4) clean else clean.take(4)
    }

    /**
     * Keeps one row per company.
     *
     * Winner logic is liquidity first (the class a real investor can actually
     * trade), then market cap (a Unit usually carries the whole company), then
     * score, then ticker — always deterministic, so the list does not flicker
     * between recompositions.
     */
    fun collapse(companies: List<RankedCompany>): List<RankedCompany> {
        if (companies.isEmpty()) return companies
        val best = HashMap<String, RankedCompany>(companies.size)
        for (company in companies) {
            val key = root(company.ticker)
            val current = best[key]
            best[key] = if (current == null || wins(company, current)) company else current
        }
        // Preserve the incoming order (already ranked) while dropping the losers.
        val keep = best.values.mapTo(HashSet()) { it.ticker }
        return companies.filter { it.ticker in keep }
    }

    /** Ranking items wrapper — same winner logic, used by the ranking screen. */
    fun collapseItems(items: List<RankingItem>): List<RankingItem> {
        if (items.isEmpty()) return items
        val best = HashMap<String, RankingItem>(items.size)
        for (item in items) {
            val key = root(item.company.ticker)
            val current = best[key]
            best[key] = if (current == null || wins(item, current)) item else current
        }
        val keep = best.values.mapTo(HashSet()) { it.company.ticker }
        return items.filter { it.company.ticker in keep }
    }

    private fun wins(candidate: RankingItem, current: RankingItem): Boolean =
        compare(
            liquidity = candidate.company.liquidity,
            marketCap = candidate.company.marketCap,
            score = candidate.company.lynchScore,
            ticker = candidate.company.ticker,
            otherLiquidity = current.company.liquidity,
            otherMarketCap = current.company.marketCap,
            otherScore = current.company.lynchScore,
            otherTicker = current.company.ticker,
        )

    private fun wins(candidate: RankedCompany, current: RankedCompany): Boolean =
        compare(
            liquidity = candidate.liquidity,
            marketCap = candidate.marketCap,
            score = candidate.lynchScore,
            ticker = candidate.ticker,
            otherLiquidity = current.liquidity,
            otherMarketCap = current.marketCap,
            otherScore = current.lynchScore,
            otherTicker = current.ticker,
        )

    private fun compare(
        liquidity: Double?,
        marketCap: Double?,
        score: Int,
        ticker: String,
        otherLiquidity: Double?,
        otherMarketCap: Double?,
        otherScore: Int,
        otherTicker: String,
    ): Boolean {
        val liq = liquidity ?: 0.0
        val otherLiq = otherLiquidity ?: 0.0
        if (liq != otherLiq) return liq > otherLiq
        val cap = marketCap ?: 0.0
        val otherCap = otherMarketCap ?: 0.0
        if (cap != otherCap) return cap > otherCap
        if (score != otherScore) return score > otherScore
        // Prefer the shorter ticker (the ON) when everything else ties.
        return ticker < otherTicker
    }
}

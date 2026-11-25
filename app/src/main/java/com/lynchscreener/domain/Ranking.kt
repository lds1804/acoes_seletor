package com.lynchscreener.domain

import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.RankingItem
import com.lynchscreener.domain.model.SectorStats

/**
 * Pure ranking logic — no Android dependencies, unit-testable.
 *
 * Assigns an overall position (best first) and a within-sector position to
 * each company, and computes sector aggregates (mean/median of P/L, P/VP and
 * ROE) so the UI can show whether a stock is cheaper or more profitable than
 * its direct competitors.
 */
object Ranking {

    /** Companies ordered by [RankedCompany.lynchScore] descending. */
    fun rank(companies: List<RankedCompany>): List<RankingItem> {
        val ordered = companies.sortedWith(
            compareByDescending<RankedCompany> { it.lynchScore }
                .thenBy { it.ticker }
        )

        val sectorRankByTicker = ordered
            .groupBy { it.sector ?: OUTROS }
            .flatMap { (_, sectorList) ->
                val sorted = sectorList.sortedWith(
                    compareByDescending<RankedCompany> { it.lynchScore }
                        .thenBy { it.ticker }
                )
                sorted.mapIndexed { index, company -> company.ticker to (index + 1) }
            }
            .toMap()

        return ordered.mapIndexed { index, company ->
            RankingItem(
                company = company,
                overallRank = index + 1,
                sectorRank = sectorRankByTicker[company.ticker] ?: 1,
            )
        }
    }

    /** Sector → aggregate stats, keyed by the normalized sector name. */
    fun sectorStats(companies: List<RankedCompany>): Map<String, SectorStats> =
        companies.groupBy { it.sector ?: OUTROS }
            .mapValues { (sector, list) ->
                val pe = list.mapNotNull { it.pe }
                val pb = list.mapNotNull { it.pb }
                val roe = list.mapNotNull { it.roe }
                SectorStats(
                    sector = sector,
                    count = list.size,
                    avgPe = pe.meanOrNull(),
                    avgPb = pb.meanOrNull(),
                    avgRoe = roe.meanOrNull(),
                    medianPe = pe.medianOrNull(),
                    medianPb = pb.medianOrNull(),
                    medianRoe = roe.medianOrNull(),
                )
            }

    const val OUTROS = "Outros"

    private fun List<Double>.meanOrNull(): Double? =
        if (isEmpty()) null else sum() / size

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = size / 2
        return if (size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }
}

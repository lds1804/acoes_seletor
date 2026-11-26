package com.lynchscreener.ui.format

import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankingItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the compact Markdown table used by "Copiar Top N".
 *
 * Pure and dependency-free (only java.text), so it is unit-tested without an
 * Android runtime and can be reused by any export surface later.
 */
object MarkdownExport {

    /** Clipboard payload size limit is far above this, but keep the list sane. */
    const val DEFAULT_LIMIT = 30
    const val LARGE_LIMIT = 50

    private val locale = Locale("pt", "BR")

    /** 1.234,5 — used for ROE, DY, CAGR and YoY (already multiplied by 100). */
    private val oneDecimal = DecimalFormat("#,##0.0", DecimalFormatSymbols(locale))

    /** 43,03 — used for the price. */
    private val twoDecimals = DecimalFormat("#,##0.00", DecimalFormatSymbols(locale))

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private const val COLUMNS =
        "| # | Ticker | Empresa | Setor | Categoria | Preço | Nota | PEGY | P/L | P/VP | " +
            "ROE | DY | CAGR 5a | YoY TTM | Tags/Alertas |"

    /** Nine numeric columns are right-aligned; the rest are text. */
    private const val SEPARATOR =
        "|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|"

    /**
     * @param items already filtrados e ordenados como aparecem na tela.
     * @param filterLabel "Todos" ou o setor selecionado.
     */
    fun build(
        items: List<RankingItem>,
        market: Market,
        filterLabel: String,
        limit: Int = DEFAULT_LIMIT,
    ): String {
        val shown = items.take(limit)
        val marketLabel = if (market == Market.BR) "Brasil B3" else "EUA S&P 500"
        val total = items.size
        val syncedAt = shown
            .flatMap { listOfNotNull(it.company.priceUpdatedAt, it.company.fundamentalsUpdatedAt) }
            .maxOrNull()

        return buildString {
            appendLine("# Lynch Screener — $marketLabel | Filtro: $filterLabel")
            appendLine(
                "Data sync: ${isoDate.format(Date(syncedAt ?: System.currentTimeMillis()))} | " +
                    "Exibindo Top ${shown.size} de $total",
            )
            appendLine()
            appendLine(COLUMNS)
            appendLine(SEPARATOR)
            shown.forEachIndexed { index, item -> appendLine(row(index + 1, item)) }
        }
    }

    /** One table row. Kept public so tests can assert it cell by cell. */
    fun row(position: Int, item: RankingItem): String {
        val c = item.company
        return listOf(
            position.toString(),
            cell(c.ticker),
            cell(c.name ?: c.ticker),
            cell(c.sector ?: "—"),
            cell(c.lynchCategory),
            c.price?.let { twoDecimals.format(it) } ?: "—",
            c.lynchScore.toString(),
            c.pegy?.let { oneDecimal.format(it) } ?: "—",
            c.pe?.let { oneDecimal.format(it) } ?: "—",
            c.pb?.let { oneDecimal.format(it) } ?: "—",
            percent(c.roe),
            percent(c.dividendYield),
            percent(c.earningsCagr5y ?: c.cagr5y),
            signedPercent(c.earningsYoy),
            cell(c.flags.joinToString(", ") { it.label }.ifBlank { "—" }),
        ).joinToString(separator = " | ", prefix = "| ", postfix = " |")
    }

    private fun percent(fraction: Double?): String =
        fraction?.let { "${oneDecimal.format(it * 100)}%" } ?: "—"

    /** Momentum reads better with an explicit sign. */
    private fun signedPercent(fraction: Double?): String {
        if (fraction == null) return "—"
        val value = fraction * 100
        val sign = if (value > 0) "+" else ""
        return "$sign${oneDecimal.format(value)}%"
    }

    /** A pipe inside a cell would break the table. */
    private fun cell(value: String): String = value.replace("|", "/").trim()
}

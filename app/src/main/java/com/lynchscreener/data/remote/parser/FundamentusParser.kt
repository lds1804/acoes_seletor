package com.lynchscreener.data.remote.parser

import com.lynchscreener.data.remote.dto.B3Details
import com.lynchscreener.data.remote.dto.B3TableRow
import org.jsoup.Jsoup

/**
 * Jsoup-based parsers for Fundamentus. Both parsers are defensive: a missing
 * cell yields `null` rather than throwing, so a single broken row never kills
 * the whole batch.
 *
 * resultado.php column order (0-based) — confirmed against live thead:
 * 0 Papel, 1 Cotação, 2 P/L, 3 P/VP, 4 PSR, 5 Div.Yield, 6 P/Ativo,
 * 7 P/Cap.Giro, 8 P/EBIT, 9 P/Ativ Circ.Liq, 10 EV/EBIT, 11 EV/EBITDA,
 * 12 Mrg Bruta, 13 Mrg Ebit, 14 Mrg. Líq., 15 Liq. Corr., 16 ROIC, 17 ROE,
 * 18 Liq.2meses, 19 Patrim. Líq, 20 Dív.Líq/Patrim., 21 Cresc. Rec.5a
 */
object FundamentusParser {

    fun parseResultTable(html: String): List<B3TableRow> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table#resultado") ?: doc.selectFirst("table") ?: return emptyList()

        val rows = mutableListOf<B3TableRow>()
        for (row in table.select("tr")) {
            val cells = row.select("td")
            if (cells.size < 10) continue
            val ticker = cells[0].text().trim()
            if (ticker.isBlank()) continue

            rows += B3TableRow(
                ticker = ticker,
                price = BrNumber.toDouble(cells[1].text()),
                pe = BrNumber.toDouble(cells[2].text()),
                pb = BrNumber.toDouble(cells[3].text()),
                dividendYield = BrNumber.toFraction(cells[5].text()),
                roe = BrNumber.toFraction(cells[17].text()),
                roic = BrNumber.toFraction(cells[16].text()),
                evEbit = BrNumber.toDouble(cells[10].text()),
                debtToEquity = BrNumber.toDouble(cells[20].text()),
                cagr5y = BrNumber.toFraction(cells[21].text()),
                liquidity = BrNumber.toDouble(cells[18].text()),
            )
        }
        return rows
    }

    /**
     * Parses the label/value table of detalhes.php. Each label cell
     * (`td.label > span.txt`) is followed by a value cell (`td > span.oscil`).
     */
    fun parseDetails(html: String): B3Details {
        val doc = Jsoup.parse(html)
        val map = mutableMapOf<String, String>()

        for (cell in doc.select("td.label")) {
            val key = cell.selectFirst("span.txt")?.text()?.trim() ?: cell.text().trim()
            if (key.isBlank()) continue
            val next = cell.nextElementSibling()
            val value = next?.selectFirst("span.oscil")?.text()?.trim()
                ?: next?.text()?.trim()
            if (value != null) map[key] = value
        }

        fun get(vararg keys: String): String? {
            for (k in keys) map[k]?.let { return it }
            return null
        }

        return B3Details(
            name = get("Empresa"),
            sector = get("Setor"),
            subsector = get("Subsetor"),
            marketCap = BrNumber.toDouble(get("Valor de mercado")),
            equity = BrNumber.toDouble(get("Patrim. Líq", "Patrimônio Líquido")),
            cash = BrNumber.toDouble(get("Disponibilidades")),
            netDebt = BrNumber.toDouble(get("Dívida Líquida", "Dív. Líquida")),
            netIncome12m = BrNumber.toDouble(get("Lucro Líquido")),
            shares = BrNumber.toDouble(get("Nro. Ações")),
        )
    }
}

package com.lynchscreener.data.remote

import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_BPP_CSV
import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_DRE_CSV
import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_YEARS
import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_ZIP
import com.lynchscreener.data.remote.HttpConfig.CVM_FCA_TICKERS
import com.lynchscreener.data.remote.HttpConfig.CVM_FCA_TICKERS_CSV
import com.lynchscreener.data.remote.HttpConfig.CVM_USER_AGENT
import com.lynchscreener.data.remote.dto.CvmCompanyRaw
import com.lynchscreener.data.remote.dto.EarningsBasis
import java.io.File
import java.util.zip.ZipFile
import okhttp3.OkHttpClient

/**
 * Client for CVM open data (dados.cvm.gov.br).
 *
 * Two artifacts are used:
 *  1. FCA "valor mobiliário" — maps each B3 ticker to its company CNPJ.
 *  2. DFP — annual filings. From each package we read:
 *       - `DRE_con` (income statement) → net income per fiscal year
 *       - `BPP_con` (balance sheet)    → parent-company equity
 *
 * Both lines are located by DESCRIPTION, never by a fixed account code, because
 * the chart of accounts changes with the filing regime:
 *
 * | type        | net income | equity | non-controlling |
 * |-------------|------------|--------|-----------------|
 * | industrial  | `3.11`     | `2.03` | `2.03.09`       |
 * | bank (Itaú) | `3.09`     | `2.08` | `2.08.09`       |
 * | insurer     | `3.13`     | `2.03` | `2.03.09`       |
 *
 * Hard-coding `2.03` for a bank reads "Passivos Financeiros ao Custo Amortizado"
 * (a liability!) as equity, so the description match is mandatory.
 *
 * Files are ISO-8859-1 encoded, semicolon separated, values usually in thousands.
 */
class CvmClient(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {

    /**
     * Ticker (e.g. "PETR4") -> CNPJ. Only equity classes (ON/PN/UNT) are kept,
     * so debentures and BDRs registered in the same file are ignored.
     */
    suspend fun fetchTickerToCnpj(
        fcaYear: Int = CVM_DFP_YEARS.first(),
        onBytes: (Long, Long) -> Unit = { _, _ -> },
    ): Map<String, String> {
        val zip = download(CVM_FCA_TICKERS.format(fcaYear), "fca_$fcaYear", onBytes)
        return zip.use { zf ->
            val entryName = CVM_FCA_TICKERS_CSV.format(fcaYear)
            val entry = zf.getEntry(entryName) ?: return emptyMap()
            zf.getInputStream(entry).reader(Charsets.ISO_8859_1).buffered().useLines { lines ->
                val map = mutableMapOf<String, String>()
                var idx: CsvIndex? = null
                for (line in lines) {
                    if (idx == null) {
                        idx = CsvIndex.parse(line)
                        continue
                    }
                    if (line.isBlank()) continue
                    val cells = splitCsv(line)
                    if (cells.size < idx.size) continue
                    val assetType = cells.getOrNull(idx.assetType) ?: continue
                    if (!assetType.contains("Ações", ignoreCase = true)) continue
                    val ticker = cells.getOrNull(idx.ticker)?.trim().orEmpty()
                    val cnpj = cells.getOrNull(idx.cnpj)?.trim().orEmpty()
                    if (ticker.isBlank() || cnpj.isBlank()) continue
                    map[ticker.uppercase()] = cnpj
                }
                map
            }
        }
    }

    /**
     * Reads the requested DFP packages (defaults to all of [CVM_DFP_YEARS]) and
     * returns CNPJ -> [CvmCompanyRaw] with net income per fiscal year plus the
     * parent-company ("controladores") equity of the latest fiscal year.
     *
     * Packages are read newest-first and an already known fiscal year is never
     * overwritten, so the most recent restatement wins.
     */
    suspend fun fetchCompanyData(
        cnpjs: Set<String>,
        years: List<Int> = CVM_DFP_YEARS,
        onProgress: (String) -> Unit = {},
        onBytes: (Long, Long) -> Unit = { _, _ -> },
    ): Map<String, CvmCompanyRaw> {
        if (cnpjs.isEmpty()) return emptyMap()

        // cnpj -> fiscal year -> candidate rows
        val incomeRows = mutableMapOf<String, MutableMap<Int, MutableList<Row>>>()
        val equityRows = mutableMapOf<String, MutableMap<Int, MutableList<Row>>>()

        for (year in years) {
            onProgress("DFP $year: baixando…")
            val zip = try {
                download(CVM_DFP_ZIP.format(year), "dfp_$year", onBytes)
            } catch (e: Exception) {
                onProgress("DFP $year: falhou (${e.message})")
                continue
            }
            zip.use { zf ->
                onProgress("DFP $year: processando DRE…")
                collectRows(zf, CVM_DFP_DRE_CSV.format(year), cnpjs, incomeRows) { row ->
                    row.description in NET_INCOME_DESCRIPTIONS ||
                        row.description == CONTROLLERS_DESCRIPTION
                }
                onProgress("DFP $year: processando balanço…")
                collectRows(zf, CVM_DFP_BPP_CSV.format(year), cnpjs, equityRows) { row ->
                    row.description in EQUITY_DESCRIPTIONS ||
                        row.description in CONTROLLERS_EQUITY_DESCRIPTIONS ||
                        row.description in NON_CONTROLLING_EQUITY_DESCRIPTIONS
                }
            }
            onProgress("DFP $year: concluído")
        }

        val all = incomeRows.keys + equityRows.keys
        return all.associateWith { cnpj ->
            val income = resolveNetIncome(incomeRows[cnpj].orEmpty())
            val equity = resolveControllersEquity(equityRows[cnpj].orEmpty())
            CvmCompanyRaw(
                netIncomeByYear = income.byYear,
                basis = income.basis,
                equityControllers = equity?.value,
                latestYear = equity?.year ?: income.byYear.keys.maxOrNull(),
            )
        }
    }

    // ---------------------------------------------------------------------
    // CSV reading
    // ---------------------------------------------------------------------

    private data class Row(val code: String, val description: String, val value: Double)

    /**
     * Reads [entryName] from [zf], keeping only full-fiscal-year rows accepted by
     * [filter].
     *
     * The period guard is essential: a 3-month row must never be compared against
     * an annual market cap, which is how a P/L of 25.9 appeared for a company
     * whose real multiple was ~7.
     */
    private inline fun collectRows(
        zf: ZipFile,
        entryName: String,
        cnpjs: Set<String>,
        target: MutableMap<String, MutableMap<Int, MutableList<Row>>>,
        filter: (Row) -> Boolean,
    ) {
        val entry = zf.getEntry(entryName) ?: return
        zf.getInputStream(entry).reader(Charsets.ISO_8859_1).buffered().useLines { lines ->
            var idx: CsvIndex? = null
            for (line in lines) {
                if (idx == null) {
                    idx = CsvIndex.parse(line)
                    continue
                }
                if (line.isBlank()) continue
                val cells = splitCsv(line)
                if (cells.size < idx.size) continue

                val cnpj = cells.getOrNull(idx.cnpj)?.trim().orEmpty()
                if (cnpj !in cnpjs) continue

                val start = cells.getOrNull(idx.startDate)
                val end = cells.getOrNull(idx.endDate)
                if (!CvmPeriod.isAnnual(start, end)) continue
                val fy = CvmPeriod.fiscalYear(end) ?: continue

                val value = cells.getOrNull(idx.value)?.trim()?.toDoubleOrNull() ?: continue
                val scale = scaleFactor(cells.getOrNull(idx.scale))
                val row = Row(
                    code = cells.getOrNull(idx.account)?.trim().orEmpty(),
                    description = cells.getOrNull(idx.description)
                        ?.trim()?.lowercase().orEmpty(),
                    value = value * scale,
                )
                if (!filter(row)) continue

                target.getOrPut(cnpj) { mutableMapOf() }
                    .getOrPut(fy) { mutableListOf() }
                    .add(row)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Resolution (account-code agnostic)
    // ---------------------------------------------------------------------

    private data class IncomeResolution(
        val byYear: Map<Int, Double>,
        val basis: EarningsBasis,
    )

    /**
     * Chooses the net income line per fiscal year, preferring the parent-company
     * slice (`Atribuído a Sócios da Empresa Controladora`).
     *
     * The controller line is only accepted when its parent is the consolidated
     * bottom line — otherwise it could belong to "Resultado Líquido das
     * Operações Continuadas", a different concept (BBSE3 case).
     */
    private fun resolveNetIncome(byYear: Map<Int, MutableList<Row>>): IncomeResolution {
        val result = mutableMapOf<Int, Double>()
        var controllersYears = 0

        for ((fy, rows) in byYear) {
            val bottom = rows.filter { it.description in NET_INCOME_DESCRIPTIONS }
                .minByOrNull { it.code.length }
            val controllers = rows.filter { it.description == CONTROLLERS_DESCRIPTION }
                .minByOrNull { it.code.length }

            val controllerIsChildOfBottom = controllers != null && bottom != null &&
                controllers.code.substringBeforeLast('.') == bottom.code

            val chosen = when {
                controllerIsChildOfBottom -> {
                    controllersYears++
                    controllers
                }
                bottom != null -> bottom
                else -> null
            } ?: continue
            result[fy] = chosen.value
        }

        return IncomeResolution(
            byYear = result.toSortedMap(),
            // The basis is decided by the years actually used.
            basis = if (result.isNotEmpty() && controllersYears * 2 >= result.size) {
                EarningsBasis.CONTROLLERS
            } else {
                EarningsBasis.CONSOLIDATED
            },
        )
    }

    private data class EquityResolution(val year: Int, val value: Double)

    /**
     * Parent-company equity of the most recent fiscal year.
     *
     * Prefers the explicit "atribuído ao controlador" line; otherwise subtracts
     * the non-controlling participation from the consolidated total; otherwise
     * uses the consolidated total as reported.
     */
    private fun resolveControllersEquity(byYear: Map<Int, MutableList<Row>>): EquityResolution? {
        val latestYear = byYear.keys.maxOrNull() ?: return null
        val rows = byYear[latestYear].orEmpty()
        val total = rows.filter { it.description in EQUITY_DESCRIPTIONS }
            .minByOrNull { it.code.length }
        val totalCode = total?.code

        fun isChildOf(row: Row): Boolean =
            totalCode == null || row.code.substringBeforeLast('.') == totalCode

        val attributable = rows.firstOrNull {
            it.description in CONTROLLERS_EQUITY_DESCRIPTIONS && isChildOf(it)
        }
        val nonControlling = rows.firstOrNull {
            it.description in NON_CONTROLLING_EQUITY_DESCRIPTIONS && isChildOf(it)
        }

        val value = when {
            attributable != null -> attributable.value
            total != null && nonControlling != null -> total.value - nonControlling.value
            else -> total?.value
        } ?: return null

        return EquityResolution(latestYear, value)
    }

    private suspend fun download(
        url: String,
        name: String,
        onBytes: (Long, Long) -> Unit = { _, _ -> },
    ): ZipFile {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, "$name.zip")
        try {
            client.downloadTo(url, file, mapOf("User-Agent" to CVM_USER_AGENT), onBytes)
            return ZipFile(file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun scaleFactor(scale: String?): Double = when (scale?.trim()?.uppercase()) {
        "MIL" -> 1_000.0          // thousands
        "MILHÃO", "MILHAO" -> 1_000_000.0
        "BILHÃO", "BILHAO" -> 1_000_000_000.0
        else -> 1.0
    }

    /** Column positions resolved from the header (order-agnostic). */
    private data class CsvIndex(
        val cnpj: Int,
        val ticker: Int,
        val assetType: Int,
        val account: Int,
        val description: Int,
        val value: Int,
        val scale: Int,
        val startDate: Int,
        val endDate: Int,
        val size: Int,
    ) {
        companion object {
            fun parse(header: String): CsvIndex {
                val cols = splitCsv(header).mapIndexed { i, c -> c.trim() to i }.toMap()
                fun idx(vararg names: String): Int {
                    for (n in names) cols[n]?.let { return it }
                    return -1
                }
                return CsvIndex(
                    cnpj = idx("CNPJ_Companhia", "CNPJ_CIA"),
                    ticker = idx("Codigo_Negociacao"),
                    assetType = idx("Valor_Mobiliario"),
                    account = idx("CD_CONTA"),
                    description = idx("DS_CONTA"),
                    value = idx("VL_CONTA"),
                    scale = idx("ESCALA_MOEDA"),
                    startDate = idx("DT_INI_EXERC"),
                    endDate = idx("DT_FIM_EXERC"),
                    size = cols.keys.size,
                )
            }
        }
    }

    private companion object {
        /**
         * Consolidated bottom line of the income statement (DRE). The code
         * varies by filing regime, so we match the description.
         */
        val NET_INCOME_DESCRIPTIONS = setOf(
            "lucro/prejuízo consolidado do período",
            "lucro ou prejuízo líquido consolidado do período",
            "lucro/prejuízo do período",
            "lucro ou prejuízo líquido do período",
        )

        /** Parent-company slice of the bottom line (e.g. `3.11.01`, `3.09.01`). */
        const val CONTROLLERS_DESCRIPTION = "atribuído a sócios da empresa controladora"

        /** Consolidated equity (BPP): `2.03` for industrials, `2.08` for banks. */
        val EQUITY_DESCRIPTIONS = setOf(
            "patrimônio líquido consolidado",
            "patrimônio líquido",
        )

        /** Explicit parent-company equity, when the company reports it. */
        val CONTROLLERS_EQUITY_DESCRIPTIONS = setOf(
            "patrimônio líquido atribuído ao controlador",
            "patrimônio líquido atribuído aos controladores",
            "atribuído ao controlador",
        )

        /** Minority interest inside equity (`2.03.09` / `2.08.09`). */
        val NON_CONTROLLING_EQUITY_DESCRIPTIONS = setOf(
            "participação dos acionistas não controladores",
            "participação dos não controladores",
            "atribuído aos não controladores",
        )

        /** Minimal semicolon splitter honoring double-quoted fields. */
        fun splitCsv(line: String): List<String> {
            if (!line.contains('"')) return line.split(';')
            val out = ArrayList<String>(24)
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    ch == '"' -> inQuotes = !inQuotes
                    ch == ';' && !inQuotes -> {
                        out.add(sb.toString()); sb.setLength(0)
                    }
                    else -> sb.append(ch)
                }
                i++
            }
            out.add(sb.toString())
            return out
        }
    }
}

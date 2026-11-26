package com.lynchscreener.data.remote

import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_DRE_CSV
import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_YEARS
import com.lynchscreener.data.remote.HttpConfig.CVM_DFP_ZIP
import com.lynchscreener.data.remote.HttpConfig.CVM_FCA_TICKERS
import com.lynchscreener.data.remote.HttpConfig.CVM_FCA_TICKERS_CSV
import com.lynchscreener.data.remote.HttpConfig.CVM_USER_AGENT
import java.io.File
import java.util.zip.ZipFile
import okhttp3.OkHttpClient

/**
 * Client for CVM open data (dados.cvm.gov.br).
 *
 * Two artifacts are used:
 *  1. FCA "valor mobiliário" — maps each B3 ticker to its company CNPJ.
 *  2. DFP "DRE consolidado" — annual income statement. The net-income line is
 *     matched by DESCRIPTION, because its account code changes per regime.
 *
 * Files are ISO-8859-1 encoded and semicolon separated.
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
     * CNPJ -> (fiscal year -> net income in BRL) for the requested DFP packages
     * (defaults to all of [CVM_DFP_YEARS]). Only the DRE entry is read.
     *
     * The net-income line is located by DESCRIPTION, never by a fixed account
     * code: the code differs per filing regime (e.g. Itaú uses 3.09, insurers
     * 3.13, industrials 3.11). Packages are read newest-first and an already
     * known fiscal year is never overwritten, so the most recent restatement
     * wins for years that appear both as ÚLTIMO and PENÚLTIMO exercício.
     */
    suspend fun fetchNetIncomeByCnpj(
        cnpjs: Set<String>,
        years: List<Int> = CVM_DFP_YEARS,
        onProgress: (String) -> Unit = {},
        onBytes: (Long, Long) -> Unit = { _, _ -> },
    ): Map<String, Map<Int, Double>> {
        // cnpj -> year -> (code, value)
        val picked = mutableMapOf<String, MutableMap<Int, Pair<String, Double>>>()

        for (year in years) {
            onProgress("DFP $year: baixando…")
            val zip = try {
                download(CVM_DFP_ZIP.format(year), "dfp_$year", onBytes)
            } catch (e: Exception) {
                onProgress("DFP $year: falhou (${e.message})")
                continue
            }
            zip.use { zf ->
                val entry = zf.getEntry(CVM_DFP_DRE_CSV.format(year)) ?: return@use
                onProgress("DFP $year: processando DRE…")
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

                        val description = cells.getOrNull(idx.description)
                            ?.trim()?.lowercase().orEmpty()
                        if (description !in NET_INCOME_DESCRIPTIONS) continue

                        val value = cells.getOrNull(idx.value)?.toDoubleOrNull() ?: continue
                        val scale = scaleFactor(cells.getOrNull(idx.scale))
                        val endDate = cells.getOrNull(idx.endDate) ?: continue
                        val fy = endDate.take(4).toIntOrNull() ?: continue
                        val code = cells.getOrNull(idx.account)?.trim().orEmpty()

                        // Newest package wins: keep the first value seen for a
                        // fiscal year instead of overwriting from older filings.
                        picked.getOrPut(cnpj) { mutableMapOf() }
                            .putIfAbsent(fy, code to (value * scale))
                    }
                }
            }
            onProgress("DFP $year: concluído")
        }

        return picked.mapValues { (_, byYear) -> byYear.mapValues { it.value.second } }
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
                val cnpj = idx("CNPJ_Companhia", "CNPJ_CIA")
                return CsvIndex(
                    cnpj = cnpj,
                    ticker = idx("Codigo_Negociacao"),
                    assetType = idx("Valor_Mobiliario"),
                    account = idx("CD_CONTA"),
                    description = idx("DS_CONTA"),
                    value = idx("VL_CONTA"),
                    scale = idx("ESCALA_MOEDA"),
                    endDate = idx("DT_FIM_EXERC"),
                    size = cols.keys.size,
                )
            }
        }
    }

    private companion object {
        /**
         * Accepted net-income line descriptions (normalized lowercase, accents
         * kept). The account code varies by filing regime, so we match text.
         */
        val NET_INCOME_DESCRIPTIONS = setOf(
            "lucro/prejuízo consolidado do período",
            "lucro ou prejuízo líquido consolidado do período",
            "lucro/prejuízo do período",
            "lucro ou prejuízo líquido do período",
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

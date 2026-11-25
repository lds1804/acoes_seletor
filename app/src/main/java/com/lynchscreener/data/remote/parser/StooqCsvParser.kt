package com.lynchscreener.data.remote.parser

import com.lynchscreener.data.remote.dto.QuotePrice

/**
 * Parses Stooq CSV (`?s=TICKER&f=sd2t2ohlcv&h&e=csv`):
 * `Symbol,Date,Time,Open,High,Low,Close,Volume` followed by one data row.
 */
object StooqCsvParser {

    fun parse(csv: String): QuotePrice? {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return null

        val header = lines[0].split(',')
        val values = lines[1].split(',')
        if (values.size != header.size) return null

        val closeIdx = header.indexOf("Close")
        if (closeIdx < 0) return null

        val price = values[closeIdx].toDoubleOrNull() ?: return null
        return QuotePrice(price = price)
    }
}

package com.lynchscreener.ui.format

import com.lynchscreener.domain.model.Market
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Locale-aware (pt-BR) number/currency/date formatting for the UI. */
object Formatters {

    private val locale = Locale("pt", "BR")
    private val money = DecimalFormat("#,##0.00", DecimalFormatSymbols(locale))
    private val ratio = DecimalFormat("#,##0.0", DecimalFormatSymbols(locale))
    private val compactFmt = DecimalFormat("#,##0.0", DecimalFormatSymbols(locale))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)

    fun currencySymbol(market: Market): String = if (market == Market.BR) "R$" else "US$"

    fun price(value: Double?, market: Market): String =
        if (value == null) "—" else "${currencySymbol(market)} ${money.format(value)}"

    fun priceSymbol(value: Double?, symbol: String): String =
        if (value == null) "—" else "$symbol ${money.format(value)}"

    /** Compact: R$ 2,5 bi / US$ 1,3 tri / R$ 850 mi / R$ 120 mil. */
    fun compact(value: Double?, market: Market): String {
        if (value == null) return "—"
        val abs = kotlin.math.abs(value)
        val (divisor, suffix) = when {
            abs >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to " tri"
            abs >= 1_000_000_000.0 -> 1_000_000_000.0 to " bi"
            abs >= 1_000_000.0 -> 1_000_000.0 to " mi"
            abs >= 1_000.0 -> 1_000.0 to " mil"
            else -> 1.0 to ""
        }
        return "${currencySymbol(market)} ${compactFmt.format(value / divisor)}$suffix"
    }

    fun percent(fraction: Double?): String =
        if (fraction == null) "—" else "${ratio.format(fraction * 100)}%"

    fun ratio(value: Double?): String = if (value == null) "—" else ratio.format(value)

    fun date(epochMs: Long?): String =
        if (epochMs == null) "—" else dateFormat.format(Date(epochMs))

    /** Relative time: "agora", "há 4 min", "há 2 h", "há 3 dias". */
    fun relative(epochMs: Long?): String {
        if (epochMs == null) return "—"
        val diff = System.currentTimeMillis() - epochMs
        if (diff < 0) return "agora"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "agora"
            minutes < 60 -> "há $minutes min"
            else -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                if (hours < 24) "há $hours h"
                else {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "há $days dia${if (days > 1) "s" else ""}"
                }
            }
        }
    }
}

package com.lynchscreener.ui.format

import androidx.compose.ui.graphics.Color
import com.lynchscreener.domain.model.Market
import com.lynchscreener.ui.theme.ScoreBad
import com.lynchscreener.ui.theme.ScoreGood
import com.lynchscreener.ui.theme.ScoreGreat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Locale-aware (pt-BR) number/currency/date formatting for the UI. */
object Formatters {

    private val locale = Locale("pt", "BR")
    private val money = DecimalFormat("#,##0.00", DecimalFormatSymbols(locale))
    private val ratio = DecimalFormat("#,##0.0", DecimalFormatSymbols(locale))
    private val compact = DecimalFormat("#,##0.0", DecimalFormatSymbols(locale))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)

    fun currencySymbol(market: Market): String = if (market == Market.BR) "R$" else "US$"

    fun price(value: Double?, market: Market): String =
        if (value == null) "—" else "${currencySymbol(market)} ${money.format(value)}"

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
        return "${currencySymbol(market)} ${compact.format(value / divisor)}$suffix"
    }

    fun percent(fraction: Double?): String =
        if (fraction == null) "—" else "${ratio.format(fraction * 100)}%"

    fun ratio(value: Double?): String = if (value == null) "—" else ratio.format(value)

    fun date(epochMs: Long?): String =
        if (epochMs == null) "—" else dateFormat.format(Date(epochMs))

    fun scoreColor(score: Int): Color = when {
        score >= 70 -> ScoreGreat
        score >= 40 -> ScoreGood
        else -> ScoreBad
    }
}

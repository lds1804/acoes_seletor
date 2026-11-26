package com.lynchscreener.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lynchscreener.ui.detail.EarningsYear
import com.lynchscreener.ui.format.Formatters
import com.lynchscreener.ui.theme.LocalLynchColors

/** Compact BRL label for the axis captions (values are always in reais). */
private fun compactBrl(value: Double): String {
    val abs = kotlin.math.abs(value)
    val (divisor, suffix) = when {
        abs >= 1_000_000_000.0 -> 1_000_000_000.0 to "bi"
        abs >= 1_000_000.0 -> 1_000_000.0 to "mi"
        abs >= 1_000.0 -> 1_000.0 to "mil"
        else -> 1.0 to ""
    }
    val scaled = value / divisor
    val text = if (abs >= 1_000.0) {
        String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", scaled)
    } else {
        String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.0f", scaled)
    }
    return "$text$suffix"
}

/**
 * Canvas-drawn bar chart of reported net income per fiscal year.
 *
 * Deliberately hand-drawn (no chart library) so the visual language matches the
 * rest of the app: no axes, no gridlines, rounded bars, loss years rendered
 * below a zero baseline in the critical colour.
 */
@Composable
fun EarningsHistoryChart(
    years: List<EarningsYear>,
    modifier: Modifier = Modifier,
) {
    if (years.isEmpty()) return
    val colors = LocalLynchColors.current
    var animate by remember(years.size) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(years.size) { animate = true }
    val progress by animateFloatAsState(
        targetValue = if (animate) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "earningsChart",
    )

    val maxAbs = years.maxOf { kotlin.math.abs(it.value) }.coerceAtLeast(1.0)
    val hasNegative = years.any { it.value < 0 }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            val slot = size.width / years.size
            val barWidth = (slot * 0.52f).coerceAtMost(34.dp.toPx())
            // With losses the baseline sits above the middle so bars fit both ways.
            val baseline = if (hasNegative) size.height * 0.70f else size.height
            val amplitude = if (hasNegative) size.height * 0.68f else size.height * 0.96f

            years.forEachIndexed { i, point ->
                val ratio = (kotlin.math.abs(point.value) / maxAbs).toFloat() * progress
                val h = (amplitude * ratio).coerceAtLeast(2.dp.toPx())
                val left = i * slot + (slot - barWidth) / 2f
                val loss = point.value < 0
                val top = if (loss) baseline else baseline - h
                val brush = if (loss) {
                    Brush.verticalGradient(
                        listOf(colors.negative.copy(alpha = 0.85f), colors.negative.copy(alpha = 0.35f)),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(colors.accentPrimary, colors.accentPrimary.copy(alpha = 0.30f)),
                    )
                }
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2.4f, barWidth / 2.4f),
                )
            }

            if (hasNegative) {
                drawLine(
                    color = colors.borderSubtle.copy(alpha = 0.6f),
                    start = Offset(0f, baseline),
                    end = Offset(size.width, baseline),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            years.forEach { point ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(44.dp),
                ) {
                    Text(
                        text = compactBrl(point.value),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (point.value < 0) colors.negative else colors.textMuted,
                        maxLines = 1,
                    )
                    Text(
                        text = point.year.toString().takeLast(2),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Small "CAGR do lucro" seal: shows the number and whether the series was
 * loss-free, which is what the Lynch classification actually relies on.
 */
@Composable
fun EarningsCagrSeal(cagr: Double?, consistent: Boolean?, modifier: Modifier = Modifier) {
    val colors = LocalLynchColors.current
    val tone: Color = when {
        cagr == null -> colors.textMuted
        consistent == false -> colors.negative
        cagr >= 0.15 -> colors.accentPrimary
        cagr >= 0.05 -> colors.scoreNeutral
        else -> colors.textSecondary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(tone))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (cagr != null) Formatters.percent(cagr) + " a.a." else "sem série",
            style = MaterialTheme.typography.labelSmall,
            color = tone,
            fontWeight = FontWeight.Medium,
        )
    }
}

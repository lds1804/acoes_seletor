package com.lynchscreener.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lynchscreener.ui.format.Formatters
import com.lynchscreener.ui.theme.LocalLynchColors

/**
 * Clean Canvas-based comparison bar: company value vs. sector median.
 * A filled bar shows the company, a tick marks the sector median, colored
 * green/red depending on whether the company is "better" (cheaper or more
 * profitable) than its peers.
 */
@Composable
fun ComparisonBar(
    label: String,
    companyValue: Double?,
    sectorValue: Double?,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLynchColors.current

    val better: Boolean? = when {
        companyValue == null || sectorValue == null -> null
        lowerIsBetter -> companyValue < sectorValue
        else -> companyValue > sectorValue
    }
    val accent = when (better) {
        true -> colors.positive
        false -> colors.negative
        null -> colors.textSecondary
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.width(44.dp))
        Spacer(Modifier.width(8.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(8.dp),
        ) {
            val track = Size(size.width, 8.dp.toPx())
            val cy = size.height / 2
            val strokeW = 4.dp.toPx()

            // Track
            drawLine(
                color = colors.surfaceElevated,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )

            val max = maxOf(companyValue ?: 0.0, sectorValue ?: 0.0).coerceAtLeast(1e-9)
            if (companyValue != null) {
                val companyEnd = (companyValue / max).toFloat().coerceIn(0f, 1f) * size.width
                drawLine(
                    color = accent,
                    start = Offset(0f, cy),
                    end = Offset(companyEnd, cy),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
            if (sectorValue != null) {
                val tickX = (sectorValue / max).toFloat().coerceIn(0f, 1f) * size.width
                drawLine(
                    color = colors.textSecondary,
                    start = Offset(tickX, cy - 4.dp.toPx()),
                    end = Offset(tickX, cy + 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = Formatters.ratio(companyValue),
            style = MaterialTheme.typography.titleSmall,
            color = accent,
        )
    }
}

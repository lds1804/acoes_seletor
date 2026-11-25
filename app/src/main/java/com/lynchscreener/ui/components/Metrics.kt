package com.lynchscreener.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lynchscreener.domain.model.Market
import com.lynchscreener.ui.format.Formatters
import com.lynchscreener.ui.theme.LocalLynchColors

/** Uppercase muted label for indicator names (P/L, P/VP, ROE, DY, PEGY). */
@Composable
fun IndicatorLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LocalLynchColors.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textMuted,
        modifier = modifier,
    )
}

/** A label + numeric value pair (tabular figures). */
@Composable
fun IndicatorValue(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLynchColors.current
    Column(modifier = modifier) {
        IndicatorLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (valueColor == Color.Unspecified) colors.textPrimary else valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Small translucent seal/badge (e.g. "Caixa Líq."). */
@Composable
fun MiniSeal(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Top-3 position pill: gold / silver / bronze subtle fills. */
@Composable
fun RankPill(rank: Int, modifier: Modifier = Modifier) {
    val colors = LocalLynchColors.current
    val (bg, fg) = when (rank) {
        1 -> Color(0xFF7C5C0A).copy(alpha = 0.18f) to Color(0xFFFBBF24)
        2 -> Color(0xFF64748B).copy(alpha = 0.18f) to Color(0xFFCBD5E1)
        3 -> Color(0xFF9A6A2F).copy(alpha = 0.18f) to Color(0xFFE0A96D)
        else -> colors.surfaceElevated to colors.textSecondary
    }
    Text(
        text = "#$rank",
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

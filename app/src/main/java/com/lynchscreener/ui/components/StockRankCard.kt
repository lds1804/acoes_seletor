package com.lynchscreener.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.lynchscreener.domain.model.RankingItem
import com.lynchscreener.ui.format.Formatters
import com.lynchscreener.ui.theme.LocalLynchColors

/**
 * Central ranking card. Renders position pill, ticker, price, sector tag,
 * category, net-cash seal and the 5-column metric strip.
 */
@Composable
fun StockRankCard(
    item: RankingItem,
    market: Market,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLynchColors.current
    val c = item.company

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Top row: rank + ticker + price | ring
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankPill(item.overallRank)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = c.ticker,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Formatters.price(c.price, market),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        text = c.name ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val hasScore = c.lynchCategory != "Sem classificação"
                if (hasScore) {
                    LynchScoreRing(score = c.lynchScore)
                } else {
                    PendingScoreBadge()
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tags: sector (icon + name + #rank) | category | net-cash seal
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sector = c.sector ?: "Outros"
                SectorTag(icon = sectorIcon(sector), text = "$sector · #${item.sectorRank} no setor")
                Spacer(Modifier.width(6.dp))
                MiniSeal(
                    text = c.lynchCategory,
                    color = colors.textSecondary,
                )
                if ((c.netCashToMarketCap ?: 0.0) > 0.0) {
                    Spacer(Modifier.width(6.dp))
                    MiniSeal(text = "Caixa Líq.", color = colors.positive)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Metric strip (5 columns)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndicatorValue("PEGY", Formatters.ratio(c.pegy), valueColor = pegyColor(c.pegy, colors), modifier = Modifier.weight(1f))
                IndicatorValue("P/L", Formatters.ratio(c.pe), modifier = Modifier.weight(1f))
                IndicatorValue("P/VP", Formatters.ratio(c.pb), modifier = Modifier.weight(1f))
                IndicatorValue("ROE", Formatters.percent(c.roe), modifier = Modifier.weight(1f))
                IndicatorValue("DY", Formatters.percent(c.dividendYield), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Neutral placeholder for companies awaiting their first fundamentals. */
@Composable
private fun PendingScoreBadge() {
    val colors = LocalLynchColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, colors.borderSubtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("—", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

@Composable
private fun SectorTag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = LocalLynchColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

@Composable
private fun pegyColor(pegy: Double?, colors: com.lynchscreener.ui.theme.LynchColors): Color =
    when {
        pegy == null -> colors.textPrimary
        pegy < 1.0 -> colors.positive
        pegy < 1.5 -> colors.scoreNeutral
        else -> colors.negative
    }

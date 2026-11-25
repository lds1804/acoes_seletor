package com.lynchscreener.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lynchscreener.ui.theme.LocalLynchColors

/**
 * Fase 2 (portfólio) placeholder. Shows an elegant empty state (the schema for
 * `portfolio_positions` already exists in the local DB) plus a low-opacity
 * static preview of how weights and exit triggers will look.
 */
@Composable
fun PortfolioScreen() {
    val colors = LocalLynchColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Text(
            "Carteira",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape),
            ) {
                Icon(Icons.Rounded.PieChart, null, tint = colors.accentPrimary, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text("Carteira em breve", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "O schema de portfolio_positions já está pronto no banco local. Aqui você poderá registrar posições, pesos-alvo e gatilhos de saída (P/L e P/VP).",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(28.dp))

            // Static low-opacity preview
            Column(Modifier.alpha(0.45f)) {
                PreviewRow("Posição", "Peso-alvo", "Saída P/L")
                PreviewRow("PETR4", "20%", "> 15,0x")
                PreviewRow("ITUB4", "15%", "> 12,0x")
                PreviewRow("GOOGL", "10%", "> 30,0x")
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, weight: String, exit: String) {
    val colors = LocalLynchColors.current
    val isHeader = label == "Posição"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.surfaceCard, RoundedCornerShape(10.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(weight, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(exit, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
    }
}

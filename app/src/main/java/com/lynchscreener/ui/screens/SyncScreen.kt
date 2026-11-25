package com.lynchscreener.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lynchscreener.ui.components.PulsingDot
import com.lynchscreener.ui.sync.SyncViewModel
import com.lynchscreener.ui.theme.LocalLynchColors

@Composable
fun SyncScreen(viewModel: SyncViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalLynchColors.current
    val snapshot = state.snapshot

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Sincronização", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = viewModel::triggerNow,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Refresh, null)
                    Text("  Sincronizar próximo lote agora")
                }
            }

            item { GlobalStatusCard(state.syncStatus.isSyncing) }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceCard,
                    modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Progresso das filas", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        if (snapshot == null) {
                            Text("Calculando…", color = colors.textSecondary)
                        } else {
                            ProgressBar("B3 · Tabela Geral", snapshot.brWithPrice, snapshot.brTotal)
                            ProgressBar("B3 · Detalhes e Setores", snapshot.brWithFundamentals, snapshot.brTotal)
                            ProgressBar("EUA · Balanços SEC EDGAR", snapshot.usWithFundamentals, snapshot.usTotal)
                            ProgressBar("EUA · Cotações Diárias", snapshot.usWithPrice, snapshot.usTotal)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceCard,
                    modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Configurações de background", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(Icons.Rounded.Wifi, "Somente no Wi-Fi", state.wifiOnly, viewModel::setWifiOnly)
                        Spacer(Modifier.height(6.dp))
                        ToggleRow(Icons.Rounded.BatteryChargingFull, "Somente carregando / bateria ok", state.chargingOnly, viewModel::setChargingOnly)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "As filas lentas respeitam esses limites para evitar rate-limit e economizar bateria.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            item { ConsoleCard(state.logLines) }
        }
    }
}

@Composable
private fun GlobalStatusCard(isSyncing: Boolean) {
    val colors = LocalLynchColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSyncing) colors.surfaceElevated else colors.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isSyncing) colors.accentPrimary.copy(alpha = 0.4f) else colors.borderSubtle, RoundedCornerShape(18.dp))
            .animateContentSize(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = if (isSyncing) colors.accentPrimary else colors.positive)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isSyncing) "Sincronizando…" else "Base atualizada",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isSyncing) "O WorkManager está processando as filas em segundo plano."
                    else "Nenhuma sincronização ativa no momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (!isSyncing) {
                Surface(color = colors.positive.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text("OK", color = colors.positive, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(label: String, done: Int, total: Int) {
    val colors = LocalLynchColors.current
    val fraction = if (total > 0) done.toFloat() / total else 0f
    val animated by animateFloatAsState(targetValue = fraction, label = "syncBar")
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$done / $total", style = MaterialTheme.typography.labelMedium, color = colors.accentPrimary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animated.coerceIn(0f, 1f) },
            color = colors.accentPrimary,
            trackColor = colors.surfaceElevated,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalLynchColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ConsoleCard(lines: List<String>) {
    val colors = LocalLynchColors.current
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceCard,
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Text("Console de logs", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${lines.size}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    null,
                    tint = colors.textMuted,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 10.dp)) {
                    if (lines.isEmpty()) {
                        Text("Nenhum evento registrado.", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        lines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = colors.textSecondary,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

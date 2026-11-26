package com.lynchscreener.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.lynchscreener.worker.DeviceConditions
import com.lynchscreener.worker.SyncProgress
import com.lynchscreener.worker.SyncStatus

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

            item { GlobalStatusCard(state.syncStatus, state.conditions, state.wifiOnly, state.chargingOnly) }

            if (state.download.active) {
                item { DownloadCard(state.download) }
            }

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
private fun GlobalStatusCard(
    status: SyncStatus,
    conditions: DeviceConditions?,
    wifiOnly: Boolean,
    chargingOnly: Boolean,
) {
    val colors = LocalLynchColors.current
    val isSyncing = status.isSyncing
    val isWaiting = status.isWaiting
    // Only meaningful while something is queued: when idle, an unmet condition is
    // not the reason for anything.
    val blockers = if (isWaiting && conditions != null) {
        conditions.blockers(wifiOnly, chargingOnly)
    } else {
        emptyList()
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSyncing) colors.surfaceElevated else colors.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when {
                    isSyncing -> colors.accentPrimary.copy(alpha = 0.4f)
                    isWaiting -> colors.scoreNeutral.copy(alpha = 0.4f)
                    else -> colors.borderSubtle
                },
                shape = RoundedCornerShape(18.dp),
            )
            .animateContentSize(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(
                color = when {
                    isSyncing -> colors.accentPrimary
                    isWaiting -> colors.scoreNeutral
                    else -> colors.positive
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        isSyncing -> "Sincronizando…"
                        isWaiting -> "Aguardando condições"
                        else -> "Base atualizada"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        isSyncing -> "O WorkManager está processando as filas em segundo plano."
                        isWaiting && blockers.isNotEmpty() ->
                            "${status.enqueuedTasks} tarefa(s) na fila, bloqueada(s) por: " +
                                blockers.joinToString(", ") + "."
                        isWaiting -> "${status.enqueuedTasks} tarefa(s) na fila. O download da " +
                            "CVM só roda no Wi-Fi."
                        else -> "Nenhuma sincronização ativa no momento."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                // Device readout: makes the wait diagnosable without a cable.
                if (conditions != null && (isWaiting || isSyncing)) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append("Bateria ")
                            append(conditions.batteryPercent?.let { "$it%" } ?: "?")
                            if (conditions.isCharging) append(" · carregando")
                            append(if (conditions.isOnWifi) " · Wi-Fi" else " · sem Wi-Fi")
                            append(if (conditions.hasConnection) "" else " · offline")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }
            when {
                isSyncing -> Unit
                isWaiting -> Surface(color = colors.scoreNeutral.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "PENDENTE",
                        color = colors.scoreNeutral,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                else -> Surface(color = colors.positive.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "OK",
                        color = colors.positive,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(download: SyncProgress.State) {
    val colors = LocalLynchColors.current
    val fraction = download.fraction
    val animated by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = 400),
        label = "downloadBar",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.accentPrimary.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = colors.accentPrimary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Baixando CSV da CVM",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = download.label.ifBlank { "preparando…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                // Byte counters are the only proof the transfer is progressing:
                // a stalled value means the connection dropped.
                Text(
                    text = buildString {
                        append(download.megabytesDone)
                        if (download.megabytesTotal > 0) append(" / ${download.megabytesTotal}")
                        append(" MB")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))

            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { animated.coerceIn(0f, 1f) },
                    color = colors.accentPrimary,
                    trackColor = colors.surfaceCard,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${(animated * 100).toInt()}% · pacotes restantes: ${download.pendingPackages}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            } else {
                LinearProgressIndicator(
                    color = colors.accentPrimary,
                    trackColor = colors.surfaceCard,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tamanho total desconhecido · pacotes restantes: ${download.pendingPackages}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "São 5 pacotes anuais (~60–80 MB cada), um por execução. " +
                    "Mantenha o app no Wi-Fi durante o download.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
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

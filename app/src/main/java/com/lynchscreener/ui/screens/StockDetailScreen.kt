package com.lynchscreener.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.ScoreComponent
import com.lynchscreener.ui.components.ComparisonBar
import com.lynchscreener.ui.components.IndicatorLabel
import com.lynchscreener.ui.components.LynchScoreRing
import com.lynchscreener.ui.components.MiniSeal
import com.lynchscreener.ui.components.sectorIcon
import com.lynchscreener.ui.detail.DetailViewModel
import com.lynchscreener.ui.format.Formatters
import com.lynchscreener.ui.theme.LocalLynchColors
import com.lynchscreener.ui.theme.scoreColor
import com.lynchscreener.ui.theme.scoreLabel

@Composable
fun StockDetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val company = state.company
    val colors = LocalLynchColors.current

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            kotlinx.coroutines.delay(2000)
            refreshing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        // Top bar (transparent)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = colors.textPrimary)
            }
            Column {
                Text(company?.ticker ?: "—", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (company != null && Market.fromCode(company.market) == Market.BR) "B3 · Brasil" else "S&P 500 · EUA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    refreshing = true
                    viewModel.refreshNow()
                },
            ) {
                if (refreshing) {
                    SpinningRefresh(color = colors.accentPrimary)
                } else {
                    Icon(Icons.Rounded.Refresh, "Atualizar agora", tint = colors.accentPrimary)
                }
            }
        }

        if (company == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carregando…", color = colors.textSecondary)
            }
            return@Column
        }

        val market = Market.fromCode(company.market)
        val isFinancial = LynchScoringEngine.isFinancialSector(company.sector, company.subsector)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeroCard(company, market, state) }
            item { ScoreBreakdownCard(state.components, isFinancial) }
            item { EarningsCard(state) }
            item { SectorComparisonCard(state) }
            item { FundamentalsGrid(company, market) }
            item { FooterCard(company) }
        }
    }
}

@Composable
private fun SpinningRefresh(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinAngle",
    )
    Icon(
        Icons.Rounded.Refresh,
        null,
        tint = color,
        modifier = Modifier.rotate(angle),
    )
}

@Composable
private fun HeroCard(company: RankedCompany, market: Market, state: com.lynchscreener.ui.detail.DetailUiState) {
    val colors = LocalLynchColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(company.name ?: "—", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectorBadge(company.sector ?: "Outros")
                        company.subsector?.let { SectorBadge(it) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = Formatters.price(company.price, market),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("#${state.overallRank ?: "—"} no ${if (market == Market.BR) "Brasil" else "S&P 500"} · #${state.sectorRank ?: "—"} no setor", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LynchScoreRing(score = company.lynchScore, size = 88.dp, strokeWidth = 8.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        scoreLabel(company.lynchScore),
                        style = MaterialTheme.typography.labelMedium,
                        color = scoreColor(company.lynchScore, colors),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectorBadge(text: String) {
    val colors = LocalLynchColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(sectorIcon(text), null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

@Composable
private fun ScoreBreakdownCard(components: List<ScoreComponent>, isFinancial: Boolean) {
    val colors = LocalLynchColors.current
    SectionCard("Raio-X do Score Peter Lynch") {
        if (isFinancial) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.infoBanner,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Regra de Setor Financeiro aplicada: passivo bancário não penalizado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onInfoBanner,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (components.isEmpty()) {
            Text("Sem detalhes de pontuação.", color = colors.textSecondary)
        } else {
            components.forEach { component ->
                val isDisqualified = component.key == "DISQUALIFIED"
                val isCeiling = component.key == "CEILING"
                // Round each criterion exactly like the engine does, so the sum of
                // the numbers on screen equals the score inside the ring.
                val shown = component.points.roundToInt()
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            component.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCeiling) colors.scoreNeutral else colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        when {
                            isCeiling -> Text(
                                "$shown pts",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.scoreNeutral,
                                fontWeight = FontWeight.Bold,
                            )
                            !isDisqualified && component.maxPoints > 0 -> Text(
                                "$shown/${component.maxPoints.roundToInt()} pts",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (!isDisqualified && !isCeiling && component.maxPoints > 0) {
                        Spacer(Modifier.height(6.dp))
                        val animated by animateFloatAsState(
                            targetValue = (component.points / component.maxPoints).toFloat().coerceIn(0f, 1f),
                            label = "componentBar",
                        )
                        LinearProgressIndicator(
                            progress = { animated },
                            color = colors.accentPrimary,
                            trackColor = colors.surfaceElevated,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        component.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCeiling) colors.scoreNeutral else colors.textSecondary,
                    )
                }
            }

            // Reconciliation line: proves the breakdown adds up to the ring.
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Soma dos critérios",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${components.sumOf { it.points.roundToInt() }} pts",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EarningsCard(state: com.lynchscreener.ui.detail.DetailUiState) {
    val company = state.company ?: return
    val colors = LocalLynchColors.current
    val isBr = Market.fromCode(company.market) == Market.BR
    val history = state.earningsHistory

    if (history.isEmpty()) {
        if (!isBr) return
        SectionCard("Histórico de Lucros") {
            Text(
                text = "Ainda não coletado. O histórico vem dos demonstrativos " +
                    "trimestrais da CVM (DFP) e é baixado em segundo plano — " +
                    "volte depois da próxima sincronização.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        return
    }

    val first = history.first().value
    val last = history.last().value
    val lossYears = history.count { it.value < 0 }

    SectionCard("Histórico de Lucros") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.earningsBasis == "CONSOLIDATED") {
                        "Lucro líquido (Consolidado)"
                    } else {
                        "Lucro líquido (Controladores)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
                Text(
                    text = "CVM · ${history.first().year}–${history.last().year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            com.lynchscreener.ui.components.EarningsCagrSeal(
                cagr = company.earningsCagr5y,
                consistent = state.earningsConsistent,
            )
        }

        Spacer(Modifier.height(14.dp))
        com.lynchscreener.ui.components.EarningsHistoryChart(years = history)
        Spacer(Modifier.height(10.dp))

        // Lynch cares about consistency at least as much as the growth rate, so
        // the verdict is spelled out instead of leaving it to the chart.
        val (verdict, tone) = when {
            state.earningsConsistent == true && lossYears == 0 && last > first ->
                "Lucro crescente e sem anos de prejuízo — padrão de qualidade." to colors.positive
            lossYears > 0 ->
                "${lossYears}x de prejuízo na série — crescimento não confiável." to colors.negative
            last <= first ->
                "Lucro estagnado ou em queda no período." to colors.textSecondary
            else ->
                "Série curta para concluir tendência." to colors.textMuted
        }
        Text(
            text = verdict,
            style = MaterialTheme.typography.bodySmall,
            color = tone,
        )
    }
}

@Composable
private fun SectorComparisonCard(state: com.lynchscreener.ui.detail.DetailUiState) {
    val stats = state.sectorStats ?: return
    val company = state.company ?: return
    val colors = LocalLynchColors.current
    SectionCard("Empresa vs. Média do Setor (${stats.sector})") {
        ComparisonBar("P/L", company.pe, stats.medianPe, lowerIsBetter = true)
        Spacer(Modifier.height(10.dp))
        ComparisonBar("P/VP", company.pb, stats.medianPb, lowerIsBetter = true)
        Spacer(Modifier.height(10.dp))
        ComparisonBar("ROE", company.roe, stats.medianRoe, lowerIsBetter = false)
        Spacer(Modifier.height(10.dp))
        ComparisonBar("DY", company.dividendYield, null, lowerIsBetter = false)
    }
}

@Composable
private fun FundamentalsGrid(company: RankedCompany, market: Market) {
    val colors = LocalLynchColors.current
    SectionCard("Fundamentos") {
        val items = listOf(
            Triple("Valor de mercado", Formatters.compact(company.marketCap, market), null),
            Triple("Patrimônio líquido", Formatters.compact(company.equity, market), null),
            Triple(
                "Caixa líquido / Dívida",
                Formatters.compact(company.netDebt, market),
                if ((company.netCashToMarketCap ?: 0.0) > 0.0) colors.positive else null,
            ),
            Triple("Lucro líquido 12m", Formatters.compact(company.netIncome12m, market), null),
            Triple("CAGR 5 anos", Formatters.percent(company.cagr5y), null),
            Triple(
                "CAGR do lucro",
                Formatters.percent(company.earningsCagr5y),
                if (company.earningsConsistent == false) colors.negative else null,
            ),
            Triple("Liquidez diária", Formatters.compact(company.liquidity, market), null),
        )
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (label, value, color) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceElevated,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            IndicatorLabel(label)
                            Spacer(Modifier.height(4.dp))
                            Text(value, style = MaterialTheme.typography.titleSmall, color = color ?: colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FooterCard(company: RankedCompany) {
    val colors = LocalLynchColors.current
    Text(
        "Preço: ${Formatters.relative(company.priceUpdatedAt)} · Balanço: ${Formatters.relative(company.fundamentalsUpdatedAt)}",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textMuted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val colors = LocalLynchColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

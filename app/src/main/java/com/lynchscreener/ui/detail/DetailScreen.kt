package com.lynchscreener.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.ScoreComponent
import com.lynchscreener.ui.components.MetricTile
import com.lynchscreener.ui.components.ScoreRing
import com.lynchscreener.ui.components.SectorBadge
import com.lynchscreener.ui.format.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val company = state.company

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(company?.ticker ?: "Detalhes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (company == null) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val market = Market.fromCode(company.market)

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeaderCard(company, market, state) }
            item { MetricsCard(company, market) }
            item { SectorComparisonCard(state) }
            item { ScoreBreakdownCard(state.components) }
            item { UpdateCard(company, market, viewModel::refreshNow) }
        }
    }
}

@Composable
private fun HeaderCard(company: com.lynchscreener.domain.model.RankedCompany, market: Market, state: DetailUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(company.name ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectorBadge(company.sector ?: "—")
                        company.subsector?.let { SectorBadge(it) }
                    }
                }
                ScoreRing(score = company.lynchScore, size = 56.dp, strokeWidth = 5.dp)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MetricTile("Cotação", Formatters.price(company.price, market))
                MetricTile("Valor de mercado", Formatters.compact(company.marketCap, market))
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MetricTile("Ranking geral", "#${state.overallRank ?: "—"}")
                MetricTile("No setor", "#${state.sectorRank ?: "—"}")
                MetricTile("Classificação", company.lynchCategory)
            }
        }
    }
}

@Composable
private fun MetricsCard(company: com.lynchscreener.domain.model.RankedCompany, market: Market) {
    SectionCard(title = "Indicadores fundamentalistas") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("P/L", Formatters.ratio(company.pe), modifier = Modifier.weight(1f))
            MetricTile("P/VP", Formatters.ratio(company.pb), modifier = Modifier.weight(1f))
            MetricTile("ROE", Formatters.percent(company.roe), modifier = Modifier.weight(1f))
            MetricTile("ROIC", Formatters.percent(company.roic), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("DY", Formatters.percent(company.dividendYield), modifier = Modifier.weight(1f))
            MetricTile("PEGY", Formatters.ratio(company.pegy), modifier = Modifier.weight(1f))
            MetricTile("Cresc. 5a", Formatters.percent(company.cagr5y), modifier = Modifier.weight(1f))
            MetricTile("Liquidez", Formatters.compact(company.liquidity, market), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Patrimônio líquido", Formatters.compact(company.equity, market), modifier = Modifier.weight(1f))
            MetricTile("Lucro líq. 12m", Formatters.compact(company.netIncome12m, market), modifier = Modifier.weight(1f))
            MetricTile("Dívida líquida", Formatters.compact(company.netDebt, market), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Caixa / valor de mercado", Formatters.percent(company.netCashToMarketCap), modifier = Modifier.weight(1f))
            MetricTile("Ações emitidas", Formatters.compact(company.sharesOutstanding, market), modifier = Modifier.weight(1f))
            MetricTile("Div. por ação", Formatters.price(company.dividendPerShare, market), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectorComparisonCard(state: DetailUiState) {
    val stats = state.sectorStats ?: return
    val company = state.company ?: return
    SectionCard(title = "Comparação com o setor (${stats.sector})") {
        ComparisonRow("P/L", company.pe, stats.avgPe, lowerIsBetter = true)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ComparisonRow("P/VP", company.pb, stats.avgPb, lowerIsBetter = true)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ComparisonRow("ROE", company.roe, stats.avgRoe, lowerIsBetter = false)
    }
}

@Composable
private fun ComparisonRow(label: String, company: Double?, sectorAvg: Double?, lowerIsBetter: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
        Text(
            Formatters.ratio(company),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        Text("vs. média ${Formatters.ratio(sectorAvg)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        val better = when {
            company == null || sectorAvg == null -> null
            lowerIsBetter -> company < sectorAvg
            else -> company > sectorAvg
        }
        if (better != null) {
            Text(
                if (better) "Melhor" else "Pior",
                style = MaterialTheme.typography.labelMedium,
                color = if (better) com.lynchscreener.ui.theme.ScoreGreat else com.lynchscreener.ui.theme.ScoreBad,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScoreBreakdownCard(components: List<ScoreComponent>) {
    SectionCard(title = "Raio-X do Score Peter Lynch") {
        if (components.isEmpty()) {
            Text("Sem detalhes de pontuação.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            components.forEach { component ->
                val isDisqualified = component.key == "DISQUALIFIED"
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            component.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isDisqualified) {
                            Text(
                                "${component.points.toInt()} pts",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (!isDisqualified && component.maxPoints > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (component.points / component.maxPoints).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        component.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (component != components.last()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(company: com.lynchscreener.domain.model.RankedCompany, market: Market, onRefresh: () -> Unit) {
    SectionCard(title = "Atualização") {
        Text("Cotação: ${Formatters.date(company.priceUpdatedAt)}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text("Balanço: ${Formatters.date(company.fundamentalsUpdatedAt)}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Atualizar esta ação agora")
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

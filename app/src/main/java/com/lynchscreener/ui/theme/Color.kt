package com.lynchscreener.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Dark Mode (default) — deep graphite, low eye-strain
// ---------------------------------------------------------------------------
val BackgroundDark = Color(0xFF0B0E11)
val SurfaceCardDark = Color(0xFF141920)
val SurfaceElevatedDark = Color(0xFF1C232D)
val BorderSubtleDark = Color(0xFF242D3A)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextSecondaryDark = Color(0xFF9CA3AF)
val TextMutedDark = Color(0xFF6B7280)

// ---------------------------------------------------------------------------
// Light Mode (clean alternative)
// ---------------------------------------------------------------------------
val BackgroundLight = Color(0xFFF5F7FA)
val SurfaceCardLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFEDF1F7)
val BorderSubtleLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextMutedLight = Color(0xFF64748B)

// ---------------------------------------------------------------------------
// Semantic — Peter Lynch score & indicators
// ---------------------------------------------------------------------------
val ScoreExcellent = Color(0xFF10B981) // 80..100 — pechincha / forte (emerald)
val ScoreGood = Color(0xFF38BDF8)      // 60..79  — bom / preço justo (cyan)
val ScoreNeutral = Color(0xFFF59E0B)   // 40..59  — neutro / atenção (amber)
val ScorePoor = Color(0xFFEF4444)      // 0..39   — caro / alerta (coral)

val AccentPrimary = Color(0xFF10B981)  // primary buttons / active indicators
val MarketBR = Color(0xFF22C55E)       // subtle BR detail
val MarketUS = Color(0xFF3B82F6)       // subtle US detail

val InfoBannerBlue = Color(0xFF1E3A5F)
val OnInfoBannerBlue = Color(0xFFBFDBFE)

@Immutable
data class LynchColors(
    val background: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentPrimary: Color,
    val onAccent: Color,
    val scoreExcellent: Color,
    val scoreGood: Color,
    val scoreNeutral: Color,
    val scorePoor: Color,
    val marketBr: Color,
    val marketUs: Color,
    val positive: Color,
    val negative: Color,
    val infoBanner: Color,
    val onInfoBanner: Color,
)

val DarkLynchColors = LynchColors(
    background = BackgroundDark,
    surfaceCard = SurfaceCardDark,
    surfaceElevated = SurfaceElevatedDark,
    borderSubtle = BorderSubtleDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    accentPrimary = AccentPrimary,
    onAccent = Color(0xFF052E1B),
    scoreExcellent = ScoreExcellent,
    scoreGood = ScoreGood,
    scoreNeutral = ScoreNeutral,
    scorePoor = ScorePoor,
    marketBr = MarketBR,
    marketUs = MarketUS,
    positive = ScoreExcellent,
    negative = ScorePoor,
    infoBanner = InfoBannerBlue,
    onInfoBanner = OnInfoBannerBlue,
)

val LightLynchColors = LynchColors(
    background = BackgroundLight,
    surfaceCard = SurfaceCardLight,
    surfaceElevated = SurfaceElevatedLight,
    borderSubtle = BorderSubtleLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    accentPrimary = AccentPrimary,
    onAccent = Color(0xFFFFFFFF),
    scoreExcellent = ScoreExcellent,
    scoreGood = Color(0xFF0284C7),
    scoreNeutral = ScoreNeutral,
    scorePoor = ScorePoor,
    marketBr = Color(0xFF16A34A),
    marketUs = Color(0xFF2563EB),
    positive = Color(0xFF059669),
    negative = Color(0xFFDC2626),
    infoBanner = Color(0xFFE0F2FE),
    onInfoBanner = Color(0xFF075985),
)

val LocalLynchColors = staticCompositionLocalOf { DarkLynchColors }

/** Maps a 0..100 Lynch score to its semantic color. */
fun scoreColor(score: Int, colors: LynchColors): Color = when {
    score >= 80 -> colors.scoreExcellent
    score >= 60 -> colors.scoreGood
    score >= 40 -> colors.scoreNeutral
    else -> colors.scorePoor
}

/** Human label for a 0..100 Lynch score. */
fun scoreLabel(score: Int): String = when {
    score >= 80 -> "Pechincha"
    score >= 60 -> "Bom preço"
    score >= 40 -> "Atenção"
    else -> "Alerta"
}

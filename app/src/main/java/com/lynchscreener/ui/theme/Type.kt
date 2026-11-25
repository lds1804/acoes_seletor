package com.lynchscreener.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography with tabular figures (`tnum`) on every numeric-heavy style, so
 * P/L, P/VP, ROE, DY, PEGY, price and ranks align perfectly in lists.
 *
 * Font: clean SansSerif fallback (Inter / Plus Jakarta Sans are downloaded
 * separately if desired); the critical requirement is `tnum`.
 */
private val tnum = "tnum"

private fun base(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Int = (size * 1.35).toInt(),
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = tnum,
)

val LynchTypography = Typography(
    // Display / headline
    headlineMedium = base(28, FontWeight.Bold, 34, -0.2f),
    headlineSmall = base(22, FontWeight.Bold, 30),
    titleLarge = base(20, FontWeight.Bold, 26, -0.1f),
    titleMedium = base(16, FontWeight.Bold, 22, -0.2f),
    titleSmall = base(13, FontWeight.SemiBold, 18),

    // Body
    bodyLarge = base(16, FontWeight.Normal, 22),
    bodyMedium = base(14, FontWeight.Normal, 20),
    bodySmall = base(12, FontWeight.Normal, 17),

    // Labels (indicator labels, captions)
    labelLarge = base(13, FontWeight.SemiBold, 18),
    labelMedium = base(11, FontWeight.Medium, 15, 0.6f),
    labelSmall = base(10, FontWeight.Medium, 14, 0.6f),
)

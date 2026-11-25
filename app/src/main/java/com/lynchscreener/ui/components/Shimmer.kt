package com.lynchscreener.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lynchscreener.ui.theme.LocalLynchColors

/** Pulsing dot that indicates live background work. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Reusable shimmer skeleton modifier. Draws an animated linear-gradient
 * sweeping across the surface using [drawBehind].
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val colors = LocalLynchColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    this.drawBehind {
        val x = progress * size.width * 2 - size.width
        val brush = Brush.linearGradient(
            colors = listOf(colors.surfaceCard, colors.surfaceElevated, colors.surfaceCard),
            start = Offset(x, 0f),
            end = Offset(x + size.width, size.height),
        )
        drawRect(brush)
    }
}

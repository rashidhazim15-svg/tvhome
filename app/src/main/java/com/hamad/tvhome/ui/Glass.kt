package com.hamad.tvhome.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val TvSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)

val TvSnappy = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = 900f,
)

val TvDpSpring = spring<Dp>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)

fun glassShape(radiusPt: Float, metrics: TvMetrics): Shape =
    RoundedCornerShape(metrics.dp(radiusPt))

/**
 * tvOS "Liquid Glass": a translucent body, a soft vertical tint, a bright specular
 * edge along the top-left and a barely-there inner rim. Android 9 has no real
 * backdrop blur, so the effect is built from layered gradients that read correctly
 * over video and over the launcher's dark backdrop.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    elevated: Boolean = false,
    alpha: Float = 1f,
    onDark: Boolean = true,
): Modifier = this
    .then(if (elevated) Modifier.shadow(24.dp, shape, clip = false) else Modifier)
    .clip(shape)
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.20f * alpha),
            0.35f to Color.White.copy(alpha = 0.11f * alpha),
            1f to Color.White.copy(alpha = 0.06f * alpha),
        ),
    )
    .background(
        if (onDark) Color(0xFF141416).copy(alpha = 0.42f * alpha)
        else Color.White.copy(alpha = 0.72f * alpha),
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.38f * alpha),
                Color.White.copy(alpha = 0.07f * alpha),
                Color.White.copy(alpha = 0.14f * alpha),
            ),
        ),
        shape = shape,
    )
    .drawWithContent {
        drawContent()
        // Specular highlight hugging the top edge.
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.45f * alpha),
                0.25f to Color.White.copy(alpha = 0.12f * alpha),
                1f to Color.Transparent,
            ),
            start = Offset(size.width * 0.06f, 1f),
            end = Offset(size.width * 0.94f, 1f),
            strokeWidth = 1.4f,
        )
    }

fun Modifier.whitePlatter(shape: Shape, focused: Boolean, elevation: Dp = 16.dp): Modifier =
    if (focused) shadow(elevation, shape, clip = false).background(Color.White, shape) else this

@Composable
fun glassPadding(metrics: TvMetrics): PaddingValues = PaddingValues(
    horizontal = metrics.dp(24f),
    vertical = metrics.dp(14f),
)

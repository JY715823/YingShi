package com.example.yingshi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.yingshi.ui.theme.YingShiColors
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlin.math.PI
import kotlin.math.sin

enum class YingShiBackdropVariant {
    SHELL,
    AUTH,
    PHOTOS,
    HOME,
    LIFE,
    ME,
}

@Composable
fun YingShiAuroraBackdrop(
    modifier: Modifier = Modifier,
    variant: YingShiBackdropVariant = YingShiBackdropVariant.SHELL,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spec = remember(variant, colors) {
        yingShiAuroraSpec(variant = variant, colors = colors)
    }
    val motionEnabled = rememberYingShiMotionEnabled()
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkleBreath",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(spec.gradient)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAuroraGlow(
                center = Offset(size.width * 0.18f, size.height * 0.14f),
                radius = size.height * 0.40f,
                colors = spec.topLeftGlow,
            )
            drawAuroraGlow(
                center = Offset(size.width * 0.86f, size.height * 0.16f),
                radius = size.height * 0.30f,
                colors = spec.topRightGlow,
            )
            drawAuroraGlow(
                center = Offset(size.width * 0.80f, size.height * 0.72f),
                radius = size.height * 0.34f,
                colors = spec.bottomRightGlow,
            )
            drawAuroraGlow(
                center = Offset(size.width * 0.16f, size.height * 0.82f),
                radius = size.height * 0.34f,
                colors = spec.bottomLeftGlow,
            )
            drawAuroraGlow(
                center = Offset(size.width * 0.50f, size.height * 0.40f),
                radius = size.height * 0.26f,
                colors = spec.centerGlow,
            )

            val width = size.width
            val height = size.height

            val upperAurora = Path().apply {
                moveTo(-width * 0.10f, height * 0.12f)
                cubicTo(
                    width * 0.12f,
                    height * 0.00f,
                    width * 0.38f,
                    height * 0.30f,
                    width * 0.58f,
                    height * 0.10f,
                )
                cubicTo(
                    width * 0.82f,
                    height * -0.02f,
                    width * 0.96f,
                    height * 0.20f,
                    width * 1.10f,
                    height * 0.02f,
                )
                lineTo(width * 1.10f, height * 0.20f)
                cubicTo(
                    width * 0.90f,
                    height * 0.34f,
                    width * 0.62f,
                    height * 0.18f,
                    width * 0.34f,
                    height * 0.34f,
                )
                cubicTo(
                    width * 0.16f,
                    height * 0.42f,
                    width * 0.00f,
                    height * 0.24f,
                    -width * 0.10f,
                    height * 0.28f,
                )
                close()
            }
            drawPath(
                path = upperAurora,
                brush = Brush.linearGradient(
                    colors = spec.upperAurora,
                    start = Offset(width * 0.04f, height * 0.04f),
                    end = Offset(width * 0.94f, height * 0.28f),
                ),
            )

            val lowerAurora = Path().apply {
                moveTo(-width * 0.08f, height * 0.92f)
                cubicTo(
                    width * 0.12f,
                    height * 0.70f,
                    width * 0.38f,
                    height * 0.98f,
                    width * 0.64f,
                    height * 0.78f,
                )
                cubicTo(
                    width * 0.84f,
                    height * 0.60f,
                    width * 0.96f,
                    height * 0.88f,
                    width * 1.08f,
                    height * 0.62f,
                )
                lineTo(width * 1.08f, height * 0.82f)
                cubicTo(
                    width * 0.86f,
                    height * 1.00f,
                    width * 0.60f,
                    height * 0.84f,
                    width * 0.28f,
                    height * 1.04f,
                )
                cubicTo(
                    width * 0.06f,
                    height * 1.08f,
                    -width * 0.04f,
                    height * 0.96f,
                    -width * 0.08f,
                    height * 0.92f,
                )
                close()
            }
            drawPath(
                path = lowerAurora,
                brush = Brush.linearGradient(
                    colors = spec.lowerAurora,
                    start = Offset(width * 0.08f, height * 0.70f),
                    end = Offset(width * 0.92f, height * 0.96f),
                ),
            )

            val glassSweep = Path().apply {
                moveTo(width * 0.08f, height * 0.44f)
                cubicTo(
                    width * 0.26f,
                    height * 0.30f,
                    width * 0.46f,
                    height * 0.62f,
                    width * 0.66f,
                    height * 0.44f,
                )
                cubicTo(
                    width * 0.82f,
                    height * 0.32f,
                    width * 0.92f,
                    height * 0.52f,
                    width * 1.02f,
                    height * 0.40f,
                )
            }
            drawPath(
                path = glassSweep,
                brush = Brush.horizontalGradient(spec.glassSweep),
                style = Stroke(width = 26f, cap = StrokeCap.Round),
            )

            val goldTrace = Path().apply {
                moveTo(width * 0.14f, height * 0.56f)
                cubicTo(
                    width * 0.30f,
                    height * 0.50f,
                    width * 0.48f,
                    height * 0.66f,
                    width * 0.70f,
                    height * 0.54f,
                )
                cubicTo(
                    width * 0.84f,
                    height * 0.46f,
                    width * 0.92f,
                    height * 0.54f,
                    width * 1.00f,
                    height * 0.48f,
                )
            }
            drawPath(
                path = goldTrace,
                brush = Brush.horizontalGradient(spec.goldTrace),
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )

            val petalVeil = Path().apply {
                moveTo(width * 0.54f, height * 0.16f)
                cubicTo(
                    width * 0.66f,
                    height * 0.26f,
                    width * 0.72f,
                    height * 0.42f,
                    width * 0.62f,
                    height * 0.54f,
                )
                cubicTo(
                    width * 0.50f,
                    height * 0.60f,
                    width * 0.42f,
                    height * 0.48f,
                    width * 0.40f,
                    height * 0.34f,
                )
                cubicTo(
                    width * 0.42f,
                    height * 0.22f,
                    width * 0.46f,
                    height * 0.16f,
                    width * 0.54f,
                    height * 0.16f,
                )
                close()
            }
            drawPath(
                path = petalVeil,
                brush = Brush.linearGradient(
                    colors = spec.petalVeil,
                    start = Offset(width * 0.42f, height * 0.20f),
                    end = Offset(width * 0.68f, height * 0.56f),
                ),
            )

            val lowerGlassBloom = Path().apply {
                moveTo(width * 0.12f, height * 0.66f)
                cubicTo(
                    width * 0.22f,
                    height * 0.58f,
                    width * 0.36f,
                    height * 0.72f,
                    width * 0.34f,
                    height * 0.88f,
                )
                cubicTo(
                    width * 0.26f,
                    height * 0.98f,
                    width * 0.10f,
                    height * 0.92f,
                    width * 0.08f,
                    height * 0.80f,
                )
                cubicTo(
                    width * 0.08f,
                    height * 0.72f,
                    width * 0.08f,
                    height * 0.68f,
                    width * 0.12f,
                    height * 0.66f,
                )
                close()
            }
            drawPath(
                path = lowerGlassBloom,
                brush = Brush.linearGradient(
                    colors = spec.lowerBloom,
                    start = Offset(width * 0.08f, height * 0.64f),
                    end = Offset(width * 0.34f, height * 0.92f),
                ),
            )

            repeat(24) { index ->
                val x = width * ((index * 29 + 17) % 100) / 100f
                val y = height * ((index * 13 + 23) % 100) / 100f
                val isWarm = index % 5 == 0
                val phaseOffset = (index * 0.7f) % (2 * PI).toFloat()
                val breathMultiplier = if (motionEnabled) {
                    (0.55f + 0.45f * sin(breathPhase + phaseOffset)).coerceIn(0.15f, 1f)
                } else {
                    1f
                }
                drawCircle(
                    color = if (isWarm) {
                        spec.warmSparkle.copy(alpha = spec.warmSparkleAlpha * breathMultiplier)
                    } else {
                        spec.coolSparkle.copy(
                            alpha = (if (index % 3 == 0) spec.coolSparkleAlphaStrong else spec.coolSparkleAlphaSoft) * breathMultiplier,
                        )
                    },
                    radius = if (index % 4 == 0) 4.4f else 2.2f,
                    center = Offset(x, y),
                )
            }
        }
        content()
    }
}

private fun DrawScope.drawAuroraGlow(
    center: Offset,
    radius: Float,
    colors: List<Color>,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = colors,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

@Immutable
private data class YingShiAuroraSpec(
    val gradient: List<Color>,
    val topLeftGlow: List<Color>,
    val topRightGlow: List<Color>,
    val bottomRightGlow: List<Color>,
    val bottomLeftGlow: List<Color>,
    val centerGlow: List<Color>,
    val upperAurora: List<Color>,
    val lowerAurora: List<Color>,
    val glassSweep: List<Color>,
    val goldTrace: List<Color>,
    val petalVeil: List<Color>,
    val lowerBloom: List<Color>,
    val warmSparkle: Color,
    val warmSparkleAlpha: Float,
    val coolSparkle: Color,
    val coolSparkleAlphaStrong: Float,
    val coolSparkleAlphaSoft: Float,
)

private fun yingShiAuroraSpec(
    variant: YingShiBackdropVariant,
    colors: YingShiColors,
): YingShiAuroraSpec = when (variant) {
    YingShiBackdropVariant.AUTH -> YingShiAuroraSpec(
        gradient = listOf(
            Color(0xFFF6FDFF),
            colors.glowWash.copy(alpha = 0.90f),
            colors.sectionBackground.copy(alpha = 0.80f),
            colors.memoryWash.copy(alpha = 0.75f),
            Color(0xFFF4FCFF),
        ),
        topLeftGlow = listOf(colors.glowWash.copy(alpha = 0.92f), colors.glowWash.copy(alpha = 0.30f), Color.Transparent),
        topRightGlow = listOf(colors.goldAccent.copy(alpha = 0.55f), colors.goldAccent.copy(alpha = 0.12f), Color.Transparent),
        bottomRightGlow = listOf(colors.memoryWash.copy(alpha = 0.80f), colors.memoryWash.copy(alpha = 0.22f), Color.Transparent),
        bottomLeftGlow = listOf(colors.glassStroke.copy(alpha = 0.50f), colors.glassStroke.copy(alpha = 0.10f), Color.Transparent),
        centerGlow = listOf(colors.raisedSurface.copy(alpha = 0.50f), colors.raisedSurface.copy(alpha = 0.10f), Color.Transparent),
        upperAurora = listOf(colors.raisedSurface.copy(alpha = 0.06f), colors.glowWash.copy(alpha = 0.52f), colors.glassStroke.copy(alpha = 0.18f), colors.raisedSurface.copy(alpha = 0.05f)),
        lowerAurora = listOf(colors.raisedSurface.copy(alpha = 0.05f), colors.glowWash.copy(alpha = 0.40f), colors.memoryWash.copy(alpha = 0.38f), colors.raisedSurface.copy(alpha = 0.05f)),
        glassSweep = listOf(Color.Transparent, colors.raisedSurface.copy(alpha = 0.80f), colors.glowWash.copy(alpha = 0.50f), Color.Transparent),
        goldTrace = listOf(Color.Transparent, Color(0xD4F7CE83), Color.Transparent),
        petalVeil = listOf(colors.raisedSurface.copy(alpha = 0.18f), colors.memoryWash.copy(alpha = 0.10f), colors.glassStroke.copy(alpha = 0.12f)),
        lowerBloom = listOf(colors.raisedSurface.copy(alpha = 0.10f), colors.glowWash.copy(alpha = 0.12f), colors.memoryWash.copy(alpha = 0.28f)),
        warmSparkle = Color(0xFFFFF7E8),
        warmSparkleAlpha = 0.52f,
        coolSparkle = Color.White,
        coolSparkleAlphaStrong = 0.38f,
        coolSparkleAlphaSoft = 0.20f,
    )

    YingShiBackdropVariant.PHOTOS -> YingShiAuroraSpec(
        gradient = listOf(
            Color(0xFFF8FCFF),
            colors.appBackground,
            colors.glowWash.copy(alpha = 0.96f),
            colors.sectionBackground.copy(alpha = 0.78f),
            Color(0xFFF5FBFF),
        ),
        topLeftGlow = listOf(Color(0xCEEFFFFF), Color(0x42D1F6FF), Color.Transparent),
        topRightGlow = listOf(Color(0xC7E7FFF2), Color(0x30C8F3EA), Color.Transparent),
        bottomRightGlow = listOf(Color(0xA8FFEBD9), Color(0x2AE0F8F0), Color.Transparent),
        bottomLeftGlow = listOf(Color(0x88D6F8FF), Color(0x20D6F8FF), Color.Transparent),
        centerGlow = listOf(Color(0x90FFFFFF), Color(0x20FFFFFF), Color.Transparent),
        upperAurora = listOf(Color(0x10FFFFFF), Color(0x66ECFAFF), Color(0x20DBFFF7), Color(0x08FFFFFF)),
        lowerAurora = listOf(Color(0x08FFFFFF), Color(0x54CFFAF8), Color(0x46FFF0D9), Color(0x08FFFFFF)),
        glassSweep = listOf(Color.Transparent, Color(0xA8FFFFFF), Color(0x58D7F8FF), Color.Transparent),
        goldTrace = listOf(Color.Transparent, Color(0xA6EAD8A6), Color.Transparent),
        petalVeil = listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF), Color(0x1EE1F6FF)),
        lowerBloom = listOf(Color(0x16FFFFFF), Color(0x14D7FFF6), Color(0x2CDDF7EA)),
        warmSparkle = Color(0xFFFFFBF1),
        warmSparkleAlpha = 0.30f,
        coolSparkle = Color.White,
        coolSparkleAlphaStrong = 0.28f,
        coolSparkleAlphaSoft = 0.14f,
    )

    YingShiBackdropVariant.HOME -> YingShiAuroraSpec(
        gradient = listOf(
            Color(0xFFF8FCFF),
            Color(0xFFEDF8FF),
            colors.glowWash.copy(alpha = 0.98f),
            colors.memoryWash.copy(alpha = 0.82f),
            Color(0xFFF8FCFF),
        ),
        topLeftGlow = listOf(Color(0xD5EFFBFF), Color(0x42D2F5FF), Color.Transparent),
        topRightGlow = listOf(Color(0xBFFFF2DD), Color(0x2EFEE7CB), Color.Transparent),
        bottomRightGlow = listOf(Color(0xABFFE1EA), Color(0x2ADDF2FF), Color.Transparent),
        bottomLeftGlow = listOf(Color(0x90D8F6FF), Color(0x20D8F6FF), Color.Transparent),
        centerGlow = listOf(Color(0x95FFFFFF), Color(0x22FFFFFF), Color.Transparent),
        upperAurora = listOf(Color(0x10FFFFFF), colors.memoryWash.copy(alpha = 0.42f), Color(0x24D7F2FF), Color(0x08FFFFFF)),
        lowerAurora = listOf(Color(0x08FFFFFF), Color(0x58D6FFF6), colors.memoryWash.copy(alpha = 0.30f), Color(0x08FFFFFF)),
        glassSweep = listOf(Color.Transparent, Color(0xB0FFFFFF), Color(0x62D8F6FF), Color.Transparent),
        goldTrace = listOf(Color.Transparent, Color(0xE0F7C88A), Color.Transparent),
        petalVeil = listOf(Color(0x34FFFFFF), Color(0x14FFFFFF), Color(0x26FFD8E6)),
        lowerBloom = listOf(Color(0x1AFFFFFF), Color(0x14D8FFF3), Color(0x3CFFE0C7)),
        warmSparkle = Color(0xFFFFFAF2),
        warmSparkleAlpha = 0.52f,
        coolSparkle = Color.White,
        coolSparkleAlphaStrong = 0.30f,
        coolSparkleAlphaSoft = 0.16f,
    )

    YingShiBackdropVariant.LIFE -> YingShiAuroraSpec(
        gradient = listOf(
            Color(0xFFF8FCFD),
            colors.appBackground,
            colors.softGreenContainer.copy(alpha = 0.48f),
            colors.glowWash.copy(alpha = 0.88f),
            Color(0xFFF7FCFF),
        ),
        topLeftGlow = listOf(Color(0xCCE8FFFF), Color(0x38D5F1F2), Color.Transparent),
        topRightGlow = listOf(Color(0xC1F1FFE6), Color(0x36DAF5D9), Color.Transparent),
        bottomRightGlow = listOf(Color(0xA4FFE8D6), Color(0x28DDF0E2), Color.Transparent),
        bottomLeftGlow = listOf(Color(0x8CD2FFF1), Color(0x20D2FFF1), Color.Transparent),
        centerGlow = listOf(Color(0x8CFFFFFF), Color(0x1CFFFFFF), Color.Transparent),
        upperAurora = listOf(Color(0x10FFFFFF), Color(0x5DEBFFF0), Color(0x20D9FFDE), Color(0x08FFFFFF)),
        lowerAurora = listOf(Color(0x08FFFFFF), Color(0x50C9FFF1), Color(0x48E6FFD8), Color(0x08FFFFFF)),
        glassSweep = listOf(Color.Transparent, Color(0xA6FFFFFF), Color(0x58D8FFF0), Color.Transparent),
        goldTrace = listOf(Color.Transparent, Color(0xA8D8F0B0), Color.Transparent),
        petalVeil = listOf(Color(0x2AFFFFFF), Color(0x10FFFFFF), Color(0x22D8FFE7)),
        lowerBloom = listOf(Color(0x14FFFFFF), Color(0x14D7FFF3), Color(0x2CE0FFD5)),
        warmSparkle = Color(0xFFFCFFF7),
        warmSparkleAlpha = 0.32f,
        coolSparkle = Color.White,
        coolSparkleAlphaStrong = 0.26f,
        coolSparkleAlphaSoft = 0.14f,
    )

    YingShiBackdropVariant.ME -> YingShiAuroraSpec(
        gradient = listOf(
            Color(0xFFF9FCFF),
            Color(0xFFF1F8FF),
            colors.glowWash.copy(alpha = 0.90f),
            colors.memoryWash.copy(alpha = 0.80f),
            Color(0xFFF9FCFF),
        ),
        topLeftGlow = listOf(Color(0xD0F4FCFF), Color(0x36DAEEFF), Color.Transparent),
        topRightGlow = listOf(Color(0xC8FFF0E4), Color(0x2CEFD8D9), Color.Transparent),
        bottomRightGlow = listOf(Color(0xAEFFE0F0), Color(0x2AD8EEFF), Color.Transparent),
        bottomLeftGlow = listOf(Color(0x92E1F3FF), Color(0x20E1F3FF), Color.Transparent),
        centerGlow = listOf(Color(0x96FFFFFF), Color(0x20FFFFFF), Color.Transparent),
        upperAurora = listOf(Color(0x10FFFFFF), Color(0x62FFF1F0), Color(0x20DAF0FF), Color(0x08FFFFFF)),
        lowerAurora = listOf(Color(0x08FFFFFF), Color(0x56D2FFF6), Color(0x4EFFDFE6), Color(0x08FFFFFF)),
        glassSweep = listOf(Color.Transparent, Color(0xAEFFFFFF), Color(0x60D7EFFA), Color.Transparent),
        goldTrace = listOf(Color.Transparent, Color(0xB8F2CBA7), Color.Transparent),
        petalVeil = listOf(Color(0x30FFFFFF), Color(0x12FFFFFF), Color(0x24FFD5EA)),
        lowerBloom = listOf(Color(0x18FFFFFF), Color(0x14D8F7F5), Color(0x30FFD8D0)),
        warmSparkle = Color(0xFFFFF8F3),
        warmSparkleAlpha = 0.40f,
        coolSparkle = Color.White,
        coolSparkleAlphaStrong = 0.32f,
        coolSparkleAlphaSoft = 0.16f,
    )

    YingShiBackdropVariant.SHELL -> YingShiAuroraSpec(
        gradient = listOf(
            Color(0xFFF8FCFF),
            colors.appBackground,
            colors.sectionBackground.copy(alpha = 0.74f),
            colors.glowWash.copy(alpha = 0.92f),
            Color(0xFFF8FCFF),
        ),
        topLeftGlow = listOf(Color(0xCFEFFFFF), Color(0x36D7F6FF), Color.Transparent),
        topRightGlow = listOf(Color(0xC0EEFDEB), Color(0x2ED5EEE2), Color.Transparent),
        bottomRightGlow = listOf(Color(0xA6FFE6E6), Color(0x28D9F0FF), Color.Transparent),
        bottomLeftGlow = listOf(Color(0x84D8F7FF), Color(0x1ED8F7FF), Color.Transparent),
        centerGlow = listOf(Color(0x88FFFFFF), Color(0x18FFFFFF), Color.Transparent),
        upperAurora = listOf(Color(0x0EFFFFFF), Color(0x5CEFF7F6), Color(0x1ED8F1FF), Color(0x08FFFFFF)),
        lowerAurora = listOf(Color(0x08FFFFFF), Color(0x50D3FBF5), Color(0x42FFE4D7), Color(0x08FFFFFF)),
        glassSweep = listOf(Color.Transparent, Color(0xA4FFFFFF), Color(0x52D8F5FF), Color.Transparent),
        goldTrace = listOf(Color.Transparent, Color(0x9EDCE6C1), Color.Transparent),
        petalVeil = listOf(Color(0x26FFFFFF), Color(0x10FFFFFF), Color(0x20E2F4FF)),
        lowerBloom = listOf(Color(0x14FFFFFF), Color(0x12D8FFF3), Color(0x26DDF4E8)),
        warmSparkle = Color(0xFFFFFBF4),
        warmSparkleAlpha = 0.26f,
        coolSparkle = Color.White,
        coolSparkleAlphaStrong = 0.22f,
        coolSparkleAlphaSoft = 0.12f,
    )
}

package com.example.yingshi.ui.components

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.yingshi.feature.photos.SettingsRepository
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun rememberYingShiMotionEnabled(): Boolean {
    val followSystemReducedMotion = SettingsRepository.getSettingsState()
        .interactionPreferences
        .followSystemReducedMotion
    val systemMotionEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    return if (followSystemReducedMotion) systemMotionEnabled else true
}

@Composable
fun Modifier.yingShiMediaEnterMotion(
    active: Boolean = true,
    motionEnabled: Boolean = rememberYingShiMotionEnabled(),
): Modifier {
    val motion = YingShiThemeTokens.motion
    val alpha by animateFloatAsState(
        targetValue = if (active || !motionEnabled) 1f else 0.92f,
        animationSpec = tween(if (motionEnabled) motion.viewerTransitionMillis else 0, easing = motion.easing),
        label = "yingShiMediaEnterAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active || !motionEnabled) 1f else motion.mediaEnterScale,
        animationSpec = tween(if (motionEnabled) motion.viewerTransitionMillis else 0, easing = motion.easing),
        label = "yingShiMediaEnterScale",
    )
    return this
        .alpha(alpha)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

@Composable
fun Modifier.yingShiSoftReveal(
    visible: Boolean = true,
    enterScale: Float = YingShiThemeTokens.motion.sectionEnterScale,
    motionEnabled: Boolean = rememberYingShiMotionEnabled(),
): Modifier {
    val motion = YingShiThemeTokens.motion
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
        label = "yingShiSoftRevealAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible || !motionEnabled) 1f else enterScale,
        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
        label = "yingShiSoftRevealScale",
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible || !motionEnabled) 0f else 18f,
        animationSpec = tween(if (motionEnabled) motion.floatingMillis else 0, easing = motion.easing),
        label = "yingShiSoftRevealTranslateY",
    )
    return this
        .alpha(alpha)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.translationY = translationY
        }
}

@Composable
fun Modifier.yingShiRouteReveal(
    visible: Boolean = true,
    motionEnabled: Boolean = rememberYingShiMotionEnabled(),
): Modifier {
    val motion = YingShiThemeTokens.motion
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (motionEnabled) motion.routeMillis else 0, easing = motion.easing),
        label = "yingShiRouteRevealAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible || !motionEnabled) 1f else motion.routeEnterScale,
        animationSpec = tween(if (motionEnabled) motion.routeMillis else 0, easing = motion.easing),
        label = "yingShiRouteRevealScale",
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible || !motionEnabled) 0f else 24f,
        animationSpec = tween(if (motionEnabled) motion.routeMillis else 0, easing = motion.easing),
        label = "yingShiRouteRevealTranslateY",
    )
    return this
        .alpha(alpha)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.translationY = translationY
        }
}

@Composable
fun Modifier.yingShiMemoryGlow(
    visible: Boolean,
    warm: Boolean = false,
    motionEnabled: Boolean = rememberYingShiMotionEnabled(),
): Modifier {
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val glowAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (motionEnabled) motion.memoryGlowMillis else 0, easing = motion.easing),
        label = "yingShiMemoryGlowAlpha",
    )
    if (glowAlpha <= 0.01f) return this

    val accent = if (warm) colors.memoryAccent else colors.primaryContainer
    return this.background(
        Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = motion.ambientGlowAlpha * glowAlpha),
                colors.glowWash.copy(alpha = 0.10f * glowAlpha),
                Color.Transparent,
            ),
            center = Offset.Unspecified,
            radius = 360f,
        ),
    )
}

/**
 * 微妙的光泽扫过效果（shimmer），用于卡片等需要丝绸光泽质感的组件。
 * 周期 3500ms，与 YingShiPrimaryMistButton 的 shimmer 设计语言一致。
 * 遵循 motionEnabled 守卫，关闭动画时退化为无效果。
 */
@Composable
fun Modifier.yingShiShimmerSweep(
    enabled: Boolean = true,
    motionEnabled: Boolean = rememberYingShiMotionEnabled(),
): Modifier {
    val transition = rememberInfiniteTransition(label = "yingShiShimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "yingShiShimmerOffset",
    )
    if (!enabled || !motionEnabled) return this
    return this.background(
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            start = Offset(offset * 3f - 1f, 0f),
            end = Offset(offset * 3f, 0f),
        ),
    )
}

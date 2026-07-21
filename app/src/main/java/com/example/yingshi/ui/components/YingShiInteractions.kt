package com.example.yingshi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.yingshi.ui.theme.YingShiThemeTokens

fun Modifier.yingShiPressFeedback(
    enabled: Boolean = true,
    pressedScale: Float = Float.NaN,
): Modifier = composed {
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val tapMillis = if (motionEnabled) motion.tapMillis else 0
    val resolvedPressedScale = if (pressedScale.isNaN()) motion.pressedScale else pressedScale
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) resolvedPressedScale else 1f,
        animationSpec = tween(durationMillis = tapMillis),
        label = "yingShiPressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.yingShiClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    pressedScale: Float = Float.NaN,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val tapMillis = if (motionEnabled) motion.tapMillis else 0
    val resolvedPressedScale = if (pressedScale.isNaN()) motion.pressedScale else pressedScale
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) resolvedPressedScale else 1f,
        animationSpec = tween(durationMillis = tapMillis),
        label = "yingShiClickableScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.52f
            pressed -> 0.90f
            else -> 1f
        },
        animationSpec = tween(durationMillis = tapMillis),
        label = "yingShiClickableAlpha",
    )
    val shapeModifier = if (shape != null) Modifier.clip(shape) else Modifier
    val base = this
        .then(shapeModifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .alpha(contentAlpha)
    if (onLongClick != null) {
        base.combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        base.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick,
        )
    }
}

@Composable
fun Modifier.yingShiHapticClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    pressedScale: Float = Float.NaN,
    hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val motion = YingShiThemeTokens.motion
    return yingShiClickable(
        enabled = enabled,
        shape = shape,
        pressedScale = pressedScale,
        onClick = {
            if (motion.hapticEnabled && enabled) {
                haptic.performHapticFeedback(hapticType)
            }
            onClick()
        },
    )
}

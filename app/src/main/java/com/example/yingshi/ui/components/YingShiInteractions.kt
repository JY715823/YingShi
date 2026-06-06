package com.example.yingshi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
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
    val resolvedPressedScale = if (pressedScale.isNaN()) motion.pressedScale else pressedScale
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) resolvedPressedScale else 1f,
        animationSpec = tween(durationMillis = motion.tapMillis),
        label = "yingShiPressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.yingShiClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    pressedScale: Float = Float.NaN,
    onClick: () -> Unit,
): Modifier = composed {
    val motion = YingShiThemeTokens.motion
    val resolvedPressedScale = if (pressedScale.isNaN()) motion.pressedScale else pressedScale
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) resolvedPressedScale else 1f,
        animationSpec = tween(durationMillis = motion.tapMillis),
        label = "yingShiClickableScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.52f
            pressed -> 0.90f
            else -> 1f
        },
        animationSpec = tween(durationMillis = motion.tapMillis),
        label = "yingShiClickableAlpha",
    )
    val shapeModifier = if (shape != null) Modifier.clip(shape) else Modifier
    this
        .then(shapeModifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .alpha(contentAlpha)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick,
        )
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

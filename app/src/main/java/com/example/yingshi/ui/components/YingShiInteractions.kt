package com.example.yingshi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

private const val YingShiTapMillis = 170

fun Modifier.yingShiPressFeedback(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = YingShiTapMillis),
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
    pressedScale: Float = 0.975f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = YingShiTapMillis),
        label = "yingShiClickableScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.52f
            pressed -> 0.90f
            else -> 1f
        },
        animationSpec = tween(durationMillis = YingShiTapMillis),
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
            indication = null,
            onClick = onClick,
        )
}

@Composable
fun Modifier.yingShiHapticClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    pressedScale: Float = 0.965f,
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

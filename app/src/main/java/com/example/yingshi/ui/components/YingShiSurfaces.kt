package com.example.yingshi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay

enum class YingShiNoticeTone {
    INFO,
    SUCCESS,
    WARNING,
}

data class YingShiNotice(
    val message: String,
    val tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    val nonce: Int,
)

@Composable
fun YingShiMistBackground(
    modifier: Modifier = Modifier,
    showWaves: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.appBackground,
                        colors.sectionBackground.copy(alpha = 0.72f),
                        colors.glowWash,
                        colors.appBackground,
                    ),
                ),
            ),
    ) {
        if (showWaves) {
            MistWaveCanvas(modifier = Modifier.matchParentSize())
        }
        content()
    }
}

@Composable
fun YingShiNoticeHost(
    notice: YingShiNotice?,
    modifier: Modifier = Modifier,
    onExpired: (Int) -> Unit,
) {
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val colors = YingShiThemeTokens.colors
    LaunchedEffect(notice?.nonce) {
        val active = notice ?: return@LaunchedEffect
        delay(motion.noticeVisibleMillis.toLong())
        onExpired(active.nonce)
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = notice != null,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = if (motionEnabled) motion.noticeMillis else 0,
                easing = motion.easing,
            ),
        ),
        exit = androidx.compose.animation.fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = if (motionEnabled) motion.stateMillis else 0,
                easing = motion.easing,
            ),
        ),
        modifier = modifier,
    ) {
        val activeNotice = notice ?: return@AnimatedVisibility
        val icon = when (activeNotice.tone) {
            YingShiNoticeTone.INFO -> Icons.Rounded.Info
            YingShiNoticeTone.SUCCESS -> Icons.Rounded.CheckCircle
            YingShiNoticeTone.WARNING -> Icons.Rounded.ErrorOutline
        }
        val container = when (activeNotice.tone) {
            YingShiNoticeTone.INFO -> colors.raisedSurface.copy(alpha = 0.94f)
            YingShiNoticeTone.SUCCESS -> colors.memoryContainer.copy(alpha = 0.95f)
            YingShiNoticeTone.WARNING -> colors.memoryWash.copy(alpha = 0.96f)
        }
        val content = when (activeNotice.tone) {
            YingShiNoticeTone.INFO -> colors.titleAccent
            YingShiNoticeTone.SUCCESS -> colors.onMemoryContainer
            YingShiNoticeTone.WARNING -> colors.memoryAccent
        }
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .yingShiSoftReveal(visible = true, motionEnabled = motionEnabled),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = container,
            border = BorderStroke(
                1.dp,
                if (activeNotice.tone == YingShiNoticeTone.INFO) {
                    colors.glassStroke.copy(alpha = 0.68f)
                } else {
                    colors.memoryAccent.copy(alpha = 0.22f)
                },
            ),
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.34f),
                                colors.glowWash.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = activeNotice.message,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun YingShiStateLayer(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val spacing = YingShiThemeTokens.spacing
    val icon = when (tone) {
        YingShiNoticeTone.INFO -> Icons.Rounded.Info
        YingShiNoticeTone.SUCCESS -> Icons.Rounded.CheckCircle
        YingShiNoticeTone.WARNING -> Icons.Rounded.ErrorOutline
    }
    val accent = when (tone) {
        YingShiNoticeTone.INFO -> colors.titleAccent
        YingShiNoticeTone.SUCCESS -> colors.memoryAccent
        YingShiNoticeTone.WARNING -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.10f),
                            colors.glowWash.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset.Zero,
                        radius = 520f,
                    ),
                )
                .padding(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            if (actionLabel != null && onAction != null) {
                Surface(
                    modifier = Modifier.yingShiHapticClickable(
                        shape = RoundedCornerShape(radius.capsule),
                        onClick = onAction,
                    ),
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.primaryContainer.copy(alpha = 0.68f),
                    border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.58f)),
                ) {
                    Text(
                        text = actionLabel,
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun MistWaveCanvas(modifier: Modifier = Modifier) {
    val colors = YingShiThemeTokens.colors
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.glowWash.copy(alpha = 0.54f), Color.Transparent),
                center = Offset(w * 0.05f, h * 0.28f),
                radius = h * 0.42f,
            ),
            radius = h * 0.42f,
            center = Offset(w * 0.05f, h * 0.28f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.primaryContainer.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(w * 0.64f, h * 0.95f),
                radius = h * 0.46f,
            ),
            radius = h * 0.46f,
            center = Offset(w * 0.64f, h * 0.95f),
        )

        val mainPath = Path().apply {
            moveTo(-w * 0.12f, h * 0.76f)
            cubicTo(w * 0.12f, h * 0.69f, w * 0.25f, h * 0.85f, w * 0.48f, h * 0.77f)
            cubicTo(w * 0.72f, h * 0.68f, w * 0.85f, h * 0.84f, w * 1.12f, h * 0.73f)
        }
        drawPath(
            path = mainPath,
            color = Color.White.copy(alpha = 0.76f),
            style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = mainPath,
            color = colors.glassStroke.copy(alpha = 0.26f),
            style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = mainPath,
            color = colors.goldAccent.copy(alpha = 0.20f),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val sidePath = Path().apply {
            moveTo(w * 0.02f, -h * 0.02f)
            cubicTo(w * 0.24f, h * 0.14f, w * 0.08f, h * 0.27f, w * 0.25f, h * 0.42f)
            cubicTo(w * 0.38f, h * 0.54f, w * 0.18f, h * 0.63f, -w * 0.08f, h * 0.70f)
        }
        drawPath(
            path = sidePath,
            color = Color.White.copy(alpha = 0.62f),
            style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = sidePath,
            color = colors.goldAccent.copy(alpha = 0.24f),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun YingShiMistCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    color: Color? = null,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val resolvedShape = shape ?: RoundedCornerShape(YingShiThemeTokens.radius.lg)
    Surface(
        modifier = modifier,
        shape = resolvedShape,
        color = color ?: YingShiThemeTokens.colors.raisedSurface.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, borderColor ?: YingShiThemeTokens.colors.dividerSoft.copy(alpha = 0.70f)),
        content = content,
    )
}

@Composable
fun YingShiIconBubble(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = YingShiThemeTokens.colors
        val bubbleColor = if (selected) colors.primaryContainer.copy(alpha = 0.86f) else colors.sectionBackground.copy(alpha = 0.78f)
    val iconColor = if (selected) colors.titleAccent else colors.titleAccent.copy(alpha = 0.86f)
    val clickableModifier = if (onClick != null) {
        Modifier
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickableModifier),
        shape = CircleShape,
        color = bubbleColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (selected) colors.glassStroke.copy(alpha = 0.76f) else colors.dividerSoft.copy(alpha = 0.66f)),
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun YingShiPrimaryMistButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier
            .yingShiClickable(
                enabled = enabled && !loading,
                shape = shape,
                pressedScale = 0.965f,
                onClick = onClick,
            ),
        shape = shape,
        color = if (enabled) colors.primaryContainer.copy(alpha = 0.88f) else colors.sectionBackground.copy(alpha = 0.82f),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) colors.glassStroke.copy(alpha = 0.78f) else colors.dividerSoft,
        ),
        shadowElevation = if (enabled) 1.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.58f),
                            colors.glowWash.copy(alpha = 0.60f),
                            colors.primaryContainer.copy(alpha = 0.44f),
                            Color.White.copy(alpha = 0.34f),
                        ),
                    ),
                )
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = colors.onPrimaryContainer,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (enabled) colors.onPrimaryContainer else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
fun YingShiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = YingShiThemeTokens.colors
    varFocusedField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        icon = icon,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary),
    )
}

@Composable
private fun varFocusedField(
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    enabled: Boolean,
    singleLine: Boolean,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation,
    textStyle: TextStyle,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val spacing = YingShiThemeTokens.spacing
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.82f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused) colors.glassStroke.copy(alpha = 0.92f) else colors.dividerSoft.copy(alpha = 0.82f),
        ),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.titleAccent,
                modifier = Modifier.size(27.dp),
            )
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 32.dp)
                    .background(colors.dividerSoft),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                enabled = enabled,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                textStyle = textStyle,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textSecondary.copy(alpha = 0.78f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
fun YingShiEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    backgroundColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val clickModifier = if (onClick != null) {
        Modifier
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(radius.lg),
        color = (backgroundColor ?: colors.raisedSurface).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.64f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.primaryContainer.copy(alpha = 0.66f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.goldAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary.copy(alpha = 0.72f),
            )
        }
    }
}

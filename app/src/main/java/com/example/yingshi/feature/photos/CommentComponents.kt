package com.example.yingshi.feature.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import com.example.yingshi.ui.theme.YingShiThemeTokens

private const val DefaultVisibleCommentCount = 10

@Composable
fun CommentInputBar(
    stateKey: String,
    placeholder: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    var value by rememberSaveable(stateKey) { mutableStateOf("") }
    val sendEnabled = value.trim().isNotEmpty()
    val containerColor = if (darkMode) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    }
    val textColor = if (darkMode) {
        Color.White.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val placeholderColor = if (darkMode) {
        Color.White.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(radius.lg),
            color = containerColor,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(textColor),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = placeholderColor,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        TextButton(
            enabled = sendEnabled,
            onClick = {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed)
                    value = ""
                }
            },
        ) {
            Text(
                text = "发送",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (sendEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    placeholderColor
                },
            )
        }
    }
}

fun List<CommentUiModel>.visibleComments(expanded: Boolean): List<CommentUiModel> {
    return if (expanded) {
        this
    } else {
        take(DefaultVisibleCommentCount)
    }
}

fun List<CommentUiModel>.hasHiddenComments(expanded: Boolean): Boolean {
    return !expanded && size > DefaultVisibleCommentCount
}

fun List<CommentUiModel>.canCollapseComments(expanded: Boolean): Boolean {
    return expanded && size > DefaultVisibleCommentCount
}

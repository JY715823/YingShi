package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun BackendLoadingCard(
    text: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.titleAccent,
                trackColor = colors.sectionBackground,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun BackendNoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    fillWidth: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (actionLabel != null && onAction != null) {
                BackendActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
internal fun BackendInlineNotice(
    text: String,
    emphasized: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (emphasized) {
            colors.primaryContainer.copy(alpha = 0.56f)
        } else {
            colors.raisedSurface.copy(alpha = 0.92f)
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) colors.glassStroke.copy(alpha = 0.78f) else colors.dividerSoft.copy(alpha = 0.66f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasized) {
                    colors.titleAccent
                } else {
                    colors.textSecondary
                },
            )
            if (actionLabel != null && onAction != null) {
                BackendActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
private fun BackendActionButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier.yingShiClickable(
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = colors.primaryContainer.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.82f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
    }
}

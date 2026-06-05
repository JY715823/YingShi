package com.example.yingshi.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun AppMediaSelectionBadge(
    selected: Boolean,
    disabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                when {
                    disabled -> Color.White.copy(alpha = 0.24f)
                    selected -> colors.primaryContainer
                    else -> Color.Black.copy(alpha = 0.10f)
                },
            )
            .border(
                width = 1.5.dp,
                color = when {
                    disabled -> Color.White.copy(alpha = 0.54f)
                    selected -> colors.glassStroke
                    else -> Color.White.copy(alpha = 0.88f)
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = colors.onPrimaryContainer,
            )
        } else if (disabled) {
            Text(
                text = "·",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

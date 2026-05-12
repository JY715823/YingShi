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

@Composable
fun AppMediaSelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val blueColor = Color(0xFF3B82F6)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) blueColor else Color.Black.copy(alpha = 0.10f),
            )
            .border(
                width = 1.5.dp,
                color = if (selected) blueColor else Color.White.copy(alpha = 0.88f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = Color.White,
            )
        }
    }
}

package com.example.yingshi.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun PhotoViewerPlaceholderScreen(
    route: PhotoViewerPlaceholderRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101821),
                        Color(0xFF162434),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        text = "返回",
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(radius.capsule),
                    color = Color.White.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = "媒体流查看态占位",
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(radius.lg),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF89A8D8),
                                        Color(0xFFCBD9F2),
                                    ),
                                ),
                                shape = RoundedCornerShape(radius.md),
                            ),
                    )

                    Text(
                        text = "这里先打通从照片页进入查看态的链路",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.96f),
                    )
                    Text(
                        text = "当前只保留路由、回退和基础入参展示，不接真实 Viewer 交互。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.74f),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(radius.md),
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    ViewerInfoRow(label = "媒体 ID", value = route.mediaId)
                    ViewerInfoRow(label = "起始位置", value = "${route.mediaPosition} / ${route.mediaCount}")
                    ViewerInfoRow(label = "来源上下文", value = "照片页全局媒体流")
                    ViewerInfoRow(label = "进入时密度", value = route.densityLabel)
                }
            }
        }
    }
}

@Composable
private fun ViewerInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.68f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.90f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoViewerPlaceholderScreenPreview() {
    YingShiTheme {
        PhotoViewerPlaceholderScreen(
            route = PhotoViewerPlaceholderRoute(
                mediaId = "media-2026-04-18-a",
                mediaPosition = 1,
                mediaCount = 38,
                densityLabel = "3列",
            ),
            onBack = { },
        )
    }
}

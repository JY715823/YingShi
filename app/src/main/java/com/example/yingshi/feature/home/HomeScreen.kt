package com.example.yingshi.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.YingShiIconBubble
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPhotos: () -> Unit = {},
    onOpenLife: () -> Unit = {},
    onOpenMe: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    YingShiMistBackground(modifier = modifier, showWaves = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "映世",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                    Text(
                        text = "欢迎回来",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                YingShiIconBubble(
                    icon = Icons.Rounded.Notifications,
                    contentDescription = "通知",
                    modifier = Modifier.size(48.dp),
                    onClick = onOpenNotifications,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                YingShiMistCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(radius.xl),
                    color = colors.raisedSurface.copy(alpha = 0.78f),
                    borderColor = colors.glassStroke.copy(alpha = 0.46f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "今天也把日常慢慢收好",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.titleAccent,
                            )
                            Text(
                                text = "照片、小相册和生活记录都在这里等你继续。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                            )
                        }
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(radius.lg),
                            color = colors.memoryWash.copy(alpha = 0.74f),
                            border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.14f)),
                        ) {
                            Text(
                                text = "最近记忆 · 刚导入、评论和恢复会在照片页点亮",
                                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onMemoryContainer,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeQuickEntry(
                    title = "照片",
                    icon = Icons.Rounded.Image,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPhotos,
                )
                HomeQuickEntry(
                    title = "生活",
                    icon = Icons.Rounded.Explore,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLife,
                )
                HomeQuickEntry(
                    title = "我的",
                    icon = Icons.Rounded.Person,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMe,
                )
            }
        }
    }
}

@Composable
private fun HomeQuickEntry(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    Surface(
        modifier = modifier.yingShiHapticClickable(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(radius.lg),
            onClick = onClick,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            YingShiIconBubble(
                icon = icon,
                contentDescription = null,
                selected = true,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    YingShiTheme {
        HomeScreen()
    }
}

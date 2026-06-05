package com.example.yingshi.feature.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun LifeScreen(
    modifier: Modifier = Modifier,
    onOpenLifeConsole: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
    onOpenChatViewer: () -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "映世",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary.copy(alpha = 0.72f),
                )
                Text(
                    text = "生活",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LifeEntryCard(
                    title = "记账",
                    summary = "账本、预算和每一笔生活开销",
                    status = "进入账本",
                    accent = LifeEntryAccent.GREEN,
                    icon = Icons.Rounded.AccountBalanceWallet,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLedger,
                )
                LifeEntryCard(
                    title = "聊天记录",
                    summary = "本地离线回看旧对话",
                    status = "打开记录",
                    accent = LifeEntryAccent.WARM,
                    icon = Icons.Rounded.ChatBubbleOutline,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenChatViewer,
                )
                LifeEntryCard(
                    title = "今日痕迹",
                    summary = "照片、饭点和今天留下的小记录",
                    status = "查看今天",
                    accent = LifeEntryAccent.BLUE,
                    icon = Icons.Rounded.DashboardCustomize,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLifeConsole,
                )
            }
        }
    }
}

@Composable
private fun LifeEntryCard(
    title: String,
    summary: String,
    status: String,
    accent: LifeEntryAccent,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val accentContainer = when (accent) {
        LifeEntryAccent.BLUE -> colors.primaryContainer.copy(alpha = 0.76f)
        LifeEntryAccent.GREEN -> colors.softGreenContainer.copy(alpha = 0.94f)
        LifeEntryAccent.WARM -> colors.memoryContainer.copy(alpha = 0.74f)
    }
    val accentContent = when (accent) {
        LifeEntryAccent.BLUE -> colors.titleAccent
        LifeEntryAccent.GREEN -> colors.softGreenAction
        LifeEntryAccent.WARM -> colors.memoryAccent
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentContainer,
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentContent,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(30.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(radius.capsule),
                color = accentContainer,
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.48f)),
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.titleAccent.copy(alpha = 0.72f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private enum class LifeEntryAccent {
    BLUE,
    GREEN,
    WARM,
}

@Preview(showBackground = true)
@Composable
private fun LifeScreenPreview() {
    YingShiTheme {
        LifeScreen()
    }
}

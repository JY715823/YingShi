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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Notifications
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
import com.example.yingshi.ui.components.YingShiIconBubble
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun LifeScreen(
    modifier: Modifier = Modifier,
    onOpenLifeConsole: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
    onOpenChatViewer: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
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
                YingShiIconBubble(
                    icon = Icons.Rounded.Notifications,
                    contentDescription = "通知",
                    modifier = Modifier.size(44.dp),
                    onClick = onOpenNotifications,
                )
            }

            LifeOverviewCard(onOpenLedger = onOpenLedger)

            LifeEntryCard(
                title = "记账",
                summary = "这个月的生活小账",
                status = "打开账本",
                accent = LifeEntryAccent.GREEN,
                icon = Icons.Rounded.AccountBalanceWallet,
                onClick = onOpenLedger,
            )
            LifeEntryCard(
                title = "今日痕迹",
                summary = "照片和饭点记录",
                status = "查看记录",
                accent = LifeEntryAccent.BLUE,
                icon = Icons.Rounded.DashboardCustomize,
                onClick = onOpenLifeConsole,
            )
            LifeEntryCard(
                title = "聊天记录",
                summary = "本地离线回看旧对话",
                status = "打开",
                accent = LifeEntryAccent.WARM,
                icon = Icons.Rounded.ChatBubbleOutline,
                onClick = onOpenChatViewer,
            )
        }
    }
}

@Composable
private fun LifeOverviewCard(
    onOpenLedger: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onOpenLedger),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = colors.sectionBackground.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.64f)),
                shadowElevation = 1.dp,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(21.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "这个月的生活小账",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = "支出、预算和账单",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "月度明细",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "预算与账单",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                }
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
    onClick: () -> Unit,
) {
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val accentContainer = when (accent) {
        LifeEntryAccent.BLUE -> colors.primaryContainer.copy(alpha = 0.70f)
        LifeEntryAccent.GREEN -> colors.softGreenContainer.copy(alpha = 0.88f)
        LifeEntryAccent.WARM -> colors.memoryContainer.copy(alpha = 0.70f)
    }
    val accentContent = when (accent) {
        LifeEntryAccent.BLUE -> colors.titleAccent
        LifeEntryAccent.GREEN -> colors.softGreenAction
        LifeEntryAccent.WARM -> colors.memoryAccent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.54f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentContainer,
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.64f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentContent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(21.dp),
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
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(radius.capsule),
                color = accentContainer,
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.50f)),
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accentContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.titleAccent.copy(alpha = 0.72f),
                modifier = Modifier.size(22.dp),
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

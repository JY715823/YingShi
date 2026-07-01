package com.example.yingshi.feature.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.util.Calendar
import java.util.Locale

@Composable
fun LifeScreen(
    modifier: Modifier = Modifier,
    onOpenLifeConsole: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
    onOpenChatViewer: () -> Unit = {},
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    var currentTimeLabel by remember { mutableStateOf("") }
    var currentDateLabel by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        currentTimeLabel = when (hour) {
            in 5..11 -> "早安"
            in 12..17 -> "午安"
            else -> "晚安"
        }
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.CHINA)
        currentDateLabel = "${month}月${day}日 $dayOfWeek"
    }

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = YingShiBackdropVariant.LIFE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg)
                .padding(top = spacing.lg, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .yingShiSoftReveal(visible = true),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = "映世",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary.copy(alpha = 0.72f),
                )
                Text(
                    text = "生活",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                if (currentTimeLabel.isNotEmpty()) {
                    Text(
                        text = "$currentTimeLabel · $currentDateLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary.copy(alpha = 0.80f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                LifeEntryCard(
                    title = "记账",
                    summary = "账本、预算和每一笔生活开销",
                    status = "进入账本",
                    accent = LifeEntryAccent.GREEN,
                    icon = Icons.Rounded.AccountBalanceWallet,
                    modifier = Modifier
                        .weight(1f)
                        .yingShiRouteReveal(visible = true),
                    onClick = onOpenLedger,
                )
                LifeEntryCard(
                    title = "聊天记录",
                    summary = "本地离线回看旧对话",
                    status = "打开记录",
                    accent = LifeEntryAccent.WARM,
                    icon = Icons.Rounded.ChatBubbleOutline,
                    modifier = Modifier
                        .weight(1f)
                        .yingShiRouteReveal(visible = true),
                    onClick = onOpenChatViewer,
                )
                LifeEntryCard(
                    title = "今日痕迹",
                    summary = "照片、饭点和今天留下的小记录",
                    status = "查看今天",
                    accent = LifeEntryAccent.BLUE,
                    icon = Icons.Rounded.DashboardCustomize,
                    modifier = Modifier
                        .weight(1f)
                        .yingShiRouteReveal(visible = true),
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
    val spacing = YingShiThemeTokens.spacing
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
    val accentGlow = when (accent) {
        LifeEntryAccent.BLUE -> colors.primaryContainer
        LifeEntryAccent.GREEN -> colors.softGreenAction
        LifeEntryAccent.WARM -> colors.memoryAccent
    }

    YingShiMistCard(
        modifier = modifier
            .fillMaxWidth()
            .yingShiClickable(shape = RoundedCornerShape(radius.lg), onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentGlow.copy(alpha = 0.07f),
                            colors.glowWash.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        center = Offset.Zero,
                        radius = 480f,
                    ),
                )
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
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
                        .padding(spacing.sm)
                        .size(28.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
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
                color = accentContainer.copy(alpha = 0.60f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.40f)),
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

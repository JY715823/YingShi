package com.example.yingshi.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.chat.data.ImportedMessageSearchResult
import com.example.yingshi.feature.chat.data.buildImportedSearchPreviewSnippet
import com.example.yingshi.ui.theme.YingShiThemeTokens

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
internal fun ChatSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onPickDate: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(radius.capsule),
            color = colors.raisedSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                Text(
                                    text = "搜索消息",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary.copy(alpha = 0.76f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (value.isNotBlank()) {
                    ChatIconActionButton(
                        icon = Icons.Default.Close,
                        contentDescription = "清空搜索",
                        size = 44.dp,
                        onClick = onClear,
                    )
                }
            }
        }
        ChatIconActionButton(
            icon = Icons.Default.CalendarToday,
            contentDescription = "选择日期",
            size = 44.dp,
            emphasized = true,
            onClick = onPickDate,
        )
    }
}

// ── Search results panel ──────────────────────────────────────────────────────

@Composable
internal fun SearchResultsPanel(
    results: List<ImportedMessageSearchResult>,
    query: String,
    currentIndex: Int,
    onResultClick: (Int, ImportedMessageSearchResult) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val qFaceCatalog = rememberQFaceCatalog()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md),
        shape = RoundedCornerShape(20.dp),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (results.isEmpty()) {
            Text(
                text = "没有找到匹配结果",
                modifier = Modifier.padding(spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            Column(
                modifier = Modifier.padding(vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                results.take(6).forEachIndexed { index, result ->
                    val snippet = remember(result.fullPreviewText, query) {
                        buildImportedSearchPreviewSnippet(
                            previewText = result.fullPreviewText,
                            query = query,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == currentIndex) colors.primaryContainer.copy(alpha = 0.42f) else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { onResultClick(index, result) }
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = result.senderDisplayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.titleAccent,
                            )
                            Text(
                                text = formatTimelineTime(result.timestamp),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                        InlineMessageText(
                            segments = remember(snippet.previewText, qFaceCatalog) {
                                buildReplyPreviewInlineSegments(
                                    previewText = snippet.previewText,
                                    qFaceCatalog = qFaceCatalog,
                                )
                            },
                            qFaceCatalog = qFaceCatalog,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            emojiScaleEm = 1.18f,
                            highlightQuery = query,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Search navigation bar ─────────────────────────────────────────────────────

@Composable
internal fun SearchNavigationBar(
    resultCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md),
        shape = RoundedCornerShape(16.dp),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = if (resultCount <= 0) {
                    "0 条命中"
                } else {
                    "${(currentIndex.coerceAtLeast(0) + 1).coerceAtMost(resultCount)} / $resultCount"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.titleAccent,
                modifier = Modifier.weight(1f),
            )
            ChatDialogActionButton(
                text = "上一条",
                onClick = onPrevious,
                enabled = resultCount > 0 && currentIndex > 0,
            )
            ChatDialogActionButton(
                text = "下一条",
                onClick = onNext,
                enabled = resultCount > 0 && currentIndex in 0 until resultCount - 1,
                emphasized = true,
            )
        }
    }
}

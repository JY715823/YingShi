package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GearEditScreen(
    route: GearEditRoute,
    onBack: () -> Unit,
    onOpenMediaManagement: (MediaManagementRoute) -> Unit,
    onDeleteCurrentPost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    val initialDraft = remember(route.postId) {
        FakeAlbumRepository.getEditablePostDraft(route.postId)
    }

    if (initialDraft == null) {
        GearEditMissingState(
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    var title by remember(route.postId) { mutableStateOf(initialDraft.title) }
    var summary by remember(route.postId) { mutableStateOf(initialDraft.summary) }
    var displayTimeMillis by remember(route.postId) { mutableLongStateOf(initialDraft.postDisplayTimeMillis) }
    val selectedAlbumIds = remember(route.postId) {
        mutableStateListOf<String>().apply {
            addAll(initialDraft.albumIds)
        }
    }
    val albums = remember { FakeAlbumRepository.getAlbums() }
    var showDeletePostDialog by rememberSaveable(route.postId) { mutableStateOf(false) }
    val hasChanges = title != initialDraft.title ||
        summary != initialDraft.summary ||
        displayTimeMillis != initialDraft.postDisplayTimeMillis ||
        selectedAlbumIds.toList() != initialDraft.albumIds

    val handleClose = {
        if (hasChanges) {
            Toast.makeText(context, "未保存修改已放弃", Toast.LENGTH_SHORT).show()
        }
        onBack()
    }

    BackHandler(onBack = handleClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        GearEditTopBar(
            onCancel = handleClose,
            onSave = {
                FakeAlbumRepository.updatePostBasicInfo(
                    postId = route.postId,
                    title = title,
                    summary = summary,
                    postDisplayTimeMillis = displayTimeMillis,
                    albumIds = selectedAlbumIds.toList(),
                )
                Toast.makeText(context, "帖子信息已保存", Toast.LENGTH_SHORT).show()
                onBack()
            },
        )

        GearEditSection(
            title = "基础信息",
            subtitle = "进入 Gear Edit 后默认就是编辑态，本轮先编辑标题和简介。",
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                placeholder = { Text("可留空") },
                minLines = 1,
                maxLines = 2,
            )
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("简介") },
                placeholder = { Text("可留空") },
                minLines = 3,
                maxLines = 5,
            )
        }

        GearEditSection(
            title = "时间设置",
            subtitle = "本轮先做轻量本地调整，后续再接正式日期选择器。",
        ) {
            Text(
                text = formatGearEditTime(displayTimeMillis),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GearEditChip(text = "-1小时") { displayTimeMillis -= 60 * 60 * 1000L }
                GearEditChip(text = "+1小时") { displayTimeMillis += 60 * 60 * 1000L }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GearEditChip(text = "-1天") { displayTimeMillis -= 24 * 60 * 60 * 1000L }
                GearEditChip(text = "+1天") { displayTimeMillis += 24 * 60 * 60 * 1000L }
                GearEditChip(text = "设为现在") { displayTimeMillis = System.currentTimeMillis() }
            }
        }

        GearEditSection(
            title = "所属相册",
            subtitle = "本轮支持在 fake albums 里做基础多选，保存后回写帖子信息区。",
        ) {
            if (selectedAlbumIds.isEmpty()) {
                Text(
                    text = "当前未归入相册",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AlbumSelectionFlow(
                albums = albums,
                selectedAlbumIds = selectedAlbumIds,
                onToggleAlbum = { albumId ->
                    if (selectedAlbumIds.contains(albumId)) {
                        selectedAlbumIds.remove(albumId)
                    } else {
                        selectedAlbumIds.add(albumId)
                    }
                },
            )
        }

        GearEditSection(
            title = "后续入口",
            subtitle = "媒体管理和删除语义本轮先接入本地流程，回收站详情与恢复后续再补。",
        ) {
            GearEditEntryRow(
                title = "媒体管理",
                subtitle = "进入独立媒体管理页，管理当前帖子的媒体。",
                onClick = {
                    onOpenMediaManagement(MediaManagementRoute(route.postId))
                },
            )
            GearEditEntryRow(
                title = "删除整个帖子",
                subtitle = "本轮先写入本地回收站的“帖子删除”，删除帖子并系统删媒体留到后续阶段。",
                danger = true,
                onClick = {
                    showDeletePostDialog = true
                },
            )
        }
    }

    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = false },
            title = { Text("删除整个帖子") },
            text = {
                Text("本轮先把帖子删除写入本地回收站。删除后帖子会从相册页和帖子详情中消失，恢复与 24h 逻辑留到 Stage 7.2。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePostDialog = false
                        onDeleteCurrentPost(route.postId)
                    },
                ) {
                    Text("仅删除帖子")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun GearEditTopBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("取消")
        }
        Text(
            text = "Gear Edit",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = onSave) {
            Text("保存")
        }
    }
}

@Composable
private fun GearEditSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun AlbumSelectionFlow(
    albums: List<AlbumSummaryUiModel>,
    selectedAlbumIds: List<String>,
    onToggleAlbum: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
    ) {
        buildGearEditAlbumRows(albums).forEach { rowAlbums ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
            ) {
                rowAlbums.forEach { album ->
                    SelectableGearEditChip(
                        text = album.title,
                        selected = selectedAlbumIds.contains(album.id),
                        onClick = { onToggleAlbum(album.id) },
                    )
                }
            }
        }
    }
}

private fun buildGearEditAlbumRows(albums: List<AlbumSummaryUiModel>): List<List<AlbumSummaryUiModel>> {
    if (albums.isEmpty()) return emptyList()
    if (albums.size == 1) return listOf(albums)

    val firstRow = mutableListOf<AlbumSummaryUiModel>()
    val secondRow = mutableListOf<AlbumSummaryUiModel>()
    albums.forEachIndexed { index, album ->
        if (index % 2 == 0) {
            firstRow += album
        } else {
            secondRow += album
        }
    }
    return listOf(firstRow, secondRow).filter { it.isNotEmpty() }
}

@Composable
private fun GearEditChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SelectableGearEditChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = YingShiThemeTokens.spacing.sm,
                vertical = YingShiThemeTokens.spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun GearEditEntryRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = if (danger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GearEditMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        GearEditTopBar(onCancel = onBack, onSave = onBack)
        Text(
            text = "当前帖子不存在，暂时无法进入 Gear Edit。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatGearEditTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun GearEditScreenPreview() {
    YingShiTheme {
        GearEditScreen(
            route = GearEditRoute(postId = "post-window-light"),
            onBack = {},
            onOpenMediaManagement = {},
            onDeleteCurrentPost = {},
        )
    }
}

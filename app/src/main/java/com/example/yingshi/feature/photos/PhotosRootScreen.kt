package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.ui.components.PlaceholderBlock
import com.example.yingshi.ui.components.PlaceholderPage
import com.example.yingshi.ui.components.TitleTabs
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

private val albumBlocks = listOf(
    PlaceholderBlock("相册目录", "后续这里承接按相册浏览帖子，不作为默认首屏。"),
    PlaceholderBlock("帖子卡片", "下一阶段补帖子封面、标题、摘要和媒体数。"),
    PlaceholderBlock("详情入口", "保留从相册进入帖子详情与 Viewer 的后续挂点。"),
)

private val trashBlocks = listOf(
    PlaceholderBlock("帖子删除", "后续回收站需要区分帖子删除与媒体删除。"),
    PlaceholderBlock("媒体移除", "目录删与系统删会在后续阶段分层处理。"),
    PlaceholderBlock("恢复逻辑", "当前只保留页面结构和恢复入口占位。"),
)

private val PhotoSelectionActionBarPadding = 88.dp

@Composable
fun PhotosRootScreen(
    modifier: Modifier = Modifier,
    onOpenViewer: (PhotoViewerPlaceholderRoute) -> Unit = { },
) {
    val spacing = YingShiThemeTokens.spacing
    val context = LocalContext.current
    var selectedSectionName by rememberSaveable {
        mutableStateOf(PhotosTopDestination.PHOTOS.name)
    }
    var photoSelectionState by remember {
        mutableStateOf(PhotoFeedSelectionState())
    }
    val selectedSection = PhotosTopDestination.valueOf(selectedSectionName)
    val isPhotoSelectionMode =
        selectedSection == PhotosTopDestination.PHOTOS && photoSelectionState.isInSelectionMode

    if (isPhotoSelectionMode) {
        BackHandler {
            photoSelectionState = photoSelectionState.clear()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        PhotoTopBar(
            selectedSection = selectedSection,
            selectionState = if (selectedSection == PhotosTopDestination.PHOTOS) {
                photoSelectionState
            } else {
                PhotoFeedSelectionState()
            },
            onCancelSelection = { photoSelectionState = photoSelectionState.clear() },
            onSelected = { selectedSectionName = PhotosTopDestination.entries[it].name },
            onOpenSystemMedia = {
                Toast.makeText(context, "系统媒体将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
            onOpenNotifications = {
                Toast.makeText(context, "通知中心将在后续阶段接入", Toast.LENGTH_SHORT).show()
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedSection) {
                PhotosTopDestination.PHOTOS -> {
                    PhotoFeedScreen(
                        modifier = Modifier.fillMaxSize(),
                        selectionState = photoSelectionState,
                        bottomOverlayPadding = if (isPhotoSelectionMode) {
                            PhotoSelectionActionBarPadding
                        } else {
                            0.dp
                        },
                        onSelectionStateChange = { photoSelectionState = it },
                        onOpenViewer = onOpenViewer,
                    )
                }

                PhotosTopDestination.ALBUMS -> {
                    PlaceholderPage(
                        title = selectedSection.headline,
                        summary = selectedSection.supporting,
                        blocks = albumBlocks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PhotosTopDestination.TRASH -> {
                    PlaceholderPage(
                        title = selectedSection.headline,
                        summary = selectedSection.supporting,
                        blocks = trashBlocks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isPhotoSelectionMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing.sm),
            ) {
                PhotoSelectionActionBar(
                    selectedCount = photoSelectionState.selectedCount,
                )
            }
        }
    }
}

@Composable
private fun PhotoTopBar(
    selectedSection: PhotosTopDestination,
    selectionState: PhotoFeedSelectionState,
    onCancelSelection: () -> Unit,
    onSelected: (Int) -> Unit,
    onOpenSystemMedia: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val isSelectionContext =
        selectedSection == PhotosTopDestination.PHOTOS && selectionState.isInSelectionMode

    if (isSelectionContext) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancelSelection) {
                Text(text = "取消")
            }

            Text(
                text = "已选 ${selectionState.selectedCount} 项",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.width(68.dp))
        }

        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleTabs(
            tabs = PhotosTopDestination.entries.map { it.label },
            selectedIndex = selectedSection.ordinal,
            modifier = Modifier.weight(1f),
            onSelected = onSelected,
        )

        Spacer(modifier = Modifier.width(spacing.sm))

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(radius.capsule),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                ),
            ) {
                OutlinedButton(
                    onClick = onOpenSystemMedia,
                    border = null,
                ) {
                    Text(text = "系统媒体")
                }
            }

            TextButton(onClick = onOpenNotifications) {
                Text(text = "铃铛")
            }
        }
    }
}

@Composable
private fun PhotoSelectionActionBar(selectedCount: Int) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 $selectedCount 项",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SelectionActionChip(text = "整理")
            SelectionActionChip(text = "导出")
            SelectionActionChip(text = "删除")
        }
    }
}

@Composable
private fun SelectionActionChip(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotosRootScreenPreview() {
    YingShiTheme {
        PhotosRootScreen()
    }
}

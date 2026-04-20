package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    PlaceholderBlock("帖子卡片", "下一阶段补帖子的封面、标题、摘要和媒体数。"),
    PlaceholderBlock("详情入口", "保留从相册进入帖子详情与 Viewer 的后续挂点。"),
)

private val trashBlocks = listOf(
    PlaceholderBlock("帖子删除", "后续回收站需要区分帖子删除与媒体删除。"),
    PlaceholderBlock("媒体移除", "目录删与系统删会在后续阶段分层处理。"),
    PlaceholderBlock("恢复逻辑", "当前只保留页面结构和恢复入口占位。"),
)

@Composable
fun PhotosRootScreen(modifier: Modifier = Modifier) {
    val spacing = YingShiThemeTokens.spacing
    var selectedSectionName by rememberSaveable {
        mutableStateOf(PhotosTopDestination.PHOTOS.name)
    }
    val selectedSection = PhotosTopDestination.valueOf(selectedSectionName)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        PhotoTopBar(
            selectedSection = selectedSection,
            onSelected = { selectedSectionName = PhotosTopDestination.entries[it].name },
        )

        when (selectedSection) {
            PhotosTopDestination.PHOTOS -> {
                PhotoFeedScreen(modifier = Modifier.weight(1f))
            }

            PhotosTopDestination.ALBUMS -> {
                PlaceholderPage(
                    title = selectedSection.headline,
                    summary = selectedSection.supporting,
                    blocks = albumBlocks,
                    modifier = Modifier.weight(1f),
                )
            }

            PhotosTopDestination.TRASH -> {
                PlaceholderPage(
                    title = selectedSection.headline,
                    summary = selectedSection.supporting,
                    blocks = trashBlocks,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhotoTopBar(
    selectedSection: PhotosTopDestination,
    onSelected: (Int) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

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
                    onClick = { },
                    border = null,
                ) {
                    Text(text = "系统媒体")
                }
            }

            TextButton(onClick = { }) {
                Text(text = "铃铛")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotosRootScreenPreview() {
    YingShiTheme {
        PhotosRootScreen()
    }
}

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.ui.components.PlaceholderBlock
import com.example.yingshi.ui.components.PlaceholderPage
import com.example.yingshi.ui.components.TitleTabs
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

private val photoFeedBlocks = listOf(
    PlaceholderBlock("全局媒体流", "照片页只代表 app 内容体系内的全局媒体流。"),
    PlaceholderBlock("系统媒体入口", "保留独立工具入口，但当前不接入真实系统媒体。"),
    PlaceholderBlock("通知入口", "保留位置，不做通知中心实现。"),
)

private val albumBlocks = listOf(
    PlaceholderBlock("相册目录", "未来用来按相册浏览帖子，不是默认首屏。"),
    PlaceholderBlock("帖子卡片", "后续承接封面、标题、摘要与媒体数。"),
    PlaceholderBlock("帖子详情", "Stage 0 不实现详情逻辑，只保留结构入口。"),
)

private val trashBlocks = listOf(
    PlaceholderBlock("帖子删除", "回收站后续需要支持帖子恢复。"),
    PlaceholderBlock("媒体移除", "目录删与系统删后续要分层处理。"),
    PlaceholderBlock("系统删恢复", "当前只保留信息架构，不接真实数据。"),
)

@Composable
fun PhotosRootScreen(modifier: Modifier = Modifier) {
    val spacing = YingShiThemeTokens.spacing
    var selectedSectionName by rememberSaveable {
        mutableStateOf(PhotosTopDestination.PHOTOS.name)
    }
    val selectedSection = PhotosTopDestination.valueOf(selectedSectionName)

    val blocks = when (selectedSection) {
        PhotosTopDestination.PHOTOS -> photoFeedBlocks
        PhotosTopDestination.ALBUMS -> albumBlocks
        PhotosTopDestination.TRASH -> trashBlocks
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
            onSelected = { selectedSectionName = PhotosTopDestination.entries[it].name },
        )

        PlaceholderPage(
            title = selectedSection.headline,
            summary = selectedSection.supporting,
            blocks = blocks,
            modifier = Modifier.weight(1f),
        )
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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

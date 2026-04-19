package com.example.yingshi.feature.photos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.yingshi.ui.theme.YingShiTheme

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
    var selectedSectionName by rememberSaveable {
        mutableStateOf(PhotosTopDestination.PHOTOS.name)
    }
    val selectedSection = PhotosTopDestination.valueOf(selectedSectionName)

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSection.ordinal) {
            PhotosTopDestination.entries.forEach { section ->
                Tab(
                    selected = section == selectedSection,
                    onClick = { selectedSectionName = section.name },
                    text = { Text(text = section.label) },
                )
            }
        }

        val blocks = when (selectedSection) {
            PhotosTopDestination.PHOTOS -> photoFeedBlocks
            PhotosTopDestination.ALBUMS -> albumBlocks
            PhotosTopDestination.TRASH -> trashBlocks
        }

        PlaceholderPage(
            title = "照片模块",
            summary = "Stage 0 先建立二级导航、工具入口位置和占位页面结构。",
            blocks = blocks,
            modifier = Modifier.fillMaxSize(),
            headerContent = {
                if (selectedSection == PhotosTopDestination.PHOTOS) {
                    PhotoToolsRow()
                }
                com.example.yingshi.ui.components.PlaceholderCard(
                    block = PlaceholderBlock(
                        title = selectedSection.headline,
                        summary = selectedSection.supporting,
                    ),
                )
            }
        )
    }
}

@Composable
private fun PhotoToolsRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        OutlinedButton(onClick = { }) {
            Text(text = "系统媒体")
        }
        TextButton(onClick = { }) {
            Text(text = "通知")
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

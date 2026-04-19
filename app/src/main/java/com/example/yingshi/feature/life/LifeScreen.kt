package com.example.yingshi.feature.life

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.ui.components.PlaceholderBlock
import com.example.yingshi.ui.components.PlaceholderPage
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.theme.YingShiTheme

private val lifeEntries = listOf(
    PlaceholderBlock(
        title = "纪念日",
        summary = "保留为后续生活模块入口，占位即可。",
    ),
    PlaceholderBlock(
        title = "记账",
        summary = "当前不实现真实能力，只预留信息架构位置。",
    ),
    PlaceholderBlock(
        title = "聊天记录查看器",
        summary = "未来是否保留、如何呈现，留到后续阶段再定。",
    ),
)

@Composable
fun LifeScreen(modifier: Modifier = Modifier) {
    ShellPage(
        title = "生活",
        summary = "生活模块在当前阶段更适合作为温和、克制的入口页，而不是复杂功能页。",
        modifier = modifier.fillMaxSize(),
        content = {
            PlaceholderPage(
                title = "生活入口卡占位",
                summary = "保留纪念日、记账、聊天记录查看器三类入口，不接真实能力。",
                blocks = lifeEntries,
                showHero = false,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun LifeScreenPreview() {
    YingShiTheme {
        LifeScreen()
    }
}

package com.example.yingshi.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.ui.components.PlaceholderBlock
import com.example.yingshi.ui.components.PlaceholderPage
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.theme.YingShiTheme

private val homeSections = listOf(
    PlaceholderBlock(
        title = "首页总览占位",
        summary = "Stage 0 只保留结构入口，不承载真实业务数据。",
    ),
    PlaceholderBlock(
        title = "后续可接入共享记忆摘要",
        summary = "未来再决定主页是聚合信息页，还是更轻的欢迎页。",
    ),
    PlaceholderBlock(
        title = "当前重点仍是照片模块壳层",
        summary = "主页先作为稳定底部导航的一部分存在。",
    ),
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    ShellPage(
        title = "主页",
        summary = "用于承接后续总览与轻入口。当前重点是保持页面气质稳定、结构简洁。",
        modifier = modifier.fillMaxSize(),
        content = {
            PlaceholderPage(
                title = "首页壳层",
                summary = "Stage 1 先统一视觉基线，不扩展真实业务逻辑。",
                blocks = homeSections,
                showHero = false,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    YingShiTheme {
        HomeScreen()
    }
}

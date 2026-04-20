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
        summary = "当前只保留结构入口，不承载真实业务数据。",
    ),
    PlaceholderBlock(
        title = "共享记忆摘要",
        summary = "后续再决定首页更偏聚合概览，还是更轻的欢迎页。",
    ),
    PlaceholderBlock(
        title = "全局壳层稳定区",
        summary = "现阶段首页主要承担一级导航中的稳定入口角色。",
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
                title = "主页壳层",
                summary = "当前先维持统一视觉基线，不扩展真实业务逻辑。",
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

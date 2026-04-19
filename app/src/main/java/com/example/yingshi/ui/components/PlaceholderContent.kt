package com.example.yingshi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

data class PlaceholderBlock(
    val title: String,
    val summary: String,
)

@Composable
fun PlaceholderPage(
    title: String,
    summary: String,
    blocks: List<PlaceholderBlock>,
    modifier: Modifier = Modifier,
    headerContent: @Composable (() -> Unit)? = null,
    showHero: Boolean = true,
) {
    val spacing = YingShiThemeTokens.spacing

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        contentPadding = PaddingValues(bottom = spacing.xxl),
    ) {
        if (showHero) {
            item { PlaceholderHero(title = title, summary = summary) }
        }

        if (headerContent != null) {
            item {
                headerContent()
            }
        }

        items(blocks) { block ->
            PlaceholderCard(block = block)
        }
    }
}

@Composable
fun PlaceholderCard(
    block: PlaceholderBlock,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = block.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceholderHero(
    title: String,
    summary: String,
) {
    val spacing = YingShiThemeTokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderCardPreview() {
    YingShiTheme {
        Column(modifier = Modifier.padding(20.dp)) {
            PlaceholderCard(
                block = PlaceholderBlock(
                    title = "页面骨架占位",
                    summary = "用于 Stage 1 统一视觉基线和内容卡片层级。",
                ),
            )
        }
    }
}

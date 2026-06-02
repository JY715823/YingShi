package com.example.yingshi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun AppShellScaffold(
    selectedDestination: RootDestination,
    onDestinationSelected: (RootDestination) -> Unit,
    onCenterAction: () -> Unit = {},
    showBottomBar: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.appBackground,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                FloatingBottomBar(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                    onCenterAction = onCenterAction,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            content = content,
        )
    }
}

@Composable
fun ShellPage(
    title: String,
    summary: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        onBack?.let { handleBack ->
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Start)
                    .yingShiClickable(shape = RoundedCornerShape(14.dp), pressedScale = 0.94f, onClick = handleBack),
                shape = RoundedCornerShape(14.dp),
                color = colors.sectionBackground.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.titleAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (title.isNotBlank() || summary.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.titleAccent,
                    )
                }
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        headerContent?.invoke()

        if (content != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                content()
            }
        }
    }
}

@Composable
fun TitleTabs(
    tabs: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            val textColor = if (selected) {
                colors.titleAccent
            } else {
                colors.textSecondary.copy(alpha = 0.72f)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.capsule))
                    .clickable { onSelected(index) }
                    .padding(
                        horizontal = if (selected) spacing.sm else spacing.xs,
                        vertical = spacing.xxs,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (selected) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    selectedDestination: RootDestination,
    onDestinationSelected: (RootDestination) -> Unit,
    onCenterAction: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appBackground)
            .windowInsetsPadding(WindowInsets.navigationBars),
        shape = RoundedCornerShape(0.dp),
        color = colors.appBackground.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.dp, Color.Transparent),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerSoft.copy(alpha = 0.72f)),
            )
            Row(
                modifier = Modifier
                    .height(62.dp)
                    .fillMaxWidth()
                    .background(colors.appBackground.copy(alpha = 0.98f))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RootDestination.entries.take(2).forEach { destination ->
                    BottomNavItem(
                        destination = destination,
                        selected = destination == selectedDestination,
                        icon = bottomNavIcon(destination),
                        modifier = Modifier.weight(1f),
                        onClick = { onDestinationSelected(destination) },
                    )
                }

                Box(modifier = Modifier.weight(0.78f), contentAlignment = Alignment.Center) {
                    CenterAddButton(onClick = onCenterAction)
                }

                RootDestination.entries.drop(2).forEach { destination ->
                    BottomNavItem(
                        destination = destination,
                        selected = destination == selectedDestination,
                        icon = bottomNavIcon(destination),
                        modifier = Modifier.weight(1f),
                        onClick = { onDestinationSelected(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: RootDestination,
    selected: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer.copy(alpha = 0.82f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "bottomNavContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.titleAccent else colors.textSecondary.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 180),
        label = "bottomNavContent",
    )
    val itemScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "bottomNavScale",
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 5.dp)
            .height(50.dp)
            .yingShiClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = containerColor,
        border = if (selected) {
            BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.58f))
        } else {
            null
        },
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Box(
                modifier = Modifier.size(27.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = destination.label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        },
                )
            }
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CenterAddButton(onClick: () -> Unit) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = Modifier
            .size(42.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick),
        shape = shape,
        color = colors.softGreenContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "添加",
                tint = colors.titleAccent,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

private fun bottomNavIcon(destination: RootDestination): ImageVector {
    return when (destination) {
        RootDestination.HOME -> Icons.Rounded.Home
        RootDestination.PHOTOS -> Icons.Rounded.Image
        RootDestination.LIFE -> Icons.Rounded.Explore
        RootDestination.ME -> Icons.Rounded.Person
    }
}

@Preview(showBackground = true)
@Composable
private fun TitleTabsPreview() {
    YingShiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(YingShiThemeTokens.colors.appBackground)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TitleTabs(
                tabs = listOf("\u7167\u7247", "\u76f8\u518c", "\u56de\u6536\u7ad9"),
                selectedIndex = 0,
                onSelected = {},
            )
        }
    }
}

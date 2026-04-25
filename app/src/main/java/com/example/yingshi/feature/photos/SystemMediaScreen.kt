package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
fun SystemMediaScreen(
    onBack: () -> Unit,
    onOpenViewer: (SystemMediaViewerRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = YingShiThemeTokens.spacing
    var permissionStateName by rememberSaveable {
        mutableStateOf(SystemMediaPermissionState.UNAUTHORIZED.name)
    }
    var selectedFilterName by rememberSaveable {
        mutableStateOf(SystemMediaFilter.ALL.name)
    }
    var selectionMode by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedIds by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }

    val permissionState = SystemMediaPermissionState.valueOf(permissionStateName)
    val selectedFilter = SystemMediaFilter.valueOf(selectedFilterName)
    val mediaItems = FakeSystemMediaRepository.getMedia(selectedFilter)
    val selectedIdSet = selectedIds.toSet()

    if (selectionMode) {
        BackHandler {
            selectionMode = false
            selectedIds = emptyList()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SystemMediaTopBar(
                selectedFilter = selectedFilter,
                selectionMode = selectionMode,
                selectedCount = selectedIds.size,
                onBack = onBack,
                onToggleSelectionMode = {
                    selectionMode = !selectionMode
                    if (!selectionMode) {
                        selectedIds = emptyList()
                    }
                },
            )

            SystemMediaPermissionCard(
                permissionState = permissionState,
                onPermissionStateSelected = { permissionStateName = it.name },
                onReRequestPermission = {
                    Toast.makeText(context, "权限重新申请入口占位", Toast.LENGTH_SHORT).show()
                },
                onOpenSettings = {
                    Toast.makeText(context, "系统设置入口占位", Toast.LENGTH_SHORT).show()
                },
            )

            SystemMediaFilterRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilterName = it.name },
            )

            Text(
                text = "时间降序 · fake system media grid",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (mediaItems.isEmpty()) {
                SystemMediaEmptyState(
                    text = "当前筛选下没有可显示的系统媒体占位数据。",
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(
                        items = mediaItems,
                        key = { it.id },
                    ) { item ->
                        SystemMediaCard(
                            item = item,
                            selectionMode = selectionMode,
                            selected = selectedIdSet.contains(item.id),
                            dimmed = permissionState == SystemMediaPermissionState.UNAUTHORIZED,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = selectedIds.toggleSystemMediaId(item.id)
                                    if (selectedIds.isEmpty()) {
                                        selectionMode = false
                                    }
                                } else {
                                    onOpenViewer(
                                        SystemMediaViewerRoute(
                                            mediaItems = mediaItems,
                                            initialIndex = mediaItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0),
                                        ),
                                    )
                                }
                            },
                            onLongPress = {
                                selectionMode = true
                                selectedIds = selectedIds.toggleSystemMediaId(item.id)
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
        ) {
            SystemMediaSelectionBar(
                selectedCount = selectedIds.size,
                onCreatePost = {
                    Toast.makeText(context, "发成新帖子占位", Toast.LENGTH_SHORT).show()
                },
                onAddToPost = {
                    Toast.makeText(context, "加入已有帖子占位", Toast.LENGTH_SHORT).show()
                },
                onMoveToTrash = {
                    Toast.makeText(context, "移到系统回收站占位", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun SystemMediaTopBar(
    selectedFilter: SystemMediaFilter,
    selectionMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onToggleSelectionMode: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemMediaCircleButton(
            text = "<",
            onClick = onBack,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "系统媒体",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (selectionMode) {
                    "多选中 $selectedCount 项"
                } else {
                    "筛选：${selectedFilter.label}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SystemMediaActionChip(
            text = "筛选",
            emphasized = false,
            onClick = {},
        )
        SystemMediaActionChip(
            text = if (selectionMode) "取消多选" else "多选",
            emphasized = selectionMode,
            onClick = onToggleSelectionMode,
        )
    }
}

@Composable
private fun SystemMediaPermissionCard(
    permissionState: SystemMediaPermissionState,
    onPermissionStateSelected: (SystemMediaPermissionState) -> Unit,
    onReRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "权限引导壳子",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = permissionState.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                SystemMediaPermissionState.entries.forEach { state ->
                    SystemMediaActionChip(
                        text = state.label,
                        emphasized = state == permissionState,
                        onClick = { onPermissionStateSelected(state) },
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                TextButton(onClick = onReRequestPermission) {
                    Text(text = "重新申请权限")
                }
                TextButton(onClick = onOpenSettings) {
                    Text(text = "去系统设置")
                }
            }
        }
    }
}

@Composable
private fun SystemMediaFilterRow(
    selectedFilter: SystemMediaFilter,
    onFilterSelected: (SystemMediaFilter) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        SystemMediaFilter.entries.forEach { filter ->
            SystemMediaActionChip(
                text = filter.label,
                emphasized = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemMediaCard(
    item: SystemMediaItem,
    selectionMode: Boolean,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.lg)

    Box(
        modifier = Modifier
            .aspectRatio(item.aspectRatio.coerceIn(0.56f, 1.25f))
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(item.palette.start, item.palette.end),
                ),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (dimmed) 0.28f else 0.10f),
                        ),
                    ),
                ),
        )

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            Color.Black.copy(alpha = 0.16f)
                        },
                    ),
            )
        }

        if (item.linkedPostIds.isNotEmpty()) {
            SystemMediaBadge(
                text = "已发帖",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }

        SystemMediaKindBadge(
            text = item.kind.label,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )

        if (selectionMode) {
            SystemMediaSelectionBadge(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SystemMediaBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SystemMediaKindBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = Color.Black.copy(alpha = 0.16f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun SystemMediaSelectionBar(
    selectedCount: Int,
    onCreatePost: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                SystemMediaActionChip(
                    text = "发成新帖子",
                    emphasized = true,
                    onClick = onCreatePost,
                )
                SystemMediaActionChip(
                    text = "加入已有帖子",
                    emphasized = false,
                    onClick = onAddToPost,
                )
                SystemMediaActionChip(
                    text = "移到系统回收站",
                    emphasized = false,
                    onClick = onMoveToTrash,
                )
            }
        }
    }
}

@Composable
private fun SystemMediaActionChip(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule)),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
        ),
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SystemMediaCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SystemMediaSelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SystemMediaEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun List<String>.toggleSystemMediaId(id: String): List<String> {
    return if (contains(id)) {
        filterNot { it == id }
    } else {
        this + id
    }
}

@Preview(showBackground = true)
@Composable
private fun SystemMediaScreenPreview() {
    YingShiTheme {
        SystemMediaScreen(
            onBack = {},
            onOpenViewer = {},
        )
    }
}

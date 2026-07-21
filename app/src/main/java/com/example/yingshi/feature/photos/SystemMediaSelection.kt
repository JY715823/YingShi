package com.example.yingshi.feature.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay

@Composable
internal fun SystemMediaSelectionBar(
    selectedCount: Int,
    onImportToApp: () -> Unit,
    onCreatePost: () -> Unit,
    onAddToPost: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    YingShiToolSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.sm),
        highlighted = selectedCount > 0,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            SystemMediaActionChip(
                text = "导入",
                emphasized = true,
                modifier = Modifier.weight(1f),
                onClick = onImportToApp,
            )
            SystemMediaActionChip(
                text = "新建",
                emphasized = false,
                modifier = Modifier.weight(1f),
                onClick = onCreatePost,
            )
            SystemMediaActionChip(
                text = "加入",
                emphasized = false,
                modifier = Modifier.weight(1f),
                onClick = onAddToPost,
            )
            SystemMediaActionChip(
                text = "删除",
                emphasized = false,
                danger = true,
                modifier = Modifier.weight(1f),
                onClick = onMoveToTrash,
            )
        }
    }
}

@Composable
internal fun SystemMediaSelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) colors.primaryContainer else colors.raisedSurface.copy(alpha = 0.74f),
            )
            .border(
                width = 1.5.dp,
                color = if (selected) colors.glassStroke else colors.raisedSurface.copy(alpha = 0.94f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun SystemMediaSelectionNumberFlashOverlay(
    flash: SelectionNumberFlash?,
    modifier: Modifier = Modifier,
) {
    if (flash == null) return
    val alpha = remember(flash.nonce) { Animatable(0f) }
    LaunchedEffect(flash.nonce) {
        alpha.snapTo(0f)
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 300))
        delay(800)
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 500))
    }

    if (alpha.value > 0f) {
        val colors = YingShiThemeTokens.colors
        Box(
            modifier = modifier
                .alpha(alpha.value)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.raisedSurface.copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = colors.selectedPillBg.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = flash.number.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = colors.titleAccent,
            )
        }
    }
}

internal fun List<String>.toggleSystemMediaId(id: String): List<String> {
    return if (contains(id)) {
        filterNot { it == id }
    } else {
        this + id
    }
}

internal data class SystemMediaRowMapping(
    val mediaToRow: Map<String, String>,
    val mediaToColumn: Map<String, Int>,
    val rowToMedia: Map<String, List<String>>,
    val rowKeys: List<String>,
) {
    private val rowKeyToIndex: Map<String, Int> = rowKeys
        .mapIndexed { index, rowKey -> rowKey to index }
        .toMap()

    fun rowIndexForMedia(mediaId: String): Int {
        return mediaToRow[mediaId]?.let { rowKey -> rowKeyToIndex[rowKey] } ?: -1
    }
}

internal fun buildSystemMediaRowMapping(
    gridBlocks: List<SystemMediaGridBlock>,
    columns: Int,
): SystemMediaRowMapping {
    val mediaToRow = mutableMapOf<String, String>()
    val mediaToColumn = mutableMapOf<String, Int>()
    val rowToMedia = mutableMapOf<String, MutableList<String>>()
    val rowKeys = mutableListOf<String>()
    var mediaInRow = 0
    var rowIndex = 0
    gridBlocks.forEach { block ->
        when (block) {
            is SystemMediaGridBlock.Media -> {
                val rowKey = "sys-row-$rowIndex"
                if (mediaInRow == 0) {
                    rowKeys += rowKey
                }
                mediaToRow[block.item.id] = rowKey
                mediaToColumn[block.item.id] = mediaInRow
                rowToMedia.getOrPut(rowKey) { mutableListOf() }.add(block.item.id)
                mediaInRow++
                if (mediaInRow >= columns) {
                    mediaInRow = 0
                    rowIndex++
                }
            }
            is SystemMediaGridBlock.MonthHeader,
            is SystemMediaGridBlock.DayHeader,
            -> {
                if (mediaInRow > 0) {
                    mediaInRow = 0
                    rowIndex++
                }
            }
        }
    }
    return SystemMediaRowMapping(
        mediaToRow = mediaToRow,
        mediaToColumn = mediaToColumn,
        rowToMedia = rowToMedia.mapValues { it.value.toList() },
        rowKeys = rowKeys,
    )
}

@Composable
internal fun rememberSystemMediaHitTestAdapter(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
    systemRowMapping: SystemMediaRowMapping,
    columns: Int,
    spacingPx: Float,
    edgePaddingPx: Float,
): MultiSelectHitTestAdapter {
    return remember(
        gridState,
        gridBlocks,
        systemRowMapping,
        columns,
        spacingPx,
        edgePaddingPx,
    ) {
        val colSpacingPx = spacingPx
        MultiSelectHitTestAdapter(
            hitTest = { touchPos ->
                val layout = gridState.layoutInfo
                val tx = (touchPos.x - edgePaddingPx).toInt()
                val ty = touchPos.y.toInt()
                val viewportW = layout.viewportSize.width.coerceAtLeast(1)
                val contentW = (viewportW - edgePaddingPx * 2f).coerceAtLeast(1f)
                val totalSpacing = (columns - 1) * colSpacingPx
                val cellWidth = ((contentW - totalSpacing) / columns).coerceAtLeast(1f)
                val segmentWidth = cellWidth + colSpacingPx
                for (vi in layout.visibleItemsInfo) {
                    val block = gridBlocks.getOrNull(vi.index) as? SystemMediaGridBlock.Media ?: continue
                    val itemEndY = vi.offset.y + vi.size.height
                    if (ty !in vi.offset.y until itemEndY) continue

                    val rowKey = systemRowMapping.mediaToRow[block.item.id] ?: continue
                    val rowItems = systemRowMapping.rowToMedia[rowKey].orEmpty()
                    if (rowItems.isEmpty()) continue

                    val colIndex = (tx / segmentWidth).toInt().coerceIn(0, columns - 1)
                    val mediaId = rowItems.getOrNull(colIndex) ?: return@MultiSelectHitTestAdapter null
                    return@MultiSelectHitTestAdapter MultiSelectHitResult(
                        mediaId = mediaId,
                        rowKey = rowKey,
                        rowIndex = systemRowMapping.rowIndexForMedia(mediaId),
                        isSelectable = true,
                        colIndex = colIndex,
                        columnsInRow = rowItems.size,
                    )
                }
                null
            },
            mediaIdsInRow = { rowKey -> systemRowMapping.rowToMedia[rowKey].orEmpty() },
            rowKeyAtIndex = { rowIndex -> systemRowMapping.rowKeys.getOrNull(rowIndex) },
        )
    }
}

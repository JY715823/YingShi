package com.example.yingshi.feature.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun buildSystemMediaScrubberYearMarkers(
    items: List<SystemMediaItem>,
): List<PhotoFeedScrubberYearMarker> {
    val anchors = items.mapIndexed { index, item ->
        PhotoFeedScrubberAnchor(
            blockKey = item.id,
            itemIndex = index,
            label = item.toSystemMediaScrubberLabel(),
            timeMillis = item.displayTimeMillis,
        )
    }
    val allMarkers = buildPhotoFeedScrubberYearMarkers(anchors)
    // 只保留近6年的年份标记, 避免旧年份过多导致堆叠.
    // 滑条范围不受影响, 仍可滑到最旧的媒体.
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val minYear = currentYear - 5
    return allMarkers.filter { it.year >= minYear }
}

// TODO: 确认是否死代码，主函数实际调用 PhotoFeedTimeScrubber
@Composable
internal fun SystemMediaTimeScrubber(
    progress: Float,
    label: String,
    showLabel: Boolean,
    onSeekToProgress: (Float) -> Unit,
    onInteractingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val thumbWidth = 22.dp
    val thumbHeight = 76.dp
    val endMargin = 8.dp

    var scrubberHeightPx by remember { mutableIntStateOf(0) }
    var labelHeightPx by remember { mutableIntStateOf(0) }
    var scrubberLabelWidthPx by remember { mutableIntStateOf(0) }
    var lastDispatchedProgress by remember { mutableStateOf(Float.NaN) }
    var dragStartProgress by remember { mutableStateOf(0f) }
    var dragAccumulatedPx by remember { mutableStateOf(0f) }
    val latestProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    val latestOnSeekToProgress by rememberUpdatedState(onSeekToProgress)
    val latestOnInteractingChanged by rememberUpdatedState(onInteractingChanged)
    val thumbWidthPx = with(density) { thumbWidth.roundToPx() }
    val thumbHeightPx = with(density) { thumbHeight.roundToPx() }
    val endMarginPx = with(density) { endMargin.roundToPx() }
    val labelGapPx = with(density) { 12.dp.roundToPx() }
    val travelHeightPx = (scrubberHeightPx - thumbHeightPx).coerceAtLeast(1)
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val thumbTopPx = (travelHeightPx * normalizedProgress).roundToInt()
    val labelTopPx = (thumbTopPx + (thumbHeightPx / 2) - (labelHeightPx / 2))
        .coerceIn(0, (scrubberHeightPx - labelHeightPx).coerceAtLeast(0))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { scrubberHeightPx = it.height },
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel && label.isNotBlank(),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -(scrubberLabelWidthPx + thumbWidthPx + endMarginPx + labelGapPx), y = labelTopPx) },
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.raisedSurface.copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .padding(horizontal = spacing.md, vertical = 8.dp)
                        .onSizeChanged {
                            scrubberLabelWidthPx = it.width
                            labelHeightPx = it.height
                        },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    color = colors.titleAccent,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -endMarginPx, y = thumbTopPx) }
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.raisedSurface.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = colors.dividerSoft.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(999.dp),
                )
                .pointerInput(scrubberHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            latestOnInteractingChanged(true)
                            dragStartProgress = latestProgress
                            dragAccumulatedPx = 0f
                            lastDispatchedProgress = Float.NaN
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulatedPx += dragAmount.y
                            val nextProgress = (dragStartProgress + (dragAccumulatedPx / travelHeightPx.toFloat()))
                                .coerceIn(0f, 1f)
                            if (!lastDispatchedProgress.isNaN() && abs(lastDispatchedProgress - nextProgress) < 0.005f) {
                                return@detectDragGestures
                            }
                            lastDispatchedProgress = nextProgress
                            latestOnSeekToProgress(nextProgress)
                        },
                        onDragEnd = {
                            lastDispatchedProgress = Float.NaN
                            latestOnInteractingChanged(false)
                        },
                        onDragCancel = {
                            lastDispatchedProgress = Float.NaN
                            latestOnInteractingChanged(false)
                        },
                    )
                },
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(8.dp),
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(0f, h)
                    lineTo(w, h)
                    close()
                }
                drawPath(path, color = colors.titleAccent.copy(alpha = 0.78f))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 14.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.dividerSoft.copy(alpha = 0.82f)),
            )

            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(8.dp),
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                    close()
                }
                drawPath(path, color = colors.titleAccent.copy(alpha = 0.78f))
            }
        }
    }
}

internal fun calculateSystemMediaScrollProgress(
    gridState: LazyGridState,
    blocks: List<SystemMediaGridBlock>,
    itemCount: Int,
): Float {
    if (itemCount <= 1) return 0f
    val layoutInfo = gridState.layoutInfo
    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val visibleMediaCount = layoutInfo.visibleItemsInfo
        .count { visible -> blocks.getOrNull(visible.index) is SystemMediaGridBlock.Media }
        .coerceAtLeast(1)
    val scrollableStart = (itemCount - visibleMediaCount).coerceAtLeast(1)
    val limit = (firstVisible.index + 1).coerceAtMost(blocks.size)
    var mediaCount = 0
    for (i in 0 until limit) {
        if (blocks[i] is SystemMediaGridBlock.Media) mediaCount++
    }
    val firstMediaOrdinal = mediaCount.coerceAtLeast(1) - 1
    val offsetFraction = ((-firstVisible.offset.y).toFloat() / maxOf(firstVisible.size.height, viewportHeight).toFloat())
        .coerceIn(0f, 1f)
    return ((firstMediaOrdinal + offsetFraction) / scrollableStart.toFloat())
        .coerceIn(0f, 1f)
}

internal fun SystemMediaItem.toSystemMediaScrubberLabel(): String {
    return SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(displayTimeMillis))
}

internal fun calculatePhotoFeedLikeSystemScrubberScrollOffset(
    gridState: LazyGridState,
): Int {
    val layoutInfo = gridState.layoutInfo
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .takeIf { it > 0 }
        ?: layoutInfo.viewportSize.height
    return -(viewportHeight * 0.36f).roundToInt()
}

internal fun findSystemMediaScrubberTargetBlockIndex(
    blocks: List<SystemMediaGridBlock>,
    mediaId: String,
): Int {
    val mediaIndex = blocks.indexOfFirst { block ->
        block is SystemMediaGridBlock.Media && block.item.id == mediaId
    }
    if (mediaIndex <= 0) return mediaIndex
    for (index in mediaIndex downTo 0) {
        when (blocks[index]) {
            is SystemMediaGridBlock.DayHeader,
            is SystemMediaGridBlock.MonthHeader,
            -> return index
            is SystemMediaGridBlock.Media -> Unit
        }
    }
    return mediaIndex
}

internal fun buildSystemMediaScrubberTargetIndexMap(
    blocks: List<SystemMediaGridBlock>,
): Map<String, Int> {
    if (blocks.isEmpty()) return emptyMap()
    val targetByMediaId = LinkedHashMap<String, Int>(blocks.size)
    var currentHeaderIndex = 0
    blocks.forEachIndexed { index, block ->
        when (block) {
            is SystemMediaGridBlock.DayHeader,
            is SystemMediaGridBlock.MonthHeader,
            -> currentHeaderIndex = index
            is SystemMediaGridBlock.Media -> {
                targetByMediaId[block.item.id] = currentHeaderIndex
            }
        }
    }
    return targetByMediaId
}

internal fun systemMediaScrubberIndexStep(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 1
        PhotoFeedDensity.COMFORT_3 -> 2
        PhotoFeedDensity.DENSE_4 -> 3
        PhotoFeedDensity.OVERVIEW_8 -> 12
        PhotoFeedDensity.OVERVIEW_16 -> 36
    }
}

internal fun resolveCurrentSystemMediaVisibleLabel(
    itemIndex: Int,
    blocks: List<SystemMediaGridBlock>,
    fallbackItems: List<SystemMediaItem>,
): String {
    if (blocks.isEmpty()) {
        return fallbackItems.firstOrNull()?.toSystemMediaScrubberLabel().orEmpty()
    }
    val safeIndex = itemIndex.coerceIn(0, blocks.lastIndex)
    val nextMedia = blocks
        .drop(safeIndex)
        .firstOrNull { it is SystemMediaGridBlock.Media } as? SystemMediaGridBlock.Media
    val previousMedia = blocks
        .take(safeIndex + 1)
        .lastOrNull { it is SystemMediaGridBlock.Media } as? SystemMediaGridBlock.Media
    return nextMedia?.item?.toSystemMediaScrubberLabel()
        ?: previousMedia?.item?.toSystemMediaScrubberLabel()
        ?: fallbackItems.firstOrNull()?.toSystemMediaScrubberLabel()
        ?: ""
}

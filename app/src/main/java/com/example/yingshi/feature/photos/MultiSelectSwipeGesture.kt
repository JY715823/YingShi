package com.example.yingshi.feature.photos

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs
import kotlin.math.sqrt

data class MultiSelectHitResult(
    val mediaId: String,
    val rowKey: String?,
    val rowIndex: Int = -1,
    val isSelectable: Boolean,
    val colIndex: Int = 0,
    val columnsInRow: Int = 0,
)

class MultiSelectHitTestAdapter(
    val hitTest: (Offset) -> MultiSelectHitResult?,
    val mediaIdsInRow: (String) -> List<String>,
    val rowKeyAtIndex: (Int) -> String?,
)

@Composable
fun Modifier.multiSelectSwipeGesture(
    enabled: Boolean,
    hitTestAdapter: MultiSelectHitTestAdapter,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onGestureActiveChanged: ((Boolean) -> Unit)? = null,
    onTouchPositionChanged: ((Offset?) -> Unit)? = null,
): Modifier {
    val currentEnabled = rememberUpdatedState(enabled)
    val currentAdapter = rememberUpdatedState(hitTestAdapter)
    val currentSelectedIds = rememberUpdatedState(selectedIds)
    val currentOnSelectionChange = rememberUpdatedState(onSelectionChange)
    val currentOnGestureActiveChanged = rememberUpdatedState(onGestureActiveChanged)
    val currentOnTouchPositionChanged = rememberUpdatedState(onTouchPositionChanged)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEnabled.value) return@awaitEachGesture

            val adapter = currentAdapter.value
            val firstHit = adapter.hitTest(down.position)
            if (firstHit == null || !firstHit.isSelectable || firstHit.rowKey == null) {
                return@awaitEachGesture
            }

            val gestureStartSelectedIds = currentSelectedIds.value.toSet()
            val firstTouchId = firstHit.mediaId
            val isSelectMode = firstTouchId !in gestureStartSelectedIds
            var currentIds = gestureStartSelectedIds
            var gestureActive = false
            var lastHit: MultiSelectHitResult? = null
            var lastPos = down.position
            var blockSwipeSelectForThisGesture = false

            fun shouldFlip(mediaId: String): Boolean {
                return if (isSelectMode) {
                    mediaId !in gestureStartSelectedIds
                } else {
                    mediaId in gestureStartSelectedIds
                }
            }

            fun applyRangeTo(hit: MultiSelectHitResult) {
                if (!hit.isSelectable || hit.rowKey == null || hit.rowIndex < 0) return
                val rangeIds = adapter.mediaIdsBetween(
                    startRowIndex = firstHit.rowIndex,
                    startColIndex = firstHit.colIndex,
                    endRowIndex = hit.rowIndex,
                    endColIndex = hit.colIndex,
                ).filter(::shouldFlip).toSet()

                val nextIds = if (isSelectMode) {
                    gestureStartSelectedIds + rangeIds
                } else {
                    gestureStartSelectedIds - rangeIds
                }
                if (nextIds != currentIds) {
                    currentIds = nextIds
                    currentOnSelectionChange.value(currentIds)
                }
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val changes: List<PointerInputChange> = event.changes
                if (changes.isEmpty()) continue
                val change = changes[0]

                if (!change.pressed) {
                    if (gestureActive) {
                        currentOnGestureActiveChanged.value?.invoke(false)
                        currentOnTouchPositionChanged.value?.invoke(null)
                    }
                    break
                }

                if (!gestureActive) {
                    if (blockSwipeSelectForThisGesture) {
                        continue
                    }
                    val totalDx = change.position.x - down.position.x
                    val totalDy = change.position.y - down.position.y
                    val absDx = abs(totalDx)
                    val absDy = abs(totalDy)

                    // Vertical intent wins early: keep this whole gesture for scrolling only.
                    val hasVerticalIntent = absDy >= touchSlop && absDy > absDx * 1.1f
                    if (hasVerticalIntent) {
                        blockSwipeSelectForThisGesture = true
                        continue
                    }

                    // Require stronger and dominant horizontal movement to start swipe-select.
                    val hasHorizontalIntent = absDx >= (touchSlop * 1.35f) && absDx > absDy * 1.2f
                    if (!hasHorizontalIntent) {
                        // Keep non-horizontal movement as normal list/grid scroll.
                        continue
                    }
                    gestureActive = true
                    currentOnGestureActiveChanged.value?.invoke(true)
                    applyRangeTo(firstHit)
                }

                change.consume()
                val hit = adapter.hitTest(change.position)
                if (hit != null) {
                    val dx = change.position.x - lastPos.x
                    val dy = change.position.y - lastPos.y
                    if (sqrt(dx * dx + dy * dy) >= 4f || hit.mediaId != lastHit?.mediaId) {
                        applyRangeTo(hit)
                        lastHit = hit
                        lastPos = change.position
                    }
                }
                currentOnTouchPositionChanged.value?.invoke(change.position)
            }
        }
    }
}

private fun MultiSelectHitTestAdapter.mediaIdsBetween(
    startRowIndex: Int,
    startColIndex: Int,
    endRowIndex: Int,
    endColIndex: Int,
): List<String> {
    if (startRowIndex < 0 || endRowIndex < 0) return emptyList()
    if (startRowIndex == endRowIndex) {
        val rowIds = rowKeyAtIndex(startRowIndex)?.let(mediaIdsInRow).orEmpty()
        if (rowIds.isEmpty()) return emptyList()
        val left = minOf(startColIndex, endColIndex).coerceIn(0, rowIds.lastIndex)
        val right = maxOf(startColIndex, endColIndex).coerceIn(0, rowIds.lastIndex)
        return rowIds.subList(left, right + 1)
    }
    return if (endRowIndex >= startRowIndex) {
        buildForwardRange(
            startRowIndex = startRowIndex,
            startColIndex = startColIndex,
            endRowIndex = endRowIndex,
            endColIndex = endColIndex,
        )
    } else {
        buildBackwardRange(
            startRowIndex = startRowIndex,
            startColIndex = startColIndex,
            endRowIndex = endRowIndex,
            endColIndex = endColIndex,
        )
    }
}

private fun MultiSelectHitTestAdapter.buildForwardRange(
    startRowIndex: Int,
    startColIndex: Int,
    endRowIndex: Int,
    endColIndex: Int,
): List<String> {
    val ids = mutableListOf<String>()
    for (rowIndex in startRowIndex..endRowIndex) {
        val rowIds = rowKeyAtIndex(rowIndex)?.let(mediaIdsInRow).orEmpty()
        if (rowIds.isEmpty()) continue

        val firstCol = if (rowIndex == startRowIndex) {
            startColIndex.coerceIn(0, rowIds.lastIndex)
        } else {
            0
        }
        val lastCol = if (rowIndex == endRowIndex) {
            endColIndex.coerceIn(0, rowIds.lastIndex)
        } else {
            rowIds.lastIndex
        }
        if (firstCol <= lastCol) {
            ids += rowIds.subList(firstCol, lastCol + 1)
        }
    }
    return ids
}

private fun MultiSelectHitTestAdapter.buildBackwardRange(
    startRowIndex: Int,
    startColIndex: Int,
    endRowIndex: Int,
    endColIndex: Int,
): List<String> {
    val ids = mutableListOf<String>()
    for (rowIndex in endRowIndex..startRowIndex) {
        val rowIds = rowKeyAtIndex(rowIndex)?.let(mediaIdsInRow).orEmpty()
        if (rowIds.isEmpty()) continue

        val firstCol = if (rowIndex == endRowIndex) {
            endColIndex.coerceIn(0, rowIds.lastIndex)
        } else {
            0
        }
        val lastCol = if (rowIndex == startRowIndex) {
            startColIndex.coerceIn(0, rowIds.lastIndex)
        } else {
            rowIds.lastIndex
        }
        if (firstCol <= lastCol) {
            ids += rowIds.subList(firstCol, lastCol + 1)
        }
    }
    return ids
}

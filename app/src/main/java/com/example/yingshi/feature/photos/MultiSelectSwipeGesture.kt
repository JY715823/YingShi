package com.example.yingshi.feature.photos

import android.util.Log
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
import kotlin.math.sqrt

private const val TAG = "MSelect"

data class MultiSelectHitResult(
    val mediaId: String,
    val rowKey: String?,
    val isSelectable: Boolean,
    val colIndex: Int = 0,
    val columnsInRow: Int = 0,
)

class MultiSelectHitTestAdapter(
    val hitTest: (Offset) -> MultiSelectHitResult?,
    val mediaIdsInRow: (String) -> List<String>,
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
            if (firstHit == null || !firstHit.isSelectable) return@awaitEachGesture

            val gestureStartIds = currentSelectedIds.value
            val isSelectMode = firstHit.mediaId !in gestureStartIds
            val firstTouchId = firstHit.mediaId
            val firstTouchRow = firstHit.rowKey
            val firstTouchCol = firstHit.colIndex
            val firstTouchY = down.position.y

            // State
            val toggledIds = mutableSetOf<String>()       // currently flipped
            var currentIds = gestureStartIds
            var lastItemId: String? = null
            var lastCol: Int? = null                      // track column for direction detection
            var lastPosX = down.position.x
            var lastPosY = down.position.y

            // Row tracking
            var lastRow = firstTouchRow
            // We track which rows have been entered (value = meaningful expanded ids or empty placeholder)
            // A row with an empty set = placeholder (first-touch row or previously entered)
            val rowState = mutableMapOf<String, MutableSet<String>?>()
            if (firstTouchRow != null) rowState[firstTouchRow] = null // placeholder

            var gestureActive = false
            var initialToggleDone = false
            var hasExpandedAnyRow = false
            var frameSeq = 0

            fun short(id: String) = id.takeLast(6)
            fun setStr(ids: Set<String>) = ids.map { short(it) }.joinToString(",")
            fun matchesMode(id: String) = if (isSelectMode) id !in gestureStartIds else id in gestureStartIds
            fun toggleOne(id: String) { currentIds = if (id in currentIds) currentIds - id else currentIds + id }
            fun revertOne(id: String) {
                if (id in gestureStartIds) { if (id !in currentIds) currentIds = currentIds + id }
                else { if (id in currentIds) currentIds = currentIds - id }
            }
            fun flush() { currentOnSelectionChange.value(currentIds) }

            fun ensureInit() {
                if (initialToggleDone) return
                initialToggleDone = true
                toggleOne(firstTouchId)
                toggledIds.add(firstTouchId)
                lastItemId = firstTouchId
                Log.d(TAG, "  INIT ${short(firstTouchId)} N=${currentIds.size}")
                flush()
            }

            fun expandRow(rk: String, tc: Int, cols: Int, y: Float, isFirst: Boolean, isOrig: Boolean) {
                val all = adapter.mediaIdsInRow(rk)
                if (all.isEmpty()) return
                val dn = y > firstTouchY
                val ids = when {
                    isOrig && dn -> all.drop(firstTouchCol + 1)
                    isOrig && !dn -> all.take(firstTouchCol)
                    isFirst -> all
                    dn -> all.take(tc + 1)
                    else -> all.drop(tc)
                }
                val lbl = if (isOrig) "ORIG" else if (isFirst) "FULL" else "PART"
                val added = mutableSetOf<String>()
                for (id in ids) {
                    if (id == firstTouchId) continue
                    if (!matchesMode(id)) continue
                    if (id in toggledIds) continue
                    toggleOne(id); toggledIds.add(id); added.add(id)
                }
                if (added.isNotEmpty()) rowState[rk] = added
                Log.d(TAG, "  EXP-$lbl ${short(rk)} +[${setStr(added)}] N=${currentIds.size}")
            }

            fun undoRow(rk: String) {
                val ids = rowState.remove(rk) ?: return
                if (ids == null) return  // placeholder, no items
                for (id in ids) { revertOne(id); toggledIds.remove(id) }
                Log.d(TAG, "  UNDO ${short(rk)} ids=[${setStr(ids)}] N=${currentIds.size}")
            }

            fun process(hit: MultiSelectHitResult, pos: Offset) {
                val id = hit.mediaId
                val nr = hit.rowKey

                // Row transition
                if (nr != null && nr != lastRow) {
                    val seen = nr in rowState
                    val wasExpanded = rowState[nr] !== null && rowState[nr]?.isNotEmpty() == true
                    Log.d(TAG, "  ROW ${short(lastRow ?: "null")} -> ${short(nr)} seen=$seen expanded=$wasExpanded")

                    if (!seen) {
                        // New row: expand
                        val isFirst = !hasExpandedAnyRow
                        hasExpandedAnyRow = true
                        if (isFirst && firstTouchRow != null && rowState[firstTouchRow] == null) {
                            expandRow(firstTouchRow, firstTouchCol, hit.columnsInRow, pos.y, true, true)
                            flush()
                        }
                        expandRow(nr, hit.colIndex, hit.columnsInRow, pos.y, isFirst, false)
                        flush()
                        lastItemId = null // reset dedup for new row
                    } else if (!wasExpanded && nr != firstTouchRow) {
                        // Previously seen (placeholder or empty), moving back: undo the old row
                        undoRow(lastRow!!)
                        flush()
                        lastItemId = null
                    }
                    // If wasExpanded: row is currently expanded, moving back into it
                    // Individual items handle it via toggledIds
                    lastRow = nr
                }

                ensureInit()

                if (id == firstTouchId) { lastItemId = id; lastCol = hit.colIndex; return }
                if (!matchesMode(id)) { lastItemId = id; lastCol = hit.colIndex; return }
                if (id == lastItemId) return

                // Detect direction within same row
                val movingBack = lastCol != null && nr == lastRow && hit.colIndex < lastCol!!

                if (movingBack) {
                    // Revert the item we just left (passed through in reverse)
                    if (lastItemId != null && lastItemId != firstTouchId && lastItemId in toggledIds) {
                        revertOne(lastItemId!!); toggledIds.remove(lastItemId!!)
                        Log.d(TAG, "  REV-EXIT ${short(lastItemId!!)} N=${currentIds.size}")
                    }
                    // Revert current item (moving back into it)
                    if (id in toggledIds) {
                        revertOne(id); toggledIds.remove(id)
                        Log.d(TAG, "  REV ${short(id)} N=${currentIds.size}")
                    }
                } else {
                    // Moving forward: only toggle new items, don't revert
                    if (id !in toggledIds) {
                        toggleOne(id); toggledIds.add(id)
                        Log.d(TAG, "  TOG ${short(id)} N=${currentIds.size}")
                    }
                }
                lastItemId = id; lastCol = hit.colIndex
                flush()

                lastPosX = pos.x; lastPosY = pos.y
            }

            Log.d(TAG, "╔══ id=${short(firstTouchId)} row=$firstTouchRow col=$firstTouchCol mode=${if (isSelectMode) "SEL" else "DESEL"} startN=${gestureStartIds.size}")

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val changes: List<PointerInputChange> = event.changes
                if (changes.isEmpty()) continue
                val change = changes[0]
                frameSeq++

                if (!change.pressed) {
                    Log.d(TAG, "╚══ UP N=${currentIds.size} frames=$frameSeq")
                    if (gestureActive) { currentOnGestureActiveChanged.value?.invoke(false); currentOnTouchPositionChanged.value?.invoke(null) }
                    break
                }

                val pastSlop = gestureActive || run {
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    sqrt(dx * dx + dy * dy) >= touchSlop
                }

                val hit = adapter.hitTest(change.position)

                if (!pastSlop) {
                    if (hit != null && hit.isSelectable) process(hit, change.position)
                    continue
                }

                if (!gestureActive) { gestureActive = true; Log.d(TAG, "  DRAG"); currentOnGestureActiveChanged.value?.invoke(true) }
                change.consume()

                if (hit != null && hit.isSelectable) {
                    // Dedup threshold: only process if finger moved >4px or item changed
                    val dx = change.position.x - lastPosX
                    val dy = change.position.y - lastPosY
                    if (sqrt(dx * dx + dy * dy) >= 4f || hit.mediaId != lastItemId) {
                        process(hit, change.position)
                    }
                }
                currentOnTouchPositionChanged.value?.invoke(change.position)
            }
        }
    }
}

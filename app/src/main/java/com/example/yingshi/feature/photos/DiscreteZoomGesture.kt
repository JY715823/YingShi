package com.example.yingshi.feature.photos

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlin.math.pow

private const val ZoomInThreshold = 1.34f
private const val ZoomOutThreshold = 0.76f
private const val ZoomDirectionLockInThreshold = 1.05f
private const val ZoomDirectionLockOutThreshold = 0.95f
private const val ZoomCommitThreshold = 0.66f

internal enum class DiscreteZoomDirection {
    TO_SPARSE,
    TO_DENSE,
}

internal data class DiscreteZoomPreviewState<T>(
    val sourceLevel: T,
    val targetLevel: T,
    val direction: DiscreteZoomDirection,
    val rawProgress: Float,
    val renderProgress: Float,
    val accumulatedZoom: Float,
    val centroid: Offset,
)

@Composable
internal fun <T> Modifier.discreteZoomLevelGesture(
    enabled: Boolean,
    levels: List<T>,
    currentLevel: T,
    onLevelChange: (T) -> Unit,
    commitOnGestureEnd: Boolean = false,
    onPreviewStateChange: (DiscreteZoomPreviewState<T>?) -> Unit = {},
    onLevelCommitted: (from: T, to: T) -> Unit = { _, _ -> },
    onGestureFinished: (
        committed: Boolean,
        finalPreviewState: DiscreteZoomPreviewState<T>?,
        committedTargetLevel: T?,
    ) -> Unit = { _, _, _ -> },
): Modifier {
    val latestLevel = rememberUpdatedState(currentLevel)
    val latestOnLevelChange = rememberUpdatedState(onLevelChange)
    val latestOnPreviewStateChange = rememberUpdatedState(onPreviewStateChange)
    val latestOnLevelCommitted = rememberUpdatedState(onLevelCommitted)
    val latestOnGestureFinished = rememberUpdatedState(onGestureFinished)

    if (!enabled || levels.size <= 1) return this

    return pointerInput(enabled, levels) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var accumulatedZoom = 1f
            var hasChangedLevel = false
            val initialLevel = latestLevel.value
            val currentIndex = levels.indexOf(initialLevel).coerceAtLeast(0)
            var lastPreviewState: DiscreteZoomPreviewState<T>? = null
            var committedTargetLevel: T? = null
            var previewStarted = false
            var lockedDirection: DiscreteZoomDirection? = null
            var multiTouchActivated = false
            var gestureFinished = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val activeChanges = event.changes.filter { it.pressed }
                if (activeChanges.size > 1) {
                    multiTouchActivated = true
                }

                if (!multiTouchActivated) {
                    if (activeChanges.isEmpty()) {
                        break
                    }
                    continue
                }

                event.changes.forEach { it.consume() }

                if (gestureFinished) {
                    if (activeChanges.isEmpty()) {
                        break
                    }
                    continue
                }

                if (activeChanges.size < 2) {
                    if (commitOnGestureEnd && !hasChangedLevel) {
                        val previewState = lastPreviewState
                        if (previewState != null && previewState.rawProgress >= ZoomCommitThreshold) {
                            val nextLevel = previewState.targetLevel
                            committedTargetLevel = nextLevel
                            latestOnLevelChange.value(nextLevel)
                            latestOnLevelCommitted.value(initialLevel, nextLevel)
                            hasChangedLevel = true
                        }
                    }
                    latestOnGestureFinished.value(
                        hasChangedLevel,
                        lastPreviewState,
                        committedTargetLevel,
                    )
                    if (previewStarted) {
                        latestOnPreviewStateChange.value(null)
                    }
                    gestureFinished = true
                    continue
                }

                val currentCentroid = activeChanges.centroid(usePrevious = false)
                val previousCentroid = activeChanges.centroid(usePrevious = true)
                val currentDistance = activeChanges.averageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.averageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                if (previousDistance > 0f) {
                    accumulatedZoom *= currentDistance / previousDistance
                }

                val previewState = buildPreviewState(
                    levels = levels,
                    accumulatedZoom = accumulatedZoom,
                    currentIndex = currentIndex,
                    lockedDirection = lockedDirection,
                    centroid = currentCentroid,
                )
                if (previewState != null) {
                    lockedDirection = previewState.direction
                }
                lastPreviewState = previewState
                latestOnPreviewStateChange.value(previewState)
                previewStarted = previewState != null

                if (!commitOnGestureEnd && !hasChangedLevel) {
                    when {
                        accumulatedZoom >= ZoomInThreshold && currentIndex > 0 -> {
                            val nextLevel = levels[currentIndex - 1]
                            latestOnLevelChange.value(nextLevel)
                            latestOnLevelCommitted.value(initialLevel, nextLevel)
                            hasChangedLevel = true
                        }

                        accumulatedZoom <= ZoomOutThreshold && currentIndex < levels.lastIndex -> {
                            val nextLevel = levels[currentIndex + 1]
                            latestOnLevelChange.value(nextLevel)
                            latestOnLevelCommitted.value(initialLevel, nextLevel)
                            hasChangedLevel = true
                        }
                    }
                }
            }
        }
    }
}

private fun List<PointerInputChange>.centroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.averageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

private fun <T> buildPreviewState(
    levels: List<T>,
    accumulatedZoom: Float,
    currentIndex: Int,
    lockedDirection: DiscreteZoomDirection?,
    centroid: Offset,
) : DiscreteZoomPreviewState<T>? {
    val direction = lockedDirection ?: when {
        accumulatedZoom >= ZoomDirectionLockInThreshold && currentIndex > 0 ->
            DiscreteZoomDirection.TO_SPARSE

        accumulatedZoom <= ZoomDirectionLockOutThreshold && currentIndex < levels.lastIndex ->
            DiscreteZoomDirection.TO_DENSE

        else -> null
    }
    val targetIndex = when (direction) {
        DiscreteZoomDirection.TO_SPARSE -> (currentIndex - 1).coerceAtLeast(0)
        DiscreteZoomDirection.TO_DENSE -> (currentIndex + 1).coerceAtMost(levels.lastIndex)
        null -> currentIndex
    }
    if (direction == null || targetIndex == currentIndex) return null
    val rawProgress = when (direction) {
        DiscreteZoomDirection.TO_SPARSE ->
            ((accumulatedZoom - ZoomDirectionLockInThreshold) /
                (ZoomInThreshold - ZoomDirectionLockInThreshold)).coerceIn(0f, 1f)

        DiscreteZoomDirection.TO_DENSE ->
            ((ZoomDirectionLockOutThreshold - accumulatedZoom) /
                (ZoomDirectionLockOutThreshold - ZoomOutThreshold)).coerceIn(0f, 1f)
    }
    val renderProgress = smootherstep(rawProgress.toDouble().pow(1.1).toFloat())
    return DiscreteZoomPreviewState(
        sourceLevel = levels[currentIndex],
        targetLevel = levels[targetIndex],
        direction = direction,
        rawProgress = rawProgress,
        renderProgress = renderProgress,
        accumulatedZoom = accumulatedZoom,
        centroid = centroid,
    )
}

private fun smootherstep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * clamped *
        (clamped * (clamped * 6f - 15f) + 10f)
}

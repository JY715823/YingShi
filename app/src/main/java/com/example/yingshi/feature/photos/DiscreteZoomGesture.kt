package com.example.yingshi.feature.photos

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture

private const val ZoomInThreshold = 1.10f
private const val ZoomOutThreshold = 0.90f

internal fun <T> Modifier.discreteZoomLevelGesture(
    enabled: Boolean,
    levels: List<T>,
    currentLevel: T,
    onLevelChange: (T) -> Unit,
): Modifier {
    if (!enabled || levels.size <= 1) return this

    return pointerInput(enabled, levels, currentLevel) {
        awaitEachGesture {
            var accumulatedZoom = 1f
            var hasChangedLevel = false
            val currentIndex = levels.indexOf(currentLevel).coerceAtLeast(0)

            while (true) {
                val event = awaitPointerEvent()
                val activeChanges = event.changes.filter { it.pressed }
                if (activeChanges.isEmpty()) break

                if (activeChanges.size < 2) continue

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

                if (!hasChangedLevel) {
                    when {
                        accumulatedZoom >= ZoomInThreshold && currentIndex > 0 -> {
                            onLevelChange(levels[currentIndex - 1])
                            hasChangedLevel = true
                        }

                        accumulatedZoom <= ZoomOutThreshold && currentIndex < levels.lastIndex -> {
                            onLevelChange(levels[currentIndex + 1])
                            hasChangedLevel = true
                        }
                    }
                }

                activeChanges.forEach { it.consume() }
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

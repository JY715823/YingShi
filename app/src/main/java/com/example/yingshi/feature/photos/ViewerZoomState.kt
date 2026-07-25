package com.example.yingshi.feature.photos

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

internal const val MinViewerScale = 1f
internal const val MaxViewerScale = 6f
internal const val MaxViewerElasticScale = 9f
internal const val ViewerDoubleTapScale = 2.5f
internal const val ViewerZoomResetThreshold = 1.02f
// 单击/双击判定窗口. 此前 260ms 偏长, 用户感觉单击响应迟钝.
// 降到 200ms (Android ViewConfiguration.doubleTapTimeout = 200ms), 单击快 60ms, 双击仍能识别.
internal const val ViewerFastDoubleTapWindowMillis = 200L

internal class ViewerZoomState {
    var scale by mutableStateOf(MinViewerScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    private var latestContentSize by mutableStateOf(IntSize.Zero)
    private var latestContentTopLeft by mutableStateOf(Offset.Zero)
    private var latestMinimumScale by mutableStateOf(MinViewerScale)

    val isZoomed: Boolean
        get() = abs(scale - MinViewerScale) > ViewerZoomResetThreshold - MinViewerScale

    fun updateContentGeometry(
        contentSize: IntSize,
        contentTopLeft: Offset,
        minimumScale: Float = MinViewerScale,
    ) {
        if (contentSize.width > 0 && contentSize.height > 0) {
            latestContentSize = contentSize
            latestContentTopLeft = contentTopLeft
            latestMinimumScale = minimumScale.coerceIn(0.05f, MinViewerScale)
        }
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: Offset,
        focalPoint: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ) {
        val previousScale = scale
        val minimumScale = latestMinimumScale
        val nextScale = (scale * zoomChange).coerceIn(minimumScale, MaxViewerElasticScale)
        if (minimumScale >= MinViewerScale && nextScale <= ViewerZoomResetThreshold) {
            reset()
            return
        }

        scale = nextScale
        val scaleRatio = if (previousScale > 0f) nextScale / previousScale else MinViewerScale
        val focalAnchoredOffset = focalPoint -
            contentTopLeft -
            (focalPoint - contentTopLeft - offset) * scaleRatio +
            panChange
        offset = clampOffset(focalAnchoredOffset, nextScale, containerSize, contentSize, contentTopLeft)
    }

    fun settleAfterGesture(
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ) {
        if (scale <= MaxViewerScale) {
            offset = clampOffset(
                value = offset,
                currentScale = scale,
                containerSize = containerSize,
                contentSize = contentSize,
                contentTopLeft = contentTopLeft,
            )
            return
        }
        val previousScale = scale
        scale = MaxViewerScale
        offset = clampOffset(
            value = offset * (MaxViewerScale / previousScale),
            currentScale = MaxViewerScale,
            containerSize = containerSize,
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
        )
    }

    fun panBy(
        panChange: Offset,
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ) {
        if (!isZoomed) return
        offset = clampOffset(offset + panChange, scale, containerSize, contentSize, contentTopLeft)
    }

    fun reset() {
        scale = MinViewerScale
        offset = Offset.Zero
    }

    fun toggleDoubleTap(
        tapPosition: Offset,
        containerSize: IntSize,
    ) {
        if (isZoomed) {
            reset()
            return
        }
        val contentSize = latestContentSize.takeIf { it.width > 0 && it.height > 0 } ?: containerSize
        val contentTopLeft = latestContentTopLeft
        val targetScale = ViewerDoubleTapScale.coerceIn(MinViewerScale, MaxViewerScale)
        scale = targetScale
        val normalizedTap = tapPosition - contentTopLeft
        offset = clampOffset(
            value = tapPosition - contentTopLeft - normalizedTap * targetScale,
            currentScale = targetScale,
            containerSize = containerSize,
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
        )
    }

    private fun clampOffset(
        value: Offset,
        currentScale: Float,
        containerSize: IntSize,
        contentSize: IntSize,
        contentTopLeft: Offset,
    ): Offset {
        val scaledWidth = contentSize.width * currentScale
        val scaledHeight = contentSize.height * currentScale
        val minX = if (scaledWidth <= containerSize.width) {
            ((containerSize.width - scaledWidth) / 2f) - contentTopLeft.x
        } else {
            containerSize.width - contentTopLeft.x - scaledWidth
        }
        val maxX = if (scaledWidth <= containerSize.width) minX else -contentTopLeft.x
        val minY = if (scaledHeight <= containerSize.height) {
            ((containerSize.height - scaledHeight) / 2f) - contentTopLeft.y
        } else {
            containerSize.height - contentTopLeft.y - scaledHeight
        }
        val maxY = if (scaledHeight <= containerSize.height) minY else -contentTopLeft.y
        return Offset(
            x = value.x.coerceIn(minX, maxX),
            y = value.y.coerceIn(minY, maxY),
        )
    }
}

internal fun Modifier.systemViewerZoomGesture(
    zoomState: ViewerZoomState,
    contentSize: IntSize,
    contentTopLeft: Offset,
    onDoubleTap: ((Offset, IntSize) -> Unit)? = null,
): Modifier = pointerInput(zoomState, contentSize, contentTopLeft) {
    var lastTapUptimeMillis = 0L
    var lastTapPosition: Offset? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val wasZoomedAtGestureStart = zoomState.isZoomed
        var pointerCountExceeded = false
        var moved = false
        var consumedByTransform = false
        var upPosition = start
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) {
                event.changes.firstOrNull { it.id == down.id }?.let { upPosition = it.position }
                break
            }
            if (activeChanges.size > 1) pointerCountExceeded = true
            event.changes.forEach { change ->
                if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
            }

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.systemViewerCentroid(usePrevious = false)
                val previousCentroid = activeChanges.systemViewerCentroid(usePrevious = true)
                val currentDistance = activeChanges.systemViewerAverageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.systemViewerAverageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinViewerScale
                }
                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    focalPoint = currentCentroid,
                    containerSize = size,
                    contentSize = contentSize,
                    contentTopLeft = contentTopLeft,
                )
                activeChanges.forEach { it.consume() }
                consumedByTransform = true
            } else if (zoomState.isZoomed) {
                val change = activeChanges.first()
                val panChange = change.positionChange()
                if (panChange.getDistance() > viewConfiguration.touchSlop / 3f) {
                    zoomState.applyTransform(
                        zoomChange = 1f,
                        panChange = panChange,
                        focalPoint = change.position,
                        containerSize = size,
                        contentSize = contentSize,
                        contentTopLeft = contentTopLeft,
                    )
                    activeChanges.forEach { it.consume() }
                    consumedByTransform = true
                }
            }
        }
        zoomState.settleAfterGesture(
            containerSize = size,
            contentSize = contentSize,
            contentTopLeft = contentTopLeft,
        )
        if (wasZoomedAtGestureStart &&
            !pointerCountExceeded &&
            !moved &&
            !consumedByTransform &&
            onDoubleTap != null
        ) {
            val now = SystemClock.uptimeMillis()
            val previousTapPosition = lastTapPosition
            val doubleTapDistance = viewConfiguration.touchSlop * 8f
            val isDoubleTap = previousTapPosition != null &&
                now - lastTapUptimeMillis <= ViewerFastDoubleTapWindowMillis &&
                (upPosition - previousTapPosition).getDistance() <= doubleTapDistance
            if (isDoubleTap) {
                lastTapUptimeMillis = 0L
                lastTapPosition = null
                onDoubleTap(upPosition, size)
            } else {
                lastTapUptimeMillis = now
                lastTapPosition = upPosition
            }
        }
    }
}

private fun List<PointerInputChange>.systemViewerCentroid(usePrevious: Boolean): Offset {
    val total = fold(Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<PointerInputChange>.systemViewerAverageDistanceTo(
    centroid: Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

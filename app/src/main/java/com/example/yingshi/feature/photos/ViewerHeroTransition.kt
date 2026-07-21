package com.example.yingshi.feature.photos

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

internal const val HeroTransitionMillis = 350

/**
 * Hero 过渡 easing：前快后慢，展开感更强。
 * 比 FastOutSlowIn 更激进地加速起始段，让"从缩略图弹出"的感觉更明显。
 */
internal val HeroEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

/**
 * Hero 起源信息（缩略图位置+尺寸）。
 * boundsInRoot 来自 PhotoFeedCard 的 onGloballyPositioned → coordinates.boundsInRoot()。
 */
internal data class HeroGraphicsParams(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val alpha: Float,
)

/**
 * 计算 Hero 过渡的 graphicsLayer 参数。
 * progress=0f: 完全在起源位置（缩略图位置+尺寸）
 * progress=1f: 完全在目标位置（全屏）
 *
 * 使用中心缩放锚点 (TransformOrigin 0.5, 0.5)，确保图片从缩略图中心自然展开到全屏。
 *
 * @param origin 起源 bounds（在 root 坐标系）
 * @param containerSize Viewer 容器尺寸（全屏）
 * @param progress 过渡进度 [0f, 1f]
 */
internal fun computeHeroGraphicsParams(
    origin: Rect,
    containerSize: IntSize,
    progress: Float,
): HeroGraphicsParams {
    if (progress >= 1f) {
        return HeroGraphicsParams(1f, 1f, 0f, 0f, 1f)
    }
    if (progress <= 0f) {
        return HeroGraphicsParams(0f, 0f, 0f, 0f, 0f)
    }
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return HeroGraphicsParams(1f, 1f, 0f, 0f, progress)
    }
    val originWidth = origin.width.takeIf { it > 0f }
        ?: return HeroGraphicsParams(1f, 1f, 0f, 0f, progress)
    val originHeight = origin.height.takeIf { it > 0f }
        ?: return HeroGraphicsParams(1f, 1f, 0f, 0f, progress)

    // 中心锚点缩放：从缩略图尺寸等比放大到全屏
    val scaleX = lerpValue(originWidth / containerWidth, 1f, progress)
    val scaleY = lerpValue(originHeight / containerHeight, 1f, progress)

    // 中心锚点位移：保持内容中心与缩略图中心对齐
    // 当 transformOrigin = (0.5, 0.5) 时，内容中心在自身坐标系中始终是 (containerWidth/2, containerHeight/2)
    // 缩放不改变中心点位置，只需平移使内容中心对齐到缩略图中心
    val originCenterX = origin.left + originWidth / 2f
    val originCenterY = origin.top + originHeight / 2f
    val translationX = originCenterX - containerWidth / 2f
    val translationY = originCenterY - containerHeight / 2f

    // 非线性 alpha：前 30% 进度保持高可见度，后段快速淡入
    // 避免线性 alpha 导致的"闪入"感
    val alpha = when {
        progress < 0.15f -> 0f
        progress < 0.4f -> (progress - 0.15f) / 0.25f
        else -> 1f
    }

    return HeroGraphicsParams(scaleX, scaleY, translationX, translationY, alpha)
}

private fun lerpValue(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * 应用 Hero 过渡的 graphicsLayer。
 * 在初始页 PhotoViewerCanvas 上使用，仅当 heroOrigin != null 且 progress < 1f 时。
 *
 * 使用中心缩放锚点 (0.5, 0.5)，图片从缩略图中心自然展开。
 */
internal fun Modifier.heroGraphicsLayer(
    origin: Rect,
    containerSize: IntSize,
    progress: Float,
): Modifier = this.graphicsLayer {
    val params = computeHeroGraphicsParams(origin, containerSize, progress)
    this.scaleX = params.scaleX
    this.scaleY = params.scaleY
    this.translationX = params.translationX
    this.translationY = params.translationY
    this.alpha = params.alpha
    // 中心锚点：从缩略图中心向外展开，而非从左上角
    this.transformOrigin = TransformOrigin(0.5f, 0.5f)
}

package com.example.yingshi.feature.photos

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 记住 RenderEffect 实例（API 31+），低版本返回 null。
 * 使用 DECAL TileMode 确保模糊边界平滑。
 *
 * 非 Composable 场景可直接调用此函数的 Composable 版本。
 */
@Composable
internal fun rememberViewerBlurEffect(blurRadius: Dp): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    return remember(blurRadius) {
        RenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.DECAL,
        ).asComposeRenderEffect()
    }
}

/**
 * Viewer 玻璃表面 Modifier。
 *
 * - API 31+（Android 12+）: 应用 RenderEffect 真实模糊 + Offscreen 合成策略
 * - API 24-30（fallback）: 不做 blur，仅返回 this（alpha 由调用方 Surface 控制）
 *
 * 配合 `Surface(color = viewerSurface.copy(alpha = 0.x))` 使用：
 * - 调用方设置半透明背景色
 * - 本 Modifier 在背景上叠加模糊效果（API 31+）
 *
 * @param blurRadius 模糊半径（建议 scrim=18.dp, capsule=12.dp）
 */
internal fun Modifier.viewerGlassSurface(
    blurRadius: Dp,
): Modifier = composed {
    val renderEffect = rememberViewerBlurEffect(blurRadius)
    if (renderEffect != null) {
        this.graphicsLayer {
            this.renderEffect = renderEffect
            this.compositingStrategy = CompositingStrategy.Offscreen
        }
    } else {
        // fallback: 不做 blur，alpha 由调用方 Surface 控制
        this
    }
}

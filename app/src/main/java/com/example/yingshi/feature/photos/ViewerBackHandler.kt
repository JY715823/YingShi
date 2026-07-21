package com.example.yingshi.feature.photos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal fun applyViewerStatusBarVisibility(view: View, immersive: Boolean) {
    val window = view.context.findActivity()?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view) ?: return
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (immersive) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

@Composable
internal fun ViewerStatusBarEffect(immersive: Boolean = false) {
    val view = LocalView.current
    DisposableEffect(immersive) {
        onDispose {
            applyViewerStatusBarVisibility(view, immersive = false)
        }
    }
    SideEffect {
        applyViewerStatusBarVisibility(view, immersive)
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

package com.example.yingshi.feature.life

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.photos.backendMediaOriginalImageRequest
import com.example.yingshi.feature.photos.resolveBackendMediaUrl
import com.example.yingshi.ui.theme.YingShiTheme

class LifeMediaQuickViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val payload = LifeMediaQuickViewerPayload.fromIntent(intent)
        val launchedFromWidget = intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_WIDGET, false)
        setContent {
            YingShiTheme {
                LifeMediaQuickViewerScreen(
                    payload = payload,
                    onClose = {
                        if (launchedFromWidget) {
                            finishAndRemoveTask()
                        } else {
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        internal const val EXTRA_MEDIA_ID = "life_media_viewer_media_id"
        internal const val EXTRA_MEDIA_TYPE = "life_media_viewer_media_type"
        internal const val EXTRA_MIME_TYPE = "life_media_viewer_mime_type"
        internal const val EXTRA_PREVIEW_URL = "life_media_viewer_preview_url"
        internal const val EXTRA_THUMBNAIL_URL = "life_media_viewer_thumbnail_url"
        internal const val EXTRA_ORIGINAL_URL = "life_media_viewer_original_url"
        internal const val EXTRA_MEDIA_URL = "life_media_viewer_media_url"
        internal const val EXTRA_COVER_URL = "life_media_viewer_cover_url"
        internal const val EXTRA_VIDEO_URL = "life_media_viewer_video_url"
        internal const val EXTRA_LAUNCHED_FROM_WIDGET = "life_media_viewer_from_widget"

        fun intent(context: Context, media: RemoteMedia): Intent {
            return Intent(context, LifeMediaQuickViewerActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_ID, media.mediaId)
                putExtra(EXTRA_MEDIA_TYPE, media.mediaType)
                putExtra(EXTRA_MIME_TYPE, media.mimeType)
                putExtra(EXTRA_PREVIEW_URL, media.previewUrl)
                putExtra(EXTRA_THUMBNAIL_URL, media.thumbnailUrl)
                putExtra(EXTRA_ORIGINAL_URL, media.originalUrl)
                putExtra(EXTRA_MEDIA_URL, media.mediaUrl)
                putExtra(EXTRA_COVER_URL, media.coverUrl)
                putExtra(EXTRA_VIDEO_URL, media.videoUrl)
                putExtra(EXTRA_LAUNCHED_FROM_WIDGET, false)
            }
        }

        fun widgetIntent(context: Context, media: RemoteMedia): Intent {
            return intent(context, media).apply {
                putExtra(EXTRA_LAUNCHED_FROM_WIDGET, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
        }
    }
}

private data class LifeMediaQuickViewerPayload(
    val mediaId: String?,
    val mediaType: String?,
    val mimeType: String?,
    val previewUrl: String?,
    val thumbnailUrl: String?,
    val originalUrl: String?,
    val mediaUrl: String?,
    val coverUrl: String?,
    val videoUrl: String?,
) {
    val isVideo: Boolean
        get() = mediaType.equals("video", ignoreCase = true) ||
            mimeType?.startsWith("video/", ignoreCase = true) == true ||
            !videoUrl.isNullOrBlank()

    val imageUrl: String?
        get() {
            return if (isVideo) {
                firstUsableImageUrl(coverUrl, thumbnailUrl, previewUrl, mediaUrl, originalUrl)
            } else {
                firstUsableImageUrl(originalUrl, mediaUrl, previewUrl, thumbnailUrl, coverUrl)
            }
        }

    companion object {
        fun fromIntent(intent: Intent): LifeMediaQuickViewerPayload {
            return LifeMediaQuickViewerPayload(
                mediaId = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MEDIA_ID),
                mediaType = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MEDIA_TYPE),
                mimeType = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MIME_TYPE),
                previewUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_PREVIEW_URL),
                thumbnailUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_THUMBNAIL_URL),
                originalUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_ORIGINAL_URL),
                mediaUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MEDIA_URL),
                coverUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_COVER_URL),
                videoUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_VIDEO_URL),
            )
        }
    }
}

@Composable
private fun LifeMediaQuickViewerScreen(
    payload: LifeMediaQuickViewerPayload,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val zoomState = remember(payload.mediaId, payload.imageUrl) { LifeQuickViewerZoomState() }
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val resolvedUrl = remember(payload.imageUrl) { resolveBackendMediaUrl(payload.imageUrl) }
    val request = remember(context, resolvedUrl, accessToken) {
        backendMediaOriginalImageRequest(
            context = context,
            url = resolvedUrl,
            accessToken = accessToken,
        )
    }
    val painter = rememberAsyncImagePainter(model = request)
    val painterState = painter.state

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null && painterState !is AsyncImagePainter.State.Error) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .quickViewerZoom(zoomState)
                    .graphicsLayer {
                        scaleX = zoomState.scale
                        scaleY = zoomState.scale
                        translationX = zoomState.offset.x
                        translationY = zoomState.offset.y
                    },
                contentScale = ContentScale.Fit,
            )
        }

        if (painterState is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color.White.copy(alpha = 0.92f),
                strokeWidth = 2.dp,
            )
        }

        if (request == null || painterState is AsyncImagePainter.State.Error) {
            Text(
                text = if (payload.isVideo) "视频暂无预览" else "图片加载失败",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.76f),
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 6.dp)
                .size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

private class LifeQuickViewerZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun transform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(1f, 5.5f)
        scale = nextScale
        offset = if (nextScale <= 1.02f) {
            Offset.Zero
        } else {
            val maxX = containerSize.width * (nextScale - 1f) / 2f
            val maxY = containerSize.height * (nextScale - 1f) / 2f
            Offset(
                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        }
    }
}

private fun Modifier.quickViewerZoom(
    zoomState: LifeQuickViewerZoomState,
): Modifier = pointerInput(zoomState) {
    detectTransformGestures { _, pan, zoom, _ ->
        zoomState.transform(
            zoomChange = zoom,
            panChange = pan,
            containerSize = size,
        )
    }
}

private fun firstUsableImageUrl(vararg urls: String?): String? {
    return urls.firstOrNull { url ->
        val normalized = url?.trim()
        !normalized.isNullOrBlank() && !looksLikeVideoUrl(normalized)
    }?.trim()
}

private fun looksLikeVideoUrl(url: String): Boolean {
    val lower = url.substringBefore('?').lowercase()
    return listOf(".mp4", ".mov", ".m4v", ".webm", ".avi", ".mkv").any(lower::endsWith)
}

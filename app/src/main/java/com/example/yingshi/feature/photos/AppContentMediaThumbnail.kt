package com.example.yingshi.feature.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
internal fun AppContentMediaThumbnail(
    mediaSource: AppContentMediaSource?,
    mediaType: AppMediaType,
    palette: PhotoThumbnailPalette,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    showStatusBadge: Boolean = false,
) {
    val context = LocalContext.current
    val thumbnailUrl = remember(mediaSource, mediaType) {
        mediaSource.thumbnailModelUrl(mediaType)
    }
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val videoPosterUrl = remember(mediaSource, mediaType, thumbnailUrl) {
        if (mediaType == AppMediaType.VIDEO &&
            (thumbnailUrl.isNullOrBlank() || looksLikeVideoSource(thumbnailUrl, mediaSource?.mimeType))
        ) {
            mediaSource.viewerVideoUrl(mediaType) ?: thumbnailUrl
        } else {
            null
        }
    }
    val videoPosterState = if (mediaType == AppMediaType.VIDEO && !videoPosterUrl.isNullOrBlank()) {
        rememberVideoPosterState(
            url = videoPosterUrl,
            accessToken = accessToken,
        ).value
    } else {
        VideoPosterState()
    }
    val imageRequest = remember(context, mediaSource, mediaType, thumbnailUrl, accessToken) {
        thumbnailUrl
            ?.takeUnless {
                mediaType == AppMediaType.VIDEO && looksLikeVideoSource(it, mediaSource?.mimeType)
            }
            ?.let { modelUrl ->
            ImageRequest.Builder(context).apply {
                data(modelUrl)
                size(512)
                precision(Precision.INEXACT)
                crossfade(false)
                memoryCacheKey(sharedPreviewMemoryCacheKey(modelUrl))
                diskCacheKey(sharedMediaDiskCacheKey(modelUrl))
                networkCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
                accessToken
                    ?.takeIf { modelUrl.startsWith("http", ignoreCase = true) }
                    ?.let { token -> addHeader("Authorization", "${RemoteConfig.AUTH_SCHEME} $token") }
            }.build()
        }
    }
    val painter = rememberAsyncImagePainter(
        model = videoPosterState.model ?: imageRequest,
    )
    val painterState = painter.state
    val showImage = (videoPosterState.model != null || imageRequest != null) &&
        painterState !is AsyncImagePainter.State.Error

    Box(
        modifier = modifier.background(
            color = if (showImage) {
                Color.Black
            } else {
                palette.start.copy(alpha = 0.94f)
            },
        ),
    ) {
        if (!showImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.end.copy(alpha = 0.82f)),
            )
        }

        if (showImage) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        if (videoPosterState.isLoading || painterState is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp),
                color = Color.White.copy(alpha = 0.92f),
                strokeWidth = 2.dp,
            )
        }

        if (mediaType == AppMediaType.VIDEO) {
            VideoThumbnailPlayOverlay(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (showStatusBadge) {
            val statusLabel = when {
                mediaType == AppMediaType.VIDEO && videoPosterState.hasError -> "视频封面缺失"
                thumbnailUrl.isNullOrBlank() && mediaType == AppMediaType.VIDEO -> "视频封面缺失"
                thumbnailUrl.isNullOrBlank() -> "暂无缩略图"
                painterState is AsyncImagePainter.State.Error -> "加载失败"
                else -> null
            }
            if (statusLabel != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = Color.Black.copy(alpha = 0.26f),
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnailPlayOverlay(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.32f),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            VideoGlyph(
                state = VideoGlyphState.PLAY,
                tint = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun looksLikeVideoSource(url: String, mimeType: String?): Boolean {
    return mimeType?.startsWith("video/", ignoreCase = true) == true || localLooksLikeVideoUrl(url)
}

private fun localLooksLikeVideoUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').substringBefore('#').lowercase()
    return normalized.endsWith(".mp4") ||
        normalized.endsWith(".mov") ||
        normalized.endsWith(".m4v") ||
        normalized.endsWith(".webm") ||
        normalized.endsWith(".3gp") ||
        normalized.endsWith(".mkv") ||
        normalized.endsWith(".avi")
}

package com.example.yingshi.feature.photos

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun AppContentMediaThumbnail(
    mediaSource: AppContentMediaSource?,
    mediaType: AppMediaType,
    palette: PhotoThumbnailPalette,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    requestSize: Int = 512,
    showLoadingIndicator: Boolean = true,
    showStatusBadge: Boolean = false,
    showVideoPlayOverlay: Boolean = true,
    originalLoadState: OriginalLoadState = OriginalLoadState.NotLoaded,
    onOriginalLoadStateChange: (OriginalLoadState) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = YingShiThemeTokens.colors
    val thumbnailUrl = remember(mediaSource, mediaType) {
        mediaSource.thumbnailModelUrl(mediaType)
    }
    val originalImageUrl = remember(mediaSource, mediaType) {
        mediaSource.viewerOriginalImageUrl(mediaType)
    }
    val shouldRequestOriginalImage = mediaType == AppMediaType.IMAGE &&
        originalLoadState == OriginalLoadState.Loaded &&
        !originalImageUrl.isNullOrBlank()
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val modelUrl = thumbnailUrl?.takeUnless {
        mediaType == AppMediaType.VIDEO && looksLikeVideoSource(it, mediaSource?.mimeType)
    }
    val previewRequest = remember(
        context,
        mediaType,
        modelUrl,
        requestSize,
        accessToken,
    ) {
        backendMediaImageRequest(
            context = context,
            url = modelUrl,
            accessToken = accessToken,
            memoryCacheKey = modelUrl?.let { thumbnailMemoryCacheKey(it, requestSize) },
            placeholderMemoryCacheKey = modelUrl?.let(::sharedPreviewMemoryCacheKey),
            size = requestSize,
        )
    }
    val originalRequest = remember(
        context,
        originalImageUrl,
        shouldRequestOriginalImage,
        accessToken,
    ) {
        if (shouldRequestOriginalImage) {
            backendMediaOriginalImageRequest(
                context = context,
                url = originalImageUrl,
                accessToken = accessToken,
                memoryCacheKey = originalImageUrl?.let(::sharedOriginalMemoryCacheKey),
            )
        } else {
            null
        }
    }
    val previewPainter = rememberAsyncImagePainter(
        model = previewRequest,
    )
    val originalPainter = rememberAsyncImagePainter(model = originalRequest)
    val previewState = previewPainter.state
    val originalState = originalPainter.state
    val videoPosterUrl = if (mediaType == AppMediaType.VIDEO) {
        mediaSource.viewerVideoUrl(mediaType)
            ?: thumbnailUrl?.takeIf { looksLikeVideoSource(it, mediaSource?.mimeType) }
    } else {
        null
    }
    val videoPosterState = if (mediaType == AppMediaType.VIDEO && !videoPosterUrl.isNullOrBlank()) {
        rememberVideoPosterState(
            url = videoPosterUrl,
            accessToken = accessToken,
        ).value
    } else {
        VideoPosterState()
    }
    val directPosterBitmap = videoPosterState.model as? Bitmap
    val videoPosterPainter = rememberAsyncImagePainter(
        model = if (directPosterBitmap == null) {
            videoPosterState.model
        } else {
            null
        },
    )
    val showOriginalImage = mediaType == AppMediaType.IMAGE &&
        originalLoadState == OriginalLoadState.Loaded &&
        originalState is AsyncImagePainter.State.Success
    val showVideoPosterImage = mediaType == AppMediaType.VIDEO && videoPosterState.model != null
    val activePainter = when {
        showOriginalImage -> originalPainter
        showVideoPosterImage && directPosterBitmap == null -> videoPosterPainter
        else -> previewPainter
    }
    val activeState = when {
        showOriginalImage -> originalState
        showVideoPosterImage && directPosterBitmap == null -> videoPosterPainter.state
        else -> previewState
    }
    LaunchedEffect(mediaType, originalImageUrl, originalLoadState, originalState) {
        if (RepositoryProvider.currentMode != RepositoryMode.FAKE) return@LaunchedEffect
        if (mediaType != AppMediaType.IMAGE || originalImageUrl.isNullOrBlank()) return@LaunchedEffect
        when {
            originalLoadState == OriginalLoadState.Loading &&
                originalState is AsyncImagePainter.State.Success -> {
                onOriginalLoadStateChange(OriginalLoadState.Loaded)
            }
            (originalLoadState == OriginalLoadState.Loading ||
                originalLoadState == OriginalLoadState.Loaded) &&
                originalState is AsyncImagePainter.State.Error -> {
                onOriginalLoadStateChange(OriginalLoadState.Failed)
            }
        }
    }
    val showImage = directPosterBitmap != null ||
        (showVideoPosterImage || previewRequest != null || showOriginalImage) &&
        activeState !is AsyncImagePainter.State.Error
    val motion = YingShiThemeTokens.motion
    val imageAlpha = animateFloatAsState(
        targetValue = if (showImage) 1f else 0f,
        animationSpec = tween(motion.mediaFadeMillis, easing = motion.easing),
        label = "appContentThumbnailFade",
    )

    Box(
        modifier = modifier.background(
            color = if (showImage) {
                colors.viewerBackground
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

        if (directPosterBitmap != null) {
            Image(
                bitmap = directPosterBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(imageAlpha.value),
                contentScale = contentScale,
            )
        } else if (showImage) {
            Image(
                painter = activePainter,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(imageAlpha.value),
                contentScale = contentScale,
            )
        }

        if (showLoadingIndicator &&
            directPosterBitmap == null &&
            (videoPosterState.isLoading ||
                previewState is AsyncImagePainter.State.Loading ||
                originalLoadState == OriginalLoadState.Loading)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp),
                color = colors.viewerText.copy(alpha = 0.92f),
                strokeWidth = 2.dp,
            )
        }

        if (mediaType == AppMediaType.VIDEO && showVideoPlayOverlay) {
            VideoThumbnailPlayOverlay(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (showStatusBadge) {
            val statusLabel = when {
                mediaType == AppMediaType.VIDEO && videoPosterState.hasError -> "视频封面缺失"
                thumbnailUrl.isNullOrBlank() && mediaType == AppMediaType.VIDEO -> "视频封面缺失"
                thumbnailUrl.isNullOrBlank() -> "暂无缩略图"
                previewState is AsyncImagePainter.State.Error -> "加载失败"
                else -> null
            }
            if (statusLabel != null &&
                (mediaType != AppMediaType.VIDEO ||
                    (!showVideoPosterImage && modelUrl.isNullOrBlank() && videoPosterState.hasError))
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    color = colors.viewerBackground.copy(alpha = 0.34f),
                    border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.viewerText.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

private fun thumbnailMemoryCacheKey(
    url: String,
    requestSize: Int,
): String {
    return if (requestSize >= 512) {
        sharedPreviewMemoryCacheKey(url)
    } else {
        sharedSizedPreviewMemoryCacheKey(url, requestSize)
    }
}

@Composable
private fun VideoThumbnailPlayOverlay(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.viewerBackground.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            VideoGlyph(
                state = VideoGlyphState.PLAY,
                tint = colors.viewerText.copy(alpha = 0.94f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

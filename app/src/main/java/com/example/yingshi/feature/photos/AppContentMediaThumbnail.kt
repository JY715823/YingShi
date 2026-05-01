package com.example.yingshi.feature.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
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
    val imageRequest = remember(context, thumbnailUrl, accessToken) {
        thumbnailUrl?.let {
            ImageRequest.Builder(context).apply {
                data(it)
                size(768)
                precision(Precision.INEXACT)
                crossfade(false)
                accessToken
                    ?.takeIf { _ -> it.startsWith("http", ignoreCase = true) }
                    ?.let { token -> addHeader("Authorization", "${RemoteConfig.AUTH_SCHEME} $token") }
            }.build()
        }
    }
    val painter = rememberAsyncImagePainter(model = imageRequest)
    val painterState = painter.state
    val showImage = imageRequest != null && painterState !is AsyncImagePainter.State.Error

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(palette.start, palette.end),
            ),
        ),
    ) {
        if (showImage) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )

        if (painterState is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp),
                color = Color.White.copy(alpha = 0.92f),
                strokeWidth = 2.dp,
            )
        }

        if (mediaType == AppMediaType.VIDEO && thumbnailUrl == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.22f),
            ) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VideoGlyph(
                        state = VideoGlyphState.PLAY,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        if (showStatusBadge) {
            val statusLabel = when {
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        YingShiThemeTokens.radius.capsule,
                    ),
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

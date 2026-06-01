package com.example.yingshi.feature.me

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.feature.photos.backendMediaImageRequest
import com.example.yingshi.feature.photos.resolveBackendMediaUrl

@Composable
internal fun ProfileAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolvedAvatarUrl = remember(avatarUrl) {
        resolveBackendMediaUrl(avatarUrl)
    }
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val request = remember(context, resolvedAvatarUrl, accessToken) {
        backendMediaImageRequest(
            context = context,
            url = resolvedAvatarUrl,
            accessToken = accessToken,
            memoryCacheKey = resolvedAvatarUrl?.let { "avatar:$it" },
            size = 256,
        )
    }
    val painter = rememberAsyncImagePainter(model = request)
    val painterState = painter.state
    val showImage = request != null && painterState !is AsyncImagePainter.State.Error
    val avatarLabel = name.firstOrNull()?.uppercaseChar()?.toString() ?: "Y"

    Surface(
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (showImage) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = avatarLabel,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (painterState is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.92f),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

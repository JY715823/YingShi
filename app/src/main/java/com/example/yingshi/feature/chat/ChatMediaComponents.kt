package com.example.yingshi.feature.chat

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.feature.chat.data.ImportedResource
import com.example.yingshi.feature.chat.data.ImportedResourceDetectedFormat
import com.example.yingshi.feature.chat.data.ImportedResourceRenderKind
import com.example.yingshi.feature.chat.data.effectiveRenderKind
import com.example.yingshi.feature.chat.data.isSilkAudio
import com.example.yingshi.ui.theme.YingShiThemeTokens

// ── Media resource card (image / video / sticker) ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaResourceCard(
    resource: ImportedResource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = YingShiThemeTokens.colors
    val displayKind = resource.effectiveRenderKind()
    val size = remember(resource.width, resource.height, displayKind) {
        calculateMediaDisplaySize(resource)
    }
    val previewSizePx = remember(size, density) {
        with(density) {
            maxOf(size.width.roundToPx(), size.height.roundToPx()).coerceAtLeast(96.dp.roundToPx())
        }
    }
    val background = colors.sectionBackground.copy(alpha = 0.54f)
    val contentScale = if (resource.isAnimatedImage) ContentScale.Fit else ContentScale.Crop
    val previewRequest = remember(
        context,
        resource.localFilePath,
        previewSizePx,
        displayKind,
    ) {
        resource.takeIf { displayKind == ImportedResourceRenderKind.IMAGE }
            ?.let { buildImportedPreviewImageRequest(context, it.localFilePath, previewSizePx) }
    }
    val videoPosterState = if (displayKind == ImportedResourceRenderKind.VIDEO) {
        rememberImportedVideoPosterState(resource.localFilePath)
    } else {
        ChatVideoPosterState()
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val previewState = previewPainter.state
    Box(
        modifier = Modifier
            .width(size.width)
            .height(size.height)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // #6 Image loading shimmer
        if (displayKind == ImportedResourceRenderKind.IMAGE && previewState is AsyncImagePainter.State.Loading) {
            val shimmerColors = listOf(
                background,
                background.copy(alpha = 0.4f),
                background,
            )
            val transition = rememberInfiniteTransition(label = "shimmer")
            val translateAnim by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmerOffset",
            )
            val shimmerBrush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translateAnim - 500f, translateAnim - 500f),
                end = Offset(translateAnim, translateAnim),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(shimmerBrush),
            )
        }

        when (displayKind) {
            ImportedResourceRenderKind.IMAGE -> {
                if (previewState is AsyncImagePainter.State.Error) {
                    MissingMediaPlaceholder(
                        label = if (resource.isAnimatedImage) "动图当前不可用" else "图片当前不可用",
                        icon = Icons.Default.Image,
                        dark = false,
                    )
                } else {
                    Image(
                        painter = previewPainter,
                        contentDescription = resource.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
            }

            ImportedResourceRenderKind.VIDEO -> {
                when (val posterModel = videoPosterState.model) {
                    is Bitmap -> Image(
                        bitmap = posterModel.asImageBitmap(),
                        contentDescription = resource.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    null -> Unit

                    else -> AsyncImage(
                        model = posterModel,
                        contentDescription = resource.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            else -> Unit
        }
        when (displayKind) {
            ImportedResourceRenderKind.VIDEO -> {
                if (videoPosterState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.viewerAccent.copy(alpha = 0.94f),
                    )
                }
                Surface(
                    color = colors.viewerBackground.copy(alpha = 0.50f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.14f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = colors.viewerText.copy(alpha = 0.94f),
                        modifier = Modifier.padding(12.dp),
                    )
                }
                if (videoPosterState.hasError && videoPosterState.model == null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = colors.viewerBackground.copy(alpha = 0.34f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.viewerAccent.copy(alpha = 0.12f)),
                    ) {
                        Text(
                            text = "视频封面暂不可用",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.viewerText.copy(alpha = 0.92f),
                        )
                    }
                }
            }

            ImportedResourceRenderKind.IMAGE -> {
                if (!resource.isAnimatedImage) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = colors.viewerText.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                    )
                }
            }

            else -> Unit
        }
    }
}

// ── Audio message card ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioMessageCard(
    resource: ImportedResource,
    audioPlayer: ChatAudioPlayerState,
    onLongPress: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val statusText = when {
        audioPlayer.isDecoding(resource.localFilePath) -> "正在解码语音"
        audioPlayer.isPreparing(resource.localFilePath) -> "正在加载语音"
        audioPlayer.isPlaying(resource.localFilePath) -> "正在播放语音"
        audioPlayer.hasError(resource.localFilePath) -> "播放失败，点按重试"
        else -> "语音消息"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { audioPlayer.toggle(resource) },
                onLongClick = onLongPress,
            ),
        color = colors.sectionBackground.copy(alpha = 0.58f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = colors.primaryContainer.copy(alpha = 0.66f),
            ) {
                if (audioPlayer.isDecoding(resource.localFilePath) || audioPlayer.isPreparing(resource.localFilePath)) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.titleAccent,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = colors.titleAccent,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = buildString {
                        append(resource.durationSeconds?.let(::formatDurationSeconds) ?: "点击播放")
                        if (resource.isSilkAudio()) {
                            append(" · SILK")
                        } else if (resource.detectedFormat == ImportedResourceDetectedFormat.AMR) {
                            append(" · AMR")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

// ── File message card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileMessageCard(
    resource: ImportedResource,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val fileLabel = normalizeFileTypeLabel(resource)
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        color = colors.sectionBackground.copy(alpha = 0.58f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPdfResource(resource)) Icons.Default.PictureAsPdf else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = colors.titleAccent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resource.originalFileName ?: resource.storedFileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(fileLabel)
                        resource.sizeBytes?.let {
                            append(" · ")
                            append(android.text.format.Formatter.formatShortFileSize(context, it))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

// ── JSON card message ─────────────────────────────────────────────────────────

@Composable
internal fun JsonCardMessage(
    title: String?,
    summary: String?,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.sectionBackground.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title ?: "分享卡片",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = summary ?: "没有更多摘要信息",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

// ── Call summary message ──────────────────────────────────────────────────────

@Composable
internal fun CallSummaryMessage(
    summary: String?,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.sectionBackground.copy(alpha = 0.58f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colors.titleAccent,
            )
            Text(
                text = summary ?: "通话记录",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
    }
}

// ── Unknown message card ──────────────────────────────────────────────────────

@Composable
internal fun UnknownMessageCard(
    text: String,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.sectionBackground.copy(alpha = 0.58f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

// ── Video poster state (local to this file) ───────────────────────────────────

private data class ChatVideoPosterState(
    val model: Any? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

@Composable
private fun rememberImportedVideoPosterState(
    absolutePath: String,
): ChatVideoPosterState {
    val context = LocalContext.current
    val cacheKey = remember(absolutePath) {
        importedLocalMediaCacheKey(java.io.File(absolutePath))
    }
    return androidx.compose.runtime.produceState(
        initialValue = cachedImportedVideoPosterModel(context, cacheKey)?.let { cached ->
            ChatVideoPosterState(model = cached)
        } ?: ChatVideoPosterState(isLoading = absolutePath.isNotBlank()),
        context,
        absolutePath,
        cacheKey,
    ) {
        if (absolutePath.isBlank()) {
            value = ChatVideoPosterState()
            return@produceState
        }
        cachedImportedVideoPosterModel(context, cacheKey)?.let { cached ->
            value = ChatVideoPosterState(model = cached)
            return@produceState
        }
        val posterFile = ensureImportedVideoPosterFile(
            context = context,
            absolutePath = absolutePath,
            cacheKey = cacheKey,
        )
        value = if (posterFile != null) {
            ChatVideoPosterState(model = cachedImportedVideoPosterModel(context, cacheKey) ?: posterFile)
        } else {
            ChatVideoPosterState(hasError = true)
        }
    }.value
}

private fun cachedImportedVideoPosterModel(
    context: android.content.Context,
    cacheKey: String,
): Any? {
    importedVideoPosterMemoryCache[cacheKey]?.let { return it }
    val file = importedVideoPosterFile(context, cacheKey).takeIf { it.exists() && it.length() > 0L } ?: return null
    val bitmap = runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    if (bitmap != null) {
        importedVideoPosterMemoryCache[cacheKey] = bitmap
        return bitmap
    }
    return file
}

private suspend fun ensureImportedVideoPosterFile(
    context: android.content.Context,
    absolutePath: String,
    cacheKey: String,
): java.io.File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val targetFile = importedVideoPosterFile(context, cacheKey)
    if (targetFile.exists() && targetFile.length() > 0L) {
        return@withContext targetFile
    }
    val lock = importedVideoPosterLocks.getOrPut(targetFile.absolutePath) { Any() }
    synchronized(lock) {
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@synchronized targetFile
        }
        targetFile.parentFile?.mkdirs()
        val bitmap = extractImportedVideoPosterBitmap(absolutePath) ?: return@synchronized null
        runCatching {
            java.io.FileOutputStream(targetFile).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, output)
            }
            targetFile.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull().also {
            if (!bitmap.isRecycled) {
                runCatching { bitmap.recycle() }
            }
        }
    }
}

private fun extractImportedVideoPosterBitmap(
    absolutePath: String,
): android.graphics.Bitmap? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(absolutePath)
        retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun importedVideoPosterFile(
    context: android.content.Context,
    cacheKey: String,
): java.io.File {
    return context.cacheDir.resolve("chat-video-posters").resolve("$cacheKey.jpg")
}

private val importedVideoPosterLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
private val importedVideoPosterMemoryCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

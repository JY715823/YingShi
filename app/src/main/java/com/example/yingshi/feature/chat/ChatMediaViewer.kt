package com.example.yingshi.feature.chat

import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.yingshi.feature.chat.data.ImportedResourceType
import com.example.yingshi.feature.chat.data.ImportedViewerMode
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.io.File

// ── Load state ────────────────────────────────────────────────────────────────

private enum class ChatOriginalLoadState {
    NotLoaded,
    Loading,
    Loaded,
    Failed,
}

private fun ChatOriginalLoadState.actionLabel(): String {
    return when (this) {
        ChatOriginalLoadState.NotLoaded -> "加载原图"
        ChatOriginalLoadState.Loading -> "原图加载中"
        ChatOriginalLoadState.Loaded -> "已加载原图"
        ChatOriginalLoadState.Failed -> "重试原图"
    }
}

// ── Zoom state ────────────────────────────────────────────────────────────────

private const val MinChatViewerScale = 1f
private const val MaxChatViewerScale = 4f
private const val ChatViewerResetScale = 1.02f

private class ChatViewerZoomState {
    var scale by mutableFloatStateOf(MinChatViewerScale)
        private set
    var offset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > ChatViewerResetScale

    fun reset() {
        scale = MinChatViewerScale
        offset = androidx.compose.ui.geometry.Offset.Zero
    }

    fun applyTransform(
        zoomChange: Float,
        panChange: androidx.compose.ui.geometry.Offset,
        containerSize: IntSize,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(MinChatViewerScale, MaxChatViewerScale)
        if (nextScale <= ChatViewerResetScale) {
            reset()
            return
        }
        scale = nextScale
        val maxX = ((containerSize.width * nextScale - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * nextScale - containerSize.height) / 2f).coerceAtLeast(0f)
        offset = androidx.compose.ui.geometry.Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
        )
    }
}

// ── Media viewer (pager) ──────────────────────────────────────────────────────

@Composable
internal fun ImportedChatMediaViewer(
    items: List<ChatViewerMediaItem>,
    initialIndex: Int,
    viewerMode: ImportedViewerMode,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val originalLoadStates = remember(items) { mutableStateMapOf<String, ChatOriginalLoadState>() }
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val background = colors.viewerBackground
    val foreground = colors.viewerText
    val secondaryForeground = colors.viewerTextSecondary
    val currentItem = items.getOrNull(pagerState.currentPage)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (item.type) {
                    ImportedResourceType.IMAGE -> ChatViewerImageCanvas(
                        item = item,
                        originalLoadState = originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded,
                        onOriginalLoadStateChange = { stableKey, nextState ->
                            originalLoadStates[stableKey] = nextState
                        },
                        modifier = Modifier.fillMaxSize(),
                        background = background,
                    )

                    ImportedResourceType.VIDEO -> LocalVideoPlayer(
                        path = item.path,
                        mimeType = item.mimeType,
                    )

                    else -> Unit
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = foreground)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground,
                )
                Text(
                    text = formatTimelineTime(items[pagerState.currentPage].timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryForeground,
                )
            }
        }

        currentItem?.takeIf { it.supportsOriginal }?.let { item ->
            ViewerCapsuleButton(
                text = (originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded).actionLabel(),
                emphasized = (originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded) == ChatOriginalLoadState.Loaded,
                enabled = (originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded) != ChatOriginalLoadState.Loading,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 20.dp),
                onClick = {
                    val currentState = originalLoadStates[item.stableKey] ?: ChatOriginalLoadState.NotLoaded
                    if (currentState == ChatOriginalLoadState.Loading || currentState == ChatOriginalLoadState.Loaded) {
                        return@ViewerCapsuleButton
                    }
                    originalLoadStates[item.stableKey] = ChatOriginalLoadState.Loading
                },
            )
        }
    }
}

// ── Image canvas (preview + original) ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatViewerImageCanvas(
    item: ChatViewerMediaItem,
    originalLoadState: ChatOriginalLoadState,
    onOriginalLoadStateChange: (String, ChatOriginalLoadState) -> Unit,
    modifier: Modifier = Modifier,
    background: Color,
) {
    val context = LocalContext.current
    val colors = YingShiThemeTokens.colors
    val displayMetrics = context.resources.displayMetrics
    val previewSizePx = remember(displayMetrics.widthPixels, displayMetrics.heightPixels) {
        maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceAtLeast(720)
    }
    val previewRequest = remember(context, item.path, previewSizePx) {
        buildImportedPreviewImageRequest(
            context = context,
            absolutePath = item.path,
            requestSizePx = previewSizePx,
        )
    }
    val shouldRequestOriginal = originalLoadState == ChatOriginalLoadState.Loading ||
        originalLoadState == ChatOriginalLoadState.Loaded
    val originalRequest = remember(context, item.path, shouldRequestOriginal) {
        if (shouldRequestOriginal) {
            buildImportedOriginalImageRequest(
                context = context,
                absolutePath = item.path,
            )
        } else {
            null
        }
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val originalPainter = rememberAsyncImagePainter(model = originalRequest)
    val previewState = previewPainter.state
    val originalState = originalPainter.state
    val showOriginal = originalLoadState == ChatOriginalLoadState.Loaded &&
        originalState is AsyncImagePainter.State.Success
    val showPreview = previewState !is AsyncImagePainter.State.Error && !showOriginal
    val isTallImage = remember(item.width, item.height) {
        val width = item.width?.toFloat()?.takeIf { it > 0f } ?: 0f
        val height = item.height?.toFloat()?.takeIf { it > 0f } ?: 0f
        width > 0f && height / width >= 2.2f
    }
    // #10 Save to album - long press menu state
    var showSaveMenu by remember { mutableStateOf(false) }
    var menuAnchorPosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.stableKey, originalLoadState, originalState) {
        when {
            originalLoadState == ChatOriginalLoadState.Loading &&
                originalState is AsyncImagePainter.State.Success -> {
                onOriginalLoadStateChange(item.stableKey, ChatOriginalLoadState.Loaded)
            }

            originalLoadState == ChatOriginalLoadState.Loading &&
                originalState is AsyncImagePainter.State.Error -> {
                onOriginalLoadStateChange(item.stableKey, ChatOriginalLoadState.Failed)
            }
        }
    }

    Box(
        modifier = modifier
            .background(background)
            .combinedClickable(
                onClick = {},
                onLongClick = { showSaveMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showPreview) {
            ZoomablePainterImage(
                key = item.stableKey,
                painter = previewPainter,
                modifier = Modifier.fillMaxSize(),
                background = background,
                alignment = if (isTallImage) Alignment.TopCenter else Alignment.Center,
            )
        }

        if (showOriginal) {
            ZoomablePainterImage(
                key = item.stableKey,
                painter = originalPainter,
                modifier = Modifier.fillMaxSize(),
                background = background,
                alignment = if (isTallImage) Alignment.TopCenter else Alignment.Center,
            )
        }

        if (previewState is AsyncImagePainter.State.Loading && !showOriginal) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.viewerAccent.copy(alpha = 0.90f),
                strokeWidth = 2.dp,
            )
        }

        if (!showPreview && !showOriginal) {
            MissingMediaPlaceholder(
                label = "图片当前不可用",
                icon = Icons.Default.Image,
                dark = true,
            )
        }

        // #10 Save to album dropdown menu
        DropdownMenu(
            expanded = showSaveMenu,
            onDismissRequest = { showSaveMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("保存到相册") },
                leadingIcon = {
                    Icon(Icons.Default.SaveAlt, contentDescription = null)
                },
                onClick = {
                    showSaveMenu = false
                    saveImageToAlbum(context, item.path)
                },
            )
        }
    }
}

// ── Zoomable image ────────────────────────────────────────────────────────────

@Composable
private fun ZoomablePainterImage(
    key: String,
    painter: Painter,
    modifier: Modifier = Modifier,
    background: Color,
    alignment: Alignment = Alignment.Center,
) {
    val zoomState = remember(key) { ChatViewerZoomState() }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier
            .background(background)
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }
            .chatViewerZoomGesture(zoomState),
        alignment = alignment,
        contentScale = ContentScale.Fit,
    )
}

// ── Local video player ────────────────────────────────────────────────────────

@Composable
private fun LocalVideoPlayer(
    path: String,
    mimeType: String?,
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(path)))
                    .setMimeType(mimeType)
                    .build(),
            )
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.player = player
        },
    )
}

// ── Viewer capsule button ─────────────────────────────────────────────────────

@Composable
private fun ViewerCapsuleButton(
    text: String,
    emphasized: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = RoundedCornerShape(999.dp),
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) {
            colors.viewerAccent.copy(alpha = 0.90f)
        } else {
            colors.viewerSurface.copy(alpha = 0.88f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled || emphasized) {
                colors.viewerText
            } else {
                colors.viewerTextSecondary
            },
        )
    }
}

// ── Zoom gesture modifier ─────────────────────────────────────────────────────

private fun Modifier.chatViewerZoomGesture(
    zoomState: ChatViewerZoomState,
): Modifier = pointerInput(zoomState) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            val activeChanges = event.changes.filter { it.pressed }
            if (activeChanges.isEmpty()) break

            if (activeChanges.size >= 2) {
                val currentCentroid = activeChanges.chatViewerCentroid(usePrevious = false)
                val previousCentroid = activeChanges.chatViewerCentroid(usePrevious = true)
                val currentDistance = activeChanges.chatViewerAverageDistanceTo(
                    centroid = currentCentroid,
                    usePrevious = false,
                )
                val previousDistance = activeChanges.chatViewerAverageDistanceTo(
                    centroid = previousCentroid,
                    usePrevious = true,
                )
                val zoomChange = if (previousDistance > 0f) {
                    currentDistance / previousDistance
                } else {
                    MinChatViewerScale
                }
                zoomState.applyTransform(
                    zoomChange = zoomChange,
                    panChange = currentCentroid - previousCentroid,
                    containerSize = size,
                )
                activeChanges.forEach { it.consume() }
            } else if (zoomState.isZoomed) {
                val change = activeChanges.first()
                zoomState.applyTransform(
                    zoomChange = 1f,
                    panChange = change.positionChange(),
                    containerSize = size,
                )
                activeChanges.forEach { it.consume() }
            }
        }
    }
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.chatViewerCentroid(
    usePrevious: Boolean,
): androidx.compose.ui.geometry.Offset {
    val total = fold(androidx.compose.ui.geometry.Offset.Zero) { sum, change ->
        sum + if (usePrevious) change.previousPosition else change.position
    }
    return total / size.toFloat()
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.chatViewerAverageDistanceTo(
    centroid: androidx.compose.ui.geometry.Offset,
    usePrevious: Boolean,
): Float {
    return sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / size
}

// #10 Save image to album using MediaStore
private fun saveImageToAlbum(context: android.content.Context, imagePath: String) {
    val file = java.io.File(imagePath)
    if (!file.exists()) {
        Toast.makeText(context, "图片文件不存在", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/${file.extension.ifBlank { "jpeg" }}")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YingShi")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

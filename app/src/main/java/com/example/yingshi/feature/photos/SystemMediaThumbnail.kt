package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SystemMediaPreviewFrameTimesUs = listOf(
    500_000L,
    1_000_000L,
    2_000_000L,
    5_000_000L,
)
private val SystemMediaThumbnailSize = Size(640, 640)

@Composable
internal fun rememberSystemVideoThumbnail(
    context: Context,
    uri: Uri,
): Bitmap? {
    val appContext = remember(context) { context.applicationContext }
    return produceState<Bitmap?>(
        initialValue = SystemMediaVideoThumbnailCache.bitmaps[uri.toString()],
        key1 = appContext,
        key2 = uri,
    ) {
        SystemMediaVideoThumbnailCache.bitmaps[uri.toString()]?.let { cached ->
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            loadSystemVideoFrame(appContext, uri)?.also { bitmap ->
                SystemMediaVideoThumbnailCache.bitmaps[uri.toString()] = bitmap
            }
        }
    }.value
}

internal fun loadSystemVideoFrame(
    context: Context,
    uri: Uri,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val thumbnail = runCatching {
            context.contentResolver.loadThumbnail(uri, SystemMediaThumbnailSize, null)
        }.getOrNull()
        if (thumbnail != null) return thumbnail
    }

    val retriever = MediaMetadataRetriever()
    val bitmap = runCatching {
        retriever.setDataSource(context, uri)
        SystemMediaPreviewFrameTimesUs.firstNotNullOfOrNull { timeUs ->
            retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
        } ?: retriever.frameAtTime
    }.getOrNull()
    runCatching { retriever.release() }
    if (bitmap != null) return bitmap

    return null
}

internal fun resolveSystemVideoDimensions(
    context: Context,
    uri: Uri,
): Pair<Int?, Int?> {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        retriever.setDataSource(context, uri)
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        if (rotation == 90 || rotation == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }
    }.getOrElse {
        null to null
    }.also {
        runCatching { retriever.release() }
    }
}

internal fun Bitmap.toComposeBitmap() = asImageBitmap()

private object SystemMediaVideoThumbnailCache {
    val bitmaps = ConcurrentHashMap<String, Bitmap>()
}

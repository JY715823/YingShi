package com.example.yingshi.feature.photos

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.util.Calendar
import java.util.Locale

interface SystemMediaRepository {
    fun peekCachedMedia(): List<SystemMediaItem>?
    suspend fun loadMedia(forceRefresh: Boolean = false): List<SystemMediaItem>
}

class LocalSystemMediaRepository(
    context: Context,
    private val appContext: Context = context.applicationContext,
    private val dataSource: SystemMediaDataSource = MediaStoreSystemMediaDataSource(
        context = context.applicationContext,
    ),
) : SystemMediaRepository {

    override fun peekCachedMedia(): List<SystemMediaItem>? {
        return LocalSystemMediaQueryCache.items
    }

    override suspend fun loadMedia(forceRefresh: Boolean): List<SystemMediaItem> {
        if (!hasSystemMediaReadAccess(appContext)) {
            throw SecurityException("Missing system media permission.")
        }
        if (!forceRefresh) {
            LocalSystemMediaQueryCache.items?.let { return it }
        }
        return dataSource.queryMedia()
            .sortedByDescending { it.displayTimeMillis }
            .also { items ->
                LocalSystemMediaQueryCache.items = items
            }
    }
}

interface SystemMediaDataSource {
    suspend fun queryMedia(): List<SystemMediaItem>
}

private object LocalSystemMediaQueryCache {
    var items: List<SystemMediaItem>? = null
}

class MediaStoreSystemMediaDataSource(
    context: Context,
) : SystemMediaDataSource {
    private val contentResolver = context.contentResolver

    override suspend fun queryMedia(): List<SystemMediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            "datetaken",
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        val selection = buildString {
            append("(")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE)
            append("=")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
            append(" OR ")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE)
            append("=")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            append(")")
        }
        val sortOrder = "datetaken DESC, ${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val items = mutableListOf<SystemMediaItem>()

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val dateTakenIndex = cursor.getColumnIndex("datetaken")
            val bucketNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idIndex)
                val type = when (cursor.getInt(mediaTypeIndex)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> SystemMediaType.VIDEO
                    else -> SystemMediaType.IMAGE
                }
                val mimeType = cursor.getStringOrEmpty(mimeTypeIndex)
                val displayName = cursor.getStringOrEmpty(displayNameIndex).ifBlank {
                    "未命名媒体"
                }
                val bucketName = cursor.getStringOrNull(bucketNameIndex)
                val width = cursor.getIntOrNull(widthIndex)
                val height = cursor.getIntOrNull(heightIndex)
                val displayTimeMillis = resolveTimeMillis(
                    dateTakenMillis = cursor.getLongOrNull(dateTakenIndex),
                    dateModifiedSeconds = cursor.getLongOrNull(dateModifiedIndex),
                )
                val dateParts = displayTimeMillis.toDateParts()

                items += SystemMediaItem(
                    id = "${type.name.lowercase(Locale.ROOT)}-$mediaStoreId",
                    mediaStoreId = mediaStoreId,
                    uri = buildContentUri(type, mediaStoreId),
                    type = type,
                    mimeType = mimeType,
                    displayName = displayName,
                    bucketName = bucketName,
                    displayTimeMillis = displayTimeMillis,
                    displayYear = dateParts.year,
                    displayMonth = dateParts.month,
                    displayDay = dateParts.day,
                    width = width,
                    height = height,
                    aspectRatio = resolveAspectRatio(width, height),
                    palette = paletteFor(mediaStoreId),
                    linkedPostIds = emptyList(),
                )
            }
        }

        return items
    }

    private fun buildContentUri(
        type: SystemMediaType,
        mediaStoreId: Long,
    ): Uri {
        return when (type) {
            SystemMediaType.IMAGE -> ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaStoreId,
            )
            SystemMediaType.VIDEO -> ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                mediaStoreId,
            )
        }
    }

    private fun resolveTimeMillis(
        dateTakenMillis: Long?,
        dateModifiedSeconds: Long?,
    ): Long {
        return when {
            dateTakenMillis != null && dateTakenMillis > 0L -> dateTakenMillis
            dateModifiedSeconds != null && dateModifiedSeconds > 0L -> dateModifiedSeconds * 1000L
            else -> System.currentTimeMillis()
        }
    }

    private fun resolveAspectRatio(width: Int?, height: Int?): Float {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return 1f
        }
        return (width.toFloat() / height.toFloat()).coerceIn(0.56f, 1.8f)
    }

    private fun paletteFor(mediaStoreId: Long): PhotoThumbnailPalette {
        val palettes = listOf(
            PhotoThumbnailPalette(
                start = Color(0xFFB8D8F8),
                end = Color(0xFF7EA6DF),
                accent = Color(0xFFE8F2FF),
            ),
            PhotoThumbnailPalette(
                start = Color(0xFFF5D2C3),
                end = Color(0xFFE7A08D),
                accent = Color(0xFFFFF0E8),
            ),
            PhotoThumbnailPalette(
                start = Color(0xFFCFE5B9),
                end = Color(0xFF84B38A),
                accent = Color(0xFFEFF8E1),
            ),
            PhotoThumbnailPalette(
                start = Color(0xFFD8D0F2),
                end = Color(0xFF8FA0D8),
                accent = Color(0xFFF0EDFF),
            ),
            PhotoThumbnailPalette(
                start = Color(0xFFE7CFB4),
                end = Color(0xFFB98B63),
                accent = Color(0xFFF7E8D4),
            ),
            PhotoThumbnailPalette(
                start = Color(0xFFC5D1DA),
                end = Color(0xFF8095A7),
                accent = Color(0xFFE7F0F6),
            ),
        )
        return palettes[(mediaStoreId % palettes.size).toInt()]
    }
}

private data class SystemMediaDateParts(
    val year: Int,
    val month: Int,
    val day: Int,
)

private fun Long.toDateParts(): SystemMediaDateParts {
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = this@toDateParts
    }
    return SystemMediaDateParts(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
    return if (index < 0 || isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getStringOrEmpty(index: Int): String {
    return if (index < 0 || isNull(index)) "" else getString(index)
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? {
    return if (index < 0 || isNull(index)) null else getLong(index)
}

private fun android.database.Cursor.getIntOrNull(index: Int): Int? {
    return if (index < 0 || isNull(index)) null else getInt(index)
}

fun requiredSystemMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

fun hasSystemMediaReadAccess(context: Context): Boolean {
    return requiredSystemMediaPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

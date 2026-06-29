package com.example.yingshi.feature.photos

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

interface SystemMediaRepository {
    fun peekCachedMedia(): List<SystemMediaItem>?
    suspend fun loadMedia(forceRefresh: Boolean = false): List<SystemMediaItem>
}

internal fun preloadSystemMediaCache(context: Context) {
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching {
            if (hasSystemMediaReadAccess(appContext)) {
                LocalSystemMediaRepository(appContext).loadMedia(forceRefresh = true)
            }
        }
    }
}

internal fun invalidateSystemMediaMetadataCache(context: Context? = null) {
    LocalSystemMediaQueryCache.invalidate(context)
}

class LocalSystemMediaRepository(
    context: Context,
    private val appContext: Context = context.applicationContext,
    private val dataSource: SystemMediaDataSource = MediaStoreSystemMediaDataSource(
        context = context.applicationContext,
    ),
) : SystemMediaRepository {

    override fun peekCachedMedia(): List<SystemMediaItem>? {
        LocalSystemMediaBridgeRepository.warmPersistentImportOverlay(appContext)
        return LocalSystemMediaQueryCache.peek(appContext)
    }

    override suspend fun loadMedia(forceRefresh: Boolean): List<SystemMediaItem> {
        LocalSystemMediaBridgeRepository.warmPersistentImportOverlay(appContext)
        if (!hasSystemMediaReadAccess(appContext)) {
            throw SecurityException("Missing system media permission.")
        }
        if (!forceRefresh) {
            LocalSystemMediaQueryCache.peek(appContext)?.let { return it }
        }
        val localItems = dataSource.queryMedia()
            .sortedByDescending { it.displayTimeMillis }
        return localItems
            .withAppImportStatus(allowRemoteRefresh = NetworkConnectivityMonitor.currentState.isConnected)
            .also { items ->
                LocalSystemMediaQueryCache.store(appContext, items)
            }
    }

    private suspend fun List<SystemMediaItem>.withAppImportStatus(allowRemoteRefresh: Boolean): List<SystemMediaItem> {
        if (
            isEmpty() ||
            RepositoryProvider.currentMode != RepositoryMode.REAL ||
            !AuthSessionManager.isLoggedIn ||
            !allowRemoteRefresh
        ) {
            return this
        }
        val itemsByFingerprint = flatMap { item ->
            listOf(
                item.stableImportSourceFingerprint(),
                item.stableImportMetadataSourceFingerprint(),
            )
        }.distinct()
        val statuses = mutableMapOf<String, Pair<String, List<String>>>()
        var anyBatchSucceeded = false
        itemsByFingerprint.chunked(SystemMediaImportStatusBatchSize).forEach { fingerprints ->
            when (val result = RepositoryProvider.mediaRepository.getImportStatus(fingerprints)) {
                is ApiResult.Success -> {
                    anyBatchSucceeded = true
                    result.data.forEach { status ->
                        statuses[status.sourceFingerprint] = status.mediaId to status.smallAlbumIds
                    }
                }
                is ApiResult.Error,
                ApiResult.Loading,
                -> return@forEach
            }
        }
        if (!anyBatchSucceeded) {
            return this
        }
        return map { item ->
            val status = statuses[item.stableImportSourceFingerprint()]
                ?: statuses[item.stableImportMetadataSourceFingerprint()]
            if (status == null) {
                LocalSystemMediaBridgeRepository.forgetImportStatus(item)
                return@map item.copy(
                    importedAppMediaId = null,
                    linkedSmallAlbumIds = emptyList(),
                    linkedPostIds = emptyList(),
                )
            }
            LocalSystemMediaBridgeRepository.rememberImportStatus(
                item = item,
                appMediaId = status.first,
                smallAlbumIds = status.second,
            )
            item.copy(
                importedAppMediaId = status.first,
                linkedSmallAlbumIds = status.second,
                linkedPostIds = status.second,
            )
        }
    }
}

private const val SystemMediaImportStatusBatchSize = 500

interface SystemMediaDataSource {
    suspend fun queryMedia(): List<SystemMediaItem>
}

private object LocalSystemMediaQueryCache {
    private const val PreferencesName = "system_media_metadata_cache"
    private const val ItemsKey = "items_json"
    private const val MaxCachedItems = 2000

    private var memoryItems: List<SystemMediaItem>? = null

    fun peek(context: Context): List<SystemMediaItem>? {
        memoryItems?.let { return it }
        val restoredItems = readFromDisk(context)
        memoryItems = restoredItems
        return restoredItems
    }

    fun store(context: Context, items: List<SystemMediaItem>) {
        val normalizedItems = items.take(MaxCachedItems)
        memoryItems = normalizedItems
        writeToDisk(context, normalizedItems)
    }

    fun invalidate(context: Context? = null) {
        memoryItems = null
        context?.let {
            preferences(it).edit().remove(ItemsKey).apply()
        }
    }

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    }

    private fun readFromDisk(context: Context): List<SystemMediaItem>? {
        val raw = preferences(context).getString(ItemsKey, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toSystemMediaItemOrNull()?.let(::add)
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun writeToDisk(
        context: Context,
        items: List<SystemMediaItem>,
    ) {
        val array = JSONArray()
        items.forEach { item -> array.put(item.toCacheJson()) }
        preferences(context).edit().putString(ItemsKey, array.toString()).apply()
    }

    private fun SystemMediaItem.toCacheJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("mediaStoreId", mediaStoreId)
            .put("uri", uri.toString())
            .put("type", type.name)
            .put("mimeType", mimeType)
            .put("displayName", displayName)
            .put("bucketName", bucketName)
            .put("displayTimeMillis", displayTimeMillis)
            .put("capturedAtMillis", capturedAtMillis)
            .put("fileModifiedAtMillis", fileModifiedAtMillis)
            .put("displayTimeSource", displayTimeSource)
            .put("displayYear", displayYear)
            .put("displayMonth", displayMonth)
            .put("displayDay", displayDay)
            .put("width", width)
            .put("height", height)
            .put("aspectRatio", aspectRatio.toDouble())
            .put("importedAppMediaId", importedAppMediaId)
            .put("linkedSmallAlbumIds", JSONArray(linkedSmallAlbumIds))
            .put("videoDurationMillis", videoDurationMillis)
            .put("uploadedByUserId", uploadedByUserId)
            .put("sizeBytes", sizeBytes)
    }

    private fun JSONObject.toSystemMediaItemOrNull(): SystemMediaItem? {
        val type = runCatching { SystemMediaType.valueOf(optString("type")) }.getOrNull()
            ?: return null
        val mediaStoreId = optLong("mediaStoreId", -1L).takeIf { it >= 0L } ?: return null
        val linkedSmallAlbumIds = optJSONArray("linkedSmallAlbumIds")
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            .orEmpty()
        return SystemMediaItem(
            id = optString("id").takeIf { it.isNotBlank() } ?: "${type.name.lowercase(Locale.ROOT)}-$mediaStoreId",
            mediaStoreId = mediaStoreId,
            uri = Uri.parse(optString("uri")),
            type = type,
            mimeType = optString("mimeType"),
            displayName = optString("displayName").ifBlank { "未命名媒体" },
            bucketName = optNullableString("bucketName"),
            displayTimeMillis = optLong("displayTimeMillis", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
            capturedAtMillis = optNullableLong("capturedAtMillis"),
            fileModifiedAtMillis = optNullableLong("fileModifiedAtMillis"),
            displayTimeSource = optString("displayTimeSource").ifBlank { DisplayTimeSourceImported },
            displayYear = optInt("displayYear", 1970),
            displayMonth = optInt("displayMonth", 1),
            displayDay = optInt("displayDay", 1),
            width = optNullableInt("width"),
            height = optNullableInt("height"),
            aspectRatio = optDouble("aspectRatio", 1.0).toFloat().coerceIn(0.56f, 1.8f),
            palette = paletteForSystemMediaId(mediaStoreId),
            importedAppMediaId = optNullableString("importedAppMediaId"),
            linkedSmallAlbumIds = linkedSmallAlbumIds,
            linkedPostIds = linkedSmallAlbumIds,
            videoDurationMillis = optNullableLong("videoDurationMillis"),
            uploadedByUserId = optNullableString("uploadedByUserId"),
            sizeBytes = optNullableLong("sizeBytes"),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return optLong(name).takeIf { it > 0L }
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name).takeIf { it > 0 }
    }
}

class MediaStoreSystemMediaDataSource(
    context: Context,
) : SystemMediaDataSource {
    private val appContext = context.applicationContext
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
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
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
            val durationIndex = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

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
                val rawWidth = cursor.getIntOrNull(widthIndex)
                val rawHeight = cursor.getIntOrNull(heightIndex)
                val contentUri = buildContentUri(type, mediaStoreId)
                val timeMetadata = resolveDeviceMediaTimeMetadata(
                    context = appContext,
                    uri = contentUri,
                    mediaType = type,
                    dateTakenMillis = cursor.getLongOrNull(dateTakenIndex),
                    fileModifiedAtMillis = cursor.getLongOrNull(dateModifiedIndex)?.times(1000L),
                )
                val displayTimeMillis = timeMetadata.capturedAtMillis
                    ?: timeMetadata.fileModifiedAtMillis
                    ?: System.currentTimeMillis()
                val dateParts = displayTimeMillis.toDateParts()
                val width = rawWidth
                val height = rawHeight
                val durationMillis = if (type == SystemMediaType.VIDEO) {
                    cursor.getLongOrNull(durationIndex)
                } else {
                    null
                }
                val sizeBytes = cursor.getLongOrNull(sizeIndex)

                items += SystemMediaItem(
                    id = "${type.name.lowercase(Locale.ROOT)}-$mediaStoreId",
                    mediaStoreId = mediaStoreId,
                    uri = contentUri,
                    type = type,
                    mimeType = mimeType,
                    displayName = displayName,
                    bucketName = bucketName,
                    displayTimeMillis = displayTimeMillis,
                    capturedAtMillis = timeMetadata.capturedAtMillis,
                    fileModifiedAtMillis = timeMetadata.fileModifiedAtMillis,
                    displayTimeSource = if (timeMetadata.capturedAtMillis != null) {
                        DisplayTimeSourceOriginal
                    } else if (timeMetadata.fileModifiedAtMillis != null) {
                        DisplayTimeSourceFileModified
                    } else {
                        DisplayTimeSourceImported
                    },
                    displayYear = dateParts.year,
                    displayMonth = dateParts.month,
                    displayDay = dateParts.day,
                    width = width,
                    height = height,
                    aspectRatio = resolveAspectRatio(width, height),
                    palette = paletteForSystemMediaId(mediaStoreId),
                    linkedPostIds = emptyList(),
                    videoDurationMillis = durationMillis,
                    sizeBytes = sizeBytes,
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

    private fun resolveAspectRatio(width: Int?, height: Int?): Float {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return 1f
        }
        return (width.toFloat() / height.toFloat()).coerceIn(0.56f, 1.8f)
    }

}

private fun paletteForSystemMediaId(mediaStoreId: Long): PhotoThumbnailPalette {
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

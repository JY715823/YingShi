package com.example.yingshi.feature.photos

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
    fun peekCachedMedia(maxAgeMillis: Long = 0L): List<SystemMediaItem>?
    suspend fun loadMedia(forceRefresh: Boolean = false): List<SystemMediaItem>
    suspend fun loadAlbums(): List<SystemMediaAlbum>
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

internal fun invalidateSystemMediaMetadataCache(
    context: Context? = null,
    clearDisk: Boolean = false,
) {
    LocalSystemMediaQueryCache.invalidate(context, clearDisk)
}

class LocalSystemMediaRepository(
    context: Context,
    private val appContext: Context = context.applicationContext,
    private val dataSource: SystemMediaDataSource = MediaStoreSystemMediaDataSource(
        context = context.applicationContext,
    ),
) : SystemMediaRepository {

    override fun peekCachedMedia(maxAgeMillis: Long): List<SystemMediaItem>? {
        LocalSystemMediaBridgeRepository.warmPersistentImportOverlay(appContext)
        return LocalSystemMediaQueryCache.peek(appContext, maxAgeMillis)
    }

    override suspend fun loadMedia(forceRefresh: Boolean): List<SystemMediaItem> {
        LocalSystemMediaBridgeRepository.warmPersistentImportOverlay(appContext)
        if (!hasSystemMediaReadAccess(appContext)) {
            throw SecurityException("Missing system media permission.")
        }
        if (!forceRefresh) {
            LocalSystemMediaQueryCache.peek(appContext, maxAgeMillis = 0L)?.let { return it }
        }
        val localItems = dataSource.queryMedia()
            .sortedByDescending { it.displayTimeMillis }
        return localItems
            .withAppImportStatus(allowRemoteRefresh = NetworkConnectivityMonitor.currentState.isConnected)
            .also { items ->
                LocalSystemMediaQueryCache.store(appContext, items)
            }
    }

    override suspend fun loadAlbums(): List<SystemMediaAlbum> {
        if (!hasSystemMediaReadAccess(appContext)) {
            return emptyList()
        }
        val cachedItems = LocalSystemMediaQueryCache.peek(appContext, maxAgeMillis = 0L)
        val items = cachedItems ?: run {
            val localItems = dataSource.queryMedia().sortedByDescending { it.displayTimeMillis }
            LocalSystemMediaQueryCache.store(appContext, localItems)
            localItems
        }
        return items.groupAlbumsFromItems()
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
                // API 未匹配时，先查本地 overlay 缓存，保留已知的导入状态
                val localStatus = LocalSystemMediaBridgeRepository.peekImportStatus(item)
                if (localStatus != null) {
                    item.copy(
                        importedAppMediaId = localStatus.first,
                        linkedSmallAlbumIds = localStatus.second,
                        linkedPostIds = localStatus.second,
                    )
                } else {
                    LocalSystemMediaBridgeRepository.forgetImportStatus(item)
                    item.copy(
                        importedAppMediaId = null,
                        linkedSmallAlbumIds = emptyList(),
                        linkedPostIds = emptyList(),
                    )
                }
            } else {
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
}

private const val SystemMediaImportStatusBatchSize = 500

interface SystemMediaDataSource {
    suspend fun queryMedia(): List<SystemMediaItem>
}

private object LocalSystemMediaQueryCache {
    private const val PreferencesName = "system_media_metadata_cache"
    private const val ItemsKey = "items_json"
    private const val StoredAtKey = "stored_at_millis"
    private const val MaxCachedItems = 10000

    private var memoryItems: List<SystemMediaItem>? = null
    private var memoryStoredAtMillis: Long = 0L
    private var cachedContext: Context? = null

    @Synchronized
    fun peek(context: Context, maxAgeMillis: Long = 0L): List<SystemMediaItem>? {
        cachedContext = context.applicationContext
        memoryItems?.let { items ->
            if (maxAgeMillis <= 0L || System.currentTimeMillis() - memoryStoredAtMillis <= maxAgeMillis) {
                return items
            }
            return null
        }
        val prefs = preferences(context)
        val storedAt = prefs.getLong(StoredAtKey, 0L)
        if (maxAgeMillis > 0L && storedAt > 0L && System.currentTimeMillis() - storedAt > maxAgeMillis) {
            return null
        }
        val restoredItems = readFromDisk(prefs) ?: return null
        memoryItems = restoredItems
        memoryStoredAtMillis = storedAt
        return restoredItems
    }

    @Synchronized
    fun store(context: Context, items: List<SystemMediaItem>) {
        cachedContext = context.applicationContext
        val normalizedItems = items.take(MaxCachedItems)
        val now = System.currentTimeMillis()
        memoryItems = normalizedItems
        memoryStoredAtMillis = now
        writeToDisk(context, normalizedItems, now)
    }

    @Synchronized
    fun invalidate(context: Context? = null, clearDisk: Boolean = false) {
        memoryItems = null
        memoryStoredAtMillis = 0L
        val effectiveContext = context ?: cachedContext
        effectiveContext?.let { ctx ->
            if (clearDisk) {
                preferences(ctx).edit().clear().apply()
            } else {
                preferences(ctx).edit().remove(ItemsKey).remove(StoredAtKey).apply()
            }
        }
    }

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    }

    private fun readFromDisk(prefs: SharedPreferences): List<SystemMediaItem>? {
        val raw = prefs.getString(ItemsKey, null)?.takeIf { it.isNotBlank() }
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
        storedAtMillis: Long,
    ) {
        val array = JSONArray()
        items.forEach { item -> array.put(item.toCacheJson()) }
        preferences(context).edit()
            .putString(ItemsKey, array.toString())
            .putLong(StoredAtKey, storedAtMillis)
            .apply()
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

private fun List<SystemMediaItem>.groupAlbumsFromItems(): List<SystemMediaAlbum> {
    val bucketMap = mutableMapOf<String?, MutableList<SystemMediaItem>>()
    forEach { item ->
        bucketMap.getOrPut(item.bucketName) { mutableListOf() }.add(item)
    }
    return bucketMap.map { (bucketName, items) ->
        val cover = items.firstOrNull()
        SystemMediaAlbum(
            bucketName = bucketName,
            displayName = bucketName ?: "未分组",
            mediaCount = items.size,
            coverUri = cover?.uri,
            coverMediaType = cover?.type ?: SystemMediaType.IMAGE,
        )
    }.sortedByDescending { it.mediaCount }
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

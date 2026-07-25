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
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

interface SystemMediaRepository {
    fun peekCachedMedia(maxAgeMillis: Long = 0L): List<SystemMediaItem>?
    suspend fun loadMedia(
        forceRefresh: Boolean = false,
        useLocalOverlayOnly: Boolean = false,
    ): List<SystemMediaItem>
    suspend fun loadAlbums(): List<SystemMediaAlbum>
}

internal fun preloadSystemMediaCache(context: Context) {
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching {
            if (hasSystemMediaReadAccess(appContext)) {
                // 变更9: 预热路径避网. App 启动期没必要走网络往返,
                // 网络刷新延后到用户进入系统媒体页.
                LocalSystemMediaRepository(appContext).loadMedia(
                    forceRefresh = true,
                    useLocalOverlayOnly = true,
                )
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

    override suspend fun loadMedia(
        forceRefresh: Boolean,
        useLocalOverlayOnly: Boolean,
    ): List<SystemMediaItem> {
        LocalSystemMediaBridgeRepository.warmPersistentImportOverlay(appContext)
        if (!hasSystemMediaReadAccess(appContext)) {
            throw SecurityException("Missing system media permission.")
        }
        if (!forceRefresh) {
            LocalSystemMediaQueryCache.peek(appContext, maxAgeMillis = 0L)?.let { return it }
        }
        val localItems = dataSource.queryMedia()
            .sortedByDescending { it.displayTimeMillis }
        // S 级刷新优化: ContentObserver 触发的刷新 (插卡/拔卡) 只用本地 overlay,
        // 跳过 getImportStatus 网络往返; 用户手动下拉刷新才走网络.
        // 此前每次插卡都把数千 fingerprint 分批发服务端, 耗时 5s+.
        val allowRemoteRefresh = !useLocalOverlayOnly &&
            NetworkConnectivityMonitor.currentState.isConnected
        val itemsWithStatus = localItems.withAppImportStatus(
            allowRemoteRefresh = allowRemoteRefresh,
            useLocalOverlayOnly = useLocalOverlayOnly,
        )
        LocalSystemMediaQueryCache.store(appContext, itemsWithStatus)
        latestLoadedItems = itemsWithStatus
        return itemsWithStatus
    }

    override suspend fun loadAlbums(): List<SystemMediaAlbum> {
        if (!hasSystemMediaReadAccess(appContext)) {
            return emptyList()
        }
        // 优先用 loadMedia 最新返回的 items 生成相册, 避免读到旧缓存导致插内存卡后新相册不出现.
        val items = latestLoadedItems ?: LocalSystemMediaQueryCache.peek(appContext, maxAgeMillis = 0L) ?: run {
            val localItems = dataSource.queryMedia().sortedByDescending { it.displayTimeMillis }
            LocalSystemMediaQueryCache.store(appContext, localItems)
            localItems
        }
        return items.groupAlbumsFromItems()
    }

    private var latestLoadedItems: List<SystemMediaItem>? = null

    private suspend fun List<SystemMediaItem>.withAppImportStatus(
        allowRemoteRefresh: Boolean,
        useLocalOverlayOnly: Boolean = false,
    ): List<SystemMediaItem> {
        if (isEmpty() || !AuthSessionManager.isLoggedIn) {
            return this
        }
        // S 级刷新路径: 仅用本地 overlay, 不发网络请求.
        // 插卡/拔卡时 ContentObserver 触发此路径, 避免数千 fingerprint 分批网络往返.
        if (useLocalOverlayOnly || !allowRemoteRefresh) {
            return map { item ->
                val cached = LocalSystemMediaBridgeRepository.peekImportStatus(item)
                if (cached != null) {
                    item.copy(
                        importedAppMediaId = cached.first,
                        linkedSmallAlbumIds = cached.second,
                        linkedPostIds = cached.second,
                    )
                } else {
                    item.copy(
                        importedAppMediaId = null,
                        linkedSmallAlbumIds = emptyList(),
                        linkedPostIds = emptyList(),
                    )
                }
            }
        }
        val itemsByFingerprint = flatMap { item ->
            listOf(
                item.stableImportSourceFingerprint(),
                item.stableImportMetadataSourceFingerprint(),
            )
        }.distinct()
        val statuses = mutableMapOf<String, Pair<String, List<String>>>()
        var anyBatchSucceeded = false
        // 变更2: getImportStatus 并发批处理.
        // 此前 5000 item → 20 batch × 200ms 串行 forEach = 4s;
        // 改为并发 4 路 (Semaphore 限流避免服务器压力), 20 batch / 4 ≈ 5 轮 × 200ms = 1s.
        val importStatusSemaphore = Semaphore(IMPORT_STATUS_PARALLELISM)
        coroutineScope {
            itemsByFingerprint.chunked(SystemMediaImportStatusBatchSize).map { fingerprints ->
                async(Dispatchers.IO) {
                    importStatusSemaphore.withPermit {
                        when (val result = RepositoryProvider.mediaRepository.getImportStatus(fingerprints)) {
                            is ApiResult.Success -> {
                                synchronized(statuses) {
                                    anyBatchSucceeded = true
                                    result.data.forEach { status ->
                                        statuses[status.sourceFingerprint] = status.mediaId to status.smallAlbumIds
                                    }
                                }
                            }
                            is ApiResult.Error,
                            ApiResult.Loading,
                            -> Unit
                        }
                    }
                }
            }.awaitAll()
        }
        if (!anyBatchSucceeded) {
            return this
        }
        return map { item ->
            val status = statuses[item.stableImportSourceFingerprint()]
                ?: statuses[item.stableImportMetadataSourceFingerprint()]
            if (status == null) {
                // 服务端已成功响应但未匹配到该媒体, 说明媒体未导入或已被移入回收站.
                // 此前会回退到本地 overlay 缓存, 导致已移入回收站的媒体仍显示"已导入".
                // 修复: 服务端响应可信时, 直接清除本地 overlay 并标记为未导入.
                LocalSystemMediaBridgeRepository.forgetImportStatus(item)
                item.copy(
                    importedAppMediaId = null,
                    linkedSmallAlbumIds = emptyList(),
                    linkedPostIds = emptyList(),
                )
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
private const val EXIF_PARALLELISM = 16
private const val IMPORT_STATUS_PARALLELISM = 4

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
            .put("locationLabel", locationLabel ?: JSONObject.NULL)
            .put("latitude", latitude ?: JSONObject.NULL)
            .put("longitude", longitude ?: JSONObject.NULL)
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
            locationLabel = optNullableString("locationLabel"),
            latitude = optNullableDouble("latitude"),
            longitude = optNullableDouble("longitude"),
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

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = optDouble(name, Double.NaN)
        return if (value.isNaN()) null else value
    }
}

// P0-1: EXIF 解析结果缓存单例. mediaStoreId -> (fileModifiedAt, capturedAt).
// 跨 DataSource/ViewModel/Worker 实例共享, 进程内持久.
// 命中条件: (mediaStoreId, fileModifiedAt) 同时匹配, 文件未修改才复用.
internal object SystemMediaExifCache {
    val cache: java.util.concurrent.ConcurrentHashMap<Long, ExifCacheEntry> =
        java.util.concurrent.ConcurrentHashMap()
}

internal data class ExifCacheEntry(
    val fileModifiedAtMillis: Long?,
    val capturedAtMillis: Long?,
    val locationLabel: String? = null,
    // EXIF GPS (WGS-84) → GCJ-02 后的坐标, 用于跳转地图只读查看.
    val latitude: Double? = null,
    val longitude: Double? = null,
)

// P0-2: 后台 EXIF 解析完成事件总线.
// DataSource 后台解析完一项 EXIF 后 emit, ViewModel collect 并 merge 到 queriedItems.
internal data class SystemMediaExifUpdate(
    val mediaStoreId: Long,
    val displayTimeMillis: Long,
    val capturedAtMillis: Long?,
    val fileModifiedAtMillis: Long?,
    val displayTimeSource: String,
    val displayYear: Int,
    val displayMonth: Int,
    val displayDay: Int,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

internal object SystemMediaExifUpdateBus {
    private val _events = MutableSharedFlow<SystemMediaExifUpdate>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SystemMediaExifUpdate> = _events.asSharedFlow()
    fun tryEmit(update: SystemMediaExifUpdate) = _events.tryEmit(update)
}

class MediaStoreSystemMediaDataSource(
    context: Context,
) : SystemMediaDataSource {
    private val appContext = context.applicationContext
    private val contentResolver = context.contentResolver

    // P0-1: exifCache 单例化. 此前是实例字段, 每次 new DataSource 都丢失缓存,
    // 导致 ViewModel/Worker/预热 各自一份, 手动刷新重新解析全部 EXIF (5s).
    // 单例化后跨 DataSource 实例共享, 二次刷新命中缓存 (<200ms).
    private val exifCache get() = SystemMediaExifCache.cache

    // P0-2: 后台 EXIF 解析 scope, 不阻塞 queryMedia 返回.
    // 用 SupervisorJob 确保单个 item 解析失败不影响其他 item.
    private val backgroundExifScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class RawMediaRow(
        val mediaStoreId: Long,
        val type: SystemMediaType,
        val mimeType: String,
        val displayName: String,
        val bucketName: String?,
        val width: Int?,
        val height: Int?,
        val dateTakenMillis: Long?,
        val fileModifiedAtMillis: Long?,
        val durationMillis: Long?,
        val sizeBytes: Long?,
    )

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
        val rawRows = mutableListOf<RawMediaRow>()

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
                rawRows += RawMediaRow(
                    mediaStoreId = mediaStoreId,
                    type = type,
                    mimeType = cursor.getStringOrEmpty(mimeTypeIndex),
                    displayName = cursor.getStringOrEmpty(displayNameIndex).ifBlank { "未命名媒体" },
                    bucketName = cursor.getStringOrNull(bucketNameIndex),
                    width = cursor.getIntOrNull(widthIndex),
                    height = cursor.getIntOrNull(heightIndex),
                    dateTakenMillis = cursor.getLongOrNull(dateTakenIndex),
                    fileModifiedAtMillis = cursor.getLongOrNull(dateModifiedIndex)?.times(1000L),
                    durationMillis = if (type == SystemMediaType.VIDEO) cursor.getLongOrNull(durationIndex) else null,
                    sizeBytes = cursor.getLongOrNull(sizeIndex),
                )
            }
        }

        if (rawRows.isEmpty()) return emptyList()

        // P0-2: 两阶段 EXIF 解析, 对标小米相册"列表不解析 EXIF"策略.
        // 阶段1 (同步, <100ms): 命中 exifCache 用缓存, 未命中用 datetaken 兜底, 立即返回列表.
        // 阶段2 (后台异步): 对未命中项异步解析 EXIF, 解析完成后通过 SystemMediaExifUpdateBus 通知 ViewModel 更新.
        // 此前对全部 5000 项 awaitAll 阻塞返回 (5s+), 改后 <200ms 返回.
        val mediaTimePreference = SettingsRepository.getSettingsState().mediaTimePreference
        val importedAtMillis = System.currentTimeMillis()

        // 阶段1: 同步构建全部 item. 命中缓存用真实 EXIF 时间 + 地点, 未命中用 datetaken 兜底.
        // 不创建 async 协程, 避免 5000 项 Semaphore(16) 调度开销 (~300ms).
        val items = rawRows.map { raw ->
            val cached = exifCache[raw.mediaStoreId]
            val timeMetadata = if (cached != null && cached.fileModifiedAtMillis == raw.fileModifiedAtMillis) {
                DeviceMediaTimeMetadata(
                    capturedAtMillis = cached.capturedAtMillis,
                    fileModifiedAtMillis = raw.fileModifiedAtMillis,
                )
            } else {
                // 未命中: 用 datetaken 兜底, 不阻塞返回. 后台阶段2 会补全真实 EXIF 时间 + 地点.
                DeviceMediaTimeMetadata(
                    capturedAtMillis = raw.dateTakenMillis,
                    fileModifiedAtMillis = raw.fileModifiedAtMillis,
                )
            }
            buildItemFromRaw(
                raw = raw,
                timeMetadata = timeMetadata,
                importedAtMillis = importedAtMillis,
                mediaTimePreference = mediaTimePreference,
                locationLabel = cached?.locationLabel,
                latitude = cached?.latitude,
                longitude = cached?.longitude,
            )
        }

        // 阶段2: 后台异步补全未命中项的 EXIF, 不阻塞 queryMedia 返回.
        // 解析完成后更新 exifCache (单例, 跨实例共享) 并通过 bus 通知 ViewModel.
        val missed = rawRows.filter { raw ->
            val cached = exifCache[raw.mediaStoreId]
            cached == null || cached.fileModifiedAtMillis != raw.fileModifiedAtMillis
        }
        if (missed.isNotEmpty()) {
            launchBackgroundExifResolve(missed, importedAtMillis, mediaTimePreference)
        }

        return items
    }

    private fun buildItemFromRaw(
        raw: RawMediaRow,
        timeMetadata: DeviceMediaTimeMetadata,
        importedAtMillis: Long,
        mediaTimePreference: MediaTimePreference,
        locationLabel: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): SystemMediaItem {
        val resolvedTime = resolvePreferredMediaDisplayTime(
            metadata = timeMetadata,
            importedAtMillis = importedAtMillis,
            preference = mediaTimePreference,
        )
        val dateParts = resolvedTime.displayTimeMillis.toDateParts()
        val contentUri = buildContentUri(raw.type, raw.mediaStoreId)
        return SystemMediaItem(
            id = "${raw.type.name.lowercase(Locale.ROOT)}-${raw.mediaStoreId}",
            mediaStoreId = raw.mediaStoreId,
            uri = contentUri,
            type = raw.type,
            mimeType = raw.mimeType,
            displayName = raw.displayName,
            bucketName = raw.bucketName,
            displayTimeMillis = resolvedTime.displayTimeMillis,
            capturedAtMillis = resolvedTime.capturedAtMillis,
            fileModifiedAtMillis = resolvedTime.fileModifiedAtMillis,
            displayTimeSource = resolvedTime.displayTimeSource,
            displayYear = dateParts.year,
            displayMonth = dateParts.month,
            displayDay = dateParts.day,
            width = raw.width,
            height = raw.height,
            aspectRatio = resolveAspectRatio(raw.width, raw.height),
            palette = paletteForSystemMediaId(raw.mediaStoreId),
            linkedPostIds = emptyList(),
            videoDurationMillis = raw.durationMillis,
            sizeBytes = raw.sizeBytes,
            locationLabel = locationLabel,
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun launchBackgroundExifResolve(
        missed: List<RawMediaRow>,
        importedAtMillis: Long,
        mediaTimePreference: MediaTimePreference,
    ) {
        backgroundExifScope.launch {
            val semaphore = Semaphore(EXIF_PARALLELISM)
            coroutineScope {
                missed.map { raw ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val contentUri = buildContentUri(raw.type, raw.mediaStoreId)
                            val resolved = runCatching {
                                resolveDeviceMediaTimeMetadata(
                                    context = appContext,
                                    uri = contentUri,
                                    mediaType = raw.type,
                                    dateTakenMillis = raw.dateTakenMillis,
                                    fileModifiedAtMillis = raw.fileModifiedAtMillis,
                                )
                            }.getOrNull() ?: return@withPermit
                            // 读取 EXIF GPS (WGS-84) → GCJ-02 → 高德逆地理编码为地点文本.
                            // 对标照片流/今日痕迹的解析口径 (LifeLocationPickerActivity.triggerReverseGeocode).
                            val geoResult = runCatching {
                                readExifGpsLocation(appContext, contentUri)?.let { gps ->
                                    LocationHelper.reverseGeocodeWithAmap(appContext, gps.latitude, gps.longitude)
                                }
                            }.getOrNull()
                            val locationLabel = geoResult?.label
                            val latitude = geoResult?.latitude
                            val longitude = geoResult?.longitude
                            exifCache[raw.mediaStoreId] = ExifCacheEntry(
                                fileModifiedAtMillis = raw.fileModifiedAtMillis,
                                capturedAtMillis = resolved.capturedAtMillis,
                                locationLabel = locationLabel,
                                latitude = latitude,
                                longitude = longitude,
                            )
                            // 计算解析后的展示时间字段, 通过 bus 通知 ViewModel 更新对应 item
                            val resolvedTime = resolvePreferredMediaDisplayTime(
                                metadata = resolved,
                                importedAtMillis = importedAtMillis,
                                preference = mediaTimePreference,
                            )
                            val dateParts = resolvedTime.displayTimeMillis.toDateParts()
                            SystemMediaExifUpdateBus.tryEmit(
                                SystemMediaExifUpdate(
                                    mediaStoreId = raw.mediaStoreId,
                                    displayTimeMillis = resolvedTime.displayTimeMillis,
                                    capturedAtMillis = resolvedTime.capturedAtMillis,
                                    fileModifiedAtMillis = resolvedTime.fileModifiedAtMillis,
                                    displayTimeSource = resolvedTime.displayTimeSource,
                                    displayYear = dateParts.year,
                                    displayMonth = dateParts.month,
                                    displayDay = dateParts.day,
                                    locationLabel = locationLabel,
                                    latitude = latitude,
                                    longitude = longitude,
                                ),
                            )
                        }
                    }
                }.awaitAll()
            }
        }
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

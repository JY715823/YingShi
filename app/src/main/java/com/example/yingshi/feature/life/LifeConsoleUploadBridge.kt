package com.example.yingshi.feature.life

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.feature.photos.DeviceMediaTimeMetadata
import com.example.yingshi.feature.photos.DisplayTimeSourceFileModified
import com.example.yingshi.feature.photos.DisplayTimeSourceImported
import com.example.yingshi.feature.photos.DisplayTimeSourceOriginal
import com.example.yingshi.feature.photos.MediaTimePreference
import com.example.yingshi.feature.photos.SettingsRepository
import com.example.yingshi.feature.photos.SystemMediaType
import com.example.yingshi.feature.photos.queryDeviceMediaTimeMetadata
import com.example.yingshi.feature.photos.resolvePreferredMediaDisplayTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Round 7 阶段 5: 上传结果包装 — 同时携带 today snapshot 和本次新上传的 mediaId 列表，
 * 供 ViewModel 驱动今日页滚动到新上传的那一条。
 * Round 8 第十四轮: 增加 isFromCamera 字段, 区分拍照/相册上传, 决定是否异步定位.
 */
data class LifeConsoleUploadResult(
    val snapshot: RemoteLifeConsoleToday,
    val uploadedMediaIds: List<String>,
    val category: String,
    val isFromCamera: Boolean = false,
)

object LifeConsoleUploadBridge {
    private const val TAG = "LifeConsoleUpload"

    /**
     * @param isFromCamera true=拍照上传(需即时定位), false=相册上传(读EXIF GPS, 没有就不定位)
     */
    suspend fun uploadMedia(
        context: Context,
        category: String,
        uris: List<Uri>,
        isFromCamera: Boolean = false,
    ): ApiResult<LifeConsoleUploadResult> {
        if (uris.isEmpty()) {
            return ApiResult.Error(
                code = "NO_MEDIA_SELECTED",
                message = "没有选择媒体。",
            )
        }

        // Round 8 第十四轮: 定位策略区分拍照 vs 相册
        // - 拍照上传: 不在这里获取定位, 上传完成后由 ViewModel 异步获取当前 GPS (attachLocationToNewMedia)
        // - 相册上传: 读取每张照片的 EXIF GPS (如果有), 上传时就携带; 没有不强求
        val uploadedMediaIds = mutableListOf<String>()
        for (uri in uris) {
            val metadata = withContext(Dispatchers.IO) {
                resolveUploadMetadata(context, uri)
            }
            // Round 8 第十四轮: 相册上传读 EXIF GPS (WGS-84 → GCJ-02); 拍照上传不读 (用实时定位)
            val exifLocation = if (!isFromCamera) {
                resolveExifGpsLocation(context, uri)
            } else {
                null
            }
            val payload = metadata.copy(
                latitude = exifLocation?.latitude,
                longitude = exifLocation?.longitude,
            ).toTokenPayload()
            // Round 8 第十六轮: 记录最终发送给服务端的 displayTimeMillis, 便于和服务端归档结果对照
            Log.i(TAG, "uploadMedia: 发送 createUploadToken, fileName=${metadata.fileName}, " +
                "displayTimeMillis=${metadata.displayTimeMillis}, " +
                "displayTimeSource=${metadata.displayTimeSource}, " +
                "capturedAtMillis=${metadata.capturedAtMillis}, " +
                "importedAtMillis=${metadata.importedAtMillis}, " +
                "isFromCamera=$isFromCamera, " +
                "exifLocation=$exifLocation")
            val tokenResult = RepositoryProvider.uploadRepository.createUploadToken(payload)
            val uploadId = when (tokenResult) {
                is ApiResult.Success -> tokenResult.data.uploadId
                is ApiResult.Error -> return tokenResult
                ApiResult.Loading -> return ApiResult.Loading
            }

            val uploadResult = RepositoryProvider.uploadRepository.uploadLocalStream(
                uploadId = uploadId,
                fileName = metadata.fileName,
                mimeType = metadata.mimeType,
                fileSizeBytes = metadata.fileSizeBytes,
                openInputStream = {
                    context.contentResolver.openInputStream(uri)
                        ?: error("无法读取已选择的媒体。")
                },
            )
            when (uploadResult) {
                is ApiResult.Success -> uploadedMediaIds += uploadResult.data.mediaId
                is ApiResult.Error -> return uploadResult
                ApiResult.Loading -> return ApiResult.Loading
            }
        }

        // Round 7 阶段 5: 包装 snapshot + uploadedMediaIds，驱动今日页滚动定位
        return when (val addResult = RepositoryProvider.lifeConsoleRepository.addMedia(
            category = category,
            mediaIds = uploadedMediaIds,
        )) {
            is ApiResult.Success -> ApiResult.Success(
                data = LifeConsoleUploadResult(
                    snapshot = addResult.data,
                    uploadedMediaIds = uploadedMediaIds,
                    category = category,
                    isFromCamera = isFromCamera,
                ),
                isFromCache = addResult.isFromCache,
            )
            is ApiResult.Error -> addResult
            ApiResult.Loading -> ApiResult.Loading
        }
    }

    /**
     * Round 8 第十四轮: 读取相册照片的 GPS 坐标.
     *
     * 解析策略 (按优先级 fallback):
     * 1. EXIF GPS — 通过 ExifInterface.getLatLong() 读取 (API 24+)
     *    EXIF 存的是 WGS-84, 需转 GCJ-02.
     * 2. MediaStore GPS — 通过 MediaStore.Images.Media.LATITUDE/LONGITUDE 查询
     *    部分相册 app 显示的"地点"来自这里 (云同步补充, 或小米/华为相册自维护的元数据),
     *    而非 EXIF. 这个字段在 API 29 已废弃, 但多数 ROM 仍会写入.
     *    注意: MediaStore 写入的可能是 GCJ-02 (国内 ROM) 也可能是 WGS-84,
     *    为安全起见统一再做一次 WGS-84 → GCJ-02 转换 (转换函数对 GCJ-02 输入会
     *    产生轻微偏差, 但偏差在可接受范围内, 远比"完全没有位置"好).
     *
     * 没有 GPS 信息返回 null.
     */
    private fun resolveExifGpsLocation(context: Context, uri: Uri): LocationSnapshot? {
        // 方式 1: EXIF GPS
        val exifResult = resolveExifGpsFromExif(context, uri)
        if (exifResult != null) {
            android.util.Log.d("LifeConsoleUpload", "Gallery GPS: using EXIF GPS")
            return exifResult
        }

        // 方式 2: MediaStore GPS 兜底
        val mediaStoreResult = resolveGpsFromMediaStore(context, uri)
        if (mediaStoreResult != null) {
            android.util.Log.d("LifeConsoleUpload", "Gallery GPS: using MediaStore GPS (fallback)")
            return mediaStoreResult
        }

        android.util.Log.d("LifeConsoleUpload", "Gallery GPS: no location in EXIF or MediaStore")
        return null
    }

    private fun resolveExifGpsFromExif(context: Context, uri: Uri): LocationSnapshot? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val rawLat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val rawLng = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                val rawLatRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val rawLngRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
                android.util.Log.d(
                    "LifeConsoleUpload",
                    "EXIF raw tags: lat=$rawLat latRef=$rawLatRef lng=$rawLng lngRef=$rawLngRef"
                )

                var lat: Double? = null
                var lng: Double? = null

                // 方式 1: ExifInterface.getLatLong — API 24+, 自动处理 REF 符号
                val latLongOut = FloatArray(2)
                if (exif.getLatLong(latLongOut)) {
                    lat = latLongOut[0].toDouble()
                    lng = latLongOut[1].toDouble()
                    android.util.Log.d("LifeConsoleUpload", "EXIF getLatLong OK: lat=$lat, lng=$lng")
                }

                // 方式 2: 手动 DMS 解析 (fallback, 处理 EXIF tag 存在但值是 "0/1,0/1,0/1" 的情况)
                // 只在 rawLat/rawLng 不是全零占位时才解析
                if ((lat == null || lat == 0.0) && rawLat != null && rawLng != null
                    && !rawLat.startsWith("0/1,0/1,0/1") && !rawLng.startsWith("0/1,0/1,0/1")
                ) {
                    val parsedLat = parseExifDms(rawLat)
                    val parsedLng = parseExifDms(rawLng)
                    if (parsedLat != null && parsedLng != null && (parsedLat != 0.0 || parsedLng != 0.0)) {
                        val latRef = rawLatRef ?: "N"
                        val lngRef = rawLngRef ?: "E"
                        lat = if (latRef.equals("S", ignoreCase = true)) -parsedLat else parsedLat
                        lng = if (lngRef.equals("W", ignoreCase = true)) -parsedLng else parsedLng
                        android.util.Log.d("LifeConsoleUpload", "EXIF DMS fallback: lat=$lat, lng=$lng")
                    }
                }

                if (lat == null || lng == null) {
                    android.util.Log.d("LifeConsoleUpload", "EXIF GPS: no valid location in EXIF tags")
                    return null
                }
                if (lat == 0.0 && lng == 0.0) {
                    android.util.Log.d("LifeConsoleUpload", "EXIF GPS: (0,0) — skipping")
                    return null
                }

                // EXIF GPS 是 WGS-84 → GCJ-02
                val (gcjLat, gcjLng) = LocationHelper.wgs84ToGcj02Public(lat, lng)
                android.util.Log.d(
                    "LifeConsoleUpload",
                    "EXIF GPS OK: WGS-84($lat,$lng) → GCJ-02($gcjLat,$gcjLng)"
                )
                LocationSnapshot(latitude = gcjLat, longitude = gcjLng)
            }
        }.onFailure { e ->
            android.util.Log.w("LifeConsoleUpload", "resolveExifGpsFromExif failed", e)
        }.getOrNull()
    }

    /**
     * 从 MediaStore 查询 GPS 坐标 (相册 app 显示的"地点"常来自这里).
     * MediaStore.Images.Media.LATITUDE/LONGITUDE 在 API 29 已废弃, 但多数 ROM 仍会写入.
     * 视频用 MediaStore.Video.Media.LATITUDE/LONGITUDE.
     */
    private fun resolveGpsFromMediaStore(context: Context, uri: Uri): LocationSnapshot? {
        return runCatching {
            val projection = arrayOf(
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE,
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val latIdx = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                val lngIdx = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)
                if (latIdx < 0 || lngIdx < 0) return null
                val lat = cursor.getDouble(latIdx)
                val lng = cursor.getDouble(lngIdx)
                android.util.Log.d("LifeConsoleUpload", "MediaStore GPS raw: lat=$lat, lng=$lng")
                if (lat == 0.0 && lng == 0.0) return null
                if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return null
                // MediaStore GPS 可能是 WGS-84 或 GCJ-02 (国内 ROM), 统一再转一次 (输入已是 GCJ-02 时偏差可接受)
                val (gcjLat, gcjLng) = LocationHelper.wgs84ToGcj02Public(lat, lng)
                android.util.Log.d(
                    "LifeConsoleUpload",
                    "MediaStore GPS OK: ($lat,$lng) → GCJ-02($gcjLat,$gcjLng)"
                )
                LocationSnapshot(latitude = gcjLat, longitude = gcjLng)
            }
        }.onFailure { e ->
            android.util.Log.w("LifeConsoleUpload", "resolveGpsFromMediaStore failed", e)
        }.getOrNull()
    }

    /**
     * 解析 EXIF GPS DMS 格式字符串 (如 "39/1,54/1,30/1" 表示 39°54'30")
     * @return 十进制度数, 解析失败返回 null
     */
    private fun parseExifDms(dms: String): Double? {
        val parts = dms.split(",").mapNotNull { it.trim().takeIf { p -> p.isNotEmpty() } }
        if (parts.size != 3) return null
        val degrees = parts[0].split("/").let { r ->
            if (r.size == 2) r[0].toDoubleOrNull()?.let { n -> r[1].toDoubleOrNull()?.let { d -> if (d != 0.0) n / d else null } }
            else r[0].toDoubleOrNull()
        } ?: return null
        val minutes = parts[1].split("/").let { r ->
            if (r.size == 2) r[0].toDoubleOrNull()?.let { n -> r[1].toDoubleOrNull()?.let { d -> if (d != 0.0) n / d else null } }
            else r[0].toDoubleOrNull()
        } ?: return null
        val seconds = parts[2].split("/").let { r ->
            if (r.size == 2) r[0].toDoubleOrNull()?.let { n -> r[1].toDoubleOrNull()?.let { d -> if (d != 0.0) n / d else null } }
            else r[0].toDoubleOrNull()
        } ?: return null
        return degrees + minutes / 60.0 + seconds / 3600.0
    }

    private fun resolveUploadMetadata(context: Context, uri: Uri): UploadMetadata {
        val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: "image/jpeg"
        val isVideo = mimeType.startsWith("video/")
        val fileName = queryDisplayName(context, uri)
            ?: "life-${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}"
        val fileSizeBytes = queryFileSize(context, uri).takeIf { it > 0L } ?: 1L
        val (width, height, durationMillis) = if (isVideo) {
            resolveVideoMetadata(context, uri)
        } else {
            resolveImageMetadata(context, uri)
        }
        val nowMillis = System.currentTimeMillis()
        // Round 8 第十六轮: 加详细日志排查"上传昨天的照片归档到今天"的问题.
        // 分别记录: MediaStore datetaken, MediaStore DATE_MODIFIED, MediaStore DATE_ADDED, EXIF datetime_original,
        // 以及最终 resolvedTime 的 displayTimeMillis + displayTimeSource.
        val rawMediaStoreTimeInfo = queryMediaStoreRawTimeInfo(context, uri)
        val timeMetadata = queryDeviceMediaTimeMetadata(
            context = context,
            uri = uri,
            mediaType = if (isVideo) SystemMediaType.VIDEO else SystemMediaType.IMAGE,
        )
        val resolvedTime = resolvePreferredMediaDisplayTime(
            metadata = timeMetadata,
            importedAtMillis = nowMillis,
            preference = SettingsRepository.getSettingsState().mediaTimePreference,
        )
        Log.i(TAG, "resolveUploadMetadata: uri=$uri, fileName=$fileName, mimeType=$mimeType, " +
            "sizeBytes=$fileSizeBytes, isVideo=$isVideo")
        Log.i(TAG, "resolveUploadMetadata: MediaStore raw times -> " +
            "datetaken=${rawMediaStoreTimeInfo.dateTakenMillis?.formatLogTime() ?: "null"}, " +
            "dateModified=${rawMediaStoreTimeInfo.dateModifiedMillis?.formatLogTime() ?: "null"}, " +
            "dateAdded=${rawMediaStoreTimeInfo.dateAddedMillis?.formatLogTime() ?: "null"}")
        Log.i(TAG, "resolveUploadMetadata: timeMetadata -> " +
            "capturedAtMillis=${timeMetadata.capturedAtMillis?.formatLogTime() ?: "null"}, " +
            "fileModifiedAtMillis=${timeMetadata.fileModifiedAtMillis?.formatLogTime() ?: "null"}")
        Log.i(TAG, "resolveUploadMetadata: resolvedTime -> " +
            "displayTimeMillis=${resolvedTime.displayTimeMillis.formatLogTime()}, " +
            "displayTimeSource=${resolvedTime.displayTimeSource}, " +
            "capturedAtMillis=${resolvedTime.capturedAtMillis?.formatLogTime() ?: "null"}, " +
            "importedAtMillis=${resolvedTime.importedAtMillis.formatLogTime()}")
        if (resolvedTime.displayTimeSource != DisplayTimeSourceOriginal) {
            Log.w(TAG, "resolveUploadMetadata: ⚠ displayTimeSource=${resolvedTime.displayTimeSource} " +
                "不是 ORIGINAL (拍摄时间), 照片可能不会按实际拍摄日期归档. " +
                "原因可能是: 1) 照片无 EXIF datetime_original (截图/微信导出/编辑过); " +
                "2) MediaStore datetaken 列为空; 3) ACTION_GET_CONTENT URI 不暴露 datetaken 列.")
        }
        val sourceItemId = resolveMediaStoreId(context, uri)
        val fingerprint = buildSourceFingerprint(fileName, fileSizeBytes, uri.toString())
        return UploadMetadata(
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            mediaType = if (isVideo) "video" else "image",
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            durationMillis = durationMillis,
            displayTimeMillis = resolvedTime.displayTimeMillis,
            capturedAtMillis = resolvedTime.capturedAtMillis,
            importedAtMillis = resolvedTime.importedAtMillis,
            displayTimeSource = resolvedTime.displayTimeSource,
            sourceFingerprint = fingerprint,
            sourceItemId = sourceItemId,
        )
    }

    /**
     * Round 8 第十六轮: 查询 MediaStore 三列时间信息 (datetaken / DATE_MODIFIED / DATE_ADDED),
     * 仅用于日志诊断, 不影响实际时间解析逻辑.
     */
    private data class MediaStoreRawTimeInfo(
        val dateTakenMillis: Long?,
        val dateModifiedMillis: Long?,
        val dateAddedMillis: Long?,
    )

    private fun queryMediaStoreRawTimeInfo(context: Context, uri: Uri): MediaStoreRawTimeInfo {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    "datetaken",
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATE_ADDED,
                ),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateTaken = cursor.getLongOrNull(cursor.getColumnIndex("datetaken"))
                    val dateModified = cursor.getLongOrNull(
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED),
                    )?.times(1000L)
                    val dateAdded = cursor.getLongOrNull(
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED),
                    )?.times(1000L)
                    MediaStoreRawTimeInfo(
                        dateTakenMillis = dateTaken?.takeIf { it > 0L },
                        dateModifiedMillis = dateModified?.takeIf { it > 0L },
                        dateAddedMillis = dateAdded?.takeIf { it > 0L },
                    )
                } else {
                    MediaStoreRawTimeInfo(null, null, null)
                }
            } ?: MediaStoreRawTimeInfo(null, null, null)
        }.getOrDefault(MediaStoreRawTimeInfo(null, null, null))
    }

    private fun Long.formatLogTime(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(java.util.Date(this))
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getStringOrNull(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLongOrNull(cursor.getColumnIndex(OpenableColumns.SIZE)) ?: 0L
                } else {
                    0L
                }
            } ?: 0L
    }

    private fun resolveImageMetadata(context: Context, uri: Uri): Triple<Int, Int, Long?> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, bounds)
            }
        }
        val orientationDegrees = resolveImageOrientationDegrees(context, uri)
        val rawWidth = bounds.outWidth.takeIf { it > 0 } ?: 1
        val rawHeight = bounds.outHeight.takeIf { it > 0 } ?: 1
        val width = if (orientationDegrees == 90 || orientationDegrees == 270) rawHeight else rawWidth
        val height = if (orientationDegrees == 90 || orientationDegrees == 270) rawWidth else rawHeight
        return Triple(width, height, null)
    }

    private fun resolveVideoMetadata(context: Context, uri: Uri): Triple<Int, Int, Long?> {
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
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val width = rawWidth ?: 1920
            val height = rawHeight ?: ((width / (16f / 9f)).roundToInt().coerceAtLeast(1))
            if (rotation == 90 || rotation == 270) {
                Triple(height, width, duration)
            } else {
                Triple(width, height, duration)
            }
        }.getOrElse {
            Triple(1920, 1080, null)
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun resolveImageOrientationDegrees(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                when (ExifInterface(inputStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        if (columnIndex < 0 || isNull(columnIndex)) return null
        return getString(columnIndex)
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        if (columnIndex < 0 || isNull(columnIndex)) return null
        return getLong(columnIndex)
    }

    private fun resolveMediaStoreId(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getLong(0).toString()
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun buildSourceFingerprint(displayName: String, fileSizeBytes: Long, uriString: String): String {
        val raw = "$displayName:$fileSizeBytes:$uriString"
        return raw.hashCode().toLong().toString(16)
    }

    /**
     * Removes media entries that the system camera app saved to the device gallery (DCIM/Camera)
     * during a recent capture. Runs after upload completes so the media scanner has had time to index.
     */
    fun cleanupCameraDuplicatesFromGallery(context: Context) {
        val cutoff = System.currentTimeMillis() / 1000 - 30
        val resolver = context.contentResolver
        var deleted = 0
        for (uri in listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )) {
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
                    "${MediaStore.MediaColumns.DATE_ADDED} >= ?",
                    arrayOf(cutoff.toString()),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val deleteUri = ContentUris.withAppendedId(uri, id)
                        runCatching { resolver.delete(deleteUri, null, null) }
                        deleted++
                    }
                }
            }
        }
        if (deleted > 0) {
            Log.i(TAG, "Cleaned up $deleted camera duplicate(s) from system gallery")
        }
    }

    private data class UploadMetadata(
        val fileName: String,
        val mimeType: String,
        val fileSizeBytes: Long,
        val mediaType: String,
        val width: Int,
        val height: Int,
        val durationMillis: Long?,
        val displayTimeMillis: Long,
        val capturedAtMillis: Long?,
        val importedAtMillis: Long,
        val displayTimeSource: String,
        val sourceFingerprint: String? = null,
        val sourceItemId: String? = null,
        // FR-19: optional location, populated by LocationHelper at upload time
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) {
        fun toTokenPayload(): CreateUploadTokenPayload {
            return CreateUploadTokenPayload(
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                mediaType = mediaType,
                width = width,
                height = height,
                durationMillis = durationMillis,
                displayTimeMillis = displayTimeMillis,
                capturedAtMillis = capturedAtMillis,
                importedAtMillis = importedAtMillis,
                displayTimeSource = displayTimeSource,
                sourceFingerprint = sourceFingerprint,
                operationType = "life_console",
                sourceItemId = sourceItemId,
                domain = "life",
                latitude = latitude,
                longitude = longitude,
                // locationLabel intentionally null — server will reverse-geocode from lat/lng via Amap.
            )
        }
    }
}

object LifeConsoleUploadRuntime {
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueueWidgetUpload(
        context: Context,
        category: String,
        uris: List<Uri>,
    ) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        AuthSessionManager.init(appContext)
        BackendDebugConfig.init(appContext)
        LifeConsoleWidgetProvider.refreshAll(appContext)
        Toast.makeText(appContext, "正在后台上传", Toast.LENGTH_SHORT).show()
        val isCameraCapture = uris.any { it.toString().contains("/life-console-capture/") }
        uploadScope.launch {
            when (val result = LifeConsoleUploadBridge.uploadMedia(appContext, category, uris)) {
                is ApiResult.Success -> {
                    // Round 7 阶段 5: result.data 现在是 LifeConsoleUploadResult，取 .snapshot 喂给 widget
                    LifeConsoleWidgetProvider.applySnapshot(appContext, result.data.snapshot)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "已上传到今日痕迹", Toast.LENGTH_SHORT).show()
                    }
                }
                is ApiResult.Error -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, result.message, Toast.LENGTH_SHORT).show()
                    }
                    LifeConsoleWidgetProvider.refreshAll(appContext)
                }
                ApiResult.Loading -> Unit
            }
            if (isCameraCapture) {
                kotlinx.coroutines.delay(5_000)
                LifeConsoleUploadBridge.cleanupCameraDuplicatesFromGallery(appContext)
            }
        }
    }
}

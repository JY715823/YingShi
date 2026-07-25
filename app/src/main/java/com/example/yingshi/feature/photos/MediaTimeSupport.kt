package com.example.yingshi.feature.photos

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DisplayTimeSourceOriginal = "ORIGINAL"
const val DisplayTimeSourceFileModified = "FILE_MODIFIED"
const val DisplayTimeSourceImported = "IMPORTED"
const val DisplayTimeSourceManual = "MANUAL"

data class DeviceMediaTimeMetadata(
    val capturedAtMillis: Long? = null,
    val fileModifiedAtMillis: Long? = null,
)

data class ResolvedMediaDisplayTime(
    val displayTimeMillis: Long,
    val capturedAtMillis: Long?,
    val fileModifiedAtMillis: Long?,
    val importedAtMillis: Long,
    val displayTimeSource: String,
)

fun resolvePreferredMediaDisplayTime(
    metadata: DeviceMediaTimeMetadata,
    importedAtMillis: Long,
    preference: MediaTimePreference,
): ResolvedMediaDisplayTime {
    val normalizedCapturedAt = metadata.capturedAtMillis.takeIf { it != null && it > 0L }
    val normalizedFileModifiedAt = metadata.fileModifiedAtMillis.takeIf { it != null && it > 0L }
    return when (preference) {
        MediaTimePreference.IMPORTED_FIRST -> ResolvedMediaDisplayTime(
            displayTimeMillis = importedAtMillis,
            capturedAtMillis = normalizedCapturedAt,
            fileModifiedAtMillis = normalizedFileModifiedAt,
            importedAtMillis = importedAtMillis,
            displayTimeSource = DisplayTimeSourceImported,
        )

        MediaTimePreference.CAPTURED_FIRST -> when {
            normalizedCapturedAt != null -> ResolvedMediaDisplayTime(
                displayTimeMillis = normalizedCapturedAt,
                capturedAtMillis = normalizedCapturedAt,
                fileModifiedAtMillis = normalizedFileModifiedAt,
                importedAtMillis = importedAtMillis,
                displayTimeSource = DisplayTimeSourceOriginal,
            )

            normalizedFileModifiedAt != null -> ResolvedMediaDisplayTime(
                displayTimeMillis = normalizedFileModifiedAt,
                capturedAtMillis = null,
                fileModifiedAtMillis = normalizedFileModifiedAt,
                importedAtMillis = importedAtMillis,
                displayTimeSource = DisplayTimeSourceFileModified,
            )

            else -> ResolvedMediaDisplayTime(
                displayTimeMillis = importedAtMillis,
                capturedAtMillis = null,
                fileModifiedAtMillis = null,
                importedAtMillis = importedAtMillis,
                displayTimeSource = DisplayTimeSourceImported,
            )
        }
    }
}

fun resolveDeviceMediaTimeMetadata(
    context: Context,
    uri: Uri,
    mediaType: SystemMediaType,
    dateTakenMillis: Long? = null,
    fileModifiedAtMillis: Long? = null,
): DeviceMediaTimeMetadata {
    // 优先级调换: IMAGE 先读 EXIF DateTimeOriginal (相机写入文件的真实拍摄时间, 最可靠),
    // 失败才回退到 MediaStore datetaken 列 (会被网盘 App 改写为下载时刻, 不可靠).
    // VIDEO 不读 EXIF (视频没有 EXIF 段), 但 fallback 到 MediaMetadataRetriever 读 MP4 creation_time.
    // 这样与小米相册行为对齐: 网盘下载的去年照片会归到去年, 不再被错归到今天.
    val normalizedCapturedAt = when (mediaType) {
        SystemMediaType.IMAGE -> readExifCapturedAtMillis(context, uri)
            ?: dateTakenMillis.takeIf { it != null && it > 0L }
        SystemMediaType.VIDEO -> dateTakenMillis.takeIf { it != null && it > 0L }
            ?: readVideoMetadataCapturedAtMillis(context, uri)
    }
    return DeviceMediaTimeMetadata(
        capturedAtMillis = normalizedCapturedAt,
        fileModifiedAtMillis = fileModifiedAtMillis?.takeIf { it > 0L },
    )
}

fun queryDeviceMediaTimeMetadata(
    context: Context,
    uri: Uri,
    mediaType: SystemMediaType,
): DeviceMediaTimeMetadata {
    // Round 8 第十七轮: 组合查询 datetaken + DATE_MODIFIED.
    // 某些 provider (如云相册/文档 URI) 不支持 datetaken 列, 组合查询可能整体失败.
    // 因此先尝试组合查询, 失败再单独查 DATE_MODIFIED, 确保至少拿到文件修改时间.
    var dateTakenMillis: Long? = null
    var dateModifiedMillis: Long? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf("datetaken", MediaStore.MediaColumns.DATE_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                dateTakenMillis = cursor.getLongOrNull(cursor.getColumnIndex("datetaken"))
                    ?.takeIf { it > 0L }
                dateModifiedMillis = cursor.getLongOrNull(
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED),
                )?.times(1000L)?.takeIf { it > 0L }
            }
        }
    }
    // 组合查询失败或 dateModified 仍为 null 时, 单独查 DATE_MODIFIED
    if (dateModifiedMillis == null) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    dateModifiedMillis = cursor.getLongOrNull(0)
                        ?.times(1000L)?.takeIf { it > 0L }
                }
            }
        }
    }
    // 仍为 null 时, 尝试 DocumentsContract.COLUMN_LAST_MODIFIED (文档 URI)
    if (dateModifiedMillis == null) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    dateModifiedMillis = cursor.getLongOrNull(0)?.takeIf { it > 0L }
                }
            }
        }
    }
    return resolveDeviceMediaTimeMetadata(
        context = context,
        uri = uri,
        mediaType = mediaType,
        dateTakenMillis = dateTakenMillis,
        fileModifiedAtMillis = dateModifiedMillis,
    )
}

fun displayTimeSourceLabel(source: String?): String {
    return when (source?.trim()?.uppercase(Locale.ROOT)) {
        DisplayTimeSourceOriginal -> "拍摄时间"
        DisplayTimeSourceFileModified -> "文件时间"
        DisplayTimeSourceImported -> "导入时间"
        DisplayTimeSourceManual -> "手动时间"
        else -> "时间"
    }
}

fun formatMediaDisplayTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

fun buildMediaDisplayTimeLabel(
    timeMillis: Long,
    source: String?,
): String {
    return "${displayTimeSourceLabel(source)} · ${formatMediaDisplayTime(timeMillis)}"
}

private fun readExifCapturedAtMillis(
    context: Context,
    uri: Uri,
): Long? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME,
            ).firstNotNullOfOrNull { tag ->
                exif.getAttribute(tag)?.let(::parseExifDateTimeMillis)
            }
        }
    }.getOrNull()
}

/**
 * 从 MP4 box 读取视频真实拍摄时间 (creation_time).
 *
 * 视频没有 EXIF 段, 拍摄时间存在 MP4 mvhd/mdhd box 的 creation_time 字段.
 * MediaStore datetaken 列会被网盘 App 改写为下载时刻, 此函数作为 fallback 提供可靠来源.
 *
 * 支持格式:
 * - QuickTime: "YYYY:MM:DD HH:MM:SS"
 * - ISO 8601: "yyyyMMddTHHmmss.SSSZ" / "yyyyMMddTHHmmssZ"
 *
 * 注意: MediaMetadataRetriever 在 API 29+ 才实现 Closeable, minSdk=24 需手动 release.
 *
 * @return 拍摄时间毫秒, 失败返回 null
 */
private fun readVideoMetadataCapturedAtMillis(
    context: Context,
    uri: Uri,
): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            ?.let(::parseExifDateTimeMillis)
    } catch (error: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * EXIF GPS 坐标 (WGS-84 原始坐标，调用方需自行转 GCJ-02).
 *
 * 解析策略 (按优先级 fallback, 全部 API 24+ 兼容):
 * 1. ExifInterface.getLatLong(FloatArray) — 系统内置, 自动处理 DMS + REF 符号
 * 2. 手动 getAttribute(TAG_GPS_LATITUDE) + DMS 解析 — fallback, 处理个别 ROM getLatLong 异常
 *
 * 注意: 必须声明 ACCESS_MEDIA_LOCATION 权限 (Android 10+), 否则 MediaProvider 会 redact EXIF GPS.
 *
 * @return WGS-84 坐标, 没有 GPS 信息返回 null
 */
data class ExifGpsLocation(
    val latitude: Double,
    val longitude: Double,
)

fun readExifGpsLocation(
    context: Context,
    uri: Uri,
): ExifGpsLocation? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            val rawLat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val rawLng = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val rawLatRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val rawLngRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

            var lat: Double? = null
            var lng: Double? = null

            // 方式 1: ExifInterface.getLatLong — API 24+, 自动处理 REF 符号
            val latLongOut = FloatArray(2)
            if (exif.getLatLong(latLongOut)) {
                lat = latLongOut[0].toDouble()
                lng = latLongOut[1].toDouble()
            }

            // 方式 2: 手动 DMS 解析 (fallback, 处理 EXIF tag 存在但 getLatLong 返回 (0,0) 的情况)
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
                }
            }

            if (lat == null || lng == null) return null
            if (lat == 0.0 && lng == 0.0) return null
            ExifGpsLocation(latitude = lat, longitude = lng)
        }
    }.getOrNull()
}

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

private fun parseExifDateTimeMillis(rawValue: String): Long? {
    val normalizedValue = rawValue.trim().takeIf { it.isNotBlank() } ?: return null
    val patterns = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy:MM:dd HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ssXXX",
        // MP4 ISO 8601 格式 (MediaMetadataRetriever.METADATA_KEY_DATE)
        "yyyyMMdd'T'HHmmss.SSSX",
        "yyyyMMdd'T'HHmmssX",
        "yyyyMMdd'T'HHmmss.SSS'Z'",
        "yyyyMMdd'T'HHmmss'Z'",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).parse(normalizedValue)?.time
        }.getOrNull()
    }
}


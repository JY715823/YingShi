package com.example.yingshi.feature.photos

import android.content.Context
import android.media.ExifInterface
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
    val normalizedCapturedAt = dateTakenMillis.takeIf { it != null && it > 0L }
        ?: if (mediaType == SystemMediaType.IMAGE) readExifCapturedAtMillis(context, uri) else null
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
    val (dateTakenMillis, dateModifiedMillis) = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf("datetaken", MediaStore.MediaColumns.DATE_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dateTaken = cursor.getLongOrNull(cursor.getColumnIndex("datetaken"))
                val dateModified = cursor.getLongOrNull(
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED),
                )?.times(1000L)
                dateTaken to dateModified
            } else {
                null to null
            }
        } ?: (null to null)
    }.getOrDefault(null to null)
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

private fun parseExifDateTimeMillis(rawValue: String): Long? {
    val normalizedValue = rawValue.trim().takeIf { it.isNotBlank() } ?: return null
    val patterns = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy:MM:dd HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ssXXX",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).parse(normalizedValue)?.time
        }.getOrNull()
    }
}

private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getLong(columnIndex)
}

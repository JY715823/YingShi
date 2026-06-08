package com.example.yingshi.feature.photos

import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private const val ImportPreviewTimeGapNoticeThresholdMillis = 24L * 60L * 60L * 1000L
private const val ImportPreviewHourMillis = 60L * 60L * 1000L
private const val ImportPreviewMinuteMillis = 60L * 1000L

data class SystemMediaImportPreview(
    val requestedCount: Int,
    val importableItems: List<SystemMediaItem>,
    val duplicateItems: List<SystemMediaImportDuplicateItem>,
    val timeNoticeItems: List<SystemMediaImportTimeNoticeItem>,
) {
    val importableCount: Int
        get() = importableItems.size

    val duplicateCount: Int
        get() = duplicateItems.size

    val timeNoticeCount: Int
        get() = timeNoticeItems.size

    val hasImportableItems: Boolean
        get() = importableItems.isNotEmpty()
}

data class SystemMediaImportDuplicateItem(
    val item: SystemMediaItem,
    val reason: SystemMediaImportDuplicateReason,
)

enum class SystemMediaImportDuplicateReason(
    val label: String,
) {
    ALREADY_IMPORTED("已导入"),
    DUPLICATE_IN_SELECTION("重复选择"),
}

data class SystemMediaImportTimeNoticeItem(
    val item: SystemMediaItem,
    val kind: SystemMediaImportTimeNoticeKind,
    val message: String,
)

enum class SystemMediaImportTimeNoticeKind {
    FILE_TIME_FALLBACK,
    IMPORTED_TIME_FALLBACK,
    CAPTURED_FILE_TIME_GAP,
}

internal fun buildSystemMediaImportPreview(
    mediaItems: List<SystemMediaItem>,
    preference: MediaTimePreference,
    importedAtBaseMillis: Long,
    knownAppMediaIdForSource: (SystemMediaItem) -> String?,
): SystemMediaImportPreview {
    val normalizedItems = normalizeSystemMediaForImport(
        mediaItems = mediaItems,
        preference = preference,
        importedAtBaseMillis = importedAtBaseMillis,
    )
    if (normalizedItems.isEmpty()) {
        return SystemMediaImportPreview(
            requestedCount = mediaItems.size,
            importableItems = emptyList(),
            duplicateItems = emptyList(),
            timeNoticeItems = emptyList(),
        )
    }

    data class PreviewCandidate(
        val item: SystemMediaItem,
        val sourceKey: String,
        val existingAppMediaId: String?,
    )

    val groupedCandidates = linkedMapOf<String, MutableList<PreviewCandidate>>()
    normalizedItems.forEach { item ->
        val candidate = PreviewCandidate(
            item = item,
            sourceKey = item.stableImportSourceKey(),
            existingAppMediaId = knownAppMediaIdForSource(item),
        )
        groupedCandidates.getOrPut(candidate.sourceKey) { mutableListOf() }.add(candidate)
    }

    val importableItems = mutableListOf<SystemMediaItem>()
    val duplicateItems = mutableListOf<SystemMediaImportDuplicateItem>()
    val timeNoticeItems = mutableListOf<SystemMediaImportTimeNoticeItem>()

    groupedCandidates.values.forEach { candidates ->
        val first = candidates.first()
        if (first.existingAppMediaId != null) {
            candidates.forEach { candidate ->
                duplicateItems += SystemMediaImportDuplicateItem(
                    item = candidate.item,
                    reason = SystemMediaImportDuplicateReason.ALREADY_IMPORTED,
                )
            }
            return@forEach
        }

        importableItems += first.item
        first.item.buildImportTimeNotice(preference)?.let(timeNoticeItems::add)
        candidates.drop(1).forEach { candidate ->
            duplicateItems += SystemMediaImportDuplicateItem(
                item = candidate.item,
                reason = SystemMediaImportDuplicateReason.DUPLICATE_IN_SELECTION,
            )
        }
    }

    return SystemMediaImportPreview(
        requestedCount = mediaItems.size,
        importableItems = importableItems,
        duplicateItems = duplicateItems,
        timeNoticeItems = timeNoticeItems,
    )
}

internal fun normalizeSystemMediaForImport(
    mediaItems: List<SystemMediaItem>,
    preference: MediaTimePreference,
    importedAtBaseMillis: Long,
): List<SystemMediaItem> {
    return mediaItems.mapIndexed { index, item ->
        val resolvedTime = resolvePreferredMediaDisplayTime(
            metadata = DeviceMediaTimeMetadata(
                capturedAtMillis = item.capturedAtMillis,
                fileModifiedAtMillis = item.fileModifiedAtMillis,
            ),
            importedAtMillis = importedAtBaseMillis - (index * 1_000L),
            preference = preference,
        )
        val calendar = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = resolvedTime.displayTimeMillis
        }
        item.copy(
            displayTimeMillis = resolvedTime.displayTimeMillis,
            capturedAtMillis = resolvedTime.capturedAtMillis,
            fileModifiedAtMillis = resolvedTime.fileModifiedAtMillis,
            displayTimeSource = resolvedTime.displayTimeSource,
            displayYear = calendar.get(Calendar.YEAR),
            displayMonth = calendar.get(Calendar.MONTH) + 1,
            displayDay = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }
}

internal fun SystemMediaItem.stableImportSourceKey(): String {
    val uriString = uri.toString().trim()
    if (!id.startsWith("picked-") && mediaStoreId > 0L) {
        return "store:${type.name.lowercase(Locale.ROOT)}:$mediaStoreId"
    }
    if (id.startsWith("picked-")) {
        return stableImportMetadataKey()
    }
    if (uriString.isNotBlank()) {
        return "uri:$uriString"
    }
    return stableImportMetadataKey()
}

internal fun SystemMediaItem.stableImportSourceFingerprint(): String {
    val bytes = stableImportSourceKey().toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun SystemMediaItem.stableImportMetadataKey(): String {
    return buildString {
        append("meta:")
        append(type.name.lowercase(Locale.ROOT))
        append('|')
        append(mimeType.trim().lowercase(Locale.ROOT))
        append('|')
        append(displayName.trim().lowercase(Locale.ROOT))
        append('|')
        append(sizeBytes ?: -1L)
        append('|')
        append(displayTimeMillis)
        append('|')
        append(width ?: -1)
        append('x')
        append(height ?: -1)
        append('|')
        append(videoDurationMillis ?: -1L)
    }
}

private fun SystemMediaItem.buildImportTimeNotice(
    preference: MediaTimePreference,
): SystemMediaImportTimeNoticeItem? {
    if (preference == MediaTimePreference.IMPORTED_FIRST) return null
    return when (displayTimeSource) {
        DisplayTimeSourceFileModified -> SystemMediaImportTimeNoticeItem(
            item = this,
            kind = SystemMediaImportTimeNoticeKind.FILE_TIME_FALLBACK,
            message = "缺少拍摄时间，将按文件时间排序",
        )

        DisplayTimeSourceImported -> SystemMediaImportTimeNoticeItem(
            item = this,
            kind = SystemMediaImportTimeNoticeKind.IMPORTED_TIME_FALLBACK,
            message = "缺少拍摄时间和文件时间，将按导入时间排序",
        )

        DisplayTimeSourceOriginal -> {
            val capturedAt = capturedAtMillis ?: return null
            val fileModifiedAt = fileModifiedAtMillis ?: return null
            val diffMillis = abs(fileModifiedAt - capturedAt)
            if (diffMillis < ImportPreviewTimeGapNoticeThresholdMillis) {
                null
            } else {
                SystemMediaImportTimeNoticeItem(
                    item = this,
                    kind = SystemMediaImportTimeNoticeKind.CAPTURED_FILE_TIME_GAP,
                    message = "拍摄时间和文件时间相差 ${formatImportTimeGap(diffMillis)}，会按拍摄时间排序",
                )
            }
        }

        else -> null
    }
}

private fun formatImportTimeGap(durationMillis: Long): String {
    val safeDuration = durationMillis.coerceAtLeast(ImportPreviewMinuteMillis)
    val totalHours = safeDuration / ImportPreviewHourMillis
    return when {
        totalHours >= 48L -> "${((totalHours + 12L) / 24L).coerceAtLeast(2L)} 天"
        totalHours >= 1L -> "$totalHours 小时"
        else -> "${(safeDuration / ImportPreviewMinuteMillis).coerceAtLeast(1L)} 分钟"
    }
}

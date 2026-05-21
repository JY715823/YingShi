package com.example.yingshi.feature.chat.data

import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

data class ImportedResourceProbe(
    val detectedFormat: ImportedResourceDetectedFormat,
    val resolvedMimeType: String?,
    val isAnimatedImage: Boolean,
)

private val resourceProbeCache = ConcurrentHashMap<String, ImportedResourceProbe>()

fun probeImportedResource(
    absoluteFilePath: String,
    type: ImportedResourceType,
    declaredMimeType: String?,
): ImportedResourceProbe {
    val file = File(absoluteFilePath)
    if (!file.exists() || !file.isFile) {
        return ImportedResourceProbe(
            detectedFormat = ImportedResourceDetectedFormat.UNKNOWN,
            resolvedMimeType = declaredMimeType,
            isAnimatedImage = false,
        )
    }
    val cacheKey = buildString {
        append(file.absolutePath)
        append('#')
        append(file.length())
        append('#')
        append(file.lastModified())
        append('#')
        append(declaredMimeType.orEmpty())
    }
    return resourceProbeCache.getOrPut(cacheKey) {
        val header = ByteArray(64)
        val bytesRead = runCatching {
            FileInputStream(file).use { input ->
                input.read(header)
            }
        }.getOrDefault(-1)
        val detectedFormat = detectFormatFromHeader(
            header = header,
            length = bytesRead.coerceAtLeast(0),
            fallbackMimeType = declaredMimeType,
            type = type,
        )
        ImportedResourceProbe(
            detectedFormat = detectedFormat,
            resolvedMimeType = resolveMimeType(
                detectedFormat = detectedFormat,
                fallbackMimeType = declaredMimeType,
                type = type,
            ),
            isAnimatedImage = detectedFormat == ImportedResourceDetectedFormat.GIF,
        )
    }
}

fun ImportedResource.effectiveRenderKind(): ImportedResourceRenderKind {
    return if (renderKind == ImportedResourceRenderKind.STICKER) {
        ImportedResourceRenderKind.IMAGE
    } else {
        renderKind
    }
}

fun ImportedResource.isImageLikeResource(): Boolean {
    return effectiveRenderKind() == ImportedResourceRenderKind.IMAGE
}

fun ImportedResource.isVideoLikeResource(): Boolean {
    return effectiveRenderKind() == ImportedResourceRenderKind.VIDEO
}

fun ImportedResource.isMediaViewerResource(): Boolean {
    val kind = effectiveRenderKind()
    return kind == ImportedResourceRenderKind.IMAGE || kind == ImportedResourceRenderKind.VIDEO
}

fun ImportedResource.isSilkAudio(): Boolean {
    return detectedFormat == ImportedResourceDetectedFormat.SILK
}

fun ImportedResource.isDirectlyPlayableAudio(): Boolean {
    return when (detectedFormat) {
        ImportedResourceDetectedFormat.SILK -> false
        ImportedResourceDetectedFormat.AMR,
        ImportedResourceDetectedFormat.MP3,
        ImportedResourceDetectedFormat.WAV,
        ImportedResourceDetectedFormat.AAC,
        -> true
        else -> resolvedMimeType?.let { mime ->
            mime.startsWith("audio/") && mime != "audio/silk" && mime != "audio/x-silk"
        } ?: false
    }
}

private fun detectFormatFromHeader(
    header: ByteArray,
    length: Int,
    fallbackMimeType: String?,
    type: ImportedResourceType,
): ImportedResourceDetectedFormat {
    if (length >= 6) {
        val signature = String(header, 0, 6, Charsets.US_ASCII)
        if (signature == "GIF87a" || signature == "GIF89a") {
            return ImportedResourceDetectedFormat.GIF
        }
    }
    if (length >= 3 &&
        header[0] == 0xFF.toByte() &&
        header[1] == 0xD8.toByte() &&
        header[2] == 0xFF.toByte()
    ) {
        return ImportedResourceDetectedFormat.JPEG
    }
    if (length >= 8 &&
        header[0] == 0x89.toByte() &&
        header[1] == 0x50.toByte() &&
        header[2] == 0x4E.toByte() &&
        header[3] == 0x47.toByte() &&
        header[4] == 0x0D.toByte() &&
        header[5] == 0x0A.toByte() &&
        header[6] == 0x1A.toByte() &&
        header[7] == 0x0A.toByte()
    ) {
        return ImportedResourceDetectedFormat.PNG
    }
    if (length >= 12 &&
        String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
    ) {
        return ImportedResourceDetectedFormat.WEBP
    }
    if (containsAscii(header, length, "#!SILK_V3")) {
        return ImportedResourceDetectedFormat.SILK
    }
    if (containsAscii(header, length, "#!AMR")) {
        return ImportedResourceDetectedFormat.AMR
    }
    if (length >= 12 &&
        String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
    ) {
        return ImportedResourceDetectedFormat.WAV
    }
    if (length >= 3 &&
        header[0] == 'I'.code.toByte() &&
        header[1] == 'D'.code.toByte() &&
        header[2] == '3'.code.toByte()
    ) {
        return ImportedResourceDetectedFormat.MP3
    }
    return when (fallbackMimeType?.lowercase()) {
        "image/gif" -> ImportedResourceDetectedFormat.GIF
        "image/jpeg", "image/jpg" -> ImportedResourceDetectedFormat.JPEG
        "image/png" -> ImportedResourceDetectedFormat.PNG
        "image/webp" -> ImportedResourceDetectedFormat.WEBP
        "audio/amr", "audio/3gpp" -> ImportedResourceDetectedFormat.AMR
        "audio/mpeg", "audio/mp3" -> ImportedResourceDetectedFormat.MP3
        "audio/wav", "audio/x-wav", "audio/wave" -> ImportedResourceDetectedFormat.WAV
        "audio/aac", "audio/mp4", "audio/m4a" -> ImportedResourceDetectedFormat.AAC
        "video/mp4" -> ImportedResourceDetectedFormat.MP4
        else -> when (type) {
            ImportedResourceType.VIDEO -> ImportedResourceDetectedFormat.MP4
            else -> ImportedResourceDetectedFormat.UNKNOWN
        }
    }
}

private fun resolveMimeType(
    detectedFormat: ImportedResourceDetectedFormat,
    fallbackMimeType: String?,
    type: ImportedResourceType,
): String? {
    return when (detectedFormat) {
        ImportedResourceDetectedFormat.GIF -> "image/gif"
        ImportedResourceDetectedFormat.JPEG -> "image/jpeg"
        ImportedResourceDetectedFormat.PNG -> "image/png"
        ImportedResourceDetectedFormat.WEBP -> "image/webp"
        ImportedResourceDetectedFormat.SILK -> "audio/x-silk"
        ImportedResourceDetectedFormat.AMR -> "audio/amr"
        ImportedResourceDetectedFormat.MP3 -> "audio/mpeg"
        ImportedResourceDetectedFormat.WAV -> "audio/wav"
        ImportedResourceDetectedFormat.AAC -> "audio/aac"
        ImportedResourceDetectedFormat.MP4 -> "video/mp4"
        ImportedResourceDetectedFormat.UNKNOWN -> fallbackMimeType ?: when (type) {
            ImportedResourceType.IMAGE -> "image/*"
            ImportedResourceType.VIDEO -> "video/*"
            ImportedResourceType.AUDIO -> "audio/*"
            ImportedResourceType.FILE,
            ImportedResourceType.UNKNOWN,
            -> null
        }
    }
}

private fun containsAscii(
    header: ByteArray,
    length: Int,
    signature: String,
): Boolean {
    if (length <= 0) return false
    val target = signature.toByteArray(Charsets.US_ASCII)
    if (target.isEmpty() || target.size > length) return false
    for (offset in 0..(length - target.size)) {
        var match = true
        for (index in target.indices) {
            if (header[offset + index] != target[index]) {
                match = false
                break
            }
        }
        if (match) return true
    }
    return false
}

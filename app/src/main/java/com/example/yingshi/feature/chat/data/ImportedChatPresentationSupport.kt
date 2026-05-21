package com.example.yingshi.feature.chat.data

import com.example.yingshi.feature.chat.QFaceCatalog
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

data class ImportedMessagePresentation(
    val previewText: String,
    val searchText: String,
)

data class SearchPreviewSnippet(
    val previewText: String,
    val highlightRange: IntRange?,
)

fun buildImportedMessagePresentation(
    type: String,
    text: String,
    rawContentJson: String,
    rawMessageJson: String?,
    replyPreviewText: String?,
    jsonTitle: String?,
    jsonSummary: String?,
    callSummary: String?,
    system: Boolean,
    recalled: Boolean,
    qFaceCatalog: QFaceCatalog?,
): ImportedMessagePresentation {
    if (system) {
        val preview = text.ifBlank { "系统消息" }
        return ImportedMessagePresentation(
            previewText = preview,
            searchText = normalizeImportedSearchText(preview),
        )
    }
    if (recalled) {
        val preview = text.ifBlank { "撤回消息" }
        return ImportedMessagePresentation(
            previewText = preview,
            searchText = normalizeImportedSearchText(preview),
        )
    }

    val segments = parseImportedPreviewSegments(
        type = type,
        text = text,
        rawContentJson = rawContentJson,
        rawMessageJson = rawMessageJson,
        replyPreviewText = replyPreviewText,
        jsonTitle = jsonTitle,
        jsonSummary = jsonSummary,
        callSummary = callSummary,
        qFaceCatalog = qFaceCatalog,
    )
    val preview = segments.joinToString(separator = "")
        .replace("\\s+".toRegex(), " ")
        .trim()
    val search = normalizeImportedSearchText(
        buildString {
            append(preview)
            if (!replyPreviewText.isNullOrBlank()) {
                append(' ')
                append(replyPreviewText)
            }
            if (!jsonTitle.isNullOrBlank()) {
                append(' ')
                append(jsonTitle)
            }
            if (!jsonSummary.isNullOrBlank()) {
                append(' ')
                append(jsonSummary)
            }
            if (!callSummary.isNullOrBlank()) {
                append(' ')
                append(callSummary)
            }
        },
    )
    return ImportedMessagePresentation(
        previewText = preview.ifBlank { fallbackImportedPreviewText(type, text, rawContentJson, rawMessageJson) },
        searchText = search,
    )
}

fun summarizeImportedUnsupportedMessage(
    type: String,
    rawJson: String,
): String {
    val normalizedType = type.trim().lowercase(Locale.ROOT)
    val normalizedJson = rawJson.lowercase(Locale.ROOT)
    return when {
        "闪传" in rawJson || "flash" in normalizedJson || "transfer" in normalizedJson -> "闪传消息"
        normalizedType == "json" || normalizedJson.contains("\"app\"") || normalizedJson.contains("\"prompt\"") -> "分享卡片"
        normalizedType == "xml" || normalizedJson.contains("xmlelement") || normalizedJson.contains("<?xml") -> "XML 消息"
        normalizedJson.contains("jsongraytipelement") || normalizedJson.contains("graytip") -> "系统提示"
        normalizedType == "market_face" -> "表情消息"
        normalizedType.isBlank() || normalizedType == "unknown" -> "暂不支持的消息"
        else -> "暂不支持的 $type 消息"
    }
}

fun resolveImportedFaceLabel(
    faceId: String?,
    faceName: String?,
    qFaceCatalog: QFaceCatalog?,
): String {
    qFaceCatalog?.resolveDetailed(faceId, faceName)?.let { resolved ->
        return resolved.label.ifBlank { fallbackImportedFaceLabel(faceName, faceId) }
    }
    return fallbackImportedFaceLabel(faceName, faceId)
}

private fun parseImportedPreviewSegments(
    type: String,
    text: String,
    rawContentJson: String,
    rawMessageJson: String?,
    replyPreviewText: String?,
    jsonTitle: String?,
    jsonSummary: String?,
    callSummary: String?,
    qFaceCatalog: QFaceCatalog?,
): List<String> {
    val root = runCatching {
        JsonParser.parseString(rawContentJson).asJsonObject
    }.getOrNull() ?: return fallbackImportedPreviewSegments(
        type = type,
        text = text,
        rawContentJson = rawContentJson,
        rawMessageJson = rawMessageJson,
        replyPreviewText = replyPreviewText,
        jsonTitle = jsonTitle,
        jsonSummary = jsonSummary,
        callSummary = callSummary,
    )
    val elements = root.getAsJsonArray("elements").orEmptyJsonArray()
    if (elements.size() == 0) {
        return fallbackImportedPreviewSegments(
            type = type,
            text = text,
            rawContentJson = rawContentJson,
            rawMessageJson = rawMessageJson,
            replyPreviewText = replyPreviewText,
            jsonTitle = jsonTitle,
            jsonSummary = jsonSummary,
            callSummary = callSummary,
        )
    }
    val segments = mutableListOf<String>()
    elements.forEach { element ->
        val objectValue = element.asJsonObjectOrNull() ?: return@forEach
        val elementType = objectValue.string("type").orEmpty()
        val data = objectValue.getAsJsonObject("data") ?: JsonObject()
        when (elementType) {
            "text", "at" -> {
                data.string("text")?.takeIf { it.isNotBlank() }?.let(segments::add)
            }
            "face" -> {
                val label = resolveImportedFaceLabel(
                    faceId = data.string("id"),
                    faceName = data.string("name"),
                    qFaceCatalog = qFaceCatalog,
                )
                segments += "[$label]"
            }
            "image", "market_face" -> segments += "[图片]"
            "video" -> segments += "[视频]"
            "audio" -> segments += "[语音]"
            "file" -> segments += "[文件]"
            "reply" -> if (segments.isEmpty()) segments += "[回复]"
            "json" -> {
                val titleValue = data.string("title")
                val summaryValue = data.string("summary") ?: data.string("description")
                segments += when {
                    !titleValue.isNullOrBlank() -> titleValue
                    !summaryValue.isNullOrBlank() -> summaryValue
                    else -> "分享卡片"
                }
            }
            "av_record" -> {
                segments += data.string("summary")
                    ?: data.string("text")
                    ?: "通话记录"
            }
            else -> {
                segments += summarizeImportedUnsupportedMessage(
                    type = elementType.ifBlank { type },
                    rawJson = objectValue.toString(),
                )
            }
        }
    }
    return segments.ifEmpty {
        fallbackImportedPreviewSegments(
            type = type,
            text = text,
            rawContentJson = rawContentJson,
            rawMessageJson = rawMessageJson,
            replyPreviewText = replyPreviewText,
            jsonTitle = jsonTitle,
            jsonSummary = jsonSummary,
            callSummary = callSummary,
        )
    }
}

private fun fallbackImportedPreviewSegments(
    type: String,
    text: String,
    rawContentJson: String,
    rawMessageJson: String?,
    replyPreviewText: String?,
    jsonTitle: String?,
    jsonSummary: String?,
    callSummary: String?,
): List<String> {
    return buildList {
        when {
            !jsonTitle.isNullOrBlank() -> add(jsonTitle)
            !jsonSummary.isNullOrBlank() -> add(jsonSummary)
            !callSummary.isNullOrBlank() -> add(callSummary)
            !replyPreviewText.isNullOrBlank() -> add("[回复] $replyPreviewText")
            text.isNotBlank() && !looksLikeSerializedPayload(text) -> add(text)
            else -> add(fallbackImportedPreviewText(type, text, rawContentJson, rawMessageJson))
        }
    }
}

private fun fallbackImportedPreviewText(
    type: String,
    text: String,
    rawContentJson: String,
    rawMessageJson: String?,
): String {
    return when {
        text.isNotBlank() && !looksLikeSerializedPayload(text) -> text
        else -> summarizeImportedUnsupportedMessage(
            type = type,
            rawJson = buildString {
                append(rawContentJson)
                if (!rawMessageJson.isNullOrBlank()) {
                    append('\n')
                    append(rawMessageJson)
                }
            },
        )
    }
}

private fun normalizeImportedSearchText(text: String): String {
    return text.replace("[", " ")
        .replace("]", " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
        .lowercase(Locale.ROOT)
}

fun buildImportedSearchPreviewSnippet(
    previewText: String,
    query: String,
    contextChars: Int = 20,
): SearchPreviewSnippet {
    val normalizedQuery = query.trim()
    val compactPreview = previewText.replace("\\s+".toRegex(), " ").trim()
    if (compactPreview.isBlank() || normalizedQuery.isBlank()) {
        return SearchPreviewSnippet(
            previewText = compactPreview,
            highlightRange = null,
        )
    }
    val lowerPreview = compactPreview.lowercase(Locale.ROOT)
    val lowerQuery = normalizedQuery.lowercase(Locale.ROOT)
    val matchStart = lowerPreview.indexOf(lowerQuery)
    if (matchStart < 0) {
        return SearchPreviewSnippet(
            previewText = compactPreview,
            highlightRange = null,
        )
    }
    val matchEndExclusive = (matchStart + normalizedQuery.length).coerceAtMost(compactPreview.length)
    val snippetStart = (matchStart - contextChars).coerceAtLeast(0)
    val snippetEnd = (matchEndExclusive + contextChars).coerceAtMost(compactPreview.length)
    val prefixEllipsis = snippetStart > 0
    val suffixEllipsis = snippetEnd < compactPreview.length
    val visibleText = compactPreview.substring(snippetStart, snippetEnd)
    val finalText = buildString {
        if (prefixEllipsis) append('…')
        append(visibleText)
        if (suffixEllipsis) append('…')
    }
    val prefixOffset = if (prefixEllipsis) 1 else 0
    return SearchPreviewSnippet(
        previewText = finalText,
        highlightRange = (matchStart - snippetStart + prefixOffset) until
            (matchEndExclusive - snippetStart + prefixOffset),
    )
}

private fun fallbackImportedFaceLabel(
    faceName: String?,
    faceId: String?,
): String {
    val normalized = faceName
        ?.trim()
        .orEmpty()
        .removePrefix("/")
        .trim()
    if (normalized.isNotBlank()) return normalized
    return faceId?.trim()?.takeIf { it.isNotBlank() }?.let { "表情" } ?: "表情"
}

private fun looksLikeSerializedPayload(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.length < 40) return false
    return (normalized.startsWith("{") && normalized.endsWith("}")) ||
        (normalized.startsWith("[") && normalized.endsWith("]")) ||
        normalized.contains("\"app\"") ||
        normalized.contains("\"prompt\"") ||
        normalized.contains("\"type\"")
}

private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (isJsonObject) asJsonObject else null
}

private fun JsonObject.string(name: String): String? = get(name).asStringOrNull()

private fun JsonElement?.asStringOrNull(): String? {
    if (this == null || isJsonNull) return null
    return runCatching { asString }.getOrNull()
}

private fun JsonArray?.orEmptyJsonArray(): JsonArray = this ?: JsonArray()

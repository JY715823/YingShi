package com.example.yingshi.feature.chat

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private const val QFaceAssetRootPrefix = "chat/qqface/"
private const val QFaceIndexAssetPath = "${QFaceAssetRootPrefix}assets/qq_emoji/_index.json"
private const val QFaceStaticPngType = 0
private const val QFaceAssetUriPrefix = "file:///android_asset/"

data class QFaceEmojiAsset(
    val emojiId: String,
    val rawName: String,
    val normalizedName: String,
    val assetPath: String,
) {
    val assetUri: String
        get() = "$QFaceAssetUriPrefix$assetPath"
}

enum class QFaceResolveSource {
    ID_AND_NAME,
    NAME,
    ID,
    ALIAS,
    FALLBACK,
}

data class QFaceResolvedEmoji(
    val asset: QFaceEmojiAsset?,
    val label: String,
    val source: QFaceResolveSource,
)

sealed interface QFaceTextToken {
    data class Text(
        val text: String,
    ) : QFaceTextToken

    data class Emoji(
        val resolved: QFaceResolvedEmoji,
    ) : QFaceTextToken
}

class QFaceCatalog private constructor(
    private val byId: Map<String, QFaceEmojiAsset>,
    private val byNormalizedName: Map<String, QFaceEmojiAsset>,
    private val rawNameMatches: List<QFaceEmojiAsset>,
) {
    fun resolve(
        faceId: String?,
        faceName: String?,
    ): QFaceEmojiAsset? {
        return resolveDetailed(faceId, faceName)?.asset
    }

    fun resolveDetailed(
        faceId: String?,
        faceName: String?,
    ): QFaceResolvedEmoji? {
        val normalizedId = faceId?.trim().orEmpty()
        val normalizedName = normalizeQFaceName(faceName)
        val canonicalNameFromId = normalizedId.takeIf { it.isNotBlank() }?.let(byId::get)
        val canonicalNameFromName = normalizedName.takeIf { it.isNotBlank() }?.let(byNormalizedName::get)
        if (canonicalNameFromId != null && canonicalNameFromName != null) {
            return if (canonicalNameFromId.normalizedName == canonicalNameFromName.normalizedName) {
                QFaceResolvedEmoji(
                    asset = canonicalNameFromId,
                    label = canonicalNameFromId.normalizedName,
                    source = QFaceResolveSource.ID_AND_NAME,
                )
            } else {
                QFaceResolvedEmoji(
                    asset = canonicalNameFromName,
                    label = canonicalNameFromName.normalizedName,
                    source = QFaceResolveSource.NAME,
                )
            }
        }
        if (canonicalNameFromName != null) {
            return QFaceResolvedEmoji(
                asset = canonicalNameFromName,
                label = canonicalNameFromName.normalizedName,
                source = QFaceResolveSource.NAME,
            )
        }
        if (normalizedId.isNotBlank()) {
            byId[normalizedId]?.let {
                return QFaceResolvedEmoji(
                    asset = it,
                    label = it.normalizedName,
                    source = QFaceResolveSource.ID,
                )
            }
        }
        if (normalizedName.isNotBlank()) {
            qFaceNameAliases[normalizedName]?.let { alias ->
                byNormalizedName[alias]?.let {
                    return QFaceResolvedEmoji(
                        asset = it,
                        label = it.normalizedName,
                        source = QFaceResolveSource.ALIAS,
                    )
                }
            }
            return QFaceResolvedEmoji(
                asset = null,
                label = normalizedName,
                source = QFaceResolveSource.FALLBACK,
            )
        }
        return null
    }

    fun tokenize(text: String): List<QFaceTextToken> {
        if (text.isBlank()) return listOf(QFaceTextToken.Text(text))
        resolveDetailed(faceId = null, faceName = text.trim())?.let { resolved ->
            val trimmed = text.trim()
            if (trimmed == resolved.label || trimmed == "/${resolved.label}") {
                return listOf(QFaceTextToken.Emoji(resolved))
            }
        }
        val output = mutableListOf<QFaceTextToken>()
        val pendingText = StringBuilder()

        fun flushText() {
            if (pendingText.isNotEmpty()) {
                output += QFaceTextToken.Text(pendingText.toString())
                pendingText.clear()
            }
        }

        var index = 0
        while (index < text.length) {
            val matched = if (text[index] == '/') {
                rawNameMatches.firstOrNull { candidate ->
                    text.regionMatches(index, candidate.rawName, 0, candidate.rawName.length)
                }
            } else {
                null
            }
            if (matched != null) {
                flushText()
                output += QFaceTextToken.Emoji(
                    QFaceResolvedEmoji(
                        asset = matched,
                        label = matched.normalizedName,
                        source = QFaceResolveSource.NAME,
                    ),
                )
                index += matched.rawName.length
            } else {
                pendingText.append(text[index])
                index += 1
            }
        }
        flushText()
        return output.ifEmpty { listOf(QFaceTextToken.Text(text)) }
    }

    companion object {
        fun load(context: Context): QFaceCatalog? {
            val assetManager = context.applicationContext.assets
            return runCatching {
                val rawJson = assetManager.open(QFaceIndexAssetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JsonParser.parseString(rawJson).asJsonArray
                val entries = root.mapNotNull { item ->
                    val entry = item.asJsonObjectOrNull() ?: return@mapNotNull null
                    val emojiId = entry.string("emojiId").orEmpty().trim()
                    val rawName = entry.string("describe").orEmpty().trim()
                    val normalizedName = normalizeQFaceName(rawName)
                    if (emojiId.isBlank() || normalizedName.isBlank()) return@mapNotNull null
                    val assetPath = entry.getAsJsonArray("assets")
                        .orEmptyJsonArray()
                        .firstObject { asset ->
                            asset.int("type") == QFaceStaticPngType &&
                                !asset.string("path").isNullOrBlank()
                        }
                        ?.string("path")
                        ?.trim()
                        ?.removePrefix("/")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "$QFaceAssetRootPrefix$it" }
                        ?: return@mapNotNull null
                    if (!assetManager.assetExists(assetPath)) return@mapNotNull null
                    QFaceEmojiAsset(
                        emojiId = emojiId,
                        rawName = rawName,
                        normalizedName = normalizedName,
                        assetPath = assetPath,
                    )
                }
                if (entries.isEmpty()) return null
                QFaceCatalog(
                    byId = entries.associateBy { it.emojiId },
                    byNormalizedName = entries.associateBy { it.normalizedName },
                    rawNameMatches = entries
                        .filter { it.rawName.startsWith("/") }
                        .sortedByDescending { it.rawName.length },
                )
            }.getOrNull()
        }
    }
}

object QFaceCatalogStore {
    @Volatile
    private var cached: QFaceCatalog? = null

    @Volatile
    private var loadAttempted = false

    fun getOrLoad(context: Context): QFaceCatalog? {
        cached?.let { return it }
        if (loadAttempted) return null
        return synchronized(this) {
            cached?.let { return@synchronized it }
            if (loadAttempted) return@synchronized null
            cached = QFaceCatalog.load(context)
            loadAttempted = true
            cached
        }
    }
}

private fun normalizeQFaceName(faceName: String?): String {
    return faceName
        ?.trim()
        .orEmpty()
        .removePrefix("/")
        .trim()
        .lowercase()
}

private val qFaceNameAliases = mapOf(
    "得意" to "得意",
    "撇嘴" to "撇嘴",
    "比心" to "比心",
    "敲敲" to "敲敲",
    "大怨种" to "大怨种",
    "超级鼓掌" to "超级鼓掌",
    "虎虎生威" to "虎虎生威",
    "菜汪" to "菜汪",
    "emo" to "emo",
)

private fun JsonArray.firstObject(
    predicate: (JsonObject) -> Boolean,
): JsonObject? {
    forEach { element ->
        val objectValue = element.asJsonObjectOrNull() ?: return@forEach
        if (predicate(objectValue)) return objectValue
    }
    return null
}

private fun JsonArray?.orEmptyJsonArray(): JsonArray = this ?: JsonArray()

private fun JsonObject.asJsonObjectOrNull(): JsonObject? {
    return this
}

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return takeIf { isJsonObject }?.asJsonObject
}

private fun JsonObject.string(key: String): String? {
    val value = get(key) ?: return null
    return if (value.isJsonNull) null else value.asString
}

private fun JsonObject.int(key: String): Int? {
    val value = get(key) ?: return null
    return if (value.isJsonNull) null else value.asInt
}

private fun AssetManager.assetExists(path: String): Boolean {
    return runCatching {
        open(path).close()
    }.isSuccess
}

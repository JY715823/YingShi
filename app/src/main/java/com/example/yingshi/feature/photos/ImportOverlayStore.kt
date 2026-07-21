package com.example.yingshi.feature.photos

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.MutationEvent
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.MutationKind
import org.json.JSONArray
import org.json.JSONObject

internal object ImportOverlayStore {
    private const val ImportOverlayPreferencesName = "system_media_import_overlay"
    private const val ImportOverlayItemsKey = "items_json"
    private const val InvalidatedAppMediaIdsKey = "invalidated_app_media_ids"

    private val appMediaIdBySystemSourceKey = linkedMapOf<String, String>()
    private val linkedPostIdsByMediaId = linkedMapOf<String, LinkedHashSet<String>>()
    private val hiddenMediaIds = linkedSetOf<String>()
    private val invalidatedAppMediaIds = linkedSetOf<String>()
    private var overlayPreferences: SharedPreferences? = null
    private var persistentOverlayLoaded = false

    internal var mutationVersion by mutableIntStateOf(0)
        private set
    internal var latestMutationEvent by mutableStateOf(MutationEvent())
        private set

    internal fun applyOverlay(items: List<SystemMediaItem>): List<SystemMediaItem> {
        return items
            .filterNot { hiddenMediaIds.contains(it.id) }
            .map { item ->
                val importedMediaId = knownAppMediaIdForSource(item)
                    ?: item.importedAppMediaId?.takeIf { it.isNotBlank() && it !in invalidatedAppMediaIds }
                val linkedPostIds = linkedPostIdsByMediaId[item.id]
                    ?: importedMediaId?.let { linkedPostIdsByMediaId[it] }
                item.copy(
                    importedAppMediaId = importedMediaId,
                    linkedSmallAlbumIds = linkedPostIds?.toList() ?: item.linkedSmallAlbumIds,
                    linkedPostIds = linkedPostIds?.toList() ?: item.linkedPostIds,
                )
            }
    }

    internal fun rememberImportStatus(
        item: SystemMediaItem,
        appMediaId: String,
        smallAlbumIds: List<String>,
    ) {
        rememberAppMediaIdForSource(item, appMediaId)
        linkedPostIdsByMediaId[item.id] = linkedSetOf<String>().apply {
            addAll(smallAlbumIds.filter { it.isNotBlank() })
        }
        linkedPostIdsByMediaId[appMediaId] = linkedSetOf<String>().apply {
            addAll(smallAlbumIds.filter { it.isNotBlank() })
        }
        invalidatedAppMediaIds.remove(appMediaId)
        persistImportOverlay()
        publishMutation(MutationKind.OVERLAY_ONLY, setOf(item.id))
    }

    internal fun forgetImportStatus(item: SystemMediaItem) {
        var changed = false
        item.stableImportSourceKeys().forEach { sourceKey ->
            if (appMediaIdBySystemSourceKey.remove(sourceKey) != null) {
                changed = true
            }
        }
        if (changed) {
            persistImportOverlay()
            publishMutation(MutationKind.OVERLAY_ONLY, setOf(item.id))
        }
    }

    /** 查询本地 overlay 缓存，返回 (appMediaId, smallAlbumIds) 或 null。不修改缓存。 */
    internal fun peekImportStatus(item: SystemMediaItem): Pair<String, List<String>>? {
        val appMediaId = item.stableImportSourceKeys().firstNotNullOfOrNull { sourceKey ->
            appMediaIdBySystemSourceKey[sourceKey]
        } ?: return null
        val smallAlbumIds = linkedPostIdsByMediaId[appMediaId]?.toList() ?: emptyList()
        return appMediaId to smallAlbumIds
    }

    internal fun forgetImportStatusByAppMediaId(appMediaId: String) {
        if (appMediaId.isBlank()) return
        invalidatedAppMediaIds += appMediaId
        val keysToRemove = appMediaIdBySystemSourceKey.entries
            .filter { it.value == appMediaId }
            .map { it.key }
        linkedPostIdsByMediaId.remove(appMediaId)
        if (keysToRemove.isEmpty()) {
            publishMutation(MutationKind.OVERLAY_ONLY)
            return
        }
        keysToRemove.forEach { appMediaIdBySystemSourceKey.remove(it) }
        linkedPostIdsByMediaId.entries.removeAll { (mediaId, _) ->
            mediaId == appMediaId || mediaId in keysToRemove
        }
        persistImportOverlay()
        publishMutation(MutationKind.OVERLAY_ONLY)
    }

    internal fun forgetImportStatusByAppMediaIds(appMediaIds: Collection<String>) {
        appMediaIds.filter { it.isNotBlank() }.distinct().forEach(::forgetImportStatusByAppMediaId)
    }

    internal fun moveToSimulatedSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        var changedCount = 0
        val normalizedIds = mediaIds.distinct()
        normalizedIds.forEach { mediaId ->
            if (hiddenMediaIds.add(mediaId)) {
                changedCount += 1
            }
        }
        if (changedCount > 0) {
            publishMutation(
                kind = MutationKind.MEDIA_STORE_CHANGED,
                mediaIds = normalizedIds,
            )
        }
        return changedCount
    }

    internal fun markMovedToSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        return moveToSimulatedSystemTrash(mediaIds)
    }

    internal fun knownAppMediaIdForSource(
        item: SystemMediaItem,
    ): String? {
        item.importedAppMediaId
            ?.takeIf { it.isNotBlank() && it !in invalidatedAppMediaIds }
            ?.let { return it }
        item.stableImportSourceKeys().forEach { sourceKey ->
            appMediaIdBySystemSourceKey[sourceKey]
                ?.takeIf { it !in invalidatedAppMediaIds }
                ?.let { return it }
        }
        if (RepositoryProvider.currentMode != RepositoryMode.REAL) {
            FakePhotoFeedRepository.findPhotoFeedItem(item.id)?.mediaId?.let { mediaId ->
                rememberAppMediaIdForSource(item, mediaId)
                return mediaId
            }
        }
        return null
    }

    internal fun rememberAppMediaIdForSource(
        item: SystemMediaItem,
        appMediaId: String,
    ) {
        if (appMediaId.isBlank()) return
        invalidatedAppMediaIds.remove(appMediaId)
        item.stableImportSourceKeys().forEach { sourceKey ->
            appMediaIdBySystemSourceKey[sourceKey] = appMediaId
        }
        persistImportOverlay()
    }

    internal fun linkMediaToPost(
        mediaIds: Collection<String>,
        postId: String,
    ) {
        var changed = false
        mediaIds.distinct().forEach { mediaId ->
            val postIds = linkedPostIdsByMediaId.getOrPut(mediaId) { linkedSetOf() }
            changed = postIds.add(postId) || changed
        }
        if (changed) {
            publishMutation(
                kind = MutationKind.OVERLAY_ONLY,
                mediaIds = mediaIds.distinct(),
            )
        }
    }

    internal fun warmPersistentImportOverlay(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            ImportOverlayPreferencesName,
            Context.MODE_PRIVATE,
        )
        overlayPreferences = prefs
        UploadManager.warmPersistentUploadTasks(context)
        if (persistentOverlayLoaded) return
        persistentOverlayLoaded = true
        val raw = prefs.getString(ImportOverlayItemsKey, null)?.takeIf { it.isNotBlank() }
            ?: run {
                warmInvalidatedAppMediaIds(prefs)
                return
            }
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val sourceKey = entry.optString("sourceKey").takeIf { it.isNotBlank() } ?: continue
                val appMediaId = entry.optString("appMediaId").takeIf { it.isNotBlank() } ?: continue
                val smallAlbumIds = entry.optJSONArray("smallAlbumIds").toStringSet()
                appMediaIdBySystemSourceKey[sourceKey] = appMediaId
                if (smallAlbumIds.isNotEmpty()) {
                    linkedPostIdsByMediaId[appMediaId] = linkedSetOf<String>().apply {
                        addAll(smallAlbumIds)
                    }
                }
            }
        }
        warmInvalidatedAppMediaIds(prefs)
    }

    private fun warmInvalidatedAppMediaIds(prefs: SharedPreferences) {
        val raw = prefs.getString(InvalidatedAppMediaIdsKey, null)?.takeIf { it.isNotBlank() }
            ?: return
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val mediaId = array.optString(index).takeIf { it.isNotBlank() } ?: continue
                invalidatedAppMediaIds.add(mediaId)
            }
        }
    }

    internal fun persistImportOverlay() {
        val prefs = overlayPreferences ?: return
        val sourceKeyByAppMediaId = appMediaIdBySystemSourceKey.entries
            .groupBy({ it.value }, { it.key })
        val array = JSONArray()
        appMediaIdBySystemSourceKey.forEach { (sourceKey, appMediaId) ->
            val smallAlbumIds = linkedPostIdsByMediaId[appMediaId]
                ?: sourceKeyByAppMediaId[appMediaId]
                    ?.asSequence()
                    ?.mapNotNull { linkedPostIdsByMediaId[it] }
                    ?.firstOrNull()
                ?: emptySet()
            array.put(
                JSONObject()
                    .put("sourceKey", sourceKey)
                    .put("appMediaId", appMediaId)
                    .put("smallAlbumIds", JSONArray(smallAlbumIds.toList())),
            )
        }
        val invalidatedArray = JSONArray()
        invalidatedAppMediaIds.forEach { invalidatedArray.put(it) }
        prefs.edit()
            .putString(ImportOverlayItemsKey, array.toString())
            .putString(InvalidatedAppMediaIdsKey, invalidatedArray.toString())
            .apply()
    }

    internal fun publishMutation(
        kind: MutationKind,
        mediaIds: Collection<String> = emptyList(),
    ) {
        val nextVersion = mutationVersion + 1
        mutationVersion = nextVersion
        latestMutationEvent = MutationEvent(
            version = nextVersion,
            kind = kind,
            mediaIds = mediaIds.filter { it.isNotBlank() }.toSet(),
        )
    }
}

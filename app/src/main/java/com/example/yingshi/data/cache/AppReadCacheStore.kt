package com.example.yingshi.data.cache

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class CachedPayload<T>(
    val cachedAtMillis: Long,
    val payload: T,
)

data class ReadCacheSummary(
    val fileCount: Int,
    val totalBytes: Long,
    val lastUpdatedAtMillis: Long?,
)

data class CachedPhotoFeed(
    val items: List<RemoteMedia>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

data class CachedAlbumDirectory(
    val albums: List<RemoteAlbum>,
    val postsByAlbumId: Map<String, List<RemotePostSummary>>,
    val previewMediaByPostId: Map<String, List<RemotePostMedia>>? = null,
)

data class CachedNotifications(
    val items: List<RemoteNotification>,
)

data class CachedTrashList(
    val selectedType: String?,
    val items: List<RemoteTrashItem>,
)

private data class CacheEnvelope<T>(
    val schemaVersion: Int,
    val cachedAtMillis: Long,
    val baseUrl: String,
    val userId: String?,
    val payload: T,
)

object AppReadCacheStore {
    private const val SCHEMA_VERSION = 1
    private const val DIRECTORY_NAME = "read-cache"
    private const val CURRENT_USER_SCOPE = "current-user"
    private const val PHOTO_FEED_SCOPE = "photo-feed"
    private const val ALBUM_DIRECTORY_SCOPE = "album-directory"
    private const val NOTIFICATION_LIST_SCOPE = "notification-list"
    private const val NOTIFICATION_DETAIL_SCOPE = "notification-detail"
    private const val TRASH_LIST_SCOPE = "trash-list"
    private const val TRASH_DETAIL_SCOPE = "trash-detail"

    private val gson = Gson()
    private val lock = Any()
    private var cacheDirectory: File? = null
    var changeVersion by mutableIntStateOf(0)
        private set

    private val remoteNotificationListType: Type =
        object : TypeToken<List<RemoteNotification>>() {}.type
    private val remotePostSummaryMapType: Type =
        object : TypeToken<Map<String, List<RemotePostSummary>>>() {}.type

    fun init(context: Context) {
        if (cacheDirectory != null) {
            return
        }
        cacheDirectory = context.applicationContext.filesDir.resolve(DIRECTORY_NAME).also {
            it.mkdirs()
        }
    }

    fun readCurrentUser(): CachedPayload<RemoteCurrentUser>? {
        return read(
            scope = CURRENT_USER_SCOPE,
            userId = null,
            payloadType = RemoteCurrentUser::class.java,
        )
    }

    fun writeCurrentUser(user: RemoteCurrentUser) {
        write(
            scope = CURRENT_USER_SCOPE,
            userId = null,
            payload = user,
            payloadType = RemoteCurrentUser::class.java,
        )
    }

    fun clearCurrentUser() {
        synchronized(lock) {
            val deleted = fileFor(scope = CURRENT_USER_SCOPE, userId = null).delete()
            if (deleted) {
                changeVersion += 1
            }
        }
    }

    fun readPhotoFeed(userId: String): CachedPayload<CachedPhotoFeed>? {
        return read(
            scope = PHOTO_FEED_SCOPE,
            userId = userId,
            payloadType = CachedPhotoFeed::class.java,
        )
    }

    fun writePhotoFeed(
        userId: String,
        payload: CachedPhotoFeed,
    ) {
        write(
            scope = PHOTO_FEED_SCOPE,
            userId = userId,
            payload = payload,
            payloadType = CachedPhotoFeed::class.java,
        )
    }

    fun readAlbumDirectory(userId: String): CachedPayload<CachedAlbumDirectory>? {
        return read(
            scope = ALBUM_DIRECTORY_SCOPE,
            userId = userId,
            payloadType = CachedAlbumDirectory::class.java,
        )
    }

    fun writeAlbumDirectory(
        userId: String,
        payload: CachedAlbumDirectory,
    ) {
        write(
            scope = ALBUM_DIRECTORY_SCOPE,
            userId = userId,
            payload = payload,
            payloadType = CachedAlbumDirectory::class.java,
        )
    }

    fun readNotifications(userId: String): CachedPayload<CachedNotifications>? {
        return read(
            scope = NOTIFICATION_LIST_SCOPE,
            userId = userId,
            payloadType = CachedNotifications::class.java,
        )
    }

    fun writeNotifications(
        userId: String,
        notifications: List<RemoteNotification>,
    ) {
        write(
            scope = NOTIFICATION_LIST_SCOPE,
            userId = userId,
            payload = CachedNotifications(items = notifications),
            payloadType = CachedNotifications::class.java,
        )
    }

    fun readNotification(
        userId: String,
        notificationId: String,
    ): CachedPayload<RemoteNotification>? {
        return read(
            scope = "$NOTIFICATION_DETAIL_SCOPE-$notificationId",
            userId = userId,
            payloadType = RemoteNotification::class.java,
        ) ?: readNotifications(userId)?.payload?.items
            ?.firstOrNull { it.notificationId == notificationId }
            ?.let { notification ->
                CachedPayload(
                    cachedAtMillis = readNotifications(userId)?.cachedAtMillis ?: System.currentTimeMillis(),
                    payload = notification,
                )
            }
    }

    fun writeNotification(
        userId: String,
        notification: RemoteNotification,
    ) {
        write(
            scope = "$NOTIFICATION_DETAIL_SCOPE-${notification.notificationId}",
            userId = userId,
            payload = notification,
            payloadType = RemoteNotification::class.java,
        )
    }

    fun readTrashList(
        userId: String,
        selectedType: String?,
    ): CachedPayload<CachedTrashList>? {
        return read(
            scope = "$TRASH_LIST_SCOPE-${selectedType ?: "all"}",
            userId = userId,
            payloadType = CachedTrashList::class.java,
        )
    }

    fun writeTrashList(
        userId: String,
        selectedType: String?,
        items: List<RemoteTrashItem>,
    ) {
        write(
            scope = "$TRASH_LIST_SCOPE-${selectedType ?: "all"}",
            userId = userId,
            payload = CachedTrashList(
                selectedType = selectedType,
                items = items,
            ),
            payloadType = CachedTrashList::class.java,
        )
    }

    fun readTrashDetail(
        userId: String,
        entryId: String,
    ): CachedPayload<RemoteTrashDetail>? {
        return read(
            scope = "$TRASH_DETAIL_SCOPE-$entryId",
            userId = userId,
            payloadType = RemoteTrashDetail::class.java,
        )
    }

    fun writeTrashDetail(
        userId: String,
        entryId: String,
        detail: RemoteTrashDetail,
    ) {
        write(
            scope = "$TRASH_DETAIL_SCOPE-$entryId",
            userId = userId,
            payload = detail,
            payloadType = RemoteTrashDetail::class.java,
        )
    }

    fun summary(): ReadCacheSummary {
        synchronized(lock) {
            val files = cacheDirectoryOrNull()?.listFiles()?.filter(File::isFile).orEmpty()
            return ReadCacheSummary(
                fileCount = files.size,
                totalBytes = files.sumOf(File::length),
                lastUpdatedAtMillis = files.maxOfOrNull(File::lastModified)?.takeIf { it > 0L },
            )
        }
    }

    fun clearProtectedData(): Boolean {
        synchronized(lock) {
            val directory = cacheDirectoryOrNull() ?: return true
            val files = directory.listFiles().orEmpty()
            var deletedAny = false
            val cleared = files.fold(true) { allCleared, file ->
                if (file.isDirectory) {
                    val deleted = file.deleteRecursively()
                    deletedAny = deletedAny || deleted
                    allCleared && deleted
                } else {
                    val deleted = file.delete()
                    deletedAny = deletedAny || deleted
                    allCleared && deleted
                }
            }
            if (deletedAny) {
                changeVersion += 1
            }
            return cleared
        }
    }

    private fun cacheDirectoryOrNull(): File? {
        return cacheDirectory
    }

    private fun cacheDirectory(): File {
        return requireNotNull(cacheDirectoryOrNull()) {
            "AppReadCacheStore must be initialized before use."
        }
    }

    private fun <T> read(
        scope: String,
        userId: String?,
        payloadType: Type,
    ): CachedPayload<T>? {
        synchronized(lock) {
            val targetFile = fileFor(scope = scope, userId = userId)
            if (!targetFile.exists()) {
                return null
            }
            return runCatching {
                val envelopeType = envelopeType(payloadType)
                val envelope = gson.fromJson<CacheEnvelope<T>>(
                    targetFile.readText(StandardCharsets.UTF_8),
                    envelopeType,
                ) ?: return null
                if (envelope.schemaVersion != SCHEMA_VERSION) {
                    targetFile.delete()
                    return null
                }
                if (envelope.baseUrl != BackendDebugConfig.currentBaseUrl()) {
                    return null
                }
                if (userId != null && envelope.userId != userId) {
                    return null
                }
                CachedPayload(
                    cachedAtMillis = envelope.cachedAtMillis,
                    payload = envelope.payload,
                )
            }.getOrElse {
                targetFile.delete()
                null
            }
        }
    }

    private fun <T> write(
        scope: String,
        userId: String?,
        payload: T,
        payloadType: Type,
    ) {
        synchronized(lock) {
            val targetFile = fileFor(scope = scope, userId = userId)
            val envelope = CacheEnvelope(
                schemaVersion = SCHEMA_VERSION,
                cachedAtMillis = System.currentTimeMillis(),
                baseUrl = BackendDebugConfig.currentBaseUrl(),
                userId = userId,
                payload = payload,
            )
            runCatching {
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(
                    gson.toJson(envelope, envelopeType(payloadType)),
                    StandardCharsets.UTF_8,
                )
            }.onSuccess {
                changeVersion += 1
            }
        }
    }

    private fun fileFor(
        scope: String,
        userId: String?,
    ): File {
        return cacheDirectory().resolve(
            buildString {
                append(sanitize(scope))
                append("__")
                append(sha1(BackendDebugConfig.currentBaseUrl()))
                append("__")
                append(sha1(userId ?: "shared"))
                append(".json")
            },
        )
    }

    private fun envelopeType(payloadType: Type): Type {
        return TypeToken.getParameterized(CacheEnvelope::class.java, payloadType).type
    }

    private fun sanitize(scope: String): String {
        return scope.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "cache" }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

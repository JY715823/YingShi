package com.example.yingshi.feature.chat.data

import com.example.yingshi.data.remote.api.ChatApi
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.UpsertChatSnapshotRequestDto
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

data class ChatLocalSnapshot(
    val chats: List<ImportedChatEntity> = emptyList(),
    val participants: List<ImportedParticipantEntity> = emptyList(),
    val messages: List<ImportedMessageEntity> = emptyList(),
    val resources: List<ImportedResourceEntity> = emptyList(),
    val messageSearchEntries: List<ImportedMessageSearchEntity> = emptyList(),
    val readingAnchors: List<StoredChatReadingAnchor> = emptyList(),
)

data class StoredChatReadingAnchor(
    val chatId: Long,
    val messageLocalId: Long,
    val scrollOffset: Int,
    val savedAtMillis: Long,
)

interface ChatSyncBridge {
    suspend fun hydrate(repository: ImportedChatRepository) {}

    suspend fun afterMutation(repository: ImportedChatRepository) {}
}

object NoOpChatSyncBridge : ChatSyncBridge

class RemoteChatSyncBridge(
    private val chatApi: ChatApi = RemoteServiceFactory.chatApi,
    private val gson: Gson = Gson(),
) : ChatSyncBridge {
    private val hydrationMutex = Mutex()
    private var hydrated = false

    override suspend fun hydrate(repository: ImportedChatRepository) {
        if (hydrated || !AuthSessionManager.isLoggedIn) {
            hydrated = true
            return
        }
        hydrationMutex.withLock {
            if (hydrated || !AuthSessionManager.isLoggedIn) return
            val remoteSnapshot = runCatching { chatApi.getSnapshot().data }
                .getOrElse {
                    hydrated = true
                    return
                }
            val payload = remoteSnapshot.payload
            if (payload == null || payload.isJsonNull) {
                pushSnapshot(repository)
            } else {
                val snapshot = gson.fromJson(payload, ChatLocalSnapshot::class.java)
                repository.replaceLocalSnapshot(snapshot)
            }
            hydrated = true
        }
    }

    override suspend fun afterMutation(repository: ImportedChatRepository) {
        if (!hydrated || !AuthSessionManager.isLoggedIn) return
        pushSnapshot(repository)
    }

    private suspend fun pushSnapshot(repository: ImportedChatRepository) {
        val snapshot = repository.exportLocalSnapshot()
        val payload = gson.toJsonTree(snapshot)
        runCatching {
            chatApi.putSnapshot(
                UpsertChatSnapshotRequestDto(
                    payload = payload,
                ),
            )
        }.getOrElse { throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                hydrated = false
            }
        }
    }
}

package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.repository.RepositoryProvider

private data class TrashActorHint(
    val actorUserId: String,
    val trashItemId: String,
    val sourceMediaId: String?,
    val sourcePostId: String?,
    val relatedMediaIds: List<String>,
    val relatedPostIds: List<String>,
)

object TrashActorHintStore {
    private val hintsByTrashItemId = linkedMapOf<String, TrashActorHint>()
    private val hintsBySourceMediaId = linkedMapOf<String, String>()
    private val hintsBySourcePostId = linkedMapOf<String, String>()
    private val hintsByRelatedMediaId = linkedMapOf<String, String>()
    private val hintsByRelatedPostId = linkedMapOf<String, String>()

    @Synchronized
    fun record(
        item: RemoteTrashItem,
        fallbackActorUserId: String? = null,
    ) {
        val actorUserId = item.actorUserId
            ?.takeIf { it.isNotBlank() }
            ?: fallbackActorUserId?.takeIf { it.isNotBlank() }
            ?: return
        val hint = TrashActorHint(
            actorUserId = actorUserId,
            trashItemId = item.trashItemId,
            sourceMediaId = item.sourceMediaId?.takeIf { it.isNotBlank() },
            sourcePostId = item.sourcePostId?.takeIf { it.isNotBlank() },
            relatedMediaIds = item.relatedMediaIds.filter { it.isNotBlank() },
            relatedPostIds = item.relatedPostIds.filter { it.isNotBlank() },
        )
        hintsByTrashItemId[item.trashItemId] = hint
        hint.sourceMediaId?.let { hintsBySourceMediaId[it] = actorUserId }
        hint.sourcePostId?.let { hintsBySourcePostId[it] = actorUserId }
        hint.relatedMediaIds.forEach { mediaId -> hintsByRelatedMediaId[mediaId] = actorUserId }
        hint.relatedPostIds.forEach { postId -> hintsByRelatedPostId[postId] = actorUserId }
    }

    @Synchronized
    fun resolve(entry: TrashEntryUiModel): String? {
        val explicitActor = entry.actorUserId?.takeIf { it.isNotBlank() }
        if (explicitActor != null) return explicitActor
        return hintsByTrashItemId[entry.id]?.actorUserId
            ?: entry.sourceMediaId?.let(hintsBySourceMediaId::get)
            ?: entry.sourcePostId?.let(hintsBySourcePostId::get)
            ?: entry.relatedMediaIds.firstNotNullOfOrNull(hintsByRelatedMediaId::get)
            ?: entry.relatedPostIds.firstNotNullOfOrNull(hintsByRelatedPostId::get)
    }
}

internal fun currentCollaboratorActorUserId(): String? {
    return CollaboratorDirectoryStore.snapshot(
        fallbackToFakeProfile = false,
    ).currentUser?.userId
}

package com.example.yingshi.feature.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object LocalSystemMediaBridgeRepository {
    var mutationVersion by mutableIntStateOf(0)
        private set

    private val hiddenMediaIds = linkedSetOf<String>()
    private val linkedPostIdsByMediaId = linkedMapOf<String, LinkedHashSet<String>>()

    fun applyOverlay(items: List<SystemMediaItem>): List<SystemMediaItem> {
        return items
            .filterNot { hiddenMediaIds.contains(it.id) }
            .map { item ->
                item.copy(
                    linkedPostIds = linkedPostIdsByMediaId[item.id]
                        ?.toList()
                        .orEmpty(),
                )
            }
    }

    fun createPostFromSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        val normalizedItems = mediaItems.distinctBy { it.id }
        val post = FakeAlbumRepository.createLocalPostFromSystemMedia(normalizedItems) ?: return null
        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = post.id,
        )
        linkMediaToPost(
            mediaIds = normalizedItems.map { it.id },
            postId = post.id,
        )
        return post
    }

    fun addSystemMediaToExistingPost(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = mediaItems.distinctBy { it.id }
        val addedCount = FakeAlbumRepository.appendSystemMediaToPost(
            postId = postId,
            mediaItems = normalizedItems,
        )
        if (addedCount <= 0) return 0

        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = postId,
        )
        linkMediaToPost(
            mediaIds = normalizedItems.map { it.id },
            postId = postId,
        )
        return addedCount
    }

    fun moveToSimulatedSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        var changedCount = 0
        mediaIds.distinct().forEach { mediaId ->
            if (hiddenMediaIds.add(mediaId)) {
                changedCount += 1
            }
        }
        if (changedCount > 0) {
            mutationVersion += 1
        }
        return changedCount
    }

    private fun linkMediaToPost(
        mediaIds: Collection<String>,
        postId: String,
    ) {
        var changed = false
        mediaIds.distinct().forEach { mediaId ->
            val postIds = linkedPostIdsByMediaId.getOrPut(mediaId) {
                linkedSetOf()
            }
            changed = postIds.add(postId) || changed
        }
        if (changed) {
            mutationVersion += 1
        }
    }
}

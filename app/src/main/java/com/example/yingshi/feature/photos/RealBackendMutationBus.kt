package com.example.yingshi.feature.photos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RealBackendRefreshScope {
    PHOTO_FEED,
    ALBUMS,
    POST_DETAIL,
    MEDIA_MANAGEMENT,
    TRASH,
    SYSTEM_MEDIA_DESTINATIONS,
}

data class RealBackendMutationEvent(
    val version: Int = 0,
    val scopes: Set<RealBackendRefreshScope> = emptySet(),
    val postIds: Set<String> = emptySet(),
    val mediaIds: Set<String> = emptySet(),
) {
    fun affectsPhotoFeed(): Boolean = affects(RealBackendRefreshScope.PHOTO_FEED)

    fun affectsAlbums(): Boolean = affects(RealBackendRefreshScope.ALBUMS)

    fun affectsTrash(): Boolean = affects(RealBackendRefreshScope.TRASH)

    fun affectsSystemMediaDestinations(): Boolean = affects(RealBackendRefreshScope.SYSTEM_MEDIA_DESTINATIONS)

    fun affectsPostDetail(
        postId: String,
        mediaIdsInPost: Collection<String> = emptyList(),
    ): Boolean {
        return affects(RealBackendRefreshScope.POST_DETAIL) && matches(postId, mediaIdsInPost)
    }

    fun affectsMediaManagement(
        postId: String,
        managedMediaIds: Collection<String> = emptyList(),
    ): Boolean {
        return affects(RealBackendRefreshScope.MEDIA_MANAGEMENT) && matches(postId, managedMediaIds)
    }

    fun isCommentOnly(): Boolean = scopes == RealBackendCommentScopes

    fun isCommentMutationForPostDetail(
        postId: String,
        mediaIdsInPost: Collection<String> = emptyList(),
    ): Boolean {
        return isCommentOnly() && affectsPostDetail(postId, mediaIdsInPost)
    }

    private fun affects(scope: RealBackendRefreshScope): Boolean {
        return scopes.isEmpty() || scopes.contains(scope)
    }

    private fun matches(
        postId: String,
        relatedMediaIds: Collection<String>,
    ): Boolean {
        return (postIds.isEmpty() && mediaIds.isEmpty()) ||
            postIds.contains(postId) ||
            relatedMediaIds.any { mediaIds.contains(it) }
    }
}

object RealBackendMutationBus {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private val _latestEvent = MutableStateFlow(RealBackendMutationEvent())
    val latestEvent: StateFlow<RealBackendMutationEvent> = _latestEvent.asStateFlow()

    fun notifyChanged(
        event: RealBackendMutationEvent = RealBackendMutationEvent(),
    ) {
        var nextVersion = 0
        _version.update { current ->
            nextVersion = current + 1
            nextVersion
        }
        _latestEvent.value = event.copy(version = nextVersion)
    }
}

private val RealBackendContentScopes = setOf(
    RealBackendRefreshScope.PHOTO_FEED,
    RealBackendRefreshScope.ALBUMS,
    RealBackendRefreshScope.POST_DETAIL,
    RealBackendRefreshScope.MEDIA_MANAGEMENT,
    RealBackendRefreshScope.TRASH,
    RealBackendRefreshScope.SYSTEM_MEDIA_DESTINATIONS,
)

private val RealBackendPostScopes = setOf(
    RealBackendRefreshScope.PHOTO_FEED,
    RealBackendRefreshScope.ALBUMS,
    RealBackendRefreshScope.POST_DETAIL,
    RealBackendRefreshScope.MEDIA_MANAGEMENT,
    RealBackendRefreshScope.SYSTEM_MEDIA_DESTINATIONS,
)

private val RealBackendCommentScopes = setOf(
    RealBackendRefreshScope.POST_DETAIL,
)

internal fun notifyRealBackendContentChanged(
    postIds: Set<String> = emptySet(),
    mediaIds: Set<String> = emptySet(),
) {
    RealBackendMutationBus.notifyChanged(
        RealBackendMutationEvent(
            scopes = RealBackendContentScopes,
            postIds = postIds,
            mediaIds = mediaIds,
        ),
    )
}

internal fun notifyRealBackendPostChanged(
    postIds: Set<String> = emptySet(),
) {
    RealBackendMutationBus.notifyChanged(
        RealBackendMutationEvent(
            scopes = RealBackendPostScopes,
            postIds = postIds,
        ),
    )
}

internal fun notifyRealBackendCommentChanged(
    postIds: Set<String> = emptySet(),
    mediaIds: Set<String> = emptySet(),
) {
    RealBackendMutationBus.notifyChanged(
        RealBackendMutationEvent(
            scopes = RealBackendCommentScopes,
            postIds = postIds,
            mediaIds = mediaIds,
        ),
    )
}

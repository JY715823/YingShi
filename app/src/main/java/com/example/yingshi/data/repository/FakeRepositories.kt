package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.model.UpdatePostAlbumsPayload
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.AlbumPostCardUiModel
import com.example.yingshi.feature.photos.CommentTargetType
import com.example.yingshi.feature.photos.FakeAlbumRepository
import com.example.yingshi.feature.photos.FakeCommentRepository
import com.example.yingshi.feature.photos.FakePhotoFeedRepository
import com.example.yingshi.feature.photos.FakeTrashRepository
import com.example.yingshi.feature.photos.PostDetailUiModel
import com.example.yingshi.feature.photos.TrashEntryType

class FakeMediaRepositoryShell : MediaRepository {
    override suspend fun getMediaFeed(
        page: Int,
        pageSize: Int,
    ): ApiResult<List<RemoteMedia>> {
        val items = FakePhotoFeedRepository.getPhotoFeed()
            .drop((page - 1).coerceAtLeast(0) * pageSize)
            .take(pageSize)
            .map { item ->
                RemoteMedia(
                    mediaId = item.mediaId,
                    mediaType = item.mediaType.toRemoteMediaType(),
                    previewUrl = null,
                    originalUrl = null,
                    videoUrl = null,
                    width = item.width,
                    height = item.height,
                    aspectRatio = item.aspectRatio,
                    displayTimeMillis = item.mediaDisplayTimeMillis,
                    commentCount = item.commentCount,
                    postIds = item.postIds,
                )
            }
        return ApiResult.Success(items)
    }
}

class FakeAlbumRepositoryShell : AlbumRepository {
    override suspend fun getAlbums(): ApiResult<List<RemoteAlbum>> {
        return ApiResult.Success(
            FakeAlbumRepository.getAlbums().map { album ->
                RemoteAlbum(
                    albumId = album.id,
                    title = album.title,
                    subtitle = album.subtitle,
                    coverMediaId = null,
                    postCount = FakeAlbumRepository.getPosts().count { it.albumIds.contains(album.id) },
                )
            },
        )
    }

    override suspend fun getAlbumPosts(albumId: String): ApiResult<List<RemotePostSummary>> {
        return ApiResult.Success(
            FakeAlbumRepository.getPosts()
                .filter { it.albumIds.contains(albumId) }
                .map { it.toRemotePostSummary() },
        )
    }

    override suspend fun updatePostAlbums(
        postId: String,
        payload: UpdatePostAlbumsPayload,
    ): ApiResult<RemotePostSummary> {
        val draft = FakeAlbumRepository.getEditablePostDraft(postId)
            ?: return ApiResult.Error(code = "POST_NOT_FOUND", message = "Fake post not found")
        FakeAlbumRepository.updatePostBasicInfo(
            postId = postId,
            title = draft.title,
            summary = draft.summary,
            postDisplayTimeMillis = draft.postDisplayTimeMillis,
            albumIds = payload.albumIds,
        )
        return FakeAlbumRepository.getPost(postId)
            ?.toRemotePostSummary()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = "POST_NOT_FOUND", message = "Fake post not found after album update")
    }
}

class FakePostRepositoryShell : PostRepository {
    override suspend fun getPosts(): ApiResult<List<RemotePostSummary>> {
        return ApiResult.Success(
            FakeAlbumRepository.getPosts().map { it.toRemotePostSummary() },
        )
    }

    override suspend fun getPostDetail(postId: String): ApiResult<RemotePostDetail> {
        val post = FakeAlbumRepository.getPost(postId)
            ?: return ApiResult.Error(code = "POST_NOT_FOUND", message = "Fake post not found")
        val detailRoute = FakeAlbumRepository.toPostDetailRoute(post)
        val detail = FakeAlbumRepository.getPostDetail(detailRoute)
        return ApiResult.Success(
            detail.toRemotePostDetail(),
        )
    }

    override suspend fun createPost(payload: CreatePostPayload): ApiResult<RemotePostSummary> {
        val post = FakeAlbumRepository.createPlaceholderPost(
            title = payload.title,
            summary = payload.summary,
            postDisplayTimeMillis = payload.displayTimeMillis,
            albumIds = payload.albumIds,
        )
        return ApiResult.Success(post.toRemotePostSummary())
    }

    override suspend fun updatePostBasicInfo(
        postId: String,
        payload: UpdatePostBasicInfoPayload,
    ): ApiResult<RemotePostSummary> {
        FakeAlbumRepository.updatePostBasicInfo(
            postId = postId,
            title = payload.title,
            summary = payload.summary,
            postDisplayTimeMillis = payload.displayTimeMillis,
            albumIds = payload.albumIds,
        )
        return FakeAlbumRepository.getPost(postId)
            ?.toRemotePostSummary()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = "POST_NOT_FOUND", message = "Fake post not found after basic info update")
    }

    override suspend fun setPostCover(
        postId: String,
        coverMediaId: String,
    ): ApiResult<RemotePostDetail> {
        val updated = FakeAlbumRepository.setPostCover(postId, coverMediaId)
        if (!updated) {
            return ApiResult.Error(code = "POST_COVER_INVALID", message = "Fake post cover update failed")
        }
        return getPostDetail(postId)
    }

    override suspend fun updatePostMediaOrder(
        postId: String,
        orderedMediaIds: List<String>,
    ): ApiResult<RemotePostDetail> {
        val updated = FakeAlbumRepository.updatePostMediaOrder(postId, orderedMediaIds)
        if (!updated) {
            return ApiResult.Error(code = "POST_MEDIA_ORDER_INVALID", message = "Fake post media order update failed")
        }
        return getPostDetail(postId)
    }
}

class FakeCommentRepositoryShell : CommentRepository {
    override suspend fun getPostComments(
        postId: String,
        page: Int,
        size: Int,
    ): ApiResult<RemoteCommentPage> {
        val allComments = FakeCommentRepository.getPostComments(postId)
            .map { comment -> comment.toRemoteComment() }
        val comments = paginateComments(allComments, page, size)
        return ApiResult.Success(
            RemoteCommentPage(
                comments = comments,
                page = page,
                size = size,
                hasMore = allComments.size > page * size,
            ),
        )
    }

    override suspend fun getMediaComments(
        mediaId: String,
        page: Int,
        size: Int,
    ): ApiResult<RemoteCommentPage> {
        val allComments = FakeCommentRepository.getMediaComments(mediaId)
            .map { comment -> comment.toRemoteComment() }
        val comments = paginateComments(allComments, page, size)
        return ApiResult.Success(
            RemoteCommentPage(
                comments = comments,
                page = page,
                size = size,
                hasMore = allComments.size > page * size,
            ),
        )
    }

    override suspend fun createPostComment(
        postId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        FakeCommentRepository.addPostComment(postId, content)
        return FakeCommentRepository.getPostComments(postId)
            .firstOrNull()
            ?.toRemoteComment()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(
                code = "COMMENT_CREATE_FAILED",
                message = "Fake post comment was not created",
            )
    }

    override suspend fun createMediaComment(
        mediaId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        FakeCommentRepository.addMediaComment(mediaId, content)
        return FakeCommentRepository.getMediaComments(mediaId)
            .firstOrNull()
            ?.toRemoteComment()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(
                code = "COMMENT_CREATE_FAILED",
                message = "Fake media comment was not created",
            )
    }

    override suspend fun updateComment(
        commentId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        val postMatch = FakeCommentRepository.findPostComment(commentId)
        if (postMatch != null) {
            FakeCommentRepository.updatePostComment(
                postId = postMatch.targetId,
                commentId = commentId,
                content = content,
            )
            return FakeCommentRepository.getPostComments(postMatch.targetId)
                .firstOrNull { it.id == commentId }
                ?.toRemoteComment()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(
                    code = "COMMENT_NOT_FOUND",
                    message = "Fake post comment update target disappeared",
                )
        }

        val mediaMatch = FakeCommentRepository.findMediaComment(commentId)
        if (mediaMatch != null) {
            FakeCommentRepository.updateMediaComment(
                mediaId = mediaMatch.targetId,
                commentId = commentId,
                content = content,
            )
            return FakeCommentRepository.getMediaComments(mediaMatch.targetId)
                .firstOrNull { it.id == commentId }
                ?.toRemoteComment()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(
                    code = "COMMENT_NOT_FOUND",
                    message = "Fake media comment update target disappeared",
                )
        }

        return ApiResult.Error(
            code = "COMMENT_NOT_FOUND",
            message = "Fake comment not found",
        )
    }

    override suspend fun deleteComment(commentId: String): ApiResult<Unit> {
        val postMatch = FakeCommentRepository.findPostComment(commentId)
        if (postMatch != null) {
            FakeCommentRepository.deletePostComment(postMatch.targetId, commentId)
            return ApiResult.Success(Unit)
        }

        val mediaMatch = FakeCommentRepository.findMediaComment(commentId)
        if (mediaMatch != null) {
            FakeCommentRepository.deleteMediaComment(mediaMatch.targetId, commentId)
            return ApiResult.Success(Unit)
        }

        return ApiResult.Error(
            code = "COMMENT_NOT_FOUND",
            message = "Fake comment not found",
        )
    }
}

class FakeTrashRepositoryShell : TrashRepository {
    override suspend fun getTrashItems(type: String?): ApiResult<List<RemoteTrashItem>> {
        val entryType = type?.toTrashEntryTypeOrNull()
        val items = if (entryType != null) {
            FakeTrashRepository.getEntries(entryType)
        } else {
            TrashEntryType.entries.flatMap(FakeTrashRepository::getEntries)
        }
        return ApiResult.Success(items.map { item -> item.toRemoteTrashItem() })
    }

    override suspend fun getTrashDetail(trashItemId: String): ApiResult<RemoteTrashDetail> {
        val entry = FakeTrashRepository.getEntry(trashItemId)
            ?: return ApiResult.Error(code = "TRASH_ITEM_NOT_FOUND", message = "Fake trash item not found")
        return ApiResult.Success(
            RemoteTrashDetail(
                item = entry.toRemoteTrashItem(),
                canRestore = true,
                canMoveOutOfTrash = true,
                pendingCleanup = null,
            ),
        )
    }

    override suspend fun restoreTrashItem(trashItemId: String): ApiResult<RemoteTrashItem> {
        val entry = FakeTrashRepository.getEntry(trashItemId)
            ?: return ApiResult.Error(code = "TRASH_ITEM_NOT_FOUND", message = "Fake trash item not found")
        val result = FakeTrashRepository.restoreEntry(trashItemId)
        return if (result.success) {
            ApiResult.Success(entry.toRemoteTrashItem())
        } else {
            ApiResult.Error(code = "TRASH_RESTORE_FAILED", message = result.message)
        }
    }

    override suspend fun moveTrashItemOut(trashItemId: String): ApiResult<RemotePendingCleanup> {
        val moved = FakeTrashRepository.moveEntryOutOfTrash(trashItemId)
        val pending = FakeTrashRepository.getPendingCleanupEntry(trashItemId)
        return if (moved && pending != null) {
            ApiResult.Success(pending.toRemotePendingCleanup())
        } else {
            ApiResult.Error(
                code = "TRASH_REMOVE_FAILED",
                message = "Fake trash item could not be moved into pending cleanup",
            )
        }
    }

    override suspend fun undoMoveTrashItemOut(trashItemId: String): ApiResult<RemoteTrashItem> {
        val pending = FakeTrashRepository.getPendingCleanupEntry(trashItemId)
            ?: return ApiResult.Error(code = "TRASH_PENDING_NOT_FOUND", message = "Fake pending cleanup item not found")
        val undone = FakeTrashRepository.undoPendingRemoval(trashItemId)
        return if (undone) {
            ApiResult.Success(pending.entry.toRemoteTrashItem())
        } else {
            ApiResult.Error(
                code = "TRASH_UNDO_REMOVE_FAILED",
                message = "Fake pending cleanup item could not be restored to trash",
            )
        }
    }

    override suspend fun getPendingCleanupItems(): ApiResult<List<RemotePendingCleanup>> {
        return ApiResult.Success(
            FakeTrashRepository.getPendingCleanupEntries().map { pending ->
                pending.toRemotePendingCleanup()
            },
        )
    }
}

class FakeUploadRepositoryShell : UploadRepository {
    override suspend fun createUploadToken(
        payload: CreateUploadTokenPayload,
    ): ApiResult<RemoteUploadToken> {
        val uploadId = "fake-upload-${payload.fileName}-${System.currentTimeMillis()}"
        fakeUploadTasks[uploadId] = RemoteUploadTask(
            uploadId = uploadId,
            fileName = payload.fileName,
            mediaType = payload.mediaType,
            objectKey = "uploads/fake/${payload.fileName}",
            state = UploadState.WAITING,
            progressPercent = 0,
        )
        return ApiResult.Success(
            RemoteUploadToken(
                uploadId = uploadId,
                provider = "local",
                uploadUrl = "/api/uploads/$uploadId/file",
                expireAtMillis = System.currentTimeMillis() + 15 * 60 * 1000L,
                state = "waiting",
            ),
        )
    }

    override suspend fun confirmUpload(
        uploadId: String,
        payload: ConfirmUploadPayload,
    ): ApiResult<RemoteUploadTask> {
        val current = fakeUploadTasks[uploadId]
            ?: return ApiResult.Error(code = "UPLOAD_NOT_FOUND", message = "Fake upload task not found")
        val updated = current.copy(
            objectKey = payload.objectKey,
            state = UploadState.SUCCESS,
            progressPercent = 100,
        )
        fakeUploadTasks[uploadId] = updated
        return ApiResult.Success(updated)
    }

    override suspend fun cancelUpload(uploadId: String): ApiResult<RemoteUploadTask> {
        val current = fakeUploadTasks[uploadId]
            ?: return ApiResult.Error(code = "UPLOAD_NOT_FOUND", message = "Fake upload task not found")
        val updated = current.copy(state = UploadState.CANCELLED)
        fakeUploadTasks[uploadId] = updated
        return ApiResult.Success(updated)
    }

    override suspend fun getUploadTask(uploadId: String): ApiResult<RemoteUploadTask> {
        return fakeUploadTasks[uploadId]
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = "UPLOAD_NOT_FOUND", message = "Fake upload task not found")
    }
}

class FakeAuthRepositoryShell : AuthRepository {
    override suspend fun login(
        request: LoginRequestDto,
    ): ApiResult<RemoteLoginSession> {
        val session = RemoteLoginSession(
            userId = "fake-user-001",
            displayName = "本地占位账号",
            spaceId = "fake-space-001",
            tokens = AuthTokens(
                accessToken = "fake-access-token",
                refreshToken = "fake-refresh-token",
                accessTokenExpireAtMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
                refreshTokenExpireAtMillis = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
            ),
        )
        AuthSessionManager.saveTokens(session.tokens)
        return ApiResult.Success(session)
    }

    override suspend fun refreshToken(
        request: RefreshTokenRequestDto,
    ): ApiResult<AuthTokens> {
        val tokens = AuthTokens(
            accessToken = "fake-access-token-refreshed",
            refreshToken = request.refreshToken.ifBlank { "fake-refresh-token" },
            accessTokenExpireAtMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
            refreshTokenExpireAtMillis = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
        )
        AuthSessionManager.saveTokens(tokens)
        return ApiResult.Success(tokens)
    }

    override suspend fun logout(): ApiResult<Unit> {
        AuthSessionManager.clearTokens()
        return ApiResult.Success(Unit)
    }

    override suspend fun getCurrentUser(): ApiResult<RemoteCurrentUser> {
        return ApiResult.Success(
            RemoteCurrentUser(
                userId = "fake-user-001",
                displayName = if (AuthSessionManager.isLoggedIn) {
                    "本地占位账号"
                } else {
                    "未接真实账号"
                },
                avatarUrl = null,
                spaceId = "fake-space-001",
                spaceDisplayName = "映世本地占位空间",
            ),
        )
    }
}

private fun AppMediaType.toRemoteMediaType(): String {
    return when (this) {
        AppMediaType.IMAGE -> "image"
        AppMediaType.VIDEO -> "video"
    }
}

private fun AlbumPostCardUiModel.toRemotePostSummary(): RemotePostSummary {
    return RemotePostSummary(
        postId = id,
        title = title,
        summary = summary,
        contributorLabel = null,
        displayTimeMillis = postDisplayTimeMillis,
        albumIds = albumIds,
        coverMediaId = null,
        mediaCount = mediaCount,
    )
}

private fun PostDetailUiModel.toRemotePostDetail(): RemotePostDetail {
    val coverMediaId = mediaItems.firstOrNull()?.id
    return RemotePostDetail(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        displayTimeMillis = postDisplayTimeMillis,
        albumIds = albumIds,
        coverMediaId = coverMediaId,
        mediaItems = mediaItems.mapIndexed { index, media ->
            RemotePostMedia(
                mediaId = media.id,
                mediaType = media.mediaType.toRemoteMediaType(),
                previewUrl = null,
                originalUrl = null,
                videoUrl = null,
                width = media.width,
                height = media.height,
                aspectRatio = media.aspectRatio,
                displayTimeMillis = media.displayTimeMillis,
                commentCount = media.commentCount,
                isCover = index == 0,
                videoDurationMillis = media.videoDurationMillis,
            )
        },
    )
}

private fun paginateComments(
    comments: List<RemoteComment>,
    page: Int,
    size: Int,
): List<RemoteComment> {
    val safePage = page.coerceAtLeast(1)
    val safeSize = size.coerceAtLeast(1)
    return comments
        .drop((safePage - 1) * safeSize)
        .take(safeSize)
}

private fun com.example.yingshi.feature.photos.CommentUiModel.toRemoteComment(): RemoteComment {
    return RemoteComment(
        commentId = id,
        targetType = when (targetType) {
            CommentTargetType.Post -> "POST"
            CommentTargetType.Media -> "MEDIA"
        },
        targetId = targetId,
        authorName = author,
        content = content,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = null,
        isDeleted = false,
    )
}

private fun String.toTrashEntryTypeOrNull(): TrashEntryType? {
    return TrashEntryType.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
}

private fun com.example.yingshi.feature.photos.TrashEntryUiModel.toRemoteTrashItem(
    state: String = "inTrash",
): RemoteTrashItem {
    return RemoteTrashItem(
        trashItemId = id,
        itemType = type.name,
        state = state,
        sourcePostId = sourcePostId,
        sourceMediaId = sourceMediaId,
        title = title,
        previewInfo = previewInfo,
        deletedAtMillis = deletedAtMillis,
        relatedPostIds = relatedPostIds,
        relatedMediaIds = relatedMediaIds,
    )
}

private fun com.example.yingshi.feature.photos.TrashPendingCleanupUiModel.toRemotePendingCleanup(): RemotePendingCleanup {
    return RemotePendingCleanup(
        trashItemId = entry.id,
        removedAtMillis = removedAtMillis,
        undoDeadlineMillis = removedAtMillis + 24L * 60L * 60L * 1000L,
        item = entry.toRemoteTrashItem(state = "pendingCleanup"),
    )
}

private val fakeUploadTasks = mutableMapOf<String, RemoteUploadTask>()

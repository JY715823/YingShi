package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreateAlbumPayload
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.NotificationMarkAllReadResult
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLifeConsoleBowelMutation
import com.example.yingshi.data.model.RemoteLifeConsoleBowelHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleBowelSummary
import com.example.yingshi.data.model.RemoteLifeConsoleBowelUserSummary
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteLifeConsoleUser
import com.example.yingshi.data.model.RemoteLoginChallenge
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemoteMediaFeedPage
import com.example.yingshi.data.model.RemoteMediaImportStatus
import com.example.yingshi.data.model.RemoteNotification
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
import com.example.yingshi.data.model.UpdateAlbumPayload
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RememberedLoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.ResendLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.dto.VerifyLoginChallengeRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.AlbumPostCardUiModel
import com.example.yingshi.feature.photos.CommentTargetType
import com.example.yingshi.feature.photos.FakeAlbumRepository
import com.example.yingshi.feature.photos.FakeCommentRepository
import com.example.yingshi.feature.photos.FakeNotificationRepository
import com.example.yingshi.feature.photos.FakePhotoFeedRepository
import com.example.yingshi.feature.photos.FakeTrashRepository
import com.example.yingshi.feature.photos.PostDetailUiModel
import com.example.yingshi.feature.photos.TrashEntryType
import java.io.InputStream

class FakeMediaRepositoryShell : MediaRepository {
    override suspend fun getMediaFeed(
        page: Int,
        pageSize: Int,
    ): ApiResult<List<RemoteMedia>> {
        val items = FakePhotoFeedRepository.getPhotoFeed()
            .drop((page - 1).coerceAtLeast(0) * pageSize)
            .take(pageSize)
            .map { item -> item.toRemoteMedia() }
        return ApiResult.Success(items)
    }

    override suspend fun getMediaFeedPage(
        cursor: String?,
        pageSize: Int,
    ): ApiResult<RemoteMediaFeedPage> {
        val page = cursor?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val result = getMediaFeed(page = page, pageSize = pageSize)
        return when (result) {
            is ApiResult.Success -> {
                val totalCount = FakePhotoFeedRepository.getPhotoFeed().size
                val loadedCount = page * pageSize
                ApiResult.Success(
                    RemoteMediaFeedPage(
                        items = result.data,
                        nextCursor = if (loadedCount < totalCount) (page + 1).toString() else null,
                        hasMore = loadedCount < totalCount,
                    ),
                )
            }
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }

    override suspend fun getImportStatus(
        sourceFingerprints: List<String>,
    ): ApiResult<List<RemoteMediaImportStatus>> {
        return ApiResult.Success(emptyList())
    }

    override suspend fun deleteMediaFromPost(
        smallAlbumId: String,
        mediaId: String,
        deleteMode: String,
    ): ApiResult<RemoteTrashItem> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "FAKE post-media delete keeps using local fake flow in this stage",
        )
    }

    override suspend fun systemDeleteMedia(mediaId: String): ApiResult<RemoteTrashItem> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "FAKE media delete keeps using local fake flow in this stage",
        )
    }
}

class FakeAlbumRepositoryShell : AlbumRepository {
    override suspend fun createAlbum(payload: CreateAlbumPayload): ApiResult<RemoteAlbum> {
        val album = FakeAlbumRepository.createAlbum(
            title = payload.title,
            subtitle = payload.subtitle,
        )
        return ApiResult.Success(
            RemoteAlbum(
                albumId = album.id,
                title = album.title,
                subtitle = album.subtitle,
                coverMediaId = null,
                smallAlbumCount = 0,
                systemKey = album.systemKey,
                includeInPhotoFeed = album.includeInPhotoFeed,
            ),
        )
    }

    override suspend fun getAlbums(): ApiResult<List<RemoteAlbum>> {
        return ApiResult.Success(
            FakeAlbumRepository.getAlbums().map { album ->
                RemoteAlbum(
                    albumId = album.id,
                    title = album.title,
                    subtitle = album.subtitle,
                    coverMediaId = null,
                    smallAlbumCount = FakeAlbumRepository.getPosts().count { it.albumId == album.id },
                    systemKey = album.systemKey,
                    includeInPhotoFeed = album.includeInPhotoFeed,
                )
            },
        )
    }

    override suspend fun getAlbumPosts(albumId: String): ApiResult<List<RemotePostSummary>> {
        return ApiResult.Success(
            FakeAlbumRepository.getPosts()
                .filter { it.albumId == albumId }
                .map { it.toRemotePostSummary() },
        )
    }

    override suspend fun updateAlbum(
        albumId: String,
        payload: UpdateAlbumPayload,
    ): ApiResult<RemoteAlbum> {
        val existing = FakeAlbumRepository.getAlbum(albumId)
            ?: return ApiResult.Error(
                code = "ALBUM_NOT_FOUND",
                message = "Fake large album not found",
            )
        val updated = FakeAlbumRepository.renameAlbum(
            albumId = albumId,
            title = payload.title,
            subtitle = payload.subtitle,
        ) ?: return ApiResult.Error(
            code = "ALBUM_NOT_FOUND",
            message = "Fake large album not found",
        )
        return ApiResult.Success(
            RemoteAlbum(
                albumId = updated.id,
                title = updated.title,
                subtitle = updated.subtitle,
                coverMediaId = null,
                smallAlbumCount = FakeAlbumRepository.getPosts().count { it.albumId == updated.id },
                systemKey = updated.systemKey,
                includeInPhotoFeed = updated.includeInPhotoFeed,
            ),
        )
    }

    override suspend fun deleteAlbum(albumId: String): ApiResult<RemoteTrashItem> {
        val existing = FakeAlbumRepository.getAlbum(albumId)
            ?: return ApiResult.Error(code = "ALBUM_NOT_FOUND", message = "Fake large album not found")
        val snapshot = FakeAlbumRepository.snapshotAlbum(albumId)
            ?: return ApiResult.Error(code = "ALBUM_NOT_FOUND", message = "Fake large album not found")
        FakeTrashRepository.recordDeletedAlbum(snapshot)
        FakeAlbumRepository.deleteAlbumLocally(albumId)
        return FakeTrashRepository.getEntries(TrashEntryType.LARGE_ALBUM_DELETED)
            .firstOrNull { it.albumSnapshot?.album?.id == albumId }
            ?.toRemoteTrashItem()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = "ALBUM_DELETE_FAILED", message = "Fake large album delete failed")
    }

    override suspend fun updatePostAlbums(
        postId: String,
        payload: UpdatePostAlbumsPayload,
    ): ApiResult<RemotePostSummary> {
        val draft = FakeAlbumRepository.getEditablePostDraft(postId)
            ?: return ApiResult.Error(code = "SMALL_ALBUM_NOT_FOUND", message = "Fake small album not found")
        FakeAlbumRepository.updatePostBasicInfo(
            postId = postId,
            title = draft.title,
            summary = draft.summary,
            postDisplayTimeMillis = draft.postDisplayTimeMillis,
            albumIds = listOf(payload.albumId),
            participantUserIds = draft.participantUserIds,
        )
        return FakeAlbumRepository.getPost(postId)
            ?.toRemotePostSummary()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = "SMALL_ALBUM_NOT_FOUND", message = "Fake small album not found after album update")
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
            ?: return ApiResult.Error(code = "SMALL_ALBUM_NOT_FOUND", message = "Fake small album not found")
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
            albumIds = listOf(payload.albumId),
            participantUserIds = payload.participantUserIds,
        )
        return ApiResult.Success(post.toRemotePostSummary())
    }

    override suspend fun addMediaToPost(
        postId: String,
        mediaIds: List<String>,
        coverMediaId: String?,
    ): ApiResult<RemotePostDetail> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "FAKE add-media flow keeps using local fake mutation in this stage",
        )
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
            albumIds = listOf(payload.albumId),
            participantUserIds = payload.participantUserIds,
        )
        return FakeAlbumRepository.getPost(postId)
            ?.toRemotePostSummary()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = "SMALL_ALBUM_NOT_FOUND", message = "Fake small album not found after basic info update")
    }

    override suspend fun setPostCover(
        postId: String,
        coverMediaId: String,
    ): ApiResult<RemotePostDetail> {
        val updated = FakeAlbumRepository.setPostCover(postId, coverMediaId)
        if (!updated) {
            return ApiResult.Error(code = "SMALL_ALBUM_COVER_INVALID", message = "Fake small album cover update failed")
        }
        return getPostDetail(postId)
    }

    override suspend fun updatePostMediaOrder(
        postId: String,
        orderedMediaIds: List<String>,
    ): ApiResult<RemotePostDetail> {
        val updated = FakeAlbumRepository.updatePostMediaOrder(postId, orderedMediaIds)
        if (!updated) {
            return ApiResult.Error(code = "SMALL_ALBUM_MEDIA_ORDER_INVALID", message = "Fake small album media order update failed")
        }
        return getPostDetail(postId)
    }

    override suspend fun deleteSmallAlbum(smallAlbumId: String): ApiResult<RemoteTrashItem> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "FAKE small album delete keeps using local fake flow in this stage",
        )
    }
}

class FakeCommentRepositoryShell : CommentRepository {
    override suspend fun getPostComments(
        smallAlbumId: String,
        page: Int,
        size: Int,
    ): ApiResult<RemoteCommentPage> {
        val allComments = FakeCommentRepository.getPostComments(smallAlbumId)
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
        smallAlbumId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        FakeCommentRepository.addPostComment(smallAlbumId, content)
        return FakeCommentRepository.getPostComments(smallAlbumId)
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

class FakeNotificationRepositoryShell : NotificationRepository {
    override suspend fun getNotifications(limit: Int?): ApiResult<List<RemoteNotification>> {
        val items = FakeNotificationRepository.getNotifications()
            .let { notifications ->
                if (limit == null || limit < 1) {
                    notifications
                } else {
                    notifications.take(limit)
                }
            }
            .map { item -> item.toRemoteNotification() }
        return ApiResult.Success(items)
    }

    override suspend fun getNotification(notificationId: String): ApiResult<RemoteNotification> {
        return FakeNotificationRepository.getNotification(notificationId)
            ?.toRemoteNotification()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(
                code = "NOTIFICATION_NOT_FOUND",
                message = "Fake notification not found",
            )
    }

    override suspend fun markRead(notificationId: String): ApiResult<RemoteNotification> {
        FakeNotificationRepository.markRead(notificationId)
        return getNotification(notificationId)
    }

    override suspend fun markAllRead(): ApiResult<NotificationMarkAllReadResult> {
        val unreadCount = FakeNotificationRepository.unreadCount()
        FakeNotificationRepository.markAllRead()
        return ApiResult.Success(
            NotificationMarkAllReadResult(
                success = true,
                affectedCount = unreadCount,
            ),
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

    override suspend fun purgeTrashItem(trashItemId: String): ApiResult<RemoteTrashItem> {
        val entry = FakeTrashRepository.getEntry(trashItemId)
            ?: FakeTrashRepository.getPendingCleanupEntry(trashItemId)?.entry
            ?: return ApiResult.Error(code = "TRASH_ITEM_NOT_FOUND", message = "Fake trash item not found")
        val deleted = FakeTrashRepository.permanentlyDeleteEntry(trashItemId)
        return if (deleted) {
            ApiResult.Success(entry.toRemoteTrashItem())
        } else {
            ApiResult.Error(
                code = "TRASH_PURGE_FAILED",
                message = "Fake trash item could not be permanently deleted",
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
            operationId = payload.operationId,
            operationType = payload.operationType,
            operationTitle = payload.operationTitle,
            operationMediaCount = payload.operationMediaCount,
            sourceItemId = payload.sourceItemId,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
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

    override suspend fun uploadLocalFile(
        uploadId: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        onProgressPercent: (Int) -> Unit,
        shouldCancel: () -> Boolean,
    ): ApiResult<RemoteMedia> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "FAKE upload keeps using confirm-upload placeholder in this stage",
        )
    }

    override suspend fun uploadLocalStream(
        uploadId: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        openInputStream: () -> InputStream,
        onProgressPercent: (Int) -> Unit,
        shouldCancel: () -> Boolean,
    ): ApiResult<RemoteMedia> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "FAKE upload keeps using confirm-upload placeholder in this stage",
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

    override suspend fun getUploadHistory(
        state: String?,
        operationType: String?,
        pageSize: Int,
    ): ApiResult<List<RemoteUploadTask>> {
        val stateFilter = state?.lowercase()
        val typeFilter = operationType?.uppercase()
        return ApiResult.Success(
            fakeUploadTasks.values
                .filter { task -> stateFilter == null || task.state.name.lowercase() == stateFilter }
                .filter { task -> typeFilter == null || task.operationType == typeFilter }
                .sortedByDescending { it.updatedAtMillis ?: 0L }
                .take(pageSize),
        )
    }

    override suspend fun dismissUpload(uploadId: String): ApiResult<RemoteUploadTask> {
        val task = fakeUploadTasks.remove(uploadId)
            ?: return ApiResult.Error(code = "UPLOAD_NOT_FOUND", message = "Fake upload task not found")
        return ApiResult.Success(task)
    }

    override suspend fun dismissUploadBatch(
        state: String?,
        operationType: String?,
    ): ApiResult<List<RemoteUploadTask>> {
        val stateFilter = state?.lowercase()
        val typeFilter = operationType?.uppercase()
        val removed = fakeUploadTasks.values
            .filter { task -> stateFilter == null || task.state.name.lowercase() == stateFilter }
            .filter { task -> typeFilter == null || task.operationType == typeFilter }
            .toList()
        removed.forEach { fakeUploadTasks.remove(it.uploadId) }
        return ApiResult.Success(removed)
    }
}

class FakeAuthRepositoryShell : AuthRepository {
    private var pendingAccount = "fake@yingshi.local"

    override suspend fun requestLoginChallenge(
        request: LoginRequestDto,
    ): ApiResult<RemoteLoginChallenge> {
        pendingAccount = request.account.ifBlank { pendingAccount }
        return ApiResult.Success(
            RemoteLoginChallenge(
                challengeId = "fake-challenge-${System.currentTimeMillis()}",
                maskedEmail = pendingAccount,
                expireAtMillis = System.currentTimeMillis() + 5 * 60 * 1000L,
                resendAvailableAtMillis = System.currentTimeMillis() + 60 * 1000L,
            ),
        )
    }

    override suspend fun resendLoginChallenge(
        request: ResendLoginChallengeRequestDto,
    ): ApiResult<RemoteLoginChallenge> {
        return ApiResult.Success(
            RemoteLoginChallenge(
                challengeId = request.challengeId.ifBlank { "fake-challenge-${System.currentTimeMillis()}" },
                maskedEmail = pendingAccount,
                expireAtMillis = System.currentTimeMillis() + 5 * 60 * 1000L,
                resendAvailableAtMillis = System.currentTimeMillis() + 60 * 1000L,
            ),
        )
    }

    override suspend fun verifyLoginChallenge(
        request: VerifyLoginChallengeRequestDto,
    ): ApiResult<RemoteLoginSession> {
        val profile = fakeAuthLoginProfile(pendingAccount.ifBlank { "fake@yingshi.local" })
        val session = profile.toFakeLoginSession().copy(
            rememberedLoginToken = "fake-remembered-${System.currentTimeMillis()}",
            rememberedLoginExpireAtMillis = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
        )
        AuthSessionManager.saveRememberedLogin(
            account = session.account,
            token = session.rememberedLoginToken,
            expireAtMillis = session.rememberedLoginExpireAtMillis,
        )
        AuthSessionManager.saveTokens(session.tokens)
        AuthSessionManager.saveCurrentUserSnapshot(profile)
        return ApiResult.Success(session)
    }

    override suspend fun loginWithRememberedDevice(
        request: RememberedLoginRequestDto,
    ): ApiResult<RemoteLoginSession> {
        pendingAccount = request.account.ifBlank { pendingAccount }
        val profile = fakeAuthLoginProfile(pendingAccount.ifBlank { "fake@yingshi.local" })
        val session = profile.toFakeLoginSession().copy(
            rememberedLoginToken = "fake-remembered-${System.currentTimeMillis()}",
            rememberedLoginExpireAtMillis = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
        )
        AuthSessionManager.saveRememberedLogin(
            account = session.account,
            token = session.rememberedLoginToken,
            expireAtMillis = session.rememberedLoginExpireAtMillis,
        )
        AuthSessionManager.saveTokens(session.tokens)
        AuthSessionManager.saveCurrentUserSnapshot(profile)
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
        fakeAuthLogout()
        return ApiResult.Success(Unit)
    }

    override suspend fun getCurrentUser(): ApiResult<RemoteCurrentUser> {
        val profile = fakeAuthCurrentProfile()
            ?: return ApiResult.Error(code = "AUTH_UNAUTHORIZED", message = "Fake auth session is missing")
        return ApiResult.Success(profile)
    }

    override suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequestDto,
    ): ApiResult<RemoteCurrentUser> {
        val updatedProfile = fakeAuthUpdateProfile(request.displayName, request.bio)
            ?: return ApiResult.Error(code = "AUTH_UNAUTHORIZED", message = "Fake auth session is missing")
        return ApiResult.Success(updatedProfile)
    }

    override suspend fun uploadCurrentUserAvatar(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        openInputStream: () -> InputStream,
    ): ApiResult<RemoteCurrentUser> {
        val fakeAvatarUrl = "content://fake-avatar/${System.currentTimeMillis()}-${fileName.ifBlank { "avatar" }}"
        val updatedProfile = fakeAuthUpdateAvatar(fakeAvatarUrl)
            ?: return ApiResult.Error(code = "AUTH_UNAUTHORIZED", message = "Fake auth session is missing")
        return ApiResult.Success(updatedProfile)
    }
}

class FakeLifeConsoleRepositoryShell : LifeConsoleRepository {
    private val bowelTimesByUserId = linkedMapOf<String, MutableList<Long>>()

    override suspend fun getToday(
        date: String?,
        zoneId: String,
    ): ApiResult<RemoteLifeConsoleToday> {
        return ApiResult.Success(fakeToday(date = date, zoneId = zoneId))
    }

    override suspend fun getHistory(
        zoneId: String,
        limitDays: Int,
    ): ApiResult<RemoteLifeConsoleHistory> {
        val today = fakeToday(zoneId = zoneId)
        val recentMedia = FakePhotoFeedRepository.getPhotoFeed()
            .sortedByDescending { it.mediaDisplayTimeMillis }
            .take(limitDays.coerceAtLeast(14) * 2)
            .map { it.toRemoteMedia() }
        val selfMedia = recentMedia.filterIndexed { index, _ -> index % 2 == 0 }
        val partnerMedia = recentMedia.filterIndexed { index, _ -> index % 2 == 1 }
        val personDays = buildFakeHistoryDays(selfMedia, partnerMedia)
        val mealDays = buildFakeHistoryDays(
            selfMedia = selfMedia.drop(1),
            partnerMedia = partnerMedia.dropLast(1),
        )
        val bowelDays = buildFakeBowelHistoryDays(
            today = today,
            limitDays = limitDays.coerceIn(7, 60),
        )
        return ApiResult.Success(
            RemoteLifeConsoleHistory(
                zoneId = zoneId,
                currentUser = today.currentUser,
                partner = today.partner,
                personDays = personDays,
                mealDays = mealDays,
                bowelDays = bowelDays,
            ),
        )
    }

    override suspend fun addMedia(
        category: String,
        mediaIds: List<String>,
    ): ApiResult<RemoteLifeConsoleToday> {
        return ApiResult.Success(fakeToday())
    }

    override suspend fun deleteMedia(
        category: String,
        mediaId: String,
    ): ApiResult<RemoteTrashItem> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "当前无法删除这张今日痕迹照片。",
        )
    }

    override suspend fun addBowelEvent(): ApiResult<RemoteLifeConsoleBowelMutation> {
        val profile = fakeAuthCurrentProfile()
            ?: return ApiResult.Error(code = "AUTH_UNAUTHORIZED", message = "Fake auth session is missing")
        val eventTime = System.currentTimeMillis()
        bowelTimesByUserId.getOrPut(profile.userId) { mutableListOf() }.add(eventTime)
        return ApiResult.Success(
            RemoteLifeConsoleBowelMutation(
                eventId = "fake-bowel-$eventTime",
                bowel = fakeBowelSummary(profile),
            ),
        )
    }

    override suspend fun deleteLatestBowelEvent(): ApiResult<RemoteLifeConsoleBowelMutation> {
        val profile = fakeAuthCurrentProfile()
            ?: return ApiResult.Error(code = "AUTH_UNAUTHORIZED", message = "Fake auth session is missing")
        val events = bowelTimesByUserId.getOrPut(profile.userId) { mutableListOf() }
        val removed = events.removeLastOrNull()
        return ApiResult.Success(
            RemoteLifeConsoleBowelMutation(
                eventId = removed?.let { "fake-bowel-$it" },
                bowel = fakeBowelSummary(profile),
            ),
        )
    }

    override suspend fun registerPushToken(
        platform: String,
        token: String,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    private fun fakeToday(
        date: String? = null,
        zoneId: String = "Asia/Shanghai",
    ): RemoteLifeConsoleToday {
        val profile = fakeAuthCurrentProfile() ?: fakeAuthLoginProfile("demo.a@yingshi.local")
        val currentUser = profile.toLifeUser()
        val partner = profile.partner?.let {
            RemoteLifeConsoleUser(
                userId = it.userId,
                account = it.account,
                displayName = it.displayName,
                avatarUrl = it.avatarUrl,
            )
        }
        return RemoteLifeConsoleToday(
            date = date ?: java.time.LocalDate.now(java.time.ZoneId.of(zoneId)).toString(),
            zoneId = zoneId,
            currentUser = currentUser,
            partner = partner,
            personSelf = emptySlot("PERSON", currentUser.userId, editable = true),
            personPartner = emptySlot("PERSON", partner?.userId, editable = false),
            mealSelf = emptySlot("MEAL", currentUser.userId, editable = true),
            mealPartner = emptySlot("MEAL", partner?.userId, editable = false),
            bowel = fakeBowelSummary(profile),
        )
    }

    private fun fakeBowelSummary(profile: RemoteCurrentUser): RemoteLifeConsoleBowelSummary {
        val userIds = listOfNotNull(profile.userId, profile.partner?.userId)
        return RemoteLifeConsoleBowelSummary(
            users = userIds.map { userId ->
                val times = bowelTimesByUserId[userId].orEmpty()
                RemoteLifeConsoleBowelUserSummary(
                    userId = userId,
                    count = times.size,
                    latestOccurredAtMillis = times.maxOrNull(),
                    eventTimesMillis = times,
                )
            },
        )
    }

    private fun buildFakeHistoryDays(
        selfMedia: List<RemoteMedia>,
        partnerMedia: List<RemoteMedia>,
    ): List<RemoteLifeConsoleHistoryDay> {
        val groupedSelf = selfMedia.groupBy { historyDateKey(it.displayTimeMillis) }
        val groupedPartner = partnerMedia.groupBy { historyDateKey(it.displayTimeMillis) }
        val orderedDates = (groupedSelf.keys + groupedPartner.keys)
            .distinct()
            .sortedDescending()
            .take(18)
        return orderedDates.map { date ->
            RemoteLifeConsoleHistoryDay(
                date = date,
                displayLabel = historyDateLabel(date),
                selfMedia = groupedSelf[date].orEmpty().sortedByDescending { it.displayTimeMillis },
                partnerMedia = groupedPartner[date].orEmpty().sortedByDescending { it.displayTimeMillis },
            )
        }.filter { it.selfMedia.isNotEmpty() || it.partnerMedia.isNotEmpty() }
    }

    private fun buildFakeBowelHistoryDays(
        today: RemoteLifeConsoleToday,
        limitDays: Int,
    ): List<RemoteLifeConsoleBowelHistoryDay> {
        val userMap = listOfNotNull(today.currentUser, today.partner).associateBy { it.userId }
        val eventPairs = bowelTimesByUserId.flatMap { (userId, times) ->
            times.map { time -> userId to time }
        }
        val grouped = eventPairs.groupBy { (_, time) -> historyDateKey(time) }
        return grouped.entries
            .sortedByDescending { it.key }
            .take(limitDays)
            .map { (date, entries) ->
                val users = entries
                    .groupBy({ it.first }, { it.second })
                    .mapNotNull { (userId, times) ->
                        val owner = userMap[userId] ?: return@mapNotNull null
                        RemoteLifeConsoleBowelUserSummary(
                            userId = owner.userId,
                            count = times.size,
                            latestOccurredAtMillis = times.maxOrNull(),
                            eventTimesMillis = times.sorted(),
                        )
                    }
                    .sortedBy { it.userId }
                RemoteLifeConsoleBowelHistoryDay(
                    date = date,
                    displayLabel = historyDateLabel(date),
                    users = users,
                )
            }
            .filter { it.users.isNotEmpty() }
    }

    private fun emptySlot(
        category: String,
        ownerUserId: String?,
        editable: Boolean,
    ): RemoteLifeConsoleMediaSlot {
        return RemoteLifeConsoleMediaSlot(
            category = category,
            ownerUserId = ownerUserId,
            editable = editable,
            mediaItems = emptyList(),
        )
    }

    private fun RemoteCurrentUser.toLifeUser(): RemoteLifeConsoleUser {
        return RemoteLifeConsoleUser(
            userId = userId,
            account = account,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )
    }
}

private fun historyDateKey(timeMillis: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timeMillis)
    val date = instant.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate()
    return date.toString()
}

private fun historyDateLabel(date: String): String {
    return runCatching {
        val parsed = java.time.LocalDate.parse(date)
        "${parsed.monthValue}月${parsed.dayOfMonth}日"
    }.getOrElse { date }
}

private fun AppMediaType.toRemoteMediaType(): String {
    return when (this) {
        AppMediaType.IMAGE -> "image"
        AppMediaType.VIDEO -> "video"
    }
}

private fun com.example.yingshi.feature.photos.PhotoFeedItem.toRemoteMedia(): RemoteMedia {
    return RemoteMedia(
        mediaId = mediaId,
        mediaType = mediaType.toRemoteMediaType(),
        previewUrl = null,
        originalUrl = null,
        videoUrl = null,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        displayTimeMillis = mediaDisplayTimeMillis,
        commentCount = commentCount,
        smallAlbumIds = postIds,
        uploadedByUserId = uploadedByUserId,
    )
}

private fun AlbumPostCardUiModel.toRemotePostSummary(): RemotePostSummary {
    return RemotePostSummary(
        postId = id,
        title = title,
        summary = summary,
        contributorLabel = null,
        creatorUserId = creatorUserId,
        participantUserIds = participantUserIds,
        displayTimeMillis = postDisplayTimeMillis,
        albumId = albumId,
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
        creatorUserId = creatorUserId,
        participantUserIds = participantUserIds,
        displayTimeMillis = postDisplayTimeMillis,
        albumId = albumId,
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
                uploadedByUserId = media.uploadedByUserId,
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
            CommentTargetType.SmallAlbum -> "SMALL_ALBUM"
            CommentTargetType.Media -> "MEDIA"
        },
        targetId = targetId,
        authorId = if (isMine) "fake-self" else null,
        authorName = author,
        content = content,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = null,
        isDeleted = false,
    )
}

private fun com.example.yingshi.feature.photos.NotificationCenterItemUiModel.toRemoteNotification(): RemoteNotification {
    return RemoteNotification(
        notificationId = id,
        type = type.apiValue,
        module = module,
        category = category,
        title = title,
        body = body,
        createdAtMillis = createdAtMillis,
        isRead = isRead,
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName,
        actorAvatarUrl = actorAvatarUrl,
        actorIsCurrentUser = actorIsCurrentUser,
        groupId = groupId,
        operationId = operationId,
        groupItemCount = groupItemCount,
        mediaItems = emptyList(),
        targetRoute = targetRoute,
        targetSummary = targetSummary,
        targetType = targetType,
        smallAlbumId = postId,
        mediaId = mediaId,
        trashItemId = trashItemId,
    )
}

private fun String.toTrashEntryTypeOrNull(): TrashEntryType? {
    return com.example.yingshi.feature.photos.parseTrashEntryTypeOrNull(this)
}

private fun com.example.yingshi.feature.photos.TrashEntryUiModel.toRemoteTrashItem(
    state: String = "inTrash",
): RemoteTrashItem {
    return RemoteTrashItem(
        trashItemId = id,
        itemType = when (type) {
            TrashEntryType.LARGE_ALBUM_DELETED -> "largeAlbumDeleted"
            TrashEntryType.SMALL_ALBUM_DELETED -> "smallAlbumDeleted"
            TrashEntryType.MEDIA_REMOVED -> "mediaRemoved"
            TrashEntryType.MEDIA_SYSTEM_DELETED -> "mediaSystemDeleted"
        },
        state = state,
        actorUserId = actorUserId,
        sourceSmallAlbumId = sourcePostId,
        sourceMediaId = sourceMediaId,
        commentTargetMediaId = commentTargetMediaId
            ?: sourceMediaId
            ?: mediaSnapshot?.mediaId
            ?: relatedMediaIds.firstOrNull { it.isNotBlank() },
        title = title,
        previewInfo = previewInfo,
        deletedAtMillis = deletedAtMillis,
        relatedSmallAlbumIds = relatedPostIds,
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


package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.UpdatePostAlbumsPayload
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.UploadTokenRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.CommentTargetType
import com.example.yingshi.feature.photos.FakeAlbumRepository
import com.example.yingshi.feature.photos.FakeCommentRepository
import com.example.yingshi.feature.photos.FakePhotoFeedRepository
import com.example.yingshi.feature.photos.FakeTrashRepository
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
        return ApiResult.Success(
            items.map { item ->
                RemoteTrashItem(
                    trashItemId = item.id,
                    itemType = item.type.name,
                    sourcePostId = item.sourcePostId,
                    sourceMediaId = item.sourceMediaId,
                    title = item.title,
                    previewInfo = item.previewInfo,
                    deletedAtMillis = item.deletedAtMillis,
                    relatedPostIds = item.relatedPostIds,
                    relatedMediaIds = item.relatedMediaIds,
                )
            },
        )
    }
}

class FakeUploadRepositoryShell : UploadRepository {
    override suspend fun requestUploadToken(
        request: UploadTokenRequestDto,
    ): ApiResult<RemoteUploadToken> {
        return ApiResult.Success(
            RemoteUploadToken(
                uploadId = "fake-upload-${request.fileName}",
                provider = "oss",
                bucket = "placeholder-bucket",
                objectKey = "uploads/fake/${request.fileName}",
                uploadUrl = "https://upload-placeholder.yingshi.local/",
                expireAtMillis = System.currentTimeMillis() + 15 * 60 * 1000L,
            ),
        )
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
            CommentTargetType.Post -> "post"
            CommentTargetType.Media -> "media"
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

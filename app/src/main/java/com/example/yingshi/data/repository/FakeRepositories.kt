package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePost
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
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

class FakePostRepositoryShell : PostRepository {
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

    override suspend fun getPosts(albumId: String?): ApiResult<List<RemotePost>> {
        val posts = FakeAlbumRepository.getPosts()
            .filter { albumId == null || it.albumIds.contains(albumId) }
            .map { post ->
                RemotePost(
                    postId = post.id,
                    title = post.title,
                    summary = post.summary,
                    contributorLabel = null,
                    displayTimeMillis = post.postDisplayTimeMillis,
                    albumIds = post.albumIds,
                    coverMediaId = null,
                    mediaItems = emptyList(),
                )
            }
        return ApiResult.Success(posts)
    }

    override suspend fun getPostDetail(postId: String): ApiResult<RemotePost> {
        val post = FakeAlbumRepository.getPost(postId)
            ?: return ApiResult.Error(code = "POST_NOT_FOUND", message = "Fake post not found")
        val detailRoute = FakeAlbumRepository.toPostDetailRoute(post)
        val detail = FakeAlbumRepository.getPostDetail(detailRoute)
        return ApiResult.Success(
            RemotePost(
                postId = detail.postId,
                title = detail.title,
                summary = detail.summary,
                contributorLabel = detail.contributorLabel,
                displayTimeMillis = detail.postDisplayTimeMillis,
                albumIds = detail.albumIds,
                coverMediaId = null,
                mediaItems = detail.mediaItems.map { media ->
                    RemoteMedia(
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
                        postIds = listOf(detail.postId),
                    )
                },
            ),
        )
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

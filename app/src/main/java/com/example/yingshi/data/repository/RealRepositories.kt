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
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UpdatePostAlbumsPayload
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.api.AlbumApi
import com.example.yingshi.data.remote.api.AuthApi
import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.PostApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.dto.CreateCommentRequestDto
import com.example.yingshi.data.remote.dto.CreateUploadTokenRequestDto
import com.example.yingshi.data.remote.dto.CreatePostRequestDto
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.SetPostCoverRequestDto
import com.example.yingshi.data.remote.dto.UpdateCommentRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostBasicInfoRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostMediaOrderRequestDto
import com.example.yingshi.data.remote.mapper.toRemoteModel
import com.example.yingshi.data.remote.mapper.toRemoteDetail
import com.example.yingshi.data.remote.mapper.toRemotePage
import com.example.yingshi.data.remote.mapper.toRemoteSummary
import com.example.yingshi.data.remote.result.ApiResult

class RealMediaRepository(
    private val mediaApi: MediaApi,
) : MediaRepository {
    override suspend fun getMediaFeed(
        page: Int,
        pageSize: Int,
    ): ApiResult<List<RemoteMedia>> {
        return runCatching {
            mediaApi.getMediaFeed().data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "MEDIA_FEED_REQUEST_FAILED",
                    message = "Stage 11.4 real media feed request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }
}

class RealPostRepository(
    private val postApi: PostApi,
) : PostRepository {
    override suspend fun getPosts(): ApiResult<List<RemotePostSummary>> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Current backend does not provide GET /api/posts list yet",
        )
    }

    override suspend fun getPostDetail(postId: String): ApiResult<RemotePostDetail> {
        return runCatching {
            postApi.getPostDetail(postId).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_DETAIL_REQUEST_FAILED",
                    message = "Stage 11.4 real post detail request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun createPost(payload: CreatePostPayload): ApiResult<RemotePostSummary> {
        return runCatching {
            postApi.createPost(
                CreatePostRequestDto(
                    title = payload.title,
                    summary = payload.summary,
                    contributorLabel = null,
                    displayTimeMillis = payload.displayTimeMillis,
                    albumIds = payload.albumIds,
                    initialMediaIds = payload.initialMediaIds,
                    coverMediaId = null,
                ),
            ).data.toRemoteSummary()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_CREATE_REQUEST_FAILED",
                    message = "Stage 11.4 real post create failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updatePostBasicInfo(
        postId: String,
        payload: UpdatePostBasicInfoPayload,
    ): ApiResult<RemotePostSummary> {
        return runCatching {
            postApi.updatePostBasicInfo(
                postId = postId,
                request = UpdatePostBasicInfoRequestDto(
                    title = payload.title,
                    summary = payload.summary,
                    contributorLabel = null,
                    displayTimeMillis = payload.displayTimeMillis,
                    albumIds = payload.albumIds,
                ),
            ).data.toRemoteSummary()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_UPDATE_REQUEST_FAILED",
                    message = "Stage 11.4 real post basic-info update failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun setPostCover(
        postId: String,
        coverMediaId: String,
    ): ApiResult<RemotePostDetail> {
        return runCatching {
            postApi.setPostCover(
                postId = postId,
                request = SetPostCoverRequestDto(coverMediaId = coverMediaId),
            ).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_COVER_REQUEST_FAILED",
                    message = "Stage 11.4 real post cover update failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updatePostMediaOrder(
        postId: String,
        orderedMediaIds: List<String>,
    ): ApiResult<RemotePostDetail> {
        return runCatching {
            postApi.updatePostMediaOrder(
                postId = postId,
                request = UpdatePostMediaOrderRequestDto(orderedMediaIds = orderedMediaIds),
            ).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_MEDIA_ORDER_REQUEST_FAILED",
                    message = "Stage 11.4 real post media-order update failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }
}

class RealAlbumRepository(
    private val albumApi: AlbumApi,
) : AlbumRepository {
    override suspend fun getAlbums(): ApiResult<List<RemoteAlbum>> {
        return runCatching {
            albumApi.getAlbums().data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_LIST_REQUEST_FAILED",
                    message = "Stage 11.4 real album list request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getAlbumPosts(albumId: String): ApiResult<List<RemotePostSummary>> {
        return runCatching {
            albumApi.getAlbumPosts(albumId = albumId).data.map { it.toRemoteSummary() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_POSTS_REQUEST_FAILED",
                    message = "Stage 11.4 real album-posts request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updatePostAlbums(
        postId: String,
        payload: UpdatePostAlbumsPayload,
    ): ApiResult<RemotePostSummary> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Current backend updates album membership through PATCH /api/posts/{postId}",
        )
    }
}

class RealCommentRepository(
    private val commentApi: CommentApi,
) : CommentRepository {
    override suspend fun getPostComments(
        postId: String,
        page: Int,
        size: Int,
    ): ApiResult<RemoteCommentPage> {
        return runCatching {
            commentApi.getPostComments(postId = postId, page = page, size = size).data.toRemotePage()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_LIST_REQUEST_FAILED",
                    message = "Stage 11.3 real post comment request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getMediaComments(
        mediaId: String,
        page: Int,
        size: Int,
    ): ApiResult<RemoteCommentPage> {
        return runCatching {
            commentApi.getMediaComments(mediaId = mediaId, page = page, size = size).data.toRemotePage()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_LIST_REQUEST_FAILED",
                    message = "Stage 11.3 real media comment request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun createPostComment(
        postId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return runCatching {
            commentApi.createPostComment(
                postId = postId,
                request = CreateCommentRequestDto(content = content),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_CREATE_REQUEST_FAILED",
                    message = "Stage 11.3 real post comment create failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun createMediaComment(
        mediaId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return runCatching {
            commentApi.createMediaComment(
                mediaId = mediaId,
                request = CreateCommentRequestDto(content = content),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_CREATE_REQUEST_FAILED",
                    message = "Stage 11.3 real media comment create failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updateComment(
        commentId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return runCatching {
            commentApi.updateComment(
                commentId = commentId,
                request = UpdateCommentRequestDto(content = content),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_UPDATE_REQUEST_FAILED",
                    message = "Stage 11.3 real comment update failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun deleteComment(commentId: String): ApiResult<Unit> {
        return runCatching {
            commentApi.deleteComment(commentId)
            Unit
        }.fold(
            onSuccess = { ApiResult.Success(Unit) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_DELETE_REQUEST_FAILED",
                    message = "Stage 11.3 real comment delete failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }
}

class RealTrashRepository(
    private val trashApi: TrashApi,
) : TrashRepository {
    override suspend fun getTrashItems(type: String?): ApiResult<List<RemoteTrashItem>> {
        return runCatching {
            trashApi.getTrashItems(itemType = type).data.items.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_LIST_REQUEST_FAILED",
                    message = "Stage 11.6 real trash list request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getTrashDetail(trashItemId: String): ApiResult<RemoteTrashDetail> {
        return runCatching {
            trashApi.getTrashDetail(trashItemId).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_DETAIL_REQUEST_FAILED",
                    message = "Stage 11.6 real trash detail request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun restoreTrashItem(trashItemId: String): ApiResult<RemoteTrashItem> {
        return runCatching {
            trashApi.restoreTrashItem(trashItemId = trashItemId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_RESTORE_REQUEST_FAILED",
                    message = "Stage 11.6 real trash restore request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun moveTrashItemOut(trashItemId: String): ApiResult<RemotePendingCleanup> {
        return runCatching {
            trashApi.removeTrashItem(trashItemId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_REMOVE_REQUEST_FAILED",
                    message = "Stage 11.6 real remove-from-trash request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun undoMoveTrashItemOut(trashItemId: String): ApiResult<RemoteTrashItem> {
        return runCatching {
            trashApi.undoRemoveTrashItem(trashItemId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_UNDO_REMOVE_REQUEST_FAILED",
                    message = "Stage 11.6 real undo-remove request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getPendingCleanupItems(): ApiResult<List<RemotePendingCleanup>> {
        return runCatching {
            trashApi.getPendingCleanupItems().data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_PENDING_REQUEST_FAILED",
                    message = "Stage 11.6 real pending-cleanup request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }
}

class RealUploadRepository(
    private val uploadApi: UploadApi,
) : UploadRepository {
    override suspend fun createUploadToken(
        payload: CreateUploadTokenPayload,
    ): ApiResult<RemoteUploadToken> {
        return runCatching {
            uploadApi.createUploadToken(
                CreateUploadTokenRequestDto(
                    fileName = payload.fileName,
                    mimeType = payload.mimeType,
                    fileSizeBytes = payload.fileSizeBytes,
                    mediaType = payload.mediaType,
                    width = 1,
                    height = 1,
                    durationMillis = null,
                    displayTimeMillis = System.currentTimeMillis(),
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_TOKEN_REQUEST_FAILED",
                    message = "Stage 11.5 real upload-token request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun confirmUpload(
        uploadId: String,
        payload: ConfirmUploadPayload,
    ): ApiResult<RemoteUploadTask> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Current backend uses multipart POST /api/uploads/{uploadId}/file instead of confirm-upload",
        )
    }

    override suspend fun cancelUpload(uploadId: String): ApiResult<RemoteUploadTask> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Current backend has no upload cancel endpoint",
        )
    }

    override suspend fun getUploadTask(uploadId: String): ApiResult<RemoteUploadTask> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Current backend has no upload status endpoint",
        )
    }
}

class RealAuthRepository(
    private val authApi: AuthApi,
) : AuthRepository {
    override suspend fun login(
        request: LoginRequestDto,
    ): ApiResult<RemoteLoginSession> {
        return runCatching {
            val response = authApi.login(request).data
            val tokens = AuthTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                accessTokenExpireAtMillis = response.accessTokenExpireAtMillis,
                refreshTokenExpireAtMillis = response.refreshTokenExpireAtMillis,
            )
            AuthSessionManager.saveTokens(tokens)
            RemoteLoginSession(
                userId = response.userId,
                displayName = response.displayName,
                spaceId = response.spaceId,
                tokens = tokens,
            )
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_LOGIN_REQUEST_FAILED",
                    message = "Stage 11.2 real login request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun refreshToken(
        request: RefreshTokenRequestDto,
    ): ApiResult<AuthTokens> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Current backend does not provide POST /api/auth/refresh-token",
        )
    }

    override suspend fun logout(): ApiResult<Unit> {
        return runCatching {
            authApi.logout(
                request = com.example.yingshi.data.remote.dto.LogoutRequestDto(
                    refreshToken = AuthSessionManager.getRefreshToken().orEmpty(),
                ),
            )
            AuthSessionManager.clearTokens()
            Unit
        }.fold(
            onSuccess = { ApiResult.Success(Unit) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_LOGOUT_REQUEST_FAILED",
                    message = "Stage 11.2 real logout request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getCurrentUser(): ApiResult<RemoteCurrentUser> {
        return runCatching {
            val response = authApi.getCurrentUser().data
            RemoteCurrentUser(
                userId = response.userId,
                displayName = response.displayName,
                avatarUrl = response.avatarUrl,
                spaceId = response.spaceId,
                spaceDisplayName = response.spaceDisplayName,
            )
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_ME_REQUEST_FAILED",
                    message = "Stage 11.2 real current-user request failed before backend is ready",
                    throwable = it,
                )
            },
        )
    }
}

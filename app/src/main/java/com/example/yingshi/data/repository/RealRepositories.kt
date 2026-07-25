package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.cache.AppReadCacheStore
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
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteLoginChallenge
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemoteMediaFeedPage
import com.example.yingshi.data.model.RemoteMediaImportStatus
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemotePushDiagnostics
import com.example.yingshi.data.model.RemotePushPreference
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.RemoteUploadHistoryPage
import com.example.yingshi.data.model.UpdateAlbumPayload
import com.example.yingshi.data.model.UpdatePostAlbumsPayload
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.api.AlbumApi
import com.example.yingshi.data.remote.api.AuthApi
import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.LifeConsoleApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.NotificationApi
import com.example.yingshi.data.remote.api.SmallAlbumApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.dto.CreateAlbumRequestDto
import com.example.yingshi.data.remote.dto.CreateCommentRequestDto
import com.example.yingshi.data.remote.dto.CreateUploadTokenRequestDto
import com.example.yingshi.data.remote.dto.CreatePostRequestDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelEventRequestDto
import com.example.yingshi.data.remote.dto.LifeConsoleMediaRequestDto
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.MediaImportStatusRequestDto
import com.example.yingshi.data.remote.dto.MoveSmallAlbumsRequestDto
import com.example.yingshi.data.remote.dto.RememberedLoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.RegisterPushTokenRequestDto
import com.example.yingshi.data.remote.dto.ResendLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.UpdateLocationRequestDto
import com.example.yingshi.data.remote.dto.UpdateMediaTimeRequestDto
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.dto.UpdateAlbumRequestDto
import com.example.yingshi.data.remote.dto.VerifyLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.AddPostMediaRequestDto
import com.example.yingshi.data.remote.dto.SetPostCoverRequestDto
import com.example.yingshi.data.remote.dto.UpdateCommentRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostBasicInfoRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostMediaBatchRequestDto
import com.example.yingshi.data.remote.dto.UpdatePostMediaOrderRequestDto
import com.example.yingshi.data.remote.dto.UpdatePushPreferenceRequestDto
import com.example.yingshi.data.remote.mapper.toRemoteModel
import com.example.yingshi.data.remote.mapper.toRemoteDetail
import com.example.yingshi.data.remote.mapper.toRemotePage
import com.example.yingshi.data.remote.mapper.toRemoteSummary
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.result.backendErrorCode
import com.example.yingshi.data.remote.result.backendErrorMessage
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
                    message = backendRequestErrorMessage(it, "读取照片流失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getMediaFeedPage(
        cursor: String?,
        pageSize: Int,
    ): ApiResult<RemoteMediaFeedPage> {
        return runCatching {
            val envelope = mediaApi.getMediaFeed(
                cursor = cursor,
                pageSize = pageSize,
            )
            RemoteMediaFeedPage(
                items = envelope.data.map { it.toRemoteModel() },
                nextCursor = envelope.page?.nextCursor,
                hasMore = envelope.page?.hasMore ?: false,
            )
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "MEDIA_FEED_PAGE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取照片流失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getImportStatus(
        sourceFingerprints: List<String>,
    ): ApiResult<List<RemoteMediaImportStatus>> {
        val normalizedFingerprints = sourceFingerprints
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedFingerprints.isEmpty()) {
            return ApiResult.Success(emptyList())
        }
        return runCatching {
            mediaApi.getImportStatus(
                MediaImportStatusRequestDto(sourceFingerprints = normalizedFingerprints),
            ).data.map { dto ->
                RemoteMediaImportStatus(
                    sourceFingerprint = dto.sourceFingerprint,
                    mediaId = dto.mediaId,
                    smallAlbumIds = dto.smallAlbumIds.orEmpty(),
                )
            }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "MEDIA_IMPORT_STATUS_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "同步系统媒体导入状态失败。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun deleteMediaFromPost(
        smallAlbumId: String,
        mediaId: String,
        deleteMode: String,
    ): ApiResult<RemoteTrashItem> {
        return runCatching {
            mediaApi.deleteMediaFromPost(
                smallAlbumId = smallAlbumId,
                mediaId = mediaId,
                deleteMode = deleteMode,
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_MEDIA_DELETE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "从小相册移除媒体失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun systemDeleteMedia(mediaId: String): ApiResult<RemoteTrashItem> {
        return runCatching {
            mediaApi.deleteMediaFromSystem(mediaId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "MEDIA_DELETE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "删除媒体失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updateMediaTime(
        mediaId: String,
        displayTimeMillis: Long,
    ): ApiResult<Long> {
        return runCatching {
            mediaApi.updateMediaTime(
                mediaId = mediaId,
                request = UpdateMediaTimeRequestDto(displayTimeMillis = displayTimeMillis),
            ).data
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "MEDIA_UPDATE_TIME_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "更新媒体时间失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }
}

class RealPostRepository(
    private val postApi: SmallAlbumApi,
) : PostRepository {
    override suspend fun getPosts(): ApiResult<List<RemotePostSummary>> {
        return runCatching {
            postApi.getPosts().data.map { it.toRemoteSummary() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_LIST_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取小相册列表失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getPostDetail(postId: String): ApiResult<RemotePostDetail> {
        return runCatching {
            postApi.getPostDetail(smallAlbumId = postId).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_DETAIL_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取小相册详情失败，请稍后重试。"),
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
                    participantUserIds = payload.participantUserIds,
                    displayTimeMillis = payload.displayTimeMillis,
                    eventStartedAtMillis = payload.eventStartedAtMillis,
                    eventEndedAtMillis = payload.eventEndedAtMillis,
                    displayTimeSource = payload.displayTimeSource,
                    albumId = payload.albumId,
                    initialMediaIds = payload.initialMediaIds,
                    coverMediaId = payload.coverMediaId,
                ),
            ).data.toRemoteSummary()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_CREATE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "创建小相册失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun addMediaToPost(
        postId: String,
        mediaIds: List<String>,
        coverMediaId: String?,
    ): ApiResult<RemotePostDetail> {
        return runCatching {
            postApi.addMediaToPost(
                smallAlbumId = postId,
                request = AddPostMediaRequestDto(
                    mediaIds = mediaIds,
                    coverMediaId = coverMediaId,
                ),
            ).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_ADD_MEDIA_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "加入小相册失败，请稍后重试。"),
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
                smallAlbumId = postId,
                request = UpdatePostBasicInfoRequestDto(
                    title = payload.title,
                    summary = payload.summary,
                    contributorLabel = null,
                    participantUserIds = payload.participantUserIds,
                    displayTimeMillis = payload.displayTimeMillis,
                    eventStartedAtMillis = payload.eventStartedAtMillis,
                    eventEndedAtMillis = payload.eventEndedAtMillis,
                    displayTimeSource = payload.displayTimeSource,
                    albumId = payload.albumId,
                ),
            ).data.toRemoteSummary()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_UPDATE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "保存小相册信息失败，请稍后重试。"),
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
                smallAlbumId = postId,
                request = SetPostCoverRequestDto(coverMediaId = coverMediaId),
            ).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_COVER_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "设置封面失败，请稍后重试。"),
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
                smallAlbumId = postId,
                request = UpdatePostMediaOrderRequestDto(orderedMediaIds = orderedMediaIds),
            ).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_MEDIA_ORDER_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "调整媒体顺序失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updatePostMediaBatch(
        postId: String,
        removeMediaIds: List<String>,
    ): ApiResult<RemotePostDetail> {
        return runCatching {
            postApi.updatePostMediaBatch(
                smallAlbumId = postId,
                request = UpdatePostMediaBatchRequestDto(removeMediaIds = removeMediaIds),
            ).data.toRemoteDetail()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "POST_MEDIA_BATCH_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "批量移除媒体失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun deleteSmallAlbum(smallAlbumId: String): ApiResult<RemoteTrashItem> {
        return runCatching {
            postApi.deleteSmallAlbum(smallAlbumId = smallAlbumId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "SMALL_ALBUM_DELETE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "删除小相册失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }
}

class RealAlbumRepository(
    private val albumApi: AlbumApi,
) : AlbumRepository {
    override suspend fun createAlbum(payload: CreateAlbumPayload): ApiResult<RemoteAlbum> {
        return runCatching {
            albumApi.createAlbum(
                CreateAlbumRequestDto(
                    title = payload.title,
                    subtitle = payload.subtitle,
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_CREATE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "创建大相册失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getAlbums(): ApiResult<List<RemoteAlbum>> {
        return runCatching {
            albumApi.getAlbums().data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_LIST_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取大相册列表失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "读取大相册内容失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updateAlbum(
        albumId: String,
        payload: UpdateAlbumPayload,
    ): ApiResult<RemoteAlbum> {
        return runCatching {
            albumApi.updateAlbum(
                albumId = albumId,
                request = UpdateAlbumRequestDto(
                    title = payload.title,
                    subtitle = payload.subtitle,
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_UPDATE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "重命名大相册失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun deleteAlbum(albumId: String): ApiResult<RemoteTrashItem> {
        return runCatching {
            albumApi.deleteAlbum(albumId = albumId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_DELETE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "删除大相册失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updatePostAlbums(
        postId: String,
        payload: UpdatePostAlbumsPayload,
    ): ApiResult<RemotePostSummary> {
        return when (val result = moveSmallAlbums(
            targetAlbumId = payload.albumId,
            smallAlbumIds = listOf(postId),
        )) {
            is ApiResult.Success -> ApiResult.Success(result.data.firstOrNull() ?: RemotePostSummary(
                postId = postId,
                title = "",
                summary = "",
                contributorLabel = null,
                displayTimeMillis = 0L,
                albumId = payload.albumId,
                coverMediaId = null,
                mediaCount = 0,
            ))
            is ApiResult.Error -> result
            ApiResult.Loading -> ApiResult.Loading
        }
    }

    override suspend fun moveSmallAlbums(
        targetAlbumId: String,
        smallAlbumIds: List<String>,
    ): ApiResult<List<RemotePostSummary>> {
        return runCatching {
            albumApi.moveSmallAlbums(
                targetAlbumId = targetAlbumId,
                request = MoveSmallAlbumsRequestDto(smallAlbumIds = smallAlbumIds),
            ).data.map { it.toRemoteSummary() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "ALBUM_MOVE_SMALL_ALBUMS_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "切换小相册所属大相册失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }
}

class RealCommentRepository(
    private val commentApi: CommentApi,
) : CommentRepository {
    override suspend fun getPostComments(
        smallAlbumId: String,
        page: Int,
        size: Int,
    ): ApiResult<RemoteCommentPage> {
        return runCatching {
            commentApi.getPostComments(smallAlbumId = smallAlbumId, page = page, size = size).data.toRemotePage()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_LIST_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取评论失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "读取评论失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun createPostComment(
        smallAlbumId: String,
        content: String,
    ): ApiResult<RemoteComment> {
        return runCatching {
            commentApi.createPostComment(
                smallAlbumId = smallAlbumId,
                request = CreateCommentRequestDto(content = content),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "COMMENT_CREATE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "发布评论失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "发布评论失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "更新评论失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "删除评论失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }
}

class RealNotificationRepository(
    private val notificationApi: NotificationApi,
) : NotificationRepository {
    override suspend fun getNotifications(limit: Int?, cursor: String?): ApiResult<List<RemoteNotification>> {
        return runCatching {
            notificationApi.getNotifications(limit = limit, cursor = cursor).data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "NOTIFICATION_LIST_REQUEST_FAILED",
                    message = backendRequestErrorMessage(
                        throwable = it,
                        fallback = "\u8bfb\u53d6\u901a\u77e5\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getNotification(notificationId: String): ApiResult<RemoteNotification> {
        return runCatching {
            notificationApi.getNotification(notificationId = notificationId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "NOTIFICATION_DETAIL_REQUEST_FAILED",
                    message = backendRequestErrorMessage(
                        throwable = it,
                        fallback = "\u8bfb\u53d6\u901a\u77e5\u8be6\u60c5\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun markRead(notificationId: String): ApiResult<RemoteNotification> {
        return runCatching {
            notificationApi.markRead(notificationId = notificationId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "NOTIFICATION_MARK_READ_REQUEST_FAILED",
                    message = backendRequestErrorMessage(
                        throwable = it,
                        fallback = "\u6807\u8bb0\u901a\u77e5\u5df2\u8bfb\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun markAllRead(): ApiResult<NotificationMarkAllReadResult> {
        return runCatching {
            notificationApi.markAllRead().data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "NOTIFICATION_MARK_ALL_READ_REQUEST_FAILED",
                    message = backendRequestErrorMessage(
                        throwable = it,
                        fallback = "\u5168\u90e8\u6807\u8bb0\u5df2\u8bfb\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
                    ),
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
        suspend fun fetchPagedItems(itemType: String?): List<RemoteTrashItem> {
            val aggregated = mutableListOf<RemoteTrashItem>()
            var page = 1
            var hasMore = true
            while (hasMore) {
                val response = trashApi.getTrashItems(
                    itemType = itemType,
                    page = page,
                    size = 100,
                ).data
                aggregated += response.items.map { it.toRemoteModel() }
                hasMore = response.hasMore && response.items.isNotEmpty()
                page += 1
            }
            return aggregated.distinctBy { it.trashItemId }
        }

        return runCatching {
            try {
                fetchPagedItems(type)
            } catch (throwable: Throwable) {
                val backendCode = throwable.backendErrorCode()
                val backendMessage = throwable.backendErrorMessage().orEmpty()
                val unsupportedTypeMessage = backendMessage.contains("Unsupported trash item", ignoreCase = true) ||
                    backendMessage.contains("Unsupported trash items", ignoreCase = true) ||
                    backendMessage.contains("Unsupported trash item type", ignoreCase = true) ||
                    backendMessage.contains("Unsupported trash item types", ignoreCase = true)
                val shouldFallbackToUnfilteredList = type == "largeAlbumDeleted" &&
                    (
                        backendCode == "VALIDATION_ERROR" ||
                            throwable is retrofit2.HttpException
                        ) &&
                    unsupportedTypeMessage
                if (!shouldFallbackToUnfilteredList) {
                    throw throwable
                }
                fetchPagedItems(itemType = null).filter { item ->
                    item.itemType.equals(type, ignoreCase = true)
                }
            }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_LIST_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取回收站失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "读取回收站详情失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "恢复回收站项目失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "移出回收站失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun purgeTrashItem(trashItemId: String): ApiResult<RemoteTrashItem> {
        return runCatching {
            trashApi.purgeTrashItem(trashItemId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "TRASH_PURGE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "永久删除失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "撤销移出失败，请稍后重试。"),
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
                    message = backendRequestErrorMessage(it, "读取待处理项目失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    // P1-2 改造: life 回收站列表（按 category 过滤）
    override suspend fun getLifeTrashItems(category: String?): ApiResult<List<RemoteTrashItem>> {
        return runCatching {
            trashApi.getLifeTrashItems(category).data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_TRASH_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取今日痕迹回收站失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    // P1-2 改造: life 回收站 24h 撤回中心（按 category 过滤）
    override suspend fun getLifePendingCleanupItems(category: String?): ApiResult<List<RemotePendingCleanup>> {
        return runCatching {
            trashApi.getLifePendingCleanupItems(category).data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_TRASH_PENDING_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取今日痕迹待处理项目失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }
}

class RealUploadRepository(
    private val uploadApi: UploadApi,
    private val directUploadClient: OkHttpClient = defaultDirectUploadClient,
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
                    width = payload.width,
                    height = payload.height,
                    durationMillis = payload.durationMillis,
                    displayTimeMillis = payload.displayTimeMillis,
                    capturedAtMillis = payload.capturedAtMillis,
                    importedAtMillis = payload.importedAtMillis,
                    displayTimeSource = payload.displayTimeSource,
                    sourceFingerprint = payload.sourceFingerprint,
                    operationId = payload.operationId,
                    operationType = payload.operationType,
                    operationTitle = payload.operationTitle,
                    operationMediaCount = payload.operationMediaCount,
                    sourceItemId = payload.sourceItemId,
                    domain = payload.domain,
                    lifeCategory = payload.lifeCategory,
                    latitude = payload.latitude,
                    longitude = payload.longitude,
                    locationLabel = payload.locationLabel,
                    idempotencyKey = payload.operationId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { operationId ->
                            payload.sourceItemId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { sourceItemId -> "$operationId:$sourceItemId" }
                        }
                        ?.take(128),
                ),
            ).data.toRemoteModel().also { token ->
                uploadTokens[token.uploadId] = token
            }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_TOKEN_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "准备上传失败，请稍后重试。"),
                    throwable = it,
                )
            },
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
        return runCatching {
            val uploadToken = uploadTokens[uploadId]
            val directUploadToken = uploadToken.takeIf { it.isDirectUploadToken() }
            if (directUploadToken != null) {
                return@runCatching uploadDirect(
                    token = directUploadToken,
                    body = ProgressRequestBody(
                        bytes = fileBytes,
                        mimeType = mimeType,
                        onProgressPercent = onProgressPercent,
                        shouldCancel = shouldCancel,
                    ),
                )
            }
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = fileName,
                body = ProgressRequestBody(
                    bytes = fileBytes,
                    mimeType = mimeType,
                    onProgressPercent = onProgressPercent,
                    shouldCancel = shouldCancel,
                ),
            )
            uploadApi.uploadFile(
                uploadId = uploadId,
                file = filePart,
            ).data.media.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_FILE_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "上传文件失败，请稍后重试。"),
                    throwable = it,
                )
            },
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
        return runCatching {
            val uploadToken = uploadTokens[uploadId]
            val directUploadToken = uploadToken.takeIf { it.isDirectUploadToken() }
            if (directUploadToken != null) {
                return@runCatching uploadDirect(
                    token = directUploadToken,
                    body = ProgressInputStreamRequestBody(
                        expectedLengthBytes = fileSizeBytes,
                        mimeType = mimeType,
                        openInputStream = openInputStream,
                        onProgressPercent = onProgressPercent,
                        shouldCancel = shouldCancel,
                    ),
                )
            }
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = fileName,
                body = ProgressInputStreamRequestBody(
                    expectedLengthBytes = fileSizeBytes,
                    mimeType = mimeType,
                    openInputStream = openInputStream,
                    onProgressPercent = onProgressPercent,
                    shouldCancel = shouldCancel,
                ),
            )
            uploadApi.uploadFile(
                uploadId = uploadId,
                file = filePart,
            ).data.media.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_FILE_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "上传文件失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun confirmUpload(
        uploadId: String,
        payload: ConfirmUploadPayload,
    ): ApiResult<RemoteUploadTask> {
        return runCatching {
            uploadApi.confirmUpload(
                uploadId = uploadId,
                request = com.example.yingshi.data.remote.dto.ConfirmUploadRequestDto(
                    etag = payload.etag,
                    objectKey = payload.objectKey,
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_CONFIRM_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "确认上传失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun cancelUpload(uploadId: String): ApiResult<RemoteUploadTask> {
        return runCatching {
            uploadApi.cancelUpload(uploadId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_CANCEL_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "取消上传失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getUploadTask(uploadId: String): ApiResult<RemoteUploadTask> {
        return runCatching {
            uploadApi.getUploadTask(uploadId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_TASK_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "读取上传任务失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getUploadHistory(
        state: String?,
        operationType: String?,
        pageSize: Int,
        cursor: String?,
    ): ApiResult<RemoteUploadHistoryPage> {
        return runCatching {
            val envelope = uploadApi.getUploadHistory(
                state = state,
                operationType = operationType,
                pageSize = pageSize,
                cursor = cursor,
            )
            RemoteUploadHistoryPage(
                tasks = envelope.data.map { it.toRemoteModel() },
                nextCursor = envelope.page?.nextCursor,
                hasMore = envelope.page?.hasMore == true,
            )
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_HISTORY_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "读取传输记录失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun dismissUpload(uploadId: String): ApiResult<RemoteUploadTask> {
        return runCatching {
            uploadApi.dismissUpload(uploadId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_DISMISS_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "清理传输记录失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun dismissUploadBatch(
        state: String?,
        operationType: String?,
    ): ApiResult<List<RemoteUploadTask>> {
        return runCatching {
            uploadApi.dismissUploadBatch(
                com.example.yingshi.data.remote.dto.UploadDismissBatchRequestDto(
                    state = state,
                    operationType = operationType,
                ),
            ).data.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "UPLOAD_DISMISS_BATCH_REQUEST_FAILED",
                    message = uploadRequestErrorMessage(it, "清理传输记录失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    private suspend fun uploadDirect(
        token: RemoteUploadToken,
        body: RequestBody,
    ): RemoteMedia {
        val objectKey = token.objectKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Direct upload token is missing objectKey.")
        val request = Request.Builder()
            .url(token.uploadUrl)
            .put(body)
            .apply {
                token.headers.forEach { (name, value) ->
                    val normalizedName = name.trim()
                    if (
                        normalizedName.isNotBlank() &&
                        value.isNotBlank() &&
                        !normalizedName.equals("host", ignoreCase = true) &&
                        !normalizedName.equals("content-length", ignoreCase = true)
                    ) {
                        header(normalizedName, value)
                    }
                }
            }
            .build()
        try {
            directUploadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Direct upload failed with HTTP ${response.code}.")
                }
                val etag = response.header("ETag")?.trim('"').orEmpty()
                val task = uploadApi.confirmUpload(
                    uploadId = token.uploadId,
                    request = com.example.yingshi.data.remote.dto.ConfirmUploadRequestDto(
                        etag = etag,
                        objectKey = objectKey,
                    ),
                ).data.toRemoteModel()
                return task.media
                    ?: getConfirmedMedia(token.uploadId)
                    ?: throw IllegalStateException("Upload confirmed but media payload was missing.")
            }
        } finally {
            uploadTokens.remove(token.uploadId)
        }
    }

    private suspend fun getConfirmedMedia(uploadId: String): RemoteMedia? {
        return runCatching {
            uploadApi.getUploadTask(uploadId).data.toRemoteModel().media
        }.getOrNull()
    }

    private fun RemoteUploadToken?.isDirectUploadToken(): Boolean {
        if (this == null) return false
        return uploadMethod.equals("presigned-put", ignoreCase = true) &&
            uploadUrl.startsWith("http", ignoreCase = true) &&
            !objectKey.isNullOrBlank()
    }

    companion object {
        private val uploadTokens = ConcurrentHashMap<String, RemoteUploadToken>()

        private val defaultDirectUploadClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
    }
}

private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mimeType: String,
    private val onProgressPercent: (Int) -> Unit,
    private val shouldCancel: () -> Boolean = { false },
) : RequestBody() {
    override fun contentType() = mimeType.toMediaTypeOrNull()

    override fun contentLength() = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        if (bytes.isEmpty()) {
            notifyProgress(100)
            return
        }

        var written = 0
        var lastProgress = -1
        notifyProgress(0)
        while (written < bytes.size) {
            throwIfCancelled()
            val byteCount = minOf(UploadProgressChunkBytes, bytes.size - written)
            sink.write(bytes, written, byteCount)
            throwIfCancelled()
            written += byteCount
            val progress = ((written.toLong() * 100L) / bytes.size.toLong()).toInt().coerceIn(0, 100)
            if (progress != lastProgress) {
                lastProgress = progress
                notifyProgress(progress)
            }
        }
    }

    private fun notifyProgress(progress: Int) {
        onProgressPercent(progress.coerceIn(0, 100))
        throwIfCancelled()
    }

    private fun throwIfCancelled() {
        if (shouldCancel()) {
            throw IOException("Upload cancelled by user")
        }
    }
}

private class ProgressInputStreamRequestBody(
    private val expectedLengthBytes: Long,
    private val mimeType: String,
    private val openInputStream: () -> InputStream,
    private val onProgressPercent: (Int) -> Unit,
    private val shouldCancel: () -> Boolean = { false },
) : RequestBody() {
    override fun contentType() = mimeType.toMediaTypeOrNull()

    override fun contentLength() = expectedLengthBytes.takeIf { it > 0L } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        var written = 0L
        var lastProgress = -1
        val buffer = ByteArray(UploadProgressChunkBytes)
        notifyProgress(0)
        openInputStream().use { input ->
            while (true) {
                throwIfCancelled()
                val readCount = input.read(buffer)
                if (readCount < 0) break
                if (readCount == 0) continue
                sink.write(buffer, 0, readCount)
                throwIfCancelled()
                written += readCount.toLong()
                val progress = if (expectedLengthBytes > 0L) {
                    ((written * 100L) / expectedLengthBytes)
                        .toInt()
                        .coerceIn(0, 100)
                } else {
                    0
                }
                if (progress != lastProgress) {
                    lastProgress = progress
                    notifyProgress(progress)
                }
            }
        }
        if (expectedLengthBytes <= 0L || lastProgress < 100) {
            notifyProgress(100)
        }
    }

    private fun notifyProgress(progress: Int) {
        onProgressPercent(progress.coerceIn(0, 100))
        throwIfCancelled()
    }

    private fun throwIfCancelled() {
        if (shouldCancel()) {
            throw IOException("Upload cancelled by user")
        }
    }
}

private const val UploadProgressChunkBytes = 512 * 1024

private fun backendRequestErrorMessage(
    throwable: Throwable,
    fallback: String,
): String {
    throwable.backendErrorMessage()?.let { return it }
    val backendCode = throwable.backendErrorCode()
    val httpException = throwable as? HttpException
    if (httpException != null) {
        return when {
            backendCode == "AUTH_SESSION_INVALID" ||
                backendCode == "AUTH_TOKEN_EXPIRED" ||
                backendCode == "AUTH_UNAUTHORIZED" -> "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002"
            backendCode == "RESOURCE_NOT_FOUND" -> "\u6ca1\u6709\u627e\u5230\u5bf9\u5e94\u7684\u8d44\u6e90\u3002"
            backendCode == "ACCESS_DENIED" -> "\u5f53\u524d\u8d26\u53f7\u6ca1\u6709\u6267\u884c\u8be5\u64cd\u4f5c\u7684\u6743\u9650\u3002"
            httpException.code() == 401 -> "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002"
            httpException.code() == 403 -> "\u5f53\u524d\u8d26\u53f7\u6ca1\u6709\u6267\u884c\u8be5\u64cd\u4f5c\u7684\u6743\u9650\u3002"
            httpException.code() == 404 -> "\u6ca1\u6709\u627e\u5230\u5bf9\u5e94\u7684\u8d44\u6e90\u3002"
            httpException.code() in 500..599 -> "\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002"
            else -> "\u8bf7\u6c42\u5931\u8d25\uff08${httpException.code()}\uff09\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
        }
    }
    val message = throwable.message?.trim().orEmpty()
    if (message.contains("Unable to resolve host", ignoreCase = true)) {
        return "\u65e0\u6cd5\u8fde\u63a5\u5230\u5f53\u524d\u670d\u52a1\uff0c\u8bf7\u68c0\u67e5\u670d\u52a1\u5730\u5740\u548c\u5c40\u57df\u7f51\u8fde\u63a5\u3002"
    }
    if (
        message.contains("Failed to connect", ignoreCase = true) ||
        message.contains("Connection refused", ignoreCase = true) ||
        message.contains("timeout", ignoreCase = true)
    ) {
        return "\u7f51\u7edc\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u670d\u52a1\u5730\u5740\u3001\u5c40\u57df\u7f51\u8fde\u63a5\u548c\u670d\u52a1\u72b6\u6001\u3002"
    }
    return message.takeIf { it.isNotBlank() } ?: fallback
}

class RealLifeConsoleRepository(
    private val lifeConsoleApi: LifeConsoleApi,
) : LifeConsoleRepository {

    companion object {
        private const val CACHE_MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1000L // 7 天
    }

    override suspend fun getToday(
        date: String?,
        zoneId: String,
    ): ApiResult<RemoteLifeConsoleToday> {
        return runCatching {
            lifeConsoleApi.getToday(date = date, zoneId = zoneId).data.toRemoteModel()
        }.fold(
            onSuccess = { data ->
                AppReadCacheStore.writeLifeConsoleToday(data)
                ApiResult.Success(data)
            },
            onFailure = { throwable ->
                val cached = AppReadCacheStore.readLifeConsoleToday()
                if (cached != null && !isCacheExpired(cached.cachedAtMillis)) {
                    ApiResult.Success(cached.payload, isFromCache = true)
                } else {
                    ApiResult.Error(
                        code = "LIFE_CONSOLE_TODAY_REQUEST_FAILED",
                        message = backendRequestErrorMessage(throwable, "读取今日痕迹失败，请稍后重试。"),
                        throwable = throwable,
                    )
                }
            },
        )
    }

    override suspend fun getHistory(
        zoneId: String,
        limitDays: Int,
    ): ApiResult<RemoteLifeConsoleHistory> {
        return runCatching {
            lifeConsoleApi.getHistory(
                zoneId = zoneId,
                limitDays = limitDays,
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { data ->
                AppReadCacheStore.writeLifeConsoleHistory(data)
                ApiResult.Success(data)
            },
            onFailure = { throwable ->
                val cached = AppReadCacheStore.readLifeConsoleHistory()
                if (cached != null && !isCacheExpired(cached.cachedAtMillis)) {
                    ApiResult.Success(cached.payload, isFromCache = true)
                } else {
                    ApiResult.Error(
                        code = "LIFE_CONSOLE_HISTORY_REQUEST_FAILED",
                        message = backendRequestErrorMessage(throwable, "读取痕迹历史失败，请稍后重试。"),
                        throwable = throwable,
                    )
                }
            },
        )
    }

    private fun isCacheExpired(cachedAtMillis: Long): Boolean {
        return System.currentTimeMillis() - cachedAtMillis > CACHE_MAX_AGE_MILLIS
    }

    override suspend fun addMedia(
        category: String,
        mediaIds: List<String>,
    ): ApiResult<RemoteLifeConsoleToday> {
        return runCatching {
            lifeConsoleApi.addMedia(
                LifeConsoleMediaRequestDto(
                    category = category,
                    mediaIds = mediaIds,
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_CONSOLE_ADD_MEDIA_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "添加今日痕迹照片失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun deleteMedia(
        category: String,
        mediaId: String,
    ): ApiResult<RemoteTrashItem> {
        return runCatching {
            lifeConsoleApi.deleteMedia(mediaId = mediaId, category = category).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_CONSOLE_DELETE_MEDIA_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "删除今日痕迹照片失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    // Round 7 阶段 7: 更新媒体位置
    override suspend fun updateMediaLocation(
        mediaId: String,
        latitude: Double?,
        longitude: Double?,
        locationLabel: String?,
    ): ApiResult<RemoteLifeConsoleToday> {
        return runCatching {
            lifeConsoleApi.updateMediaLocation(
                mediaId = mediaId,
                request = UpdateLocationRequestDto(
                    latitude = latitude,
                    longitude = longitude,
                    locationLabel = locationLabel,
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_CONSOLE_UPDATE_MEDIA_LOCATION_FAILED",
                    message = backendRequestErrorMessage(it, "更新位置失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    // Round 7 阶段 7: 更新大便事件位置
    override suspend fun updateBowelEventLocation(
        eventId: String,
        latitude: Double?,
        longitude: Double?,
        locationLabel: String?,
    ): ApiResult<RemoteLifeConsoleBowelMutation> {
        return runCatching {
            lifeConsoleApi.updateBowelEventLocation(
                eventId = eventId,
                request = UpdateLocationRequestDto(
                    latitude = latitude,
                    longitude = longitude,
                    locationLabel = locationLabel,
                ),
            ).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_CONSOLE_UPDATE_BOWEL_LOCATION_FAILED",
                    message = backendRequestErrorMessage(it, "更新位置失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun addBowelEvent(
        zoneId: String,
        latitude: Double?,
        longitude: Double?,
        locationLabel: String?,
    ): ApiResult<RemoteLifeConsoleBowelMutation> {
        return runCatching {
            val body = LifeConsoleBowelEventRequestDto(
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
            )
            lifeConsoleApi.addBowelEvent(zoneId, body).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_CONSOLE_BOWEL_ADD_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "记录失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun deleteLatestBowelEvent(
        zoneId: String,
    ): ApiResult<RemoteLifeConsoleBowelMutation> {
        return runCatching {
            lifeConsoleApi.deleteLatestBowelEvent(zoneId).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "LIFE_CONSOLE_BOWEL_DELETE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "撤销记录失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun registerPushToken(
        platform: String,
        token: String,
    ): ApiResult<Unit> {
        return runCatching {
            lifeConsoleApi.registerPushToken(
                RegisterPushTokenRequestDto(
                    platform = platform,
                    token = token,
                ),
            )
            Unit
        }.fold(
            onSuccess = { ApiResult.Success(Unit) },
            onFailure = {
                ApiResult.Error(
                    code = "PUSH_TOKEN_REGISTER_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "通知同步失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

}

class RealPushPreferenceRepository(
    private val lifeConsoleApi: LifeConsoleApi,
) : PushPreferenceRepository {
    override suspend fun getPushPreferences(): ApiResult<List<RemotePushPreference>> {
        return runCatching {
            lifeConsoleApi.getPushPreferences().data.preferences.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "PUSH_PREFERENCES_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取推送设置失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getPushDiagnostics(): ApiResult<RemotePushDiagnostics> {
        return runCatching {
            lifeConsoleApi.getPushDiagnostics().data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "PUSH_DIAGNOSTICS_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "读取推送诊断失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updatePushPreference(
        module: String,
        category: String,
        enabled: Boolean,
    ): ApiResult<List<RemotePushPreference>> {
        return runCatching {
            lifeConsoleApi.updatePushPreference(
                UpdatePushPreferenceRequestDto(
                    module = module,
                    category = category,
                    enabled = enabled,
                ),
            ).data.preferences.map { it.toRemoteModel() }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "PUSH_PREFERENCE_UPDATE_REQUEST_FAILED",
                    message = backendRequestErrorMessage(it, "保存推送设置失败，请稍后重试。"),
                    throwable = it,
                )
            },
        )
    }
}

private fun uploadRequestErrorMessage(
    throwable: Throwable,
    fallback: String,
): String {
    throwable.backendErrorMessage()?.let { return it }
    val backendCode = throwable.backendErrorCode()
    val httpException = throwable as? HttpException
    if (httpException != null) {
        return when {
            backendCode == "UPLOAD_NOT_FOUND" -> "\u6ca1\u6709\u627e\u5230\u5bf9\u5e94\u7684\u4e0a\u4f20\u4efb\u52a1\u3002"
            backendCode == "UPLOAD_EXPIRED" -> "\u4e0a\u4f20\u51ed\u8bc1\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20\u3002"
            backendCode == "UPLOAD_OBJECT_MISMATCH" -> "\u4e0a\u4f20\u5bf9\u8c61\u4e0e\u5f53\u524d\u4efb\u52a1\u4e0d\u5339\u914d\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20\u3002"
            backendCode == "UPLOAD_OBJECT_INVALID" ||
                backendCode == "UPLOAD_SIZE_MISMATCH" ||
                backendCode == "UPLOAD_CONTENT_TYPE_MISMATCH" -> "\u4e0a\u4f20\u6587\u4ef6\u6821\u9a8c\u5931\u8d25\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20\u3002"
            httpException.code() == 401 -> "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002"
            httpException.code() == 413 -> "\u6587\u4ef6\u8fc7\u5927\uff0c\u8bf7\u538b\u7f29\u6216\u9009\u62e9\u8f83\u5c0f\u6587\u4ef6\u3002"
            httpException.code() in 500..599 -> "\u4e0a\u4f20\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002"
            else -> "\u4e0a\u4f20\u5931\u8d25\uff08${httpException.code()}\uff09\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
        }
    }
    return throwable.message?.takeIf { it.isNotBlank() } ?: fallback
}

class RealAuthRepository(
    private val authApi: AuthApi,
) : AuthRepository {
    override suspend fun requestLoginChallenge(
        request: LoginRequestDto,
    ): ApiResult<RemoteLoginChallenge> {
        return runCatching {
            authApi.requestLoginChallenge(request).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_LOGIN_CHALLENGE_REQUEST_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "发送验证码失败，请检查服务地址、网络连接和邮件服务配置。",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun resendLoginChallenge(
        request: ResendLoginChallengeRequestDto,
    ): ApiResult<RemoteLoginChallenge> {
        return runCatching {
            authApi.resendLoginChallenge(request).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_LOGIN_CHALLENGE_RESEND_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "重新发送验证码失败，请稍后重试。",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun verifyLoginChallenge(
        request: VerifyLoginChallengeRequestDto,
    ): ApiResult<RemoteLoginSession> {
        return runCatching {
            authApi.verifyLoginChallenge(request).data.toRemoteModel().also(::saveLoginSession)
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_LOGIN_VERIFY_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "登录验证失败，请检查验证码后重试。",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun loginWithRememberedDevice(
        request: RememberedLoginRequestDto,
    ): ApiResult<RemoteLoginSession> {
        return runCatching {
            authApi.loginWithRememberedDevice(request).data.toRemoteModel().also(::saveLoginSession)
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                ApiResult.Error(
                    code = "AUTH_REMEMBERED_LOGIN_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "本机快速登录失败，请重新获取验证码。",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun refreshToken(
        request: RefreshTokenRequestDto,
    ): ApiResult<AuthTokens> {
        return runCatching {
            authApi.refreshToken(request).data.toRemoteModel().also {
                AuthSessionManager.saveTokens(it)
            }
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                val httpCode = (it as? HttpException)?.code()
                ApiResult.Error(
                    code = if (httpCode == 401) "AUTH_UNAUTHORIZED" else "AUTH_REFRESH_REQUEST_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "刷新登录状态失败，请重新登录。",
                    ),
                    throwable = it,
                )
            },
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
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "\u9000\u51fa\u767b\u5f55\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun getCurrentUser(): ApiResult<RemoteCurrentUser> {
        return runCatching {
            authApi.getCurrentUser().data.toRemoteModel().also(AuthSessionManager::saveCurrentUserSnapshot)
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                val httpCode = (it as? HttpException)?.code()
                ApiResult.Error(
                    code = if (httpCode == 401) "AUTH_UNAUTHORIZED" else "AUTH_ME_REQUEST_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "\u83b7\u53d6\u5f53\u524d\u767b\u5f55\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u540e\u7aef\u8fde\u63a5\u72b6\u6001\u3002",
                    ),
                    throwable = it,
                )
            },
        )
    }

    override suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequestDto,
    ): ApiResult<RemoteCurrentUser> {
        return runCatching {
            authApi.updateCurrentUserProfile(request).data.toRemoteModel().also(
                AuthSessionManager::saveCurrentUserSnapshot,
            )
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                val httpCode = (it as? HttpException)?.code()
                ApiResult.Error(
                    code = if (httpCode == 401) "AUTH_UNAUTHORIZED" else "AUTH_PROFILE_UPDATE_REQUEST_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "\u4fdd\u5b58\u8d44\u6599\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
                    ),
                    throwable = it,
                )
            },
        )
    }

    private fun saveLoginSession(session: RemoteLoginSession) {
        AuthSessionManager.saveTokens(session.tokens)
        AuthSessionManager.saveRememberedLogin(
            account = session.account,
            token = session.rememberedLoginToken,
            expireAtMillis = session.rememberedLoginExpireAtMillis,
        )
        AuthSessionManager.saveCurrentUserSnapshot(
            RemoteCurrentUser(
                userId = session.userId,
                account = session.account,
                displayName = session.displayName,
                avatarUrl = session.avatarUrl,
                libraryId = session.libraryId,
                libraryDisplayName = session.libraryDisplayName,
                bio = session.bio,
                partner = session.partner,
                createdAtMillis = session.createdAtMillis,
                updatedAtMillis = session.updatedAtMillis,
            ),
        )
    }

    override suspend fun uploadCurrentUserAvatar(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        openInputStream: () -> InputStream,
        onProgress: (Int) -> Unit,
    ): ApiResult<RemoteCurrentUser> {
        return runCatching {
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = fileName,
                body = ProgressInputStreamRequestBody(
                    expectedLengthBytes = fileSizeBytes,
                    mimeType = mimeType,
                    openInputStream = openInputStream,
                    onProgressPercent = onProgress,
                ),
            )
            authApi.uploadCurrentUserAvatar(filePart).data.toRemoteModel()
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = {
                val httpCode = (it as? HttpException)?.code()
                ApiResult.Error(
                    code = if (httpCode == 401) "AUTH_UNAUTHORIZED" else "AUTH_AVATAR_UPLOAD_REQUEST_FAILED",
                    message = authRequestErrorMessage(
                        throwable = it,
                        fallback = "上传头像失败，请稍后重试。",
                    ),
                    throwable = it,
                )
            },
        )
    }
}

private fun authRequestErrorMessage(
    throwable: Throwable,
    fallback: String,
): String {
    throwable.backendErrorMessage()?.let { return it }
    val backendCode = throwable.backendErrorCode()
    val httpException = throwable as? HttpException
    if (httpException != null) {
        return when {
            backendCode == "AUTH_SESSION_INVALID" ||
                backendCode == "AUTH_TOKEN_EXPIRED" ||
                backendCode == "AUTH_UNAUTHORIZED" -> "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002"
            backendCode == "NOT_FOUND" -> "\u5f53\u524d\u670d\u52a1\u7aef\u8fd8\u6ca1\u5347\u7ea7\u5230\u9a8c\u8bc1\u7801\u767b\u5f55\u7248\u672c\uff0c\u8bf7\u91cd\u542f\u6216\u91cd\u65b0\u90e8\u7f72\u540e\u7aef\u3002"
            httpException.code() == 400 -> "\u8bf7\u6c42\u53c2\u6570\u4e0d\u5b8c\u6574\uff0c\u8bf7\u68c0\u67e5\u540e\u91cd\u8bd5\u3002"
            httpException.code() == 401 -> "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u3002"
            httpException.code() == 403 -> "\u5f53\u524d\u8d26\u53f7\u6ca1\u6709\u6267\u884c\u8be5\u64cd\u4f5c\u7684\u6743\u9650\u3002"
            httpException.code() == 404 -> "\u5f53\u524d\u670d\u52a1\u7aef\u8fd8\u6ca1\u5347\u7ea7\u5230\u9a8c\u8bc1\u7801\u767b\u5f55\u7248\u672c\uff0c\u8bf7\u91cd\u542f\u6216\u91cd\u65b0\u90e8\u7f72\u540e\u7aef\u3002"
            httpException.code() in 500..599 -> "\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002"
            else -> "\u8bf7\u6c42\u5931\u8d25\uff08${httpException.code()}\uff09\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002"
        }
    }
    val message = throwable.message?.trim().orEmpty()
    if (message.contains("Unable to resolve host", ignoreCase = true)) {
        return "\u65e0\u6cd5\u8fde\u63a5\u5230\u5f53\u524d\u670d\u52a1\uff0c\u8bf7\u68c0\u67e5\u670d\u52a1\u5730\u5740\u548c\u5c40\u57df\u7f51\u7f51\u7edc\u3002"
    }
    if (
        message.contains("Failed to connect", ignoreCase = true) ||
        message.contains("Connection refused", ignoreCase = true) ||
        message.contains("timeout", ignoreCase = true)
    ) {
        return "\u767b\u5f55\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u5c40\u57df\u7f51\u8fde\u63a5\u3001\u670d\u52a1\u5730\u5740\u548c\u670d\u52a1\u72b6\u6001\u3002"
    }
    return message.takeIf { it.isNotBlank() } ?: fallback
}

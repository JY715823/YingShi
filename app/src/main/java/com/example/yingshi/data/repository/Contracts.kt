package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreateAlbumPayload
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.NotificationMarkAllReadResult
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginChallenge
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteLifeConsoleBowelMutation
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemoteMediaFeedPage
import com.example.yingshi.data.model.RemoteMediaImportStatus
import com.example.yingshi.data.model.RemoteNotification
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UpdateAlbumPayload
import com.example.yingshi.data.model.UpdatePostAlbumsPayload
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import java.io.InputStream
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RememberedLoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.ResendLoginChallengeRequestDto
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.dto.VerifyLoginChallengeRequestDto
import com.example.yingshi.data.remote.result.ApiResult

interface MediaRepository {
    suspend fun getMediaFeed(
        page: Int = 1,
        pageSize: Int = 20,
    ): ApiResult<List<RemoteMedia>>

    suspend fun getMediaFeedPage(
        cursor: String? = null,
        pageSize: Int = 60,
    ): ApiResult<RemoteMediaFeedPage>

    suspend fun getImportStatus(
        sourceFingerprints: List<String>,
    ): ApiResult<List<RemoteMediaImportStatus>>

    suspend fun deleteMediaFromPost(
        smallAlbumId: String,
        mediaId: String,
        deleteMode: String,
    ): ApiResult<RemoteTrashItem>

    suspend fun systemDeleteMedia(
        mediaId: String,
    ): ApiResult<RemoteTrashItem>
}

interface AlbumRepository {
    suspend fun createAlbum(payload: CreateAlbumPayload): ApiResult<RemoteAlbum>
    suspend fun getAlbums(): ApiResult<List<RemoteAlbum>>
    suspend fun getAlbumPosts(albumId: String): ApiResult<List<RemotePostSummary>>
    suspend fun updateAlbum(
        albumId: String,
        payload: UpdateAlbumPayload,
    ): ApiResult<RemoteAlbum>
    suspend fun deleteAlbum(
        albumId: String,
    ): ApiResult<RemoteTrashItem>
    suspend fun updatePostAlbums(
        postId: String,
        payload: UpdatePostAlbumsPayload,
    ): ApiResult<RemotePostSummary>
}

interface PostRepository {
    suspend fun getPosts(): ApiResult<List<RemotePostSummary>>
    suspend fun getPostDetail(postId: String): ApiResult<RemotePostDetail>
    suspend fun createPost(payload: CreatePostPayload): ApiResult<RemotePostSummary>
    suspend fun addMediaToPost(
        postId: String,
        mediaIds: List<String>,
        coverMediaId: String? = null,
    ): ApiResult<RemotePostDetail>
    suspend fun updatePostBasicInfo(
        postId: String,
        payload: UpdatePostBasicInfoPayload,
    ): ApiResult<RemotePostSummary>
    suspend fun setPostCover(
        postId: String,
        coverMediaId: String,
    ): ApiResult<RemotePostDetail>
    suspend fun updatePostMediaOrder(
        postId: String,
        orderedMediaIds: List<String>,
    ): ApiResult<RemotePostDetail>

    suspend fun deleteSmallAlbum(
        smallAlbumId: String,
    ): ApiResult<RemoteTrashItem>
}

interface CommentRepository {
    suspend fun getPostComments(
        smallAlbumId: String,
        page: Int = 1,
        size: Int = 20,
    ): ApiResult<RemoteCommentPage>

    suspend fun getMediaComments(
        mediaId: String,
        page: Int = 1,
        size: Int = 20,
    ): ApiResult<RemoteCommentPage>

    suspend fun createPostComment(
        smallAlbumId: String,
        content: String,
    ): ApiResult<RemoteComment>

    suspend fun createMediaComment(
        mediaId: String,
        content: String,
    ): ApiResult<RemoteComment>

    suspend fun updateComment(
        commentId: String,
        content: String,
    ): ApiResult<RemoteComment>

    suspend fun deleteComment(
        commentId: String,
    ): ApiResult<Unit>
}

interface NotificationRepository {
    suspend fun getNotifications(
        limit: Int? = null,
    ): ApiResult<List<RemoteNotification>>

    suspend fun getNotification(
        notificationId: String,
    ): ApiResult<RemoteNotification>

    suspend fun markRead(
        notificationId: String,
    ): ApiResult<RemoteNotification>

    suspend fun markAllRead(): ApiResult<NotificationMarkAllReadResult>
}

interface TrashRepository {
    suspend fun getTrashItems(type: String? = null): ApiResult<List<RemoteTrashItem>>
    suspend fun getTrashDetail(trashItemId: String): ApiResult<RemoteTrashDetail>
    suspend fun restoreTrashItem(trashItemId: String): ApiResult<RemoteTrashItem>
    suspend fun moveTrashItemOut(trashItemId: String): ApiResult<RemotePendingCleanup>
    suspend fun purgeTrashItem(trashItemId: String): ApiResult<RemoteTrashItem>
    suspend fun undoMoveTrashItemOut(trashItemId: String): ApiResult<RemoteTrashItem>
    suspend fun getPendingCleanupItems(): ApiResult<List<RemotePendingCleanup>>
}

interface UploadRepository {
    suspend fun createUploadToken(
        payload: CreateUploadTokenPayload,
    ): ApiResult<RemoteUploadToken>

    suspend fun uploadLocalFile(
        uploadId: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        onProgressPercent: (Int) -> Unit = {},
    ): ApiResult<RemoteMedia>

    suspend fun uploadLocalStream(
        uploadId: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        openInputStream: () -> InputStream,
        onProgressPercent: (Int) -> Unit = {},
    ): ApiResult<RemoteMedia>

    suspend fun confirmUpload(
        uploadId: String,
        payload: ConfirmUploadPayload,
    ): ApiResult<RemoteUploadTask>

    suspend fun cancelUpload(
        uploadId: String,
    ): ApiResult<RemoteUploadTask>

    suspend fun getUploadTask(
        uploadId: String,
    ): ApiResult<RemoteUploadTask>

    suspend fun getUploadHistory(
        state: String? = null,
        operationType: String? = null,
        pageSize: Int = 50,
    ): ApiResult<List<RemoteUploadTask>>

    suspend fun dismissUpload(
        uploadId: String,
    ): ApiResult<RemoteUploadTask>

    suspend fun dismissUploadBatch(
        state: String? = null,
        operationType: String? = null,
    ): ApiResult<List<RemoteUploadTask>>
}

interface AuthRepository {
    suspend fun requestLoginChallenge(
        request: LoginRequestDto,
    ): ApiResult<RemoteLoginChallenge>

    suspend fun resendLoginChallenge(
        request: ResendLoginChallengeRequestDto,
    ): ApiResult<RemoteLoginChallenge>

    suspend fun verifyLoginChallenge(
        request: VerifyLoginChallengeRequestDto,
    ): ApiResult<RemoteLoginSession>

    suspend fun loginWithRememberedDevice(
        request: RememberedLoginRequestDto,
    ): ApiResult<RemoteLoginSession>

    suspend fun refreshToken(
        request: RefreshTokenRequestDto,
    ): ApiResult<AuthTokens>

    suspend fun logout(): ApiResult<Unit>

    suspend fun getCurrentUser(): ApiResult<RemoteCurrentUser>

    suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequestDto,
    ): ApiResult<RemoteCurrentUser>

    suspend fun uploadCurrentUserAvatar(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        openInputStream: () -> InputStream,
    ): ApiResult<RemoteCurrentUser>
}

interface LifeConsoleRepository {
    suspend fun getToday(
        date: String? = null,
        zoneId: String = "Asia/Shanghai",
    ): ApiResult<RemoteLifeConsoleToday>

    suspend fun getHistory(
        zoneId: String = "Asia/Shanghai",
        limitDays: Int = 60,
    ): ApiResult<RemoteLifeConsoleHistory>

    suspend fun addMedia(
        category: String,
        mediaIds: List<String>,
    ): ApiResult<RemoteLifeConsoleToday>

    suspend fun deleteMedia(
        category: String,
        mediaId: String,
    ): ApiResult<RemoteTrashItem>

    suspend fun addBowelEvent(): ApiResult<RemoteLifeConsoleBowelMutation>

    suspend fun deleteLatestBowelEvent(): ApiResult<RemoteLifeConsoleBowelMutation>

    suspend fun registerPushToken(
        platform: String,
        token: String,
    ): ApiResult<Unit>
}

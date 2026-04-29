package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.UpdatePostAlbumsPayload
import com.example.yingshi.data.model.UpdatePostBasicInfoPayload
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.remote.dto.RefreshTokenRequestDto
import com.example.yingshi.data.remote.dto.UploadTokenRequestDto
import com.example.yingshi.data.remote.result.ApiResult

interface MediaRepository {
    suspend fun getMediaFeed(
        page: Int = 1,
        pageSize: Int = 20,
    ): ApiResult<List<RemoteMedia>>
}

interface AlbumRepository {
    suspend fun getAlbums(): ApiResult<List<RemoteAlbum>>
    suspend fun getAlbumPosts(albumId: String): ApiResult<List<RemotePostSummary>>
    suspend fun updatePostAlbums(
        postId: String,
        payload: UpdatePostAlbumsPayload,
    ): ApiResult<RemotePostSummary>
}

interface PostRepository {
    suspend fun getPosts(): ApiResult<List<RemotePostSummary>>
    suspend fun getPostDetail(postId: String): ApiResult<RemotePostDetail>
    suspend fun createPost(payload: CreatePostPayload): ApiResult<RemotePostSummary>
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
}

interface CommentRepository {
    suspend fun getPostComments(
        postId: String,
        page: Int = 1,
        size: Int = 20,
    ): ApiResult<RemoteCommentPage>

    suspend fun getMediaComments(
        mediaId: String,
        page: Int = 1,
        size: Int = 20,
    ): ApiResult<RemoteCommentPage>

    suspend fun createPostComment(
        postId: String,
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

interface TrashRepository {
    suspend fun getTrashItems(type: String? = null): ApiResult<List<RemoteTrashItem>>
}

interface UploadRepository {
    suspend fun requestUploadToken(
        request: UploadTokenRequestDto,
    ): ApiResult<RemoteUploadToken>
}

interface AuthRepository {
    suspend fun login(
        request: LoginRequestDto,
    ): ApiResult<RemoteLoginSession>

    suspend fun refreshToken(
        request: RefreshTokenRequestDto,
    ): ApiResult<AuthTokens>

    suspend fun logout(): ApiResult<Unit>

    suspend fun getCurrentUser(): ApiResult<RemoteCurrentUser>
}

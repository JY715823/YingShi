package com.example.yingshi.data.repository

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteLoginSession
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePost
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
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

interface PostRepository {
    suspend fun getAlbums(): ApiResult<List<RemoteAlbum>>
    suspend fun getPosts(albumId: String? = null): ApiResult<List<RemotePost>>
    suspend fun getPostDetail(postId: String): ApiResult<RemotePost>
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

package com.example.yingshi.data.repository

import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePost
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
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
    suspend fun getPostComments(postId: String): ApiResult<List<RemoteComment>>
    suspend fun getMediaComments(mediaId: String): ApiResult<List<RemoteComment>>
}

interface TrashRepository {
    suspend fun getTrashItems(type: String? = null): ApiResult<List<RemoteTrashItem>>
}

interface UploadRepository {
    suspend fun requestUploadToken(
        request: UploadTokenRequestDto,
    ): ApiResult<RemoteUploadToken>
}

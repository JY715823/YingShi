package com.example.yingshi.data.repository

import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePost
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.remote.api.CommentApi
import com.example.yingshi.data.remote.api.MediaApi
import com.example.yingshi.data.remote.api.PostApi
import com.example.yingshi.data.remote.api.TrashApi
import com.example.yingshi.data.remote.api.UploadApi
import com.example.yingshi.data.remote.dto.UploadTokenRequestDto
import com.example.yingshi.data.remote.result.ApiResult

class RealMediaRepository(
    private val mediaApi: MediaApi,
) : MediaRepository {
    override suspend fun getMediaFeed(
        page: Int,
        pageSize: Int,
    ): ApiResult<List<RemoteMedia>> {
        return ApiResult.Error(
            code = "NOT_IMPLEMENTED",
            message = "Stage 11.1 real media repository is a shell only",
        )
    }
}

class RealPostRepository(
    private val postApi: PostApi,
) : PostRepository {
    override suspend fun getAlbums(): ApiResult<List<RemoteAlbum>> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real albums repository is a shell only")
    }

    override suspend fun getPosts(albumId: String?): ApiResult<List<RemotePost>> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real posts repository is a shell only")
    }

    override suspend fun getPostDetail(postId: String): ApiResult<RemotePost> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real post detail repository is a shell only")
    }
}

class RealCommentRepository(
    private val commentApi: CommentApi,
) : CommentRepository {
    override suspend fun getPostComments(postId: String): ApiResult<List<RemoteComment>> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real post comments repository is a shell only")
    }

    override suspend fun getMediaComments(mediaId: String): ApiResult<List<RemoteComment>> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real media comments repository is a shell only")
    }
}

class RealTrashRepository(
    private val trashApi: TrashApi,
) : TrashRepository {
    override suspend fun getTrashItems(type: String?): ApiResult<List<RemoteTrashItem>> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real trash repository is a shell only")
    }
}

class RealUploadRepository(
    private val uploadApi: UploadApi,
) : UploadRepository {
    override suspend fun requestUploadToken(
        request: UploadTokenRequestDto,
    ): ApiResult<RemoteUploadToken> {
        return ApiResult.Error(code = "NOT_IMPLEMENTED", message = "Stage 11.1 real upload repository is a shell only")
    }
}

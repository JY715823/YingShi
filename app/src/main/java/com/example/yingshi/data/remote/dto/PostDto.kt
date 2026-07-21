package com.example.yingshi.data.remote.dto

data class PostSummaryDto(
    val smallAlbumId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val creatorUserId: String? = null,
    val participantUserIds: List<String>? = emptyList(),
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = null,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val albumId: String,
    val systemKey: String? = null,
    val coverMediaId: String? = null,
    val mediaCount: Int = 0,
) {
    val postId: String
        get() = smallAlbumId

    val albumIds: List<String>
        get() = listOf(albumId)
}

typealias SmallAlbumSummaryDto = PostSummaryDto

data class PostDetailDto(
    val smallAlbumId: String,
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val creatorUserId: String? = null,
    val participantUserIds: List<String>? = emptyList(),
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = null,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val albumId: String,
    val systemKey: String? = null,
    val coverMediaId: String? = null,
    val mediaCount: Int = 0,
    val mediaItems: List<PostMediaDto> = emptyList(),
) {
    val postId: String
        get() = smallAlbumId

    val albumIds: List<String>
        get() = listOf(albumId)
}

typealias SmallAlbumDetailDto = PostDetailDto

data class PostMediaDto(
    val sortOrder: Int = 0,
    val isCover: Boolean = false,
    val media: MediaDto,
)

typealias SmallAlbumMediaDto = PostMediaDto

data class CreatePostRequestDto(
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val participantUserIds: List<String> = emptyList(),
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = null,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val albumId: String,
    val initialMediaIds: List<String> = emptyList(),
    val coverMediaId: String? = null,
) {
    val albumIds: List<String>
        get() = listOf(albumId)
}

typealias CreateSmallAlbumRequestDto = CreatePostRequestDto

data class UpdatePostBasicInfoRequestDto(
    val title: String,
    val summary: String,
    val contributorLabel: String? = null,
    val participantUserIds: List<String>? = null,
    val displayTimeMillis: Long,
    val eventStartedAtMillis: Long? = null,
    val eventEndedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val albumId: String,
) {
    val albumIds: List<String>
        get() = listOf(albumId)
}

typealias UpdateSmallAlbumBasicInfoRequestDto = UpdatePostBasicInfoRequestDto

data class SetPostCoverRequestDto(
    val coverMediaId: String,
)

typealias SetSmallAlbumCoverRequestDto = SetPostCoverRequestDto

data class UpdatePostMediaOrderRequestDto(
    val orderedMediaIds: List<String>,
)

typealias UpdateSmallAlbumMediaOrderRequestDto = UpdatePostMediaOrderRequestDto

data class UpdatePostMediaBatchRequestDto(
    val removeMediaIds: List<String>,
)

typealias UpdateSmallAlbumMediaBatchRequestDto = UpdatePostMediaBatchRequestDto

data class AddPostMediaRequestDto(
    val mediaIds: List<String>,
    val coverMediaId: String? = null,
)

typealias AddSmallAlbumMediaRequestDto = AddPostMediaRequestDto

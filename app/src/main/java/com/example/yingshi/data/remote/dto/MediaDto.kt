package com.example.yingshi.data.remote.dto

data class MediaAccessDto(
    val variant: String,
    val url: String? = null,
    val signedUrl: String? = null,
    val expiresAtMillis: Long? = null,
    val cacheKey: String? = null,
    val revision: String? = null,
)

data class MediaDto(
    val mediaId: String,
    val mediaType: String? = null,
    val type: String? = null,
    val url: String? = null,
    val mediaUrl: String? = null,
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,
    val originalUrl: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Float? = null,
    val durationMillis: Long? = null,
    val duration: Long? = null,
    val displayTimeMillis: Long = 0L,
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val recordOwnerUserId: String? = null,
    val uploadedByUserId: String? = null,
    val createdAtMillis: Long? = null,
    val smallAlbumIds: List<String>? = null,
    val access: List<MediaAccessDto>? = null,
    // FR-18: optional location tag (nullable for backward compat with older server responses)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
) {
    val postIds: List<String>
        get() = smallAlbumIds.orEmpty()
}

data class MediaImportStatusRequestDto(
    val sourceFingerprints: List<String>,
)

data class MediaImportStatusDto(
    val sourceFingerprint: String,
    val mediaId: String,
    val smallAlbumIds: List<String>? = emptyList(),
)

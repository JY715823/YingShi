package com.example.yingshi.data.remote.dto

data class UploadTokenDto(
    val uploadId: String,
    val provider: String,
    val uploadUrl: String,
    val expireAtMillis: Long,
    val state: String,
    val uploadMethod: String? = null,
    val objectKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val confirmUrl: String? = null,
)

data class CreateUploadTokenRequestDto(
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val mediaType: String,
    val width: Int,
    val height: Int,
    val durationMillis: Long? = null,
    val displayTimeMillis: Long,
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val sourceFingerprint: String? = null,
)

data class UploadCompleteResponseDto(
    val uploadId: String,
    val state: String,
    val media: MediaDto,
)

data class UploadTaskDto(
    val uploadId: String,
    val fileName: String,
    val mediaType: String,
    val objectKey: String? = null,
    val mediaId: String? = null,
    val state: String,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
    val media: MediaDto? = null,
)

data class ConfirmUploadRequestDto(
    val etag: String,
    val objectKey: String,
)

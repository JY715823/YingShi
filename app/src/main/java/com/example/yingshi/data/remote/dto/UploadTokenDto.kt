package com.example.yingshi.data.remote.dto

data class UploadTokenDto(
    val uploadId: String,
    val provider: String,
    val bucket: String,
    val objectKey: String,
    val uploadUrl: String,
    val accessKeyId: String? = null,
    val policy: String? = null,
    val signature: String? = null,
    val expireAtMillis: Long,
)

data class UploadTokenRequestDto(
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val mediaType: String,
)

data class CompleteUploadRequestDto(
    val etag: String,
    val objectKey: String,
)

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

data class CreateUploadTokenRequestDto(
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val mediaType: String,
)

data class ConfirmUploadRequestDto(
    val etag: String,
    val objectKey: String,
)

data class UploadTaskDto(
    val uploadId: String,
    val fileName: String,
    val mediaType: String,
    val objectKey: String? = null,
    val state: String,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
)

package com.example.yingshi.data.model

enum class UploadState {
    WAITING,
    UPLOADING,
    SUCCESS,
    FAILURE,
    CANCELLED,
}

data class CreateUploadTokenPayload(
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val mediaType: String,
    val width: Int,
    val height: Int,
    val durationMillis: Long? = null,
    val displayTimeMillis: Long,
)

data class ConfirmUploadPayload(
    val etag: String,
    val objectKey: String,
)

data class RemoteUploadTask(
    val uploadId: String,
    val fileName: String,
    val mediaType: String,
    val objectKey: String?,
    val state: UploadState,
    val progressPercent: Int,
    val errorMessage: String? = null,
)

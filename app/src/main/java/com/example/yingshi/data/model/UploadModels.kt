package com.example.yingshi.data.model

enum class UploadState {
    WAITING,
    UPLOADING,
    SUCCESS,
    FAILED,
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
    val capturedAtMillis: Long? = null,
    val importedAtMillis: Long? = null,
    val displayTimeSource: String? = null,
    val sourceFingerprint: String? = null,
    val operationId: String? = null,
    val operationType: String? = null,
    val operationTitle: String? = null,
    val operationMediaCount: Int? = null,
    val sourceItemId: String? = null,
    val domain: String? = null,
    // life 模块分类: PERSON / MEAL / null（非 life 上传）
    val lifeCategory: String? = null,
    // FR-18: optional location fields
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
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
    val mediaId: String? = null,
    val state: UploadState,
    val progressPercent: Int,
    val errorMessage: String? = null,
    val operationId: String? = null,
    val operationType: String? = null,
    val operationTitle: String? = null,
    val operationMediaCount: Int? = null,
    val sourceItemId: String? = null,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val media: RemoteMedia? = null,
)

/**
 * FR-3: 分页后的上传历史。
 * tasks 为本页任务，nextCursor 为下一页游标（null 表示无更多），hasMore 表示是否还有下一页。
 */
data class RemoteUploadHistoryPage(
    val tasks: List<RemoteUploadTask>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

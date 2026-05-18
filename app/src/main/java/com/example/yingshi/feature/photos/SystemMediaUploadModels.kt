package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import com.example.yingshi.data.model.UploadState

@Immutable
data class SystemMediaUploadTaskUiModel(
    val taskId: String,
    val operationId: String,
    val mediaId: String,
    val fileName: String,
    val targetLabel: String,
    val mediaType: SystemMediaType = SystemMediaType.IMAGE,
    val previewUri: String? = null,
    val resultMediaId: String? = null,
    val progressPercent: Int,
    val state: UploadState,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val operationType: LocalSystemMediaBridgeRepository.OperationType = LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP,
    val operationTitle: String? = null,
    val operationMediaCount: Int = 1,
    val operationSuccessCount: Int = 0,
    val operationFailureCount: Int = 0,
    val operationCancelledCount: Int = 0,
    val resultPostRoute: PostDetailPlaceholderRoute? = null,
) {
    val isTerminal: Boolean
        get() = state == UploadState.SUCCESS || state == UploadState.FAILURE || state == UploadState.CANCELLED
}

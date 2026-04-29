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
    val progressPercent: Int,
    val state: UploadState,
    val errorMessage: String? = null,
) {
    val isTerminal: Boolean
        get() = state == UploadState.SUCCESS || state == UploadState.FAILURE || state == UploadState.CANCELLED
}

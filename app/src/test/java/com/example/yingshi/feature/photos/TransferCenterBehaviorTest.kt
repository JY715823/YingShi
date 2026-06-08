package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.UploadState
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferCenterBehaviorTest {

    @Test
    fun terminalFailedTasksStillCountAsCompletedProgress() {
        val tasks = listOf(
            sampleTask(taskId = "task-1", state = UploadState.SUCCESS, progressPercent = 100),
            sampleTask(taskId = "task-2", state = UploadState.FAILURE, progressPercent = 18),
            sampleTask(taskId = "task-3", state = UploadState.CANCELLED, progressPercent = 0),
        )

        assertEquals(3, calculateTransferOperationProcessedCount(tasks, totalCount = 3))
        assertEquals(100, calculateTransferOperationProgressPercent(tasks, totalCount = 3))
    }

    @Test
    fun virtualCompletedMediaContributeToGroupProgress() {
        val tasks = listOf(
            sampleTask(taskId = "task-1", state = UploadState.SUCCESS, progressPercent = 100),
            sampleTask(taskId = "task-2", state = UploadState.UPLOADING, progressPercent = 50),
        )

        assertEquals(2, calculateTransferOperationProcessedCount(tasks, totalCount = 3))
        assertEquals(83, calculateTransferOperationProgressPercent(tasks, totalCount = 3))
    }

    @Test
    fun retryActionLabelReflectsRetryableCount() {
        assertEquals("重试", transferRetryActionLabel(1))
        assertEquals("重试 3 项", transferRetryActionLabel(3))
    }

    private fun sampleTask(
        taskId: String,
        state: UploadState,
        progressPercent: Int,
    ): SystemMediaUploadTaskUiModel {
        return SystemMediaUploadTaskUiModel(
            taskId = taskId,
            operationId = "operation-1",
            mediaId = "media-$taskId",
            fileName = "$taskId.jpg",
            targetLabel = "导入照片流",
            progressPercent = progressPercent,
            state = state,
            canRetry = state == UploadState.FAILURE || state == UploadState.CANCELLED,
        )
    }
}

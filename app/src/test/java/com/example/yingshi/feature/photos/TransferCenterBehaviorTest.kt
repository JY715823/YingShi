package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.UploadState
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TransferCenterBehaviorTest {

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun terminalFailedTasksStillCountAsCompletedProgress() {
        val tasks = listOf(
            sampleTask(taskId = "task-1", state = UploadState.SUCCESS, progressPercent = 100),
            sampleTask(taskId = "task-2", state = UploadState.FAILED, progressPercent = 18),
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

    // FR-3 TC-EXT-1: loadMoreUploadHistory 守卫逻辑
    @Test
    fun loadMoreUploadHistory_respectsHasMoreAndCursorState() {
        // 重置状态
        UploadManager.remoteHistoryHasMore.value = false
        UploadManager.remoteHistoryCursor.value = null
        UploadManager.remoteHistoryLoadingMore.value = false
        UploadManager.remoteHistoryLoadMoreFailed.value = false

        // 验证 hasMore=false 时直接返回，isLoadingMore 保持 false
        UploadManager.loadMoreUploadHistory()
        assertFalse(UploadManager.remoteHistoryLoadingMore.value)

        // 验证 cursor=null (hasMore=true) 时直接返回
        UploadManager.remoteHistoryHasMore.value = true
        UploadManager.remoteHistoryCursor.value = null
        UploadManager.loadMoreUploadHistory()
        assertFalse(UploadManager.remoteHistoryLoadingMore.value)
    }

    // FR-3 AC-7 TC-EXT-3: loadMoreFailed 默认 false，守卫返回时不触发失败标记
    @Test
    fun loadMoreFailed_defaultsFalseAndUnaffectedByGuardReturn() {
        UploadManager.remoteHistoryHasMore.value = false
        UploadManager.remoteHistoryCursor.value = null
        UploadManager.remoteHistoryLoadMoreFailed.value = false

        UploadManager.loadMoreUploadHistory()
        // 守卫直接返回，failed 保持 false
        assertFalse(UploadManager.remoteHistoryLoadMoreFailed.value)
    }

    // FR-11.1 TC-EXT-2: 时间筛选阈值过滤逻辑
    @Test
    fun timeRangeFilter_excludesOlderGroups() {
        val now = System.currentTimeMillis()
        val todayTask = sampleTask(
            taskId = "t1",
            state = UploadState.SUCCESS,
            progressPercent = 100,
            createdAtMillis = now,
        )
        val oldTask = sampleTask(
            taskId = "t2",
            state = UploadState.SUCCESS,
            progressPercent = 100,
            createdAtMillis = now - 48L * 60 * 60 * 1000,
        )
        val tasks = listOf(todayTask, oldTask)
        val threshold = now - 24L * 60 * 60 * 1000
        val filtered = tasks.filter { it.transferSortMillis() >= threshold }
        assertEquals(1, filtered.size)
        assertEquals("t1", filtered.first().taskId)
    }

    private fun sampleTask(
        taskId: String,
        state: UploadState,
        progressPercent: Int,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): SystemMediaUploadTaskUiModel {
        return SystemMediaUploadTaskUiModel(
            taskId = taskId,
            operationId = "operation-1",
            mediaId = "media-$taskId",
            fileName = "$taskId.jpg",
            targetLabel = "导入照片流",
            progressPercent = progressPercent,
            state = state,
            canRetry = state == UploadState.FAILED || state == UploadState.CANCELLED,
            createdAtMillis = createdAtMillis,
        )
    }
}

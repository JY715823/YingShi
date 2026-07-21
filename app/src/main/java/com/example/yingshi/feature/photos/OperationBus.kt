package com.example.yingshi.feature.photos

import androidx.compose.runtime.mutableStateListOf
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.MutationKind
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationResultEvent
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationType

internal data class OperationTaskMeta(
    val operationType: OperationType,
    val targetLabel: String,
    val operationTitle: String,
    val mediaCount: Int,
)

internal object OperationBus {
    internal val operationResultsState = mutableStateListOf<OperationResultEvent>()
    internal val publishedOperationSummaryIds = mutableSetOf<String>()
    internal val publishedFirstSuccessOperationIds = mutableSetOf<String>()
    internal val finalizedOperationIds = mutableSetOf<String>()

    internal var uploadTasksProvider: () -> List<SystemMediaUploadTaskUiModel> = { emptyList() }
    internal var operationRequestProvider: (String) -> PendingOperationRequest? = { null }

    val operationResults: List<OperationResultEvent>
        get() = operationResultsState

    internal fun publishOperationResult(event: OperationResultEvent) {
        operationResultsState.add(event)
        while (operationResultsState.size > 12) {
            operationResultsState.removeAt(0)
        }
    }

    internal fun publishOperationResult(
        operationId: String,
        operationType: OperationType,
        succeeded: Boolean,
        message: String,
        postRoute: PostDetailPlaceholderRoute? = null,
    ) {
        publishOperationResult(
            OperationResultEvent(
                eventId = "${operationId}-${if (succeeded) "success" else "failure"}-${System.currentTimeMillis()}",
                operationId = operationId,
                operationType = operationType,
                succeeded = succeeded,
                message = message,
                postRoute = postRoute,
            ),
        )
    }

    internal fun publishOperationSummaryIfReady(
        operationId: String,
        operationType: OperationType? = null,
        postRoute: PostDetailPlaceholderRoute? = null,
    ) {
        if (publishedOperationSummaryIds.contains(operationId)) return
        val operationTasks = uploadTasksProvider().filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        if (!operationTasks.all { it.isTerminal }) return

        val successCount = operationTasks.count { it.state == UploadState.SUCCESS }
        val failureCount = operationTasks.count { it.state == UploadState.FAILURE }
        val cancelledCount = operationTasks.count { it.state == UploadState.CANCELLED }
        val resultMediaIds = operationTasks
            .filter { it.state == UploadState.SUCCESS }
            .mapNotNull { it.resultMediaId?.takeIf { mediaId -> mediaId.isNotBlank() } }
            .distinct()
        val request = operationRequestProvider(operationId)
        val resolvedOperationType = operationType ?: request?.operationType ?: OperationType.IMPORT_TO_APP
        publishedOperationSummaryIds += operationId
        publishOperationResult(
            OperationResultEvent(
                eventId = "$operationId-summary-${System.currentTimeMillis()}",
                operationId = operationId,
                operationType = resolvedOperationType,
                succeeded = failureCount == 0 && cancelledCount == 0,
                message = uploadSummaryMessage(successCount = successCount, failureCount = failureCount + cancelledCount),
                postRoute = postRoute,
                resultMediaIds = resultMediaIds,
                successCount = successCount,
                failureCount = failureCount,
                cancelledCount = cancelledCount,
                totalCount = operationTasks.size,
                shouldAutoOpenResult = resolvedOperationType == OperationType.IMPORT_TO_APP &&
                    successCount > 0 &&
                    failureCount == 0 &&
                    cancelledCount == 0,
            ),
        )
    }

    internal fun publishFirstSuccessIfNeeded(
        operationId: String,
        operationType: OperationType,
        mediaId: String,
    ) {
        if (mediaId.isBlank()) return
        if (!publishedFirstSuccessOperationIds.add(operationId)) return
        publishOperationResult(
            OperationResultEvent(
                eventId = "$operationId-first-success-${System.currentTimeMillis()}",
                operationId = operationId,
                operationType = operationType,
                succeeded = true,
                message = "\u5df2\u4e0a\u4f20 1 \u4e2a\uff0c\u6b63\u5728\u7ee7\u7eed\u4e0a\u4f20\u5269\u4f59\u5a92\u4f53\u3002",
                resultMediaIds = listOf(mediaId),
                successCount = 1,
                failureCount = 0,
                totalCount = uploadTasksProvider().count { it.operationId == operationId }.coerceAtLeast(1),
                shouldAutoOpenResult = false,
            ),
        )
    }

    internal fun publishUploadFailure(
        operationId: String,
        message: String,
    ) {
        publishOperationSummaryIfReady(operationId)
    }

    internal fun dismissOperationResult(eventId: String) {
        operationResultsState.removeAll { it.eventId == eventId }
    }

    internal fun operationTaskMeta(operationId: String): OperationTaskMeta {
        val request = operationRequestProvider(operationId)
        return when (request) {
            is CreatePostOperationRequest -> {
                OperationTaskMeta(
                    operationType = OperationType.CREATE_POST,
                    targetLabel = "新建小相册",
                    operationTitle = request.draft.title.ifBlank { buildRealPostTitle(request.mediaItems) },
                    mediaCount = request.mediaItems.size + request.additionalAppMediaIds.size,
                )
            }
            is AddToExistingPostOperationRequest -> {
                val title = request.postTitle.ifBlank {
                    FakeAlbumRepository.getPost(request.postId)?.title ?: request.postId
                }
                OperationTaskMeta(
                    operationType = OperationType.ADD_TO_EXISTING_POST,
                    targetLabel = "加入已有小相册",
                    operationTitle = title,
                    mediaCount = request.mediaItems.size,
                )
            }
            is ImportToAppOperationRequest -> {
                OperationTaskMeta(
                    operationType = OperationType.IMPORT_TO_APP,
                    targetLabel = "导入照片流",
                    operationTitle = "导入到照片流",
                    mediaCount = request.mediaItems.size,
                )
            }
            null -> {
                OperationTaskMeta(
                    operationType = OperationType.IMPORT_TO_APP,
                    targetLabel = "导入照片流",
                    operationTitle = "传输任务",
                    mediaCount = uploadTasksProvider().count { it.operationId == operationId }.coerceAtLeast(1),
                )
            }
        }
    }

    private fun uploadSummaryMessage(
        successCount: Int,
        failureCount: Int,
    ): String {
        return if (failureCount > 0) {
            "\u4e0a\u4f20\u5b8c\u6210\uff1a\u6210\u529f $successCount \u4e2a\uff0c\u5931\u8d25 $failureCount \u4e2a"
        } else {
            "\u4e0a\u4f20\u5b8c\u6210\uff1a\u6210\u529f $successCount \u4e2a"
        }
    }
}

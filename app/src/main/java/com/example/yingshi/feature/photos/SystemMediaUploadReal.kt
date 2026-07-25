package com.example.yingshi.feature.photos

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.LocationHelper
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.MutationKind
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationResultEvent
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ReadLocalMediaTimeoutMillis = 180_000L
private const val CreateUploadTokenTimeoutMillis = 15_000L
private const val UploadFileTimeoutMillis = 10 * 60_000L

internal data class RealUploadMetadata(
    val fileName: String,
    val mimeType: String,
    val mediaType: SystemMediaType,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long? = null,
    val displayTimeMillis: Long,
    val capturedAtMillis: Long?,
    val importedAtMillis: Long,
    val displayTimeSource: String,
    val sourceUri: Uri,
    // Round 8 第十四轮: EXIF GPS (已转 GCJ-02), 没有则为 null
    val latitude: Double? = null,
    val longitude: Double? = null,
)

internal data class UploadedOperationMedia(
    val orderedUploadedMediaIds: List<String>,
    val uploadedMediaIdBySourceId: Map<String, String>,
)

internal data class RealFinalizeResult(
    val operationType: OperationType,
    val successMessage: String,
    val postRoute: PostDetailPlaceholderRoute? = null,
    val affectedPostIds: Set<String> = emptySet(),
)

internal fun enqueueImportToAppUploadReal(
    context: Context,
    mediaItems: List<SystemMediaItem>,
): Int {
    val operationId = "real-import-app-${System.currentTimeMillis()}"
    UploadManager.operationRequestsById[operationId] = ImportToAppOperationRequest(mediaItems = mediaItems)
    mediaItems.forEach { item ->
        enqueueRealUploadTask(
            context = context,
            operationId = operationId,
            mediaItem = item,
            targetLabel = "导入照片流",
            sourceItems = mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeImportToAppReal(uploadedMedia)
            },
        )
    }
    return mediaItems.size
}

internal fun enqueueCreatePostUploadReal(
    context: Context,
    mediaItems: List<SystemMediaItem>,
    draft: CreatePostDraft,
    additionalAppMediaIds: List<String>,
    additionalAppCoverMediaId: String?,
): Int {
    val operationId = "real-create-post-${System.currentTimeMillis()}"
    val normalizedAdditionalAppMediaIds = additionalAppMediaIds.distinct()
    val finalDraft = draft.copy(coverSourceMediaId = draft.coverSourceMediaId ?: additionalAppCoverMediaId)
    val reusableMediaIdsBySourceId = reusableAppMediaIdsBySourceId(mediaItems)
    val uploadItems = mediaItems.filterNot { reusableMediaIdsBySourceId.containsKey(it.id) }
    UploadManager.operationRequestsById[operationId] = CreatePostOperationRequest(
        draft = finalDraft,
        mediaItems = mediaItems,
        additionalAppMediaIds = normalizedAdditionalAppMediaIds,
        additionalAppCoverMediaId = additionalAppCoverMediaId,
    )
    UploadManager.rememberUploadedMediaIds(
        operationId = operationId,
        sourceMediaIdsToUploadedMediaIds = reusableMediaIdsBySourceId,
    )
    if (uploadItems.isEmpty()) {
        finalizeRealOperationWithKnownMedia(
            operationId = operationId,
            sourceItems = mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeCreatePostReal(
                    draft = finalDraft,
                    sourceItems = mediaItems,
                    uploadedMedia = uploadedMedia,
                    additionalAppMediaIds = normalizedAdditionalAppMediaIds,
                    additionalAppCoverMediaId = additionalAppCoverMediaId,
                )
            },
        )
        return mediaItems.size
    }
    uploadItems.forEach { item ->
        enqueueRealUploadTask(
            context = context,
            operationId = operationId,
            mediaItem = item,
            targetLabel = "新建小相册",
            sourceItems = mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeCreatePostReal(
                    draft = finalDraft,
                    sourceItems = mediaItems,
                    uploadedMedia = uploadedMedia,
                    additionalAppMediaIds = normalizedAdditionalAppMediaIds,
                    additionalAppCoverMediaId = additionalAppCoverMediaId,
                )
            },
        )
    }
    return uploadItems.size
}

internal fun enqueueAddToExistingPostUploadReal(
    context: Context,
    postId: String,
    mediaItems: List<SystemMediaItem>,
    postTitle: String,
): Int {
    val operationId = "real-append-post-$postId-${System.currentTimeMillis()}"
    val reusableMediaIdsBySourceId = reusableAppMediaIdsBySourceId(mediaItems)
    val uploadItems = mediaItems.filterNot { reusableMediaIdsBySourceId.containsKey(it.id) }
    UploadManager.operationRequestsById[operationId] = AddToExistingPostOperationRequest(
        postId = postId,
        postTitle = postTitle,
        mediaItems = mediaItems,
    )
    UploadManager.rememberUploadedMediaIds(
        operationId = operationId,
        sourceMediaIdsToUploadedMediaIds = reusableMediaIdsBySourceId,
    )
    if (uploadItems.isEmpty()) {
        finalizeRealOperationWithKnownMedia(
            operationId = operationId,
            sourceItems = mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeAppendToPostReal(
                    postId = postId,
                    sourceItems = mediaItems,
                    uploadedMedia = uploadedMedia,
                )
            },
        )
        return mediaItems.size
    }
    uploadItems.forEach { item ->
        enqueueRealUploadTask(
            context = context,
            operationId = operationId,
            mediaItem = item,
            targetLabel = "加入已有小相册",
            sourceItems = mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeAppendToPostReal(
                    postId = postId,
                    sourceItems = mediaItems,
                    uploadedMedia = uploadedMedia,
                )
            },
        )
    }
    return uploadItems.size
}

internal fun enqueueRealUploadTask(
    context: Context,
    operationId: String,
    mediaItem: SystemMediaItem,
    targetLabel: String,
    sourceItems: List<SystemMediaItem>,
    finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
) {
    val initialTaskId = "${operationId}-${mediaItem.id}"
    UploadManager.uploadTasksState.removeAll { it.taskId == initialTaskId }
    UploadManager.uploadTasksState.add(
        SystemMediaUploadTaskUiModel(
            taskId = initialTaskId,
            operationId = operationId,
            mediaId = mediaItem.id,
            fileName = mediaItem.displayName.ifBlank { mediaItem.id },
            targetLabel = targetLabel,
            mediaType = mediaItem.type,
            previewUri = mediaItem.uri.toString(),
            progressPercent = 0,
            state = UploadState.WAITING,
            statusMessage = "等待读取本地媒体",
        ),
    )
    UploadManager.refreshOperationTaskMeta(operationId)

    val job = UploadManager.uploadScope.launch {
        var activeTaskId = initialTaskId
        var uploadPermitAcquired = false
        try {
            UploadManager.uploadSemaphore.acquire()
            uploadPermitAcquired = true
            if (UploadManager.isUploadTaskCancelled(activeTaskId)) return@launch
            debugUploadLog(
                "enqueue operation=$operationId target=$targetLabel mediaId=${mediaItem.id} " +
                    "uri=${mediaItem.uri} mime=${mediaItem.mimeType} displayName=${mediaItem.displayName}",
            )
            UploadManager.updateUploadTask(
                taskId = activeTaskId,
                state = UploadState.UPLOADING,
                progressPercent = 3,
                statusMessage = "正在读取本地媒体",
            )
            val metadata = runCatching {
                withTimeout(ReadLocalMediaTimeoutMillis) {
                    readRealUploadMetadata(context, mediaItem)
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                    throw throwable
                }
                val message = friendlyUploadError(
                    throwable = throwable,
                    fallback = "无法读取所选媒体，请重新选择。",
                )
                UploadManager.addFailedUploadTask(
                    taskId = activeTaskId,
                    operationId = operationId,
                    mediaId = mediaItem.id,
                    fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                    targetLabel = targetLabel,
                    mediaType = mediaItem.type,
                    previewUri = mediaItem.uri.toString(),
                    statusMessage = "读取本地媒体失败",
                    errorMessage = message,
                )
                finalizeRealOperationIfReady(
                    operationId = operationId,
                    sourceItems = sourceItems,
                    finalizeAction = finalizeAction,
                )
                OperationBus.publishOperationSummaryIfReady(operationId)
                debugUploadLog("read failed operation=$operationId mediaId=${mediaItem.id}: $message", throwable)
                return@launch
            }
            if (UploadManager.isUploadTaskCancelled(activeTaskId)) return@launch

            debugUploadLog(
                "metadata operation=$operationId uri=${metadata.sourceUri} file=${metadata.fileName} " +
                    "mime=${metadata.mimeType} size=${metadata.fileSizeBytes} " +
                    "width=${metadata.width} height=${metadata.height} duration=${metadata.durationMillis}",
            )
            UploadManager.updateUploadTask(
                taskId = activeTaskId,
                state = UploadState.UPLOADING,
                progressPercent = 12,
                statusMessage = "正在创建上传任务",
            )
            val tokenPayload = CreateUploadTokenPayload(
                fileName = metadata.fileName,
                mimeType = metadata.mimeType,
                fileSizeBytes = metadata.fileSizeBytes,
                mediaType = metadata.mediaType.name.lowercase(Locale.ROOT),
                width = metadata.width,
                height = metadata.height,
                durationMillis = metadata.durationMillis,
                displayTimeMillis = metadata.displayTimeMillis,
                capturedAtMillis = metadata.capturedAtMillis,
                importedAtMillis = metadata.importedAtMillis,
                displayTimeSource = metadata.displayTimeSource,
                sourceFingerprint = mediaItem.stableImportMetadataSourceFingerprint(),
                operationId = operationId,
                operationType = UploadManager.operationRequestsById[operationId]?.operationType?.name,
                operationTitle = OperationBus.operationTaskMeta(operationId).operationTitle,
                operationMediaCount = OperationBus.operationTaskMeta(operationId).mediaCount,
                sourceItemId = mediaItem.id,
                // Round 8 第十四轮: 携带 EXIF GPS (已转 GCJ-02)
                latitude = metadata.latitude,
                longitude = metadata.longitude,
            )
            val tokenResult = runUploadApiWithTimeout(
                timeoutMillis = CreateUploadTokenTimeoutMillis,
                timeoutMessage = "创建上传任务超时，请检查网络后重试。",
            ) {
                RepositoryProvider.uploadRepository.createUploadToken(tokenPayload)
            }
            val uploadId = when (tokenResult) {
                is ApiResult.Success -> tokenResult.data.uploadId
                is ApiResult.Error -> {
                    val message = tokenResult.message.ifBlank { "创建上传任务失败，请稍后重试。" }
                    UploadManager.addFailedUploadTask(
                        taskId = activeTaskId,
                        operationId = operationId,
                        mediaId = mediaItem.id,
                        fileName = metadata.fileName,
                        targetLabel = targetLabel,
                        mediaType = mediaItem.type,
                        previewUri = mediaItem.uri.toString(),
                        statusMessage = "创建上传任务失败",
                        errorMessage = message,
                    )
                    finalizeRealOperationIfReady(
                        operationId = operationId,
                        sourceItems = sourceItems,
                        finalizeAction = finalizeAction,
                    )
                    OperationBus.publishOperationSummaryIfReady(operationId)
                    debugUploadLog("token failed operation=$operationId mediaId=${mediaItem.id}: $message", tokenResult.throwable)
                    return@launch
                }
                ApiResult.Loading -> return@launch
            }
            if (!UploadManager.replaceUploadTaskId(
                    oldTaskId = activeTaskId,
                    newTaskId = uploadId,
                    fileName = metadata.fileName,
                    progressPercent = 20,
                    statusMessage = "等待上传",
                )
            ) {
                return@launch
            }
            activeTaskId = uploadId
            debugUploadLog("token success operation=$operationId uploadId=$uploadId endpoint=/api/uploads/$uploadId/file")

            UploadManager.updateUploadTask(
                taskId = uploadId,
                state = UploadState.UPLOADING,
                progressPercent = 35,
                statusMessage = "正在上传 35%",
            )

            val uploadResult = runUploadApiWithTimeout(
                timeoutMillis = UploadFileTimeoutMillis,
                timeoutMessage = "上传超时，请检查网络后重试。",
            ) {
                RepositoryProvider.uploadRepository.uploadLocalStream(
                    uploadId = uploadId,
                    fileName = metadata.fileName,
                    mimeType = metadata.mimeType,
                    fileSizeBytes = metadata.fileSizeBytes,
                    openInputStream = {
                        context.contentResolver.openInputStream(metadata.sourceUri)
                            ?: error("无法读取已选择的媒体。")
                    },
                    onProgressPercent = { progress ->
                        val mappedProgress = if (progress >= 100) {
                            98
                        } else {
                            (35 + progress * 63 / 100).coerceIn(35, 97)
                        }
                        UploadManager.uploadScope.launch {
                            if (!UploadManager.isUploadTaskCancelled(uploadId)) {
                                val message = if (progress >= 100) "服务器正在确认接收" else "正在上传 $mappedProgress%"
                                UploadManager.updateUploadTask(
                                    taskId = uploadId,
                                    state = UploadState.UPLOADING,
                                    progressPercent = mappedProgress,
                                    statusMessage = message,
                                )
                            }
                        }
                    },
                    shouldCancel = { UploadManager.isUploadTaskCancelled(uploadId) },
                )
            }
            when (uploadResult) {
                is ApiResult.Success -> {
                    if (UploadManager.uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                        return@launch
                    }
                    UploadManager.updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.UPLOADING,
                        progressPercent = 99,
                        statusMessage = "上传完成，正在处理",
                    )
                    val uploadedMediaId = uploadResult.data.mediaId
                    if (uploadedMediaId.isBlank()) {
                        val message = "上传已完成，但服务器没有返回媒体编号。"
                        UploadManager.updateUploadTask(
                            taskId = uploadId,
                            state = UploadState.FAILED,
                            progressPercent = 95,
                            statusMessage = "上传失败",
                            errorMessage = message,
                            canRetry = true,
                        )
                        finalizeRealOperationIfReady(
                            operationId = operationId,
                            sourceItems = sourceItems,
                            finalizeAction = finalizeAction,
                        )
                        OperationBus.publishOperationSummaryIfReady(operationId)
                        debugUploadLog("upload response missing mediaId operation=$operationId uploadId=$uploadId")
                        return@launch
                    }
                    UploadManager.updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.SUCCESS,
                        progressPercent = 100,
                        statusMessage = finalizeWaitingMessage(targetLabel),
                        resultMediaId = uploadedMediaId,
                        thumbnailUrl = uploadResult.data.coverUrl
                            ?: uploadResult.data.previewUrl
                            ?: uploadResult.data.thumbnailUrl
                            ?: uploadResult.data.mediaUrl,
                    )
                    UploadManager.rememberUploadedMediaId(
                        operationId = operationId,
                        sourceItem = mediaItem,
                        uploadedMediaId = uploadedMediaId,
                    )
                    ImportOverlayStore.publishMutation(
                        kind = MutationKind.OVERLAY_ONLY,
                        mediaIds = listOf(mediaItem.id),
                    )
                    notifyRealBackendContentChanged(mediaIds = setOf(uploadedMediaId))
                    OperationBus.publishFirstSuccessIfNeeded(
                        operationId = operationId,
                        operationType = UploadManager.operationRequestsById[operationId]?.operationType ?: OperationType.IMPORT_TO_APP,
                        mediaId = uploadedMediaId,
                    )
                    debugUploadLog(
                        "upload success operation=$operationId uploadId=$uploadId mediaId=$uploadedMediaId " +
                            "preview=${uploadResult.data.previewUrl} media=${uploadResult.data.mediaUrl} " +
                            "original=${uploadResult.data.originalUrl} video=${uploadResult.data.videoUrl}",
                    )
                    finalizeRealOperationIfReady(
                        operationId = operationId,
                        sourceItems = sourceItems,
                        finalizeAction = finalizeAction,
                    )
                    invalidateSystemMediaMetadataCache(clearDisk = true)
                    OperationBus.publishOperationSummaryIfReady(operationId)
                }
                is ApiResult.Error -> {
                    if (UploadManager.uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                        return@launch
                    }
                    val message = uploadResult.message.ifBlank { "上传失败，请稍后重试。" }
                    UploadManager.updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.FAILED,
                        progressPercent = 35,
                        statusMessage = "上传失败",
                        errorMessage = message,
                        canRetry = true,
                    )
                    finalizeRealOperationIfReady(
                        operationId = operationId,
                        sourceItems = sourceItems,
                        finalizeAction = finalizeAction,
                    )
                    OperationBus.publishOperationSummaryIfReady(operationId)
                    debugUploadLog("upload failed operation=$operationId uploadId=$uploadId: $message", uploadResult.throwable)
                }
                ApiResult.Loading -> Unit
            }
        } catch (throwable: CancellationException) {
            if (!UploadManager.isUploadTaskCancelled(activeTaskId)) {
                UploadManager.updateUploadTask(
                    taskId = activeTaskId,
                    state = UploadState.CANCELLED,
                    progressPercent = UploadManager.uploadTasksState.firstOrNull { it.taskId == activeTaskId }?.progressPercent ?: 0,
                    statusMessage = "上传已取消",
                    canRetry = true,
                )
            }
            finalizeRealOperationIfReady(
                operationId = operationId,
                sourceItems = sourceItems,
                finalizeAction = finalizeAction,
            )
            OperationBus.publishOperationSummaryIfReady(operationId)
        } catch (throwable: Throwable) {
            val message = friendlyUploadError(
                throwable = throwable,
                fallback = "上传任务异常中断，请稍后重试。",
            )
            if (UploadManager.uploadTasksState.any { it.taskId == activeTaskId }) {
                UploadManager.updateUploadTask(
                    taskId = activeTaskId,
                    state = UploadState.FAILED,
                    progressPercent = 0,
                    statusMessage = "上传失败",
                    errorMessage = message,
                    canRetry = true,
                )
            } else {
                UploadManager.addFailedUploadTask(
                    taskId = activeTaskId,
                    operationId = operationId,
                    mediaId = mediaItem.id,
                    fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                    targetLabel = targetLabel,
                    mediaType = mediaItem.type,
                    previewUri = mediaItem.uri.toString(),
                    statusMessage = "上传失败",
                    errorMessage = message,
                )
            }
            finalizeRealOperationIfReady(
                operationId = operationId,
                sourceItems = sourceItems,
                finalizeAction = finalizeAction,
            )
            OperationBus.publishOperationSummaryIfReady(operationId)
            debugUploadLog("upload crashed operation=$operationId task=$activeTaskId", throwable)
        } finally {
            if (uploadPermitAcquired) {
                UploadManager.uploadSemaphore.release()
            }
            UploadManager.uploadJobsByTaskId.remove(initialTaskId)
            UploadManager.uploadJobsByTaskId.remove(activeTaskId)
        }
    }
    UploadManager.uploadJobsByTaskId[initialTaskId] = job
}

internal suspend fun finalizeImportToAppReal(
    uploadedMedia: UploadedOperationMedia,
): ApiResult<RealFinalizeResult> {
    if (uploadedMedia.orderedUploadedMediaIds.isEmpty()) {
        return ApiResult.Error(
            code = "IMPORT_EMPTY",
            message = "上传完成，但没有可导入的媒体。",
        )
    }
    return ApiResult.Success(
        RealFinalizeResult(
            operationType = OperationType.IMPORT_TO_APP,
            successMessage = "导入完成",
            affectedPostIds = emptySet(),
        ),
    )
}

internal suspend fun finalizeCreatePostReal(
    draft: CreatePostDraft,
    sourceItems: List<SystemMediaItem>,
    uploadedMedia: UploadedOperationMedia,
    additionalAppMediaIds: List<String> = emptyList(),
    additionalAppCoverMediaId: String? = null,
): ApiResult<RealFinalizeResult> {
    val albums = when (val result = RepositoryProvider.albumRepository.getAlbums()) {
        is ApiResult.Success -> result.data
        is ApiResult.Error -> {
            return ApiResult.Error(
                code = result.code,
                message = result.message.ifBlank { "读取相册失败，当前无法创建小相册。" },
                throwable = result.throwable,
            )
        }
        ApiResult.Loading -> {
            return ApiResult.Error(
                code = "ALBUMS_LOADING",
                message = "小相册列表还在读取中，请稍后重试。",
            )
        }
    }
    val defaultAlbumId = albums.firstOrNull()?.albumId
        ?: return ApiResult.Error(
            code = "ALBUM_REQUIRED",
            message = "No album is available; cannot create post now.",
        )
    val finalAlbumIds = draft.albumIds.ifEmpty { listOf(defaultAlbumId) }
    val normalizedAdditionalAppMediaIds = additionalAppMediaIds.distinct()
    val finalMediaIds = (uploadedMedia.orderedUploadedMediaIds + normalizedAdditionalAppMediaIds).distinct()
    val coverMediaId = draft.coverSourceMediaId?.let { coverId ->
        uploadedMedia.uploadedMediaIdBySourceId[coverId] ?: coverId.takeIf { normalizedAdditionalAppMediaIds.contains(it) }
    } ?: additionalAppCoverMediaId?.takeIf { normalizedAdditionalAppMediaIds.contains(it) }

    return when (
        val result = RepositoryProvider.postRepository.createPost(
            CreatePostPayload(
                title = draft.title.ifBlank { buildRealPostTitle(sourceItems) },
                summary = draft.summary.ifBlank { buildRealPostSummary(sourceItems) },
                participantUserIds = draft.participantUserIds,
                displayTimeMillis = draft.displayTimeMillis,
                albumId = finalAlbumIds.first(),
                initialMediaIds = finalMediaIds,
                coverMediaId = coverMediaId?.takeIf { finalMediaIds.contains(it) },
            ),
        )
    ) {
        is ApiResult.Success -> {
            ImportOverlayStore.linkMediaToPost(
                mediaIds = sourceItems.map { it.id } + normalizedAdditionalAppMediaIds,
                postId = result.data.postId,
            )
            ApiResult.Success(
                RealFinalizeResult(
                    operationType = OperationType.CREATE_POST,
                    successMessage = "小相册创建完成",
                    postRoute = result.data.toPostDetailPlaceholderRoute(
                        selectedAlbumId = result.data.albumIds.firstOrNull() ?: finalAlbumIds.first(),
                    ),
                    affectedPostIds = setOf(result.data.postId),
                ),
            )
        }
        is ApiResult.Error -> ApiResult.Error(
            code = result.code,
            message = result.message.ifBlank { "上传完成，但小相册创建失败，可重试。" },
            throwable = result.throwable,
        )
        ApiResult.Loading -> ApiResult.Loading
    }
}

internal suspend fun finalizeAppendToPostReal(
    postId: String,
    sourceItems: List<SystemMediaItem>,
    uploadedMedia: UploadedOperationMedia,
): ApiResult<RealFinalizeResult> {
    return when (
        val result = RepositoryProvider.postRepository.addMediaToPost(
            postId = postId,
            mediaIds = uploadedMedia.orderedUploadedMediaIds,
        )
    ) {
        is ApiResult.Success -> {
            ImportOverlayStore.linkMediaToPost(
                mediaIds = sourceItems.map { it.id },
                postId = postId,
            )
            val postRoute = result.data.toPostDetailPlaceholderRoute(
                selectedAlbumId = result.data.albumIds.firstOrNull().orEmpty(),
            )
            ApiResult.Success(
                RealFinalizeResult(
                    operationType = OperationType.ADD_TO_EXISTING_POST,
                    successMessage = "已加入小相册",
                    postRoute = postRoute,
                    affectedPostIds = setOf(postId),
                ),
            )
        }
        is ApiResult.Error -> ApiResult.Error(
            code = result.code,
            message = result.message.ifBlank { "上传完成，但加入小相册失败，可重试。" },
            throwable = result.throwable,
        )
        ApiResult.Loading -> ApiResult.Loading
    }
}

internal suspend fun finalizeRealOperationIfReady(
    operationId: String,
    sourceItems: List<SystemMediaItem>,
    finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
) {
    if (OperationBus.finalizedOperationIds.contains(operationId)) return
    val operationTasks = UploadManager.uploadTasksState.filter { it.operationId == operationId }
    if (operationTasks.isEmpty()) return
    val request = UploadManager.operationRequestsById[operationId]
    val hasFailedTasks = operationTasks.any { it.state == UploadState.FAILED || it.state == UploadState.CANCELLED }
    val allTasksFinished = operationTasks.all { it.isTerminal }
    val allTasksSucceeded = operationTasks.all { it.state == UploadState.SUCCESS }
    val canFinalizePartialCreatePost = request?.operationType == OperationType.CREATE_POST &&
        hasFailedTasks &&
        allTasksFinished &&
        operationTasks.any { it.state == UploadState.SUCCESS }
    val canFinalizePartialAddToPost = request?.operationType == OperationType.ADD_TO_EXISTING_POST &&
        hasFailedTasks &&
        allTasksFinished &&
        operationTasks.any { it.state == UploadState.SUCCESS }
    if (!allTasksSucceeded && !canFinalizePartialCreatePost && !canFinalizePartialAddToPost) return

    val uploadedMap = UploadManager.realUploadedMediaIdsByOperationId[operationId].orEmpty()
    val orderedIds = sourceItems.mapNotNull { uploadedMap[it.id] }
    if (allTasksSucceeded && orderedIds.distinct().size != sourceItems.distinctBy { it.id }.size) return
    if (orderedIds.isEmpty()) return

    OperationBus.finalizedOperationIds += operationId
    if (canFinalizePartialCreatePost || canFinalizePartialAddToPost) {
        UploadManager.updateSuccessfulOperationTasks(
            operationId = operationId,
            state = UploadState.UPLOADING,
            statusMessage = "部分上传完成，正在用成功项创建小相册",
        )
    } else {
        UploadManager.updateOperationTasks(
            operationId = operationId,
            state = UploadState.UPLOADING,
            statusMessage = when (request?.operationType) {
                OperationType.IMPORT_TO_APP -> "上传完成，正在刷新照片流"
                OperationType.ADD_TO_EXISTING_POST -> "上传完成，正在加入小相册"
                OperationType.CREATE_POST,
                null -> "上传完成，正在创建小相册"
            },
        )
    }

    when (
        val result = finalizeAction(
            UploadedOperationMedia(
                orderedUploadedMediaIds = orderedIds,
                uploadedMediaIdBySourceId = uploadedMap.toMap(),
            ),
        )
    ) {
        is ApiResult.Success -> {
            ImportOverlayStore.publishMutation(
                kind = MutationKind.OVERLAY_ONLY,
                mediaIds = sourceItems.map { it.id },
            )
            notifyRealBackendContentChanged(
                postIds = result.data.affectedPostIds,
                mediaIds = orderedIds.toSet(),
            )
            invalidateSystemMediaMetadataCache(clearDisk = true)
            if (canFinalizePartialCreatePost || canFinalizePartialAddToPost) {
                UploadManager.updateSuccessfulOperationTasks(
                    operationId = operationId,
                    state = UploadState.SUCCESS,
                    statusMessage = result.data.successMessage,
                    resultPostRoute = result.data.postRoute,
                )
                UploadManager.markFailedOperationTasksAfterPartialPost(operationId)
            } else {
                UploadManager.updateOperationTasks(
                    operationId = operationId,
                    state = UploadState.SUCCESS,
                    statusMessage = result.data.successMessage,
                    resultPostRoute = result.data.postRoute,
                )
            }
            OperationBus.publishOperationSummaryIfReady(
                operationId = operationId,
                operationType = result.data.operationType,
                postRoute = result.data.postRoute,
            )
        }
        is ApiResult.Error -> {
            OperationBus.finalizedOperationIds.remove(operationId)
            if (canFinalizePartialCreatePost || canFinalizePartialAddToPost) {
                UploadManager.updateSuccessfulOperationTasks(
                    operationId = operationId,
                    state = UploadState.FAILED,
                    statusMessage = if (canFinalizePartialAddToPost) "加入小相册失败" else "小相册创建失败",
                    errorMessage = result.message.ifBlank {
                        if (canFinalizePartialAddToPost) {
                            "上传完成，但加入小相册失败，可重试。"
                        } else {
                            "上传完成，但小相册创建失败，可重试。"
                        }
                    },
                    canRetry = true,
                )
            } else {
                UploadManager.updateOperationTasks(
                    operationId = operationId,
                    state = UploadState.FAILED,
                    statusMessage = when (request?.operationType) {
                        OperationType.ADD_TO_EXISTING_POST -> "加入小相册失败"
                        else -> "小相册创建失败"
                    },
                    errorMessage = result.message.ifBlank { "上传完成，但收尾处理失败，可重试。" },
                    canRetry = true,
                )
            }
            OperationBus.publishOperationSummaryIfReady(
                operationId = operationId,
                operationType = request?.operationType ?: OperationType.CREATE_POST,
            )
        }
        ApiResult.Loading -> Unit
    }
}

internal fun finalizeRealOperationWithKnownMedia(
    operationId: String,
    sourceItems: List<SystemMediaItem>,
    finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
) {
    if (OperationBus.finalizedOperationIds.contains(operationId)) return
    UploadManager.uploadScope.launch {
        val uploadedMap = UploadManager.realUploadedMediaIdsByOperationId[operationId].orEmpty()
        val orderedIds = sourceItems.mapNotNull { uploadedMap[it.id] }.distinct()
        if (orderedIds.isEmpty()) {
            OperationBus.publishOperationResult(
                OperationResultEvent(
                    eventId = "$operationId-duplicate-${System.currentTimeMillis()}",
                    operationId = operationId,
                    operationType = UploadManager.operationRequestsById[operationId]?.operationType ?: OperationType.IMPORT_TO_APP,
                    succeeded = false,
                    message = "已跳过重复媒体。",
                    totalCount = sourceItems.size,
                ),
            )
            return@launch
        }

        OperationBus.finalizedOperationIds += operationId
        when (
            val result = finalizeAction(
                UploadedOperationMedia(
                    orderedUploadedMediaIds = orderedIds,
                    uploadedMediaIdBySourceId = uploadedMap.toMap(),
                ),
            )
        ) {
            is ApiResult.Success -> {
                sourceItems.forEach { item ->
                    uploadedMap[item.id]?.let { appMediaId ->
                        ImportOverlayStore.rememberAppMediaIdForSource(item, appMediaId)
                    }
                }
                invalidateSystemMediaMetadataCache(clearDisk = true)
                ImportOverlayStore.publishMutation(
                    kind = MutationKind.OVERLAY_ONLY,
                    mediaIds = sourceItems.map { it.id },
                )
                notifyRealBackendContentChanged(
                    postIds = result.data.affectedPostIds,
                    mediaIds = orderedIds.toSet(),
                )
                OperationBus.publishOperationResult(
                    OperationResultEvent(
                        eventId = "$operationId-reused-${System.currentTimeMillis()}",
                        operationId = operationId,
                        operationType = result.data.operationType,
                        succeeded = true,
                        message = result.data.successMessage,
                        postRoute = result.data.postRoute,
                        resultMediaIds = orderedIds,
                        successCount = sourceItems.size,
                        failureCount = 0,
                        totalCount = sourceItems.size,
                        shouldAutoOpenResult = result.data.operationType == OperationType.IMPORT_TO_APP,
                    ),
                )
            }
            is ApiResult.Error -> {
                OperationBus.finalizedOperationIds.remove(operationId)
                OperationBus.publishOperationResult(
                    OperationResultEvent(
                        eventId = "$operationId-reused-failure-${System.currentTimeMillis()}",
                        operationId = operationId,
                        operationType = UploadManager.operationRequestsById[operationId]?.operationType ?: OperationType.CREATE_POST,
                        succeeded = false,
                        message = result.message.ifBlank { "重复媒体复用失败，请稍后重试。" },
                        failureCount = sourceItems.size,
                        totalCount = sourceItems.size,
                    ),
                )
            }
            ApiResult.Loading -> Unit
        }
    }
}

internal suspend fun readRealUploadMetadata(
    context: Context,
    mediaItem: SystemMediaItem,
): RealUploadMetadata = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver
    var queriedSizeBytes: Long? = null
    val queriedDisplayName = contentResolver.query(
        mediaItem.uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            queriedSizeBytes = cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let(cursor::getLong)
                ?.takeIf { it > 0L }
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                .takeIf { it >= 0 }
                ?.let(cursor::getString)
        } else {
            null
        }
    }.orEmpty()
    val fileName = queriedDisplayName.ifBlank { mediaItem.displayName }.ifBlank {
        val extension = if (mediaItem.type == SystemMediaType.VIDEO) "mp4" else "jpg"
        "${mediaItem.id}.$extension"
    }
    val mimeType = contentResolver.getType(mediaItem.uri)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: mediaItem.mimeType.ifBlank { fakeMimeType(mediaItem.type) }
    val resolvedMediaType = resolvePickedMediaType(
        uri = mediaItem.uri,
        displayName = fileName,
        mimeType = mimeType,
        fallback = mediaItem.type,
    )
    val fileSizeBytes = queriedSizeBytes
        ?: mediaItem.sizeBytes?.takeIf { it > 0L }
        ?: contentResolver.openAssetFileDescriptor(mediaItem.uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it > 0L }
        }
        ?: error("Cannot resolve selected media size.")
    if (fileSizeBytes <= 0L) {
        error("Selected media file is empty.")
    }
    val (resolvedWidth, resolvedHeight, resolvedDuration) = resolvePickedMediaMetadata(
        context = context,
        uri = mediaItem.uri,
        type = resolvedMediaType,
    )
    val (uploadWidth, uploadHeight) = resolveUploadDimensions(
        mediaType = resolvedMediaType,
        aspectRatio = mediaItem.aspectRatio,
        width = mediaItem.width,
        height = mediaItem.height,
        resolvedWidth = resolvedWidth,
        resolvedHeight = resolvedHeight,
    )
    val importedAtMillis = System.currentTimeMillis()
    val resolvedTime = resolvePreferredMediaDisplayTime(
        metadata = DeviceMediaTimeMetadata(
            capturedAtMillis = mediaItem.capturedAtMillis,
            fileModifiedAtMillis = mediaItem.fileModifiedAtMillis,
        ),
        importedAtMillis = importedAtMillis,
        preference = SettingsRepository.getSettingsState().mediaTimePreference,
    )
    // Round 8 第十四轮: 读取 EXIF GPS (WGS-84) 并转 GCJ-02, 没有则为 null
    // 注意: 必须声明 ACCESS_MEDIA_LOCATION 权限, 否则 MediaProvider 会 redact EXIF GPS
    val exifGps = readExifGpsLocation(context, mediaItem.uri)
    val (gcjLat, gcjLng) = if (exifGps != null) {
        LocationHelper.wgs84ToGcj02Public(exifGps.latitude, exifGps.longitude)
    } else {
        null to null
    }
    if (exifGps != null) {
        android.util.Log.d(
            "SystemMediaUpload",
            "EXIF GPS: WGS-84(${exifGps.latitude},${exifGps.longitude}) → GCJ-02($gcjLat,$gcjLng)",
        )
    }
    RealUploadMetadata(
        fileName = fileName,
        mimeType = mimeType,
        mediaType = resolvedMediaType,
        fileSizeBytes = fileSizeBytes,
        width = uploadWidth,
        height = uploadHeight,
        durationMillis = if (resolvedMediaType == SystemMediaType.VIDEO) {
            resolvedDuration ?: mediaItem.videoDurationMillis
        } else {
            null
        },
        displayTimeMillis = resolvedTime.displayTimeMillis,
        capturedAtMillis = resolvedTime.capturedAtMillis,
        importedAtMillis = resolvedTime.importedAtMillis,
        displayTimeSource = resolvedTime.displayTimeSource,
        sourceUri = mediaItem.uri,
        latitude = gcjLat,
        longitude = gcjLng,
    )
}

internal fun buildRealPostTitle(items: List<SystemMediaItem>): String {
    val dateLabel = SimpleDateFormat("M\u6708d\u65e5 HH:mm", Locale.CHINA)
        .format(Date(items.maxOfOrNull { it.displayTimeMillis } ?: System.currentTimeMillis()))
    return if (items.size == 1) {
        "\u4ece\u7cfb\u7edf\u5a92\u4f53\u521b\u5efa \u00b7 $dateLabel"
    } else {
        "\u4ece\u7cfb\u7edf\u5a92\u4f53\u5bfc\u5165 ${items.size} \u9879 \u00b7 $dateLabel"
    }
}

internal fun buildRealPostSummary(items: List<SystemMediaItem>): String {
    return "\u4ece\u7cfb\u7edf\u5a92\u4f53\u5bfc\u5165 ${items.size} \u9879\u5185\u5bb9\u3002"
}

internal fun defaultCreatePostDraft(
    mediaItems: List<SystemMediaItem>,
): CreatePostDraft {
    val normalizedItems = normalizeSystemMedia(mediaItems)
    return CreatePostDraft(
        title = "",
        summary = "",
        displayTimeMillis = normalizedItems.maxOfOrNull { it.displayTimeMillis }
            ?: System.currentTimeMillis(),
        albumIds = emptyList(),
        coverSourceMediaId = normalizedItems.firstOrNull()?.id,
    )
}

internal fun finalizeWaitingMessage(targetLabel: String): String {
    return when (targetLabel) {
        "Import to App",
        "\u5bfc\u5165 App",
        "导入照片流",
        -> "\u4e0a\u4f20\u5b8c\u6210\uff0c\u6b63\u5728\u5237\u65b0\u7167\u7247\u6d41"
        "Add to post",
        "\u52a0\u5165\u5df2\u6709\u5e16\u5b50",
        -> "\u4e0a\u4f20\u5b8c\u6210\uff0c\u6b63\u5728\u52a0\u5165\u5e16\u5b50"
        else -> "\u4e0a\u4f20\u5b8c\u6210\uff0c\u6b63\u5728\u521b\u5efa\u5e16\u5b50"
    }
}

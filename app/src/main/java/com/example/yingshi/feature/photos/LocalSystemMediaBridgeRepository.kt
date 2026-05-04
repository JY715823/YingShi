package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalSystemMediaBridgeRepository {
    enum class MutationKind {
        OVERLAY_ONLY,
        MEDIA_STORE_CHANGED,
    }

    enum class OperationType {
        IMPORT_TO_APP,
        CREATE_POST,
        ADD_TO_EXISTING_POST,
    }

    data class MutationEvent(
        val version: Int = 0,
        val kind: MutationKind = MutationKind.OVERLAY_ONLY,
        val mediaIds: Set<String> = emptySet(),
    )

    data class OperationResultEvent(
        val eventId: String,
        val operationId: String,
        val operationType: OperationType,
        val succeeded: Boolean,
        val message: String,
        val postRoute: PostDetailPlaceholderRoute? = null,
    )

    private data class RealUploadMetadata(
        val fileName: String,
        val mimeType: String,
        val fileBytes: ByteArray,
        val width: Int,
        val height: Int,
        val durationMillis: Long? = null,
    )

    private data class UploadedOperationMedia(
        val orderedUploadedMediaIds: List<String>,
        val uploadedMediaIdBySourceId: Map<String, String>,
    )

    private data class RealFinalizeResult(
        val operationType: OperationType,
        val successMessage: String,
        val postRoute: PostDetailPlaceholderRoute? = null,
        val affectedPostIds: Set<String> = emptySet(),
    )

    private sealed interface PendingOperationRequest {
        val mediaItems: List<SystemMediaItem>
        val operationType: OperationType
        val targetLabel: String
    }

    private data class CreatePostOperationRequest(
        val draft: CreatePostDraft,
        override val mediaItems: List<SystemMediaItem>,
    ) : PendingOperationRequest {
        override val operationType: OperationType = OperationType.CREATE_POST
        override val targetLabel: String = "发成新帖子"
    }

    private data class ImportToAppOperationRequest(
        override val mediaItems: List<SystemMediaItem>,
    ) : PendingOperationRequest {
        override val operationType: OperationType = OperationType.IMPORT_TO_APP
        override val targetLabel: String = "导入app"
    }

    private data class AddToExistingPostOperationRequest(
        val postId: String,
        override val mediaItems: List<SystemMediaItem>,
    ) : PendingOperationRequest {
        override val operationType: OperationType = OperationType.ADD_TO_EXISTING_POST
        override val targetLabel: String = "加入已有帖子"
    }

    var mutationVersion by mutableIntStateOf(0)
        private set
    var latestMutationEvent by mutableStateOf(MutationEvent())
        private set

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val uploadTasksState = mutableStateListOf<SystemMediaUploadTaskUiModel>()
    private val operationResultsState = mutableStateListOf<OperationResultEvent>()
    private val finalizedOperationIds = linkedSetOf<String>()
    private val hiddenMediaIds = linkedSetOf<String>()
    private val linkedPostIdsByMediaId = linkedMapOf<String, LinkedHashSet<String>>()
    private val realUploadedMediaIdsByOperationId = linkedMapOf<String, LinkedHashMap<String, String>>()
    private val operationRequestsById = linkedMapOf<String, PendingOperationRequest>()

    val uploadTasks: List<SystemMediaUploadTaskUiModel>
        get() = uploadTasksState

    val operationResults: List<OperationResultEvent>
        get() = operationResultsState

    fun applyOverlay(items: List<SystemMediaItem>): List<SystemMediaItem> {
        return items
            .filterNot { hiddenMediaIds.contains(it.id) }
            .map { item ->
                item.copy(
                    linkedPostIds = linkedPostIdsByMediaId[item.id]?.toList().orEmpty(),
                )
            }
    }

    fun createPostFromSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        return createPostFromSystemMediaDraft(
            draft = defaultCreatePostDraft(mediaItems),
            mediaItems = mediaItems,
        )
    }

    fun createPostFromSystemMediaDraft(
        draft: CreatePostDraft,
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        val post = FakeAlbumRepository.createConfiguredLocalPostFromSystemMedia(
            draft = draft,
            mediaItems = normalizedItems,
        ) ?: return null
        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = post.id,
        )
        linkMediaToPost(
            mediaIds = normalizedItems.map { it.id },
            postId = post.id,
        )
        return post
    }

    fun enqueueCreatePostUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
        draft: CreatePostDraft = defaultCreatePostDraft(mediaItems),
    ): Int {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        if (normalizedItems.isEmpty()) return 0
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            enqueueCreatePostUploadReal(
                context = context,
                mediaItems = normalizedItems,
                draft = draft,
            )
        } else {
            enqueueCreatePostUploadFake(
                mediaItems = normalizedItems,
                draft = draft,
            )
        }
    }

    fun importSystemMediaToApp(
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        if (normalizedItems.isEmpty()) return 0
        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = null,
        )
        publishMutation(
            kind = MutationKind.OVERLAY_ONLY,
            mediaIds = normalizedItems.map { it.id },
        )
        return normalizedItems.size
    }

    fun enqueueImportToAppUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        if (normalizedItems.isEmpty()) return 0
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            enqueueImportToAppUploadReal(
                context = context,
                mediaItems = normalizedItems,
            )
        } else {
            enqueueImportToAppUploadFake(
                mediaItems = normalizedItems,
            )
        }
    }

    fun enqueueImportPickedMediaToAppUpload(
        context: Context,
        mediaUris: List<Uri>,
    ): Int {
        val items = mediaUris
            .distinct()
            .mapIndexedNotNull { index, uri ->
                uri.toPickedSystemMediaItem(
                    context = context,
                    index = index,
                )
            }
        if (items.isEmpty()) return 0
        return enqueueImportToAppUpload(
            context = context,
            mediaItems = items,
        )
    }

    fun addSystemMediaToExistingPost(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = normalizeSystemMedia(mediaItems)
            .filterNot { it.linkedPostIds.contains(postId) }
        val addedCount = FakeAlbumRepository.appendSystemMediaToPost(
            postId = postId,
            mediaItems = normalizedItems,
        )
        if (addedCount <= 0) return 0

        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = postId,
        )
        linkMediaToPost(
            mediaIds = normalizedItems.map { it.id },
            postId = postId,
        )
        return addedCount
    }

    fun enqueueAddToExistingPostUpload(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = normalizeSystemMedia(mediaItems)
            .filterNot { it.linkedPostIds.contains(postId) }
        if (normalizedItems.isEmpty()) return 0
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            enqueueAddToExistingPostUploadReal(
                context = context,
                postId = postId,
                mediaItems = normalizedItems,
            )
        } else {
            enqueueAddToExistingPostUploadFake(
                postId = postId,
                mediaItems = normalizedItems,
            )
        }
    }

    fun moveToSimulatedSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        var changedCount = 0
        val normalizedIds = mediaIds.distinct()
        normalizedIds.forEach { mediaId ->
            if (hiddenMediaIds.add(mediaId)) {
                changedCount += 1
            }
        }
        if (changedCount > 0) {
            publishMutation(
                kind = MutationKind.MEDIA_STORE_CHANGED,
                mediaIds = normalizedIds,
            )
        }
        return changedCount
    }

    fun markMovedToSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        return moveToSimulatedSystemTrash(mediaIds)
    }

    fun cancelUploadTask(taskId: String) {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return
        if (task.isTerminal) return
        updateOperationTasks(
            operationId = task.operationId,
            state = UploadState.CANCELLED,
            statusMessage = "已取消上传",
            canRetry = true,
        )
        uploadScope.launch {
            RepositoryProvider.uploadRepository.cancelUpload(taskId)
        }
    }

    fun dismissUploadTask(taskId: String) {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return
        uploadTasksState.removeAll { it.taskId == taskId }
        cleanupOperationIfIdle(task.operationId)
    }

    fun dismissOperationResult(eventId: String) {
        operationResultsState.removeAll { it.eventId == eventId }
    }

    fun retryUploadTask(
        context: Context,
        taskId: String,
    ): Boolean {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return false
        val request = operationRequestsById[task.operationId] ?: return false
        clearOperationState(task.operationId, keepRequest = false)
        return when (request) {
            is ImportToAppOperationRequest -> {
                enqueueImportToAppUpload(
                    context = context,
                    mediaItems = request.mediaItems,
                ) > 0
            }
            is CreatePostOperationRequest -> {
                enqueueCreatePostUpload(
                    context = context,
                    mediaItems = request.mediaItems,
                    draft = request.draft,
                ) > 0
            }
            is AddToExistingPostOperationRequest -> {
                enqueueAddToExistingPostUpload(
                    context = context,
                    postId = request.postId,
                    mediaItems = request.mediaItems,
                ) > 0
            }
        }
    }

    private fun enqueueImportToAppUploadFake(
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val operationId = "import-app-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = ImportToAppOperationRequest(mediaItems = mediaItems)
        mediaItems.forEach { item ->
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "导入app",
                onOperationSuccess = {
                    val importedCount = importSystemMediaToApp(mediaItems)
                    OperationResultEvent(
                        eventId = if (importedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.IMPORT_TO_APP,
                        succeeded = importedCount > 0,
                        message = if (importedCount > 0) {
                            "媒体已导入 app 照片流。"
                        } else {
                            "当前没有可导入的媒体。"
                        },
                    )
                },
            )
        }
        return mediaItems.size
    }

    private fun enqueueCreatePostUploadFake(
        mediaItems: List<SystemMediaItem>,
        draft: CreatePostDraft,
    ): Int {
        val operationId = "create-post-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = CreatePostOperationRequest(
            draft = draft,
            mediaItems = mediaItems,
        )
        mediaItems.forEach { item ->
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "发成新帖子",
                onOperationSuccess = {
                    val createdPost = createPostFromSystemMediaDraft(
                        draft = draft,
                        mediaItems = mediaItems,
                    )
                    if (createdPost == null) {
                        OperationResultEvent(
                            eventId = "$operationId-failure",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = false,
                            message = "新帖子创建失败，请稍后重试。",
                        )
                    } else {
                        OperationResultEvent(
                            eventId = "$operationId-success",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = true,
                            message = "新帖子已创建，照片流和相册列表已刷新。",
                            postRoute = FakeAlbumRepository.toPostDetailRoute(createdPost),
                        )
                    }
                },
            )
        }
        return mediaItems.size
    }

    private fun enqueueAddToExistingPostUploadFake(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val operationId = "append-post-$postId-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = AddToExistingPostOperationRequest(
            postId = postId,
            mediaItems = mediaItems,
        )
        mediaItems.forEach { item ->
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "加入已有帖子",
                onOperationSuccess = {
                    val addedCount = addSystemMediaToExistingPost(postId, mediaItems)
                    OperationResultEvent(
                        eventId = if (addedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.ADD_TO_EXISTING_POST,
                        succeeded = addedCount > 0,
                        message = if (addedCount > 0) {
                            "媒体已加入已有帖子，帖子详情和媒体管理已刷新。"
                        } else {
                            "这些媒体已经在目标帖子里了。"
                        },
                    )
                },
            )
        }
        return mediaItems.size
    }

    private fun linkMediaToPost(
        mediaIds: Collection<String>,
        postId: String,
    ) {
        var changed = false
        mediaIds.distinct().forEach { mediaId ->
            val postIds = linkedPostIdsByMediaId.getOrPut(mediaId) { linkedSetOf() }
            changed = postIds.add(postId) || changed
        }
        if (changed) {
            publishMutation(
                kind = MutationKind.OVERLAY_ONLY,
                mediaIds = mediaIds.distinct(),
            )
        }
    }

    private fun enqueueFakeUploadTask(
        operationId: String,
        mediaItem: SystemMediaItem,
        targetLabel: String,
        onOperationSuccess: () -> OperationResultEvent,
    ) {
        uploadScope.launch {
            val tokenResult = RepositoryProvider.uploadRepository.createUploadToken(
                CreateUploadTokenPayload(
                    fileName = mediaItem.displayName.ifBlank { "${mediaItem.id}.jpg" },
                    mimeType = fakeMimeType(mediaItem.type),
                    fileSizeBytes = fakeFileSizeBytes(mediaItem),
                    mediaType = mediaItem.type.name.lowercase(),
                    width = mediaItem.width?.coerceAtLeast(1) ?: 1,
                    height = mediaItem.height?.coerceAtLeast(1) ?: 1,
                    durationMillis = null,
                    displayTimeMillis = mediaItem.displayTimeMillis,
                ),
            )
            val uploadId = when (tokenResult) {
                is ApiResult.Success -> tokenResult.data.uploadId
                is ApiResult.Error -> {
                    uploadTasksState.add(
                        SystemMediaUploadTaskUiModel(
                            taskId = "${operationId}-${mediaItem.id}",
                            operationId = operationId,
                            mediaId = mediaItem.id,
                            fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                            targetLabel = targetLabel,
                            progressPercent = 0,
                            state = UploadState.FAILURE,
                            statusMessage = "申请上传失败",
                            errorMessage = tokenResult.message,
                            canRetry = true,
                        ),
                    )
                    return@launch
                }
                ApiResult.Loading -> return@launch
            }

            uploadTasksState.add(
                SystemMediaUploadTaskUiModel(
                    taskId = uploadId,
                    operationId = operationId,
                    mediaId = mediaItem.id,
                    fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                    targetLabel = targetLabel,
                    progressPercent = 0,
                    state = UploadState.WAITING,
                    statusMessage = "等待上传",
                ),
            )

            val progressSteps = listOf(12, 28, 46, 63, 81, 100)
            progressSteps.forEachIndexed { index, progress ->
                delay(220L + (index * 30L))
                val currentIndex = uploadTasksState.indexOfFirst { it.taskId == uploadId }
                if (currentIndex < 0) return@launch
                val current = uploadTasksState[currentIndex]
                if (current.state == UploadState.CANCELLED) return@launch
                uploadTasksState[currentIndex] = current.copy(
                    state = UploadState.UPLOADING,
                    progressPercent = progress,
                    statusMessage = "正在上传 $progress%",
                    canRetry = false,
                )
            }

            val confirmResult = RepositoryProvider.uploadRepository.confirmUpload(
                uploadId = uploadId,
                payload = ConfirmUploadPayload(
                    etag = "fake-etag-$uploadId",
                    objectKey = "uploads/fake/${mediaItem.id}",
                ),
            )
            if (uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                return@launch
            }
            when (confirmResult) {
                is ApiResult.Success -> {
                    updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.SUCCESS,
                        progressPercent = 100,
                        statusMessage = finalizeWaitingMessage(targetLabel),
                    )
                    finalizeOperationIfReady(operationId, onOperationSuccess)
                }
                is ApiResult.Error -> {
                    updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.FAILURE,
                        progressPercent = 100,
                        statusMessage = "上传失败",
                        errorMessage = confirmResult.message,
                        canRetry = true,
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun enqueueImportToAppUploadReal(
        context: Context,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val operationId = "real-import-app-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = ImportToAppOperationRequest(mediaItems = mediaItems)
        mediaItems.forEach { item ->
            enqueueRealUploadTask(
                context = context,
                operationId = operationId,
                mediaItem = item,
                targetLabel = "导入app",
                sourceItems = mediaItems,
                finalizeAction = { uploadedMedia ->
                    finalizeImportToAppReal(uploadedMedia)
                },
            )
        }
        return mediaItems.size
    }

    private fun enqueueCreatePostUploadReal(
        context: Context,
        mediaItems: List<SystemMediaItem>,
        draft: CreatePostDraft,
    ): Int {
        val operationId = "real-create-post-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = CreatePostOperationRequest(
            draft = draft,
            mediaItems = mediaItems,
        )
        mediaItems.forEach { item ->
            enqueueRealUploadTask(
                context = context,
                operationId = operationId,
                mediaItem = item,
                targetLabel = "发成新帖子",
                sourceItems = mediaItems,
                finalizeAction = { uploadedMedia ->
                    finalizeCreatePostReal(
                        draft = draft,
                        sourceItems = mediaItems,
                        uploadedMedia = uploadedMedia,
                    )
                },
            )
        }
        return mediaItems.size
    }

    private fun enqueueAddToExistingPostUploadReal(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val operationId = "real-append-post-$postId-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = AddToExistingPostOperationRequest(
            postId = postId,
            mediaItems = mediaItems,
        )
        mediaItems.forEach { item ->
            enqueueRealUploadTask(
                context = context,
                operationId = operationId,
                mediaItem = item,
                targetLabel = "加入已有帖子",
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
        return mediaItems.size
    }

    private fun enqueueRealUploadTask(
        context: Context,
        operationId: String,
        mediaItem: SystemMediaItem,
        targetLabel: String,
        sourceItems: List<SystemMediaItem>,
        finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
    ) {
        uploadScope.launch {
            val metadata = runCatching {
                readRealUploadMetadata(context, mediaItem)
            }.getOrElse { throwable ->
                uploadTasksState.add(
                    SystemMediaUploadTaskUiModel(
                        taskId = "${operationId}-${mediaItem.id}",
                        operationId = operationId,
                        mediaId = mediaItem.id,
                        fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                        targetLabel = targetLabel,
                        progressPercent = 0,
                        state = UploadState.FAILURE,
                        statusMessage = "本地媒体读取失败",
                        errorMessage = throwable.message ?: "读取本地媒体失败。",
                        canRetry = true,
                    ),
                )
                return@launch
            }

            val tokenResult = RepositoryProvider.uploadRepository.createUploadToken(
                CreateUploadTokenPayload(
                    fileName = metadata.fileName,
                    mimeType = metadata.mimeType,
                    fileSizeBytes = metadata.fileBytes.size.toLong(),
                    mediaType = mediaItem.type.name.lowercase(),
                    width = metadata.width,
                    height = metadata.height,
                    durationMillis = metadata.durationMillis,
                    displayTimeMillis = mediaItem.displayTimeMillis,
                ),
            )
            val uploadId = when (tokenResult) {
                is ApiResult.Success -> tokenResult.data.uploadId
                is ApiResult.Error -> {
                    uploadTasksState.add(
                        SystemMediaUploadTaskUiModel(
                            taskId = "${operationId}-${mediaItem.id}",
                            operationId = operationId,
                            mediaId = mediaItem.id,
                            fileName = metadata.fileName,
                            targetLabel = targetLabel,
                            progressPercent = 0,
                            state = UploadState.FAILURE,
                            statusMessage = "申请上传失败",
                            errorMessage = tokenResult.message,
                            canRetry = true,
                        ),
                    )
                    return@launch
                }
                ApiResult.Loading -> return@launch
            }

            uploadTasksState.add(
                SystemMediaUploadTaskUiModel(
                    taskId = uploadId,
                    operationId = operationId,
                    mediaId = mediaItem.id,
                    fileName = metadata.fileName,
                    targetLabel = targetLabel,
                    progressPercent = 8,
                    state = UploadState.WAITING,
                    statusMessage = "等待上传",
                ),
            )
            updateUploadTask(
                taskId = uploadId,
                state = UploadState.UPLOADING,
                progressPercent = 42,
                statusMessage = "正在上传 42%",
            )

            when (
                val uploadResult = RepositoryProvider.uploadRepository.uploadLocalFile(
                    uploadId = uploadId,
                    fileName = metadata.fileName,
                    mimeType = metadata.mimeType,
                    fileBytes = metadata.fileBytes,
                )
            ) {
                is ApiResult.Success -> {
                    if (uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                        return@launch
                    }
                    updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.SUCCESS,
                        progressPercent = 100,
                        statusMessage = finalizeWaitingMessage(targetLabel),
                    )
                    rememberUploadedMediaId(
                        operationId = operationId,
                        sourceMediaId = mediaItem.id,
                        uploadedMediaId = uploadResult.data.mediaId,
                    )
                    finalizeRealOperationIfReady(
                        operationId = operationId,
                        sourceItems = sourceItems,
                        finalizeAction = finalizeAction,
                    )
                }
                is ApiResult.Error -> {
                    if (uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                        return@launch
                    }
                    updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.FAILURE,
                        progressPercent = 42,
                        statusMessage = "上传失败",
                        errorMessage = uploadResult.message,
                        canRetry = true,
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private suspend fun finalizeImportToAppReal(
        uploadedMedia: UploadedOperationMedia,
    ): ApiResult<RealFinalizeResult> {
        if (uploadedMedia.orderedUploadedMediaIds.isEmpty()) {
            return ApiResult.Error(
                code = "IMPORT_EMPTY",
                message = "上传已完成，但没有拿到可导入的媒体 ID。",
            )
        }
        return ApiResult.Success(
            RealFinalizeResult(
                operationType = OperationType.IMPORT_TO_APP,
                successMessage = "媒体已导入 app 照片流。",
                affectedPostIds = emptySet(),
            ),
        )
    }

    private suspend fun finalizeCreatePostReal(
        draft: CreatePostDraft,
        sourceItems: List<SystemMediaItem>,
        uploadedMedia: UploadedOperationMedia,
    ): ApiResult<RealFinalizeResult> {
        val albums = when (val result = RepositoryProvider.albumRepository.getAlbums()) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> {
                return ApiResult.Error(
                    code = result.code,
                    message = result.message.ifBlank { "读取相册失败，暂时无法创建新帖子。" },
                    throwable = result.throwable,
                )
            }
            ApiResult.Loading -> {
                return ApiResult.Error(
                    code = "ALBUMS_LOADING",
                    message = "相册仍在加载中，请稍后再试。",
                )
            }
        }
        val defaultAlbumId = albums.firstOrNull()?.albumId
            ?: return ApiResult.Error(
                code = "ALBUM_REQUIRED",
                message = "当前没有可用相册，暂时无法创建新帖子。",
            )
        val finalAlbumIds = draft.albumIds.ifEmpty { listOf(defaultAlbumId) }
        val coverMediaId = draft.coverSourceMediaId
            ?.let(uploadedMedia.uploadedMediaIdBySourceId::get)

        return when (
            val result = RepositoryProvider.postRepository.createPost(
                CreatePostPayload(
                    title = draft.title.ifBlank { buildRealPostTitle(sourceItems) },
                    summary = draft.summary.ifBlank { buildRealPostSummary(sourceItems) },
                    displayTimeMillis = draft.displayTimeMillis,
                    albumIds = finalAlbumIds,
                    initialMediaIds = uploadedMedia.orderedUploadedMediaIds,
                    coverMediaId = coverMediaId,
                ),
            )
        ) {
            is ApiResult.Success -> {
                linkMediaToPost(
                    mediaIds = sourceItems.map { it.id },
                    postId = result.data.postId,
                )
                ApiResult.Success(
                    RealFinalizeResult(
                        operationType = OperationType.CREATE_POST,
                        successMessage = "新帖子已创建，照片流和相册列表已刷新。",
                        postRoute = result.data.toPostDetailPlaceholderRoute(
                            selectedAlbumId = result.data.albumIds.firstOrNull() ?: finalAlbumIds.first(),
                        ),
                        affectedPostIds = setOf(result.data.postId),
                    ),
                )
            }
            is ApiResult.Error -> ApiResult.Error(
                code = result.code,
                message = result.message.ifBlank { "上传已完成，但创建帖子失败了。" },
                throwable = result.throwable,
            )
            ApiResult.Loading -> ApiResult.Loading
        }
    }

    private suspend fun finalizeAppendToPostReal(
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
                linkMediaToPost(
                    mediaIds = sourceItems.map { it.id },
                    postId = postId,
                )
                ApiResult.Success(
                    RealFinalizeResult(
                        operationType = OperationType.ADD_TO_EXISTING_POST,
                        successMessage = "媒体已加入已有帖子，帖子详情和媒体管理已刷新。",
                        affectedPostIds = setOf(postId),
                    ),
                )
            }
            is ApiResult.Error -> ApiResult.Error(
                code = result.code,
                message = result.message.ifBlank { "上传已完成，但加入已有帖子失败了。" },
                throwable = result.throwable,
            )
            ApiResult.Loading -> ApiResult.Loading
        }
    }

    private suspend fun finalizeRealOperationIfReady(
        operationId: String,
        sourceItems: List<SystemMediaItem>,
        finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
    ) {
        if (finalizedOperationIds.contains(operationId)) return
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        if (operationTasks.any { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED }) return
        if (!operationTasks.all { it.state == UploadState.SUCCESS }) return

        val uploadedMap = realUploadedMediaIdsByOperationId[operationId].orEmpty()
        val orderedIds = sourceItems.mapNotNull { uploadedMap[it.id] }
        if (orderedIds.distinct().size != sourceItems.distinctBy { it.id }.size) return

        finalizedOperationIds += operationId
        updateOperationTasks(
            operationId = operationId,
            state = UploadState.UPLOADING,
            statusMessage = "上传完成，正在整理帖子…",
        )

        when (
            val result = finalizeAction(
                UploadedOperationMedia(
                    orderedUploadedMediaIds = orderedIds,
                    uploadedMediaIdBySourceId = uploadedMap.toMap(),
                ),
            )
        ) {
            is ApiResult.Success -> {
                publishMutation(
                    kind = MutationKind.OVERLAY_ONLY,
                    mediaIds = sourceItems.map { it.id },
                )
                notifyRealBackendContentChanged(
                    postIds = result.data.affectedPostIds,
                )
                updateOperationTasks(
                    operationId = operationId,
                    state = UploadState.SUCCESS,
                    statusMessage = result.data.successMessage,
                )
                publishOperationResult(
                    operationId = operationId,
                    operationType = result.data.operationType,
                    succeeded = true,
                    message = result.data.successMessage,
                    postRoute = result.data.postRoute,
                )
            }
            is ApiResult.Error -> {
                finalizedOperationIds.remove(operationId)
                val request = operationRequestsById[operationId]
                updateOperationTasks(
                    operationId = operationId,
                    state = UploadState.FAILURE,
                    statusMessage = when (request?.operationType) {
                        OperationType.ADD_TO_EXISTING_POST -> "加入已有帖子失败"
                        else -> "创建帖子失败"
                    },
                    errorMessage = result.message.ifBlank { "上传已完成，但后续整理帖子失败了。" },
                    canRetry = true,
                )
                publishOperationResult(
                    operationId = operationId,
                    operationType = request?.operationType ?: OperationType.CREATE_POST,
                    succeeded = false,
                    message = result.message.ifBlank { "上传已完成，但后续整理帖子失败了。" },
                )
            }
            ApiResult.Loading -> Unit
        }
    }

    private fun publishMutation(
        kind: MutationKind,
        mediaIds: Collection<String> = emptyList(),
    ) {
        val nextVersion = mutationVersion + 1
        mutationVersion = nextVersion
        latestMutationEvent = MutationEvent(
            version = nextVersion,
            kind = kind,
            mediaIds = mediaIds.filter { it.isNotBlank() }.toSet(),
        )
    }

    private fun rememberUploadedMediaId(
        operationId: String,
        sourceMediaId: String,
        uploadedMediaId: String,
    ) {
        val uploadedIds = realUploadedMediaIdsByOperationId.getOrPut(operationId) { linkedMapOf() }
        uploadedIds[sourceMediaId] = uploadedMediaId
    }

    private fun updateUploadTask(
        taskId: String,
        state: UploadState,
        progressPercent: Int,
        statusMessage: String? = null,
        errorMessage: String? = null,
        canRetry: Boolean = false,
    ) {
        val currentIndex = uploadTasksState.indexOfFirst { it.taskId == taskId }
        if (currentIndex < 0) return
        val current = uploadTasksState[currentIndex]
        uploadTasksState[currentIndex] = current.copy(
            state = state,
            progressPercent = progressPercent,
            statusMessage = statusMessage ?: current.statusMessage,
            errorMessage = errorMessage,
            canRetry = canRetry,
        )
    }

    private fun updateOperationTasks(
        operationId: String,
        state: UploadState,
        statusMessage: String,
        errorMessage: String? = null,
        canRetry: Boolean = false,
    ) {
        uploadTasksState.indices.forEach { index ->
            val task = uploadTasksState[index]
            if (task.operationId == operationId) {
                uploadTasksState[index] = task.copy(
                    state = state,
                    progressPercent = if (state == UploadState.FAILURE || state == UploadState.CANCELLED) {
                        task.progressPercent.coerceAtLeast(0)
                    } else {
                        task.progressPercent.coerceAtLeast(100)
                    },
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    canRetry = canRetry,
                )
            }
        }
    }

    private suspend fun readRealUploadMetadata(
        context: Context,
        mediaItem: SystemMediaItem,
    ): RealUploadMetadata = withContext(Dispatchers.IO) {
        val fileName = mediaItem.displayName.ifBlank {
            val extension = if (mediaItem.type == SystemMediaType.VIDEO) "mp4" else "jpg"
            "${mediaItem.id}.$extension"
        }
        val mimeType = mediaItem.mimeType.ifBlank { fakeMimeType(mediaItem.type) }
        val fileBytes = context.contentResolver.openInputStream(mediaItem.uri)?.use { it.readBytes() }
            ?: error("无法读取本地媒体内容。")
        RealUploadMetadata(
            fileName = fileName,
            mimeType = mimeType,
            fileBytes = fileBytes,
            width = mediaItem.width?.coerceAtLeast(1) ?: 1,
            height = mediaItem.height?.coerceAtLeast(1) ?: 1,
        )
    }

    private fun buildRealPostTitle(items: List<SystemMediaItem>): String {
        val dateLabel = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
            .format(Date(items.maxOfOrNull { it.displayTimeMillis } ?: System.currentTimeMillis()))
        return if (items.size == 1) {
            "系统导入 · $dateLabel"
        } else {
            "批量导入 · $dateLabel"
        }
    }

    private fun buildRealPostSummary(items: List<SystemMediaItem>): String {
        return "从系统媒体导入 ${items.size} 项内容。"
    }

    private fun defaultCreatePostDraft(
        mediaItems: List<SystemMediaItem>,
    ): CreatePostDraft {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        val firstAlbumId = FakeAlbumRepository.getAlbums().firstOrNull()?.id
        return CreatePostDraft(
            title = "",
            summary = "",
            displayTimeMillis = normalizedItems.maxOfOrNull { it.displayTimeMillis }
                ?: System.currentTimeMillis(),
            albumIds = firstAlbumId?.let(::listOf).orEmpty(),
            coverSourceMediaId = normalizedItems.firstOrNull()?.id,
            locationLabel = null,
        )
    }

    private fun finalizeWaitingMessage(targetLabel: String): String {
        return when (targetLabel) {
            "加入已有帖子" -> "上传完成，等待加入帖子"
            else -> "上传完成，等待创建帖子"
        }
    }

    private fun normalizeSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): List<SystemMediaItem> {
        return mediaItems.distinctBy { it.id }
    }

    private fun Uri.toPickedSystemMediaItem(
        context: Context,
        index: Int,
    ): SystemMediaItem? {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(this).orEmpty()
        val type = if (mimeType.startsWith("video/", ignoreCase = true)) {
            SystemMediaType.VIDEO
        } else {
            SystemMediaType.IMAGE
        }
        val displayName = contentResolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) cursor.getString(columnIndex) else null
            } else {
                null
            }
        }.orEmpty().ifBlank { "picked-${index + 1}" }
        val (width, height, _) = resolvePickedMediaMetadata(context, this, type)
        val aspectRatio = resolvePickedMediaAspectRatio(width, height, type)
        val now = System.currentTimeMillis() - (index * 1_000L)
        val calendar = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = now
        }

        return SystemMediaItem(
            id = "picked-${now}-${index + 1}-${displayName.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }}",
            mediaStoreId = displayName.hashCode().toLong().and(Long.MAX_VALUE),
            uri = this,
            type = type,
            mimeType = mimeType.ifBlank { if (type == SystemMediaType.VIDEO) "video/mp4" else "image/jpeg" },
            displayName = displayName,
            bucketName = "系统相册",
            displayTimeMillis = now,
            displayYear = calendar.get(java.util.Calendar.YEAR),
            displayMonth = calendar.get(java.util.Calendar.MONTH) + 1,
            displayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH),
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            palette = pickedPaletteFor(index, type),
            linkedPostIds = emptyList(),
        )
    }

    private fun resolvePickedMediaMetadata(
        context: Context,
        uri: Uri,
        type: SystemMediaType,
    ): Triple<Int?, Int?, Long?> {
        return when (type) {
            SystemMediaType.IMAGE -> {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, bounds)
                    }
                }
                Triple(
                    bounds.outWidth.takeIf { it > 0 },
                    bounds.outHeight.takeIf { it > 0 },
                    null,
                )
            }
            SystemMediaType.VIDEO -> {
                val retriever = MediaMetadataRetriever()
                runCatching {
                    retriever.setDataSource(context, uri)
                    val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                    val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull()
                        ?: 0
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    val (resolvedWidth, resolvedHeight) = if (rotation == 90 || rotation == 270) {
                        rawHeight to rawWidth
                    } else {
                        rawWidth to rawHeight
                    }
                    Triple(resolvedWidth, resolvedHeight, duration)
                }.getOrElse {
                    Triple(null, null, null)
                }.also {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    private fun resolvePickedMediaAspectRatio(
        width: Int?,
        height: Int?,
        type: SystemMediaType,
    ): Float {
        if (width != null && height != null && width > 0 && height > 0) {
            return (width.toFloat() / height.toFloat()).coerceIn(0.56f, 1.8f)
        }
        return if (type == SystemMediaType.VIDEO) 1.33f else 1f
    }

    private fun pickedPaletteFor(index: Int, type: SystemMediaType): PhotoThumbnailPalette {
        val palettes = listOf(
            PhotoThumbnailPalette(
                start = androidx.compose.ui.graphics.Color(0xFFB8D8F8),
                end = androidx.compose.ui.graphics.Color(0xFF7EA6DF),
                accent = androidx.compose.ui.graphics.Color(0xFFE8F2FF),
            ),
            PhotoThumbnailPalette(
                start = androidx.compose.ui.graphics.Color(0xFFF5D2C3),
                end = androidx.compose.ui.graphics.Color(0xFFE7A08D),
                accent = androidx.compose.ui.graphics.Color(0xFFFFF0E8),
            ),
            PhotoThumbnailPalette(
                start = androidx.compose.ui.graphics.Color(0xFFCFE5B9),
                end = androidx.compose.ui.graphics.Color(0xFF84B38A),
                accent = androidx.compose.ui.graphics.Color(0xFFEFF8E1),
            ),
            PhotoThumbnailPalette(
                start = androidx.compose.ui.graphics.Color(0xFFD8D0F2),
                end = androidx.compose.ui.graphics.Color(0xFF8FA0D8),
                accent = androidx.compose.ui.graphics.Color(0xFFF0EDFF),
            ),
        )
        val offset = if (type == SystemMediaType.VIDEO) 1 else 0
        return palettes[(index + offset) % palettes.size]
    }

    private fun finalizeOperationIfReady(
        operationId: String,
        onOperationSuccess: () -> OperationResultEvent,
    ) {
        if (finalizedOperationIds.contains(operationId)) return
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        if (operationTasks.any { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED }) return
        if (operationTasks.all { it.state == UploadState.SUCCESS }) {
            finalizedOperationIds += operationId
            val result = onOperationSuccess()
            updateOperationTasks(
                operationId = operationId,
                state = if (result.succeeded) UploadState.SUCCESS else UploadState.FAILURE,
                statusMessage = result.message,
                errorMessage = if (result.succeeded) null else result.message,
                canRetry = !result.succeeded,
            )
            publishOperationResult(result)
        }
    }

    private fun publishOperationResult(event: OperationResultEvent) {
        operationResultsState.add(event)
        while (operationResultsState.size > 12) {
            operationResultsState.removeAt(0)
        }
    }

    private fun publishOperationResult(
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

    private fun clearOperationState(
        operationId: String,
        keepRequest: Boolean,
    ) {
        uploadTasksState.removeAll { it.operationId == operationId }
        finalizedOperationIds.remove(operationId)
        realUploadedMediaIdsByOperationId.remove(operationId)
        if (!keepRequest) {
            operationRequestsById.remove(operationId)
        }
    }

    private fun cleanupOperationIfIdle(operationId: String) {
        if (uploadTasksState.none { it.operationId == operationId }) {
            clearOperationState(operationId, keepRequest = false)
        }
    }

    private fun fakeMimeType(type: SystemMediaType): String {
        return when (type) {
            SystemMediaType.IMAGE -> "image/jpeg"
            SystemMediaType.VIDEO -> "video/mp4"
        }
    }

    private fun fakeFileSizeBytes(item: SystemMediaItem): Long {
        val base = if (item.type == SystemMediaType.VIDEO) 12_000_000L else 3_000_000L
        return base + (item.id.hashCode().toLong().and(0xFFFF))
    }
}

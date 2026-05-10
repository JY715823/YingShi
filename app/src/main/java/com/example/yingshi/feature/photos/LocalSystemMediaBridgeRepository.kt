package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.BuildConfig
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreatePostPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object LocalSystemMediaBridgeRepository {
    private const val UploadLogTag = "SystemMediaUpload"
    private const val ReadLocalMediaTimeoutMillis = 20_000L
    private const val CreateUploadTokenTimeoutMillis = 15_000L
    private const val UploadFileTimeoutMillis = 120_000L

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
        val resultMediaIds: List<String> = emptyList(),
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val totalCount: Int = 0,
    )

    private data class RealUploadMetadata(
        val fileName: String,
        val mimeType: String,
        val fileBytes: ByteArray,
        val width: Int,
        val height: Int,
        val durationMillis: Long? = null,
        val displayTimeMillis: Long,
        val capturedAtMillis: Long?,
        val importedAtMillis: Long,
        val sourceUri: Uri,
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
        override val targetLabel: String = "导入 App"
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
    private val publishedOperationSummaryIds = linkedSetOf<String>()
    private val hiddenMediaIds = linkedSetOf<String>()
    private val linkedPostIdsByMediaId = linkedMapOf<String, LinkedHashSet<String>>()
    private val realUploadedMediaIdsByOperationId = linkedMapOf<String, LinkedHashMap<String, String>>()
    private val operationRequestsById = linkedMapOf<String, PendingOperationRequest>()
    private val uploadJobsByTaskId = linkedMapOf<String, Job>()

    val uploadTasks: List<SystemMediaUploadTaskUiModel>
        get() = uploadTasksState

    val operationResults: List<OperationResultEvent>
        get() = operationResultsState

    fun remainingUploadTaskCount(): Int {
        return uploadTasksState.count { task ->
            task.state == UploadState.WAITING ||
                task.state == UploadState.UPLOADING ||
                (task.state == UploadState.FAILURE && task.canRetry)
        }
    }

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
        updateUploadTask(
            taskId = taskId,
            state = UploadState.CANCELLED,
            progressPercent = task.progressPercent,
            statusMessage = "上传已取消",
            canRetry = true,
        )
        uploadJobsByTaskId.remove(taskId)?.cancel(CancellationException("Upload task canceled by user"))
        publishOperationSummaryIfReady(task.operationId)
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
                targetLabel = "导入 App",
                onOperationSuccess = {
                    val importedCount = importSystemMediaToApp(mediaItems)
                    OperationResultEvent(
                        eventId = if (importedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.IMPORT_TO_APP,
                        succeeded = importedCount > 0,
                        message = if (importedCount > 0) {
                            "Imported to app photo feed."
                        } else {
                            "No media can be imported."
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
                            message = "Create post failed. Please retry.",
                        )
                    } else {
                        OperationResultEvent(
                            eventId = "$operationId-success",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = true,
                            message = "Post created. Photo feed and albums refreshed.",
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
                            "Media added to post. Detail and media manager refreshed."
                        } else {
                            "These media items are already in the target post."
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
                    capturedAtMillis = mediaItem.displayTimeMillis,
                    importedAtMillis = System.currentTimeMillis(),
                    displayTimeSource = "ORIGINAL",
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
                            mediaType = mediaItem.type,
                            previewUri = mediaItem.uri.toString(),
                            progressPercent = 0,
                            state = UploadState.FAILURE,
                            statusMessage = "Upload failed",
                            errorMessage = tokenResult.message,
                            canRetry = true,
                        ),
                    )
                    publishOperationSummaryIfReady(operationId)
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
                    mediaType = mediaItem.type,
                    previewUri = mediaItem.uri.toString(),
                    progressPercent = 0,
                    state = UploadState.WAITING,
                    statusMessage = "Waiting to upload",
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
                    statusMessage = "濠殿喗绻愮徊钘夛耿椤忓懐鈻斿┑鐘辫兌閻?$progress%",
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
                        resultMediaId = mediaItem.id,
                    )
                    finalizeOperationIfReady(operationId, onOperationSuccess)
                    publishOperationSummaryIfReady(operationId)
                }
                is ApiResult.Error -> {
                    updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.FAILURE,
                        progressPercent = 100,
                        statusMessage = "Upload failed",
                        errorMessage = confirmResult.message,
                        canRetry = true,
                    )
                    publishOperationSummaryIfReady(operationId)
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
                targetLabel = "导入 App",
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
        val initialTaskId = "${operationId}-${mediaItem.id}"
        uploadTasksState.removeAll { it.taskId == initialTaskId }
        uploadTasksState.add(
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

        val job = uploadScope.launch {
            var activeTaskId = initialTaskId
            try {
                debugUploadLog(
                    "enqueue operation=$operationId target=$targetLabel mediaId=${mediaItem.id} " +
                        "uri=${mediaItem.uri} mime=${mediaItem.mimeType} displayName=${mediaItem.displayName}",
                )
                updateUploadTask(
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
                        fallback = "Cannot read selected media. Please select again.",
                    )
                    addFailedUploadTask(
                        taskId = activeTaskId,
                        operationId = operationId,
                        mediaId = mediaItem.id,
                        fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                        targetLabel = targetLabel,
                        mediaType = mediaItem.type,
                        previewUri = mediaItem.uri.toString(),
                        statusMessage = "Reading local media failed",
                        errorMessage = message,
                    )
                    publishOperationSummaryIfReady(operationId)
                    debugUploadLog("read failed operation=$operationId mediaId=${mediaItem.id}: $message", throwable)
                    return@launch
                }
                if (isUploadTaskCancelled(activeTaskId)) return@launch

                debugUploadLog(
                    "metadata operation=$operationId uri=${metadata.sourceUri} file=${metadata.fileName} " +
                        "mime=${metadata.mimeType} size=${metadata.fileBytes.size} " +
                        "width=${metadata.width} height=${metadata.height} duration=${metadata.durationMillis}",
                )
                updateUploadTask(
                    taskId = activeTaskId,
                    state = UploadState.UPLOADING,
                    progressPercent = 12,
                    statusMessage = "正在创建上传任务",
                )
                val tokenPayload = CreateUploadTokenPayload(
                    fileName = metadata.fileName,
                    mimeType = metadata.mimeType,
                    fileSizeBytes = metadata.fileBytes.size.toLong(),
                    mediaType = mediaItem.type.name.lowercase(Locale.ROOT),
                    width = metadata.width,
                    height = metadata.height,
                    durationMillis = metadata.durationMillis,
                    displayTimeMillis = metadata.displayTimeMillis,
                    capturedAtMillis = metadata.capturedAtMillis,
                    importedAtMillis = metadata.importedAtMillis,
                    displayTimeSource = "ORIGINAL",
                )
                val tokenResult = runUploadApiWithTimeout(
                    timeoutMillis = CreateUploadTokenTimeoutMillis,
                    timeoutMessage = "Create upload token timed out. Check server access.",
                ) {
                    RepositoryProvider.uploadRepository.createUploadToken(tokenPayload)
                }
                val uploadId = when (tokenResult) {
                    is ApiResult.Success -> tokenResult.data.uploadId
                    is ApiResult.Error -> {
                        val message = tokenResult.message.ifBlank { "Create upload token failed." }
                        addFailedUploadTask(
                            taskId = activeTaskId,
                            operationId = operationId,
                            mediaId = mediaItem.id,
                            fileName = metadata.fileName,
                            targetLabel = targetLabel,
                        mediaType = mediaItem.type,
                        previewUri = mediaItem.uri.toString(),
                            statusMessage = "Create upload token failed",
                            errorMessage = message,
                        )
                        publishOperationSummaryIfReady(operationId)
                        debugUploadLog("token failed operation=$operationId mediaId=${mediaItem.id}: $message", tokenResult.throwable)
                        return@launch
                    }
                    ApiResult.Loading -> return@launch
                }
                if (!replaceUploadTaskId(
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

                updateUploadTask(
                    taskId = uploadId,
                    state = UploadState.UPLOADING,
                    progressPercent = 35,
                    statusMessage = "濠殿喗绻愮徊钘夛耿椤忓懐鈻斿┑鐘辫兌閻?35%",
                )

                updateUploadTask(
                    taskId = uploadId,
                    state = UploadState.UPLOADING,
                    progressPercent = 35,
                    statusMessage = "正在上传 35%",
                )

                val uploadResult = runUploadApiWithTimeout(
                    timeoutMillis = UploadFileTimeoutMillis,
                    timeoutMessage = "Upload timed out. Check network or server and retry.",
                ) {
                    RepositoryProvider.uploadRepository.uploadLocalFile(
                        uploadId = uploadId,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        fileBytes = metadata.fileBytes,
                        onProgressPercent = { progress ->
                            val mappedProgress = (35 + progress * 55 / 100).coerceIn(35, 90)
                            uploadScope.launch {
                                if (!isUploadTaskCancelled(uploadId)) {
                                    updateUploadTask(
                                        taskId = uploadId,
                                        state = UploadState.UPLOADING,
                                        progressPercent = mappedProgress,
                                        statusMessage = "正在上传 $mappedProgress%",
                                    )
                                }
                            }
                        },
                    )
                }
                when (uploadResult) {
                    is ApiResult.Success -> {
                        if (uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                            return@launch
                        }
                        updateUploadTask(
                            taskId = uploadId,
                            state = UploadState.UPLOADING,
                            progressPercent = 95,
                            statusMessage = "上传完成，正在处理",
                        )
                        val uploadedMediaId = uploadResult.data.mediaId
                        if (uploadedMediaId.isBlank()) {
                            val message = "Server upload succeeded but returned no media ID."
                            updateUploadTask(
                                taskId = uploadId,
                                state = UploadState.FAILURE,
                                progressPercent = 95,
                        statusMessage = "Upload failed",
                                errorMessage = message,
                                canRetry = true,
                            )
                            publishOperationSummaryIfReady(operationId)
                            debugUploadLog("upload response missing mediaId operation=$operationId uploadId=$uploadId")
                            return@launch
                        }
                        updateUploadTask(
                            taskId = uploadId,
                            state = UploadState.SUCCESS,
                            progressPercent = 100,
                            statusMessage = finalizeWaitingMessage(targetLabel),
                            resultMediaId = uploadedMediaId,
                        )
                        rememberUploadedMediaId(
                            operationId = operationId,
                            sourceMediaId = mediaItem.id,
                            uploadedMediaId = uploadedMediaId,
                        )
                        publishMutation(
                            kind = MutationKind.OVERLAY_ONLY,
                            mediaIds = listOf(mediaItem.id),
                        )
                        notifyRealBackendContentChanged(mediaIds = setOf(uploadedMediaId))
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
                        publishOperationSummaryIfReady(operationId)
                    }
                    is ApiResult.Error -> {
                        if (uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                            return@launch
                        }
                        val message = uploadResult.message.ifBlank { "Upload failed. Please retry." }
                        updateUploadTask(
                            taskId = uploadId,
                            state = UploadState.FAILURE,
                            progressPercent = 35,
                        statusMessage = "Upload failed",
                            errorMessage = message,
                            canRetry = true,
                        )
                        publishOperationSummaryIfReady(operationId)
                        debugUploadLog("upload failed operation=$operationId uploadId=$uploadId: $message", uploadResult.throwable)
                    }
                    ApiResult.Loading -> Unit
                }
            } catch (throwable: CancellationException) {
                if (!isUploadTaskCancelled(activeTaskId)) {
                    updateUploadTask(
                        taskId = activeTaskId,
                        state = UploadState.CANCELLED,
                        progressPercent = uploadTasksState.firstOrNull { it.taskId == activeTaskId }?.progressPercent ?: 0,
                        statusMessage = "上传已取消",
                        canRetry = true,
                    )
                }
                publishOperationSummaryIfReady(operationId)
            } catch (throwable: Throwable) {
                val message = friendlyUploadError(
                    throwable = throwable,
                    fallback = "Upload task stopped unexpectedly. Please retry.",
                )
                if (uploadTasksState.any { it.taskId == activeTaskId }) {
                    updateUploadTask(
                        taskId = activeTaskId,
                        state = UploadState.FAILURE,
                        progressPercent = 0,
                        statusMessage = "Upload failed",
                        errorMessage = message,
                        canRetry = true,
                    )
                } else {
                    addFailedUploadTask(
                        taskId = activeTaskId,
                        operationId = operationId,
                        mediaId = mediaItem.id,
                        fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                        targetLabel = targetLabel,
                        mediaType = mediaItem.type,
                        previewUri = mediaItem.uri.toString(),
                        statusMessage = "Upload failed",
                        errorMessage = message,
                    )
                }
                publishOperationSummaryIfReady(operationId)
                debugUploadLog("upload crashed operation=$operationId task=$activeTaskId", throwable)
            } finally {
                uploadJobsByTaskId.remove(initialTaskId)
                uploadJobsByTaskId.remove(activeTaskId)
            }
        }
        uploadJobsByTaskId[initialTaskId] = job
    }

    private suspend fun finalizeImportToAppReal(
        uploadedMedia: UploadedOperationMedia,
    ): ApiResult<RealFinalizeResult> {
        if (uploadedMedia.orderedUploadedMediaIds.isEmpty()) {
            return ApiResult.Error(
                code = "IMPORT_EMPTY",
                message = "Upload finished but no importable media ID was returned.",
            )
        }
        return ApiResult.Success(
            RealFinalizeResult(
                operationType = OperationType.IMPORT_TO_APP,
                successMessage = "Imported to app photo feed.",
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
                    message = result.message.ifBlank { "Failed to load albums; cannot create post now." },
                    throwable = result.throwable,
                )
            }
            ApiResult.Loading -> {
                return ApiResult.Error(
                    code = "ALBUMS_LOADING",
                    message = "Albums are still loading. Please retry later.",
                )
            }
        }
        val defaultAlbumId = albums.firstOrNull()?.albumId
            ?: return ApiResult.Error(
                code = "ALBUM_REQUIRED",
                message = "No album is available; cannot create post now.",
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
                        successMessage = "Post created. Photo feed and albums refreshed.",
                        postRoute = result.data.toPostDetailPlaceholderRoute(
                            selectedAlbumId = result.data.albumIds.firstOrNull() ?: finalAlbumIds.first(),
                        ),
                        affectedPostIds = setOf(result.data.postId),
                    ),
                )
            }
            is ApiResult.Error -> ApiResult.Error(
                code = result.code,
                message = result.message.ifBlank { "Upload finished, but creating the post failed." },
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
                        successMessage = "Media added to post. Detail and media manager refreshed.",
                        affectedPostIds = setOf(postId),
                    ),
                )
            }
            is ApiResult.Error -> ApiResult.Error(
                code = result.code,
                message = result.message.ifBlank { "Upload finished, but adding to the post failed." },
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
        val request = operationRequestsById[operationId]
        updateOperationTasks(
            operationId = operationId,
            state = UploadState.UPLOADING,
            statusMessage = when (request?.operationType) {
                OperationType.IMPORT_TO_APP -> "Upload finished. Refreshing photo feed"
                OperationType.ADD_TO_EXISTING_POST -> "Upload finished. Adding to post"
                OperationType.CREATE_POST,
                null -> "Upload finished. Creating post"
            },
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
                    mediaIds = orderedIds.toSet(),
                )
                updateOperationTasks(
                    operationId = operationId,
                    state = UploadState.SUCCESS,
                    statusMessage = result.data.successMessage,
                )
                publishOperationSummaryIfReady(
                    operationId = operationId,
                    operationType = result.data.operationType,
                    postRoute = result.data.postRoute,
                )
            }
            is ApiResult.Error -> {
                finalizedOperationIds.remove(operationId)
                updateOperationTasks(
                    operationId = operationId,
                    state = UploadState.FAILURE,
                    statusMessage = when (request?.operationType) {
                        OperationType.ADD_TO_EXISTING_POST -> "Add to post failed"
                        else -> "Create post failed"
                    },
                    errorMessage = result.message.ifBlank { "Upload finished, but finalizing the operation failed." },
                    canRetry = true,
                )
                publishOperationSummaryIfReady(
                    operationId = operationId,
                    operationType = request?.operationType ?: OperationType.CREATE_POST,
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

    private fun isUploadTaskCancelled(taskId: String): Boolean {
        return uploadTasksState.firstOrNull { it.taskId == taskId }?.state == UploadState.CANCELLED
    }

    private fun replaceUploadTaskId(
        oldTaskId: String,
        newTaskId: String,
        fileName: String,
        progressPercent: Int,
        statusMessage: String,
    ): Boolean {
        val currentIndex = uploadTasksState.indexOfFirst { it.taskId == oldTaskId }
        if (currentIndex < 0) return false
        val current = uploadTasksState[currentIndex]
        if (current.state == UploadState.CANCELLED) return false
        uploadTasksState[currentIndex] = current.copy(
            taskId = newTaskId,
            fileName = fileName,
            progressPercent = progressPercent,
            state = UploadState.WAITING,
            statusMessage = statusMessage,
            errorMessage = null,
            canRetry = false,
        )
        uploadJobsByTaskId.remove(oldTaskId)?.let { job ->
            uploadJobsByTaskId[newTaskId] = job
        }
        return true
    }

    private fun updateUploadTask(
        taskId: String,
        state: UploadState,
        progressPercent: Int,
        statusMessage: String? = null,
        errorMessage: String? = null,
        canRetry: Boolean = false,
        resultMediaId: String? = null,
        previewUri: String? = null,
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
            resultMediaId = resultMediaId ?: current.resultMediaId,
            previewUri = previewUri ?: current.previewUri,
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

    private fun addFailedUploadTask(
        taskId: String,
        operationId: String,
        mediaId: String,
        fileName: String,
        targetLabel: String,
        mediaType: SystemMediaType = SystemMediaType.IMAGE,
        previewUri: String? = null,
        statusMessage: String,
        errorMessage: String,
    ) {
        uploadTasksState.removeAll { it.taskId == taskId }
        uploadTasksState.add(
            SystemMediaUploadTaskUiModel(
                taskId = taskId,
                operationId = operationId,
                mediaId = mediaId,
                fileName = fileName,
                targetLabel = targetLabel,
                mediaType = mediaType,
                previewUri = previewUri,
                progressPercent = 0,
                state = UploadState.FAILURE,
                statusMessage = statusMessage,
                errorMessage = errorMessage,
                canRetry = true,
            ),
        )
    }

    private fun publishUploadFailure(
        operationId: String,
        message: String,
    ) {
        publishOperationSummaryIfReady(operationId)
    }

    private suspend fun <T> runUploadApiWithTimeout(
        timeoutMillis: Long,
        timeoutMessage: String,
        block: suspend () -> ApiResult<T>,
    ): ApiResult<T> {
        return try {
            withTimeout(timeoutMillis) {
                block()
            }
        } catch (throwable: TimeoutCancellationException) {
            ApiResult.Error(
                code = "UPLOAD_TIMEOUT",
                message = timeoutMessage,
                throwable = throwable,
            )
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            ApiResult.Error(
                code = "UPLOAD_REQUEST_FAILED",
                message = friendlyUploadError(throwable, "Upload request failed. Check network and server."),
                throwable = throwable,
            )
        }
    }

    private fun friendlyUploadError(
        throwable: Throwable,
        fallback: String,
    ): String {
        return when (throwable) {
            is TimeoutCancellationException -> "Operation timed out. Check network and server, then retry."
            is SecurityException -> "No permission to read selected media. Grant access and retry."
            is java.io.FileNotFoundException -> "Selected media no longer exists or cannot be accessed."
            is java.net.ConnectException -> "Cannot connect to server. Check physical-device baseUrl and network."
            is java.net.SocketTimeoutException -> "Network request timed out. Please retry."
            else -> throwable.message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    private fun debugUploadLog(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        if (throwable == null) {
            Log.d(UploadLogTag, message)
        } else {
            Log.e(UploadLogTag, message, throwable)
        }
    }

    private suspend fun readRealUploadMetadata(
        context: Context,
        mediaItem: SystemMediaItem,
    ): RealUploadMetadata = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val queriedDisplayName = contentResolver.query(
            mediaItem.uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
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
        val fileBytes = contentResolver.openInputStream(mediaItem.uri)?.use { it.readBytes() }
            ?: error("Cannot read local media bytes.")
        if (fileBytes.isEmpty()) {
            error("Selected media file is empty.")
        }
        val (resolvedWidth, resolvedHeight, resolvedDuration) = resolvePickedMediaMetadata(
            context = context,
            uri = mediaItem.uri,
            type = mediaItem.type,
        )
        val (uploadWidth, uploadHeight) = resolveUploadDimensions(
            mediaItem = mediaItem,
            resolvedWidth = resolvedWidth,
            resolvedHeight = resolvedHeight,
        )
        val displayTimeMillis = normalizeUploadTimeMillis(mediaItem.displayTimeMillis)
        val importedAtMillis = System.currentTimeMillis()
        RealUploadMetadata(
            fileName = fileName,
            mimeType = mimeType,
            fileBytes = fileBytes,
            width = uploadWidth,
            height = uploadHeight,
            durationMillis = if (mediaItem.type == SystemMediaType.VIDEO) {
                resolvedDuration ?: mediaItem.videoDurationMillis
            } else {
                null
            },
            displayTimeMillis = displayTimeMillis,
            capturedAtMillis = displayTimeMillis,
            importedAtMillis = importedAtMillis,
            sourceUri = mediaItem.uri,
        )
    }

    private fun buildRealPostTitle(items: List<SystemMediaItem>): String {
        val dateLabel = SimpleDateFormat("M\u6708d\u65e5 HH:mm", Locale.CHINA)
            .format(Date(items.maxOfOrNull { it.displayTimeMillis } ?: System.currentTimeMillis()))
        return if (items.size == 1) {
            "\u4ece\u7cfb\u7edf\u5a92\u4f53\u521b\u5efa \u00b7 $dateLabel"
        } else {
            "\u4ece\u7cfb\u7edf\u5a92\u4f53\u5bfc\u5165 ${items.size} \u9879 \u00b7 $dateLabel"
        }
    }

    private fun buildRealPostSummary(items: List<SystemMediaItem>): String {
        return "\u4ece\u7cfb\u7edf\u5a92\u4f53\u5bfc\u5165 ${items.size} \u9879\u5185\u5bb9\u3002"
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
            "Import to App",
            "\u5bfc\u5165 App",
            -> "\u4e0a\u4f20\u5b8c\u6210\uff0c\u6b63\u5728\u5237\u65b0\u7167\u7247\u6d41"
            "Add to post",
            "\u52a0\u5165\u5df2\u6709\u5e16\u5b50",
            -> "\u4e0a\u4f20\u5b8c\u6210\uff0c\u6b63\u5728\u52a0\u5165\u5e16\u5b50"
            else -> "\u4e0a\u4f20\u5b8c\u6210\uff0c\u6b63\u5728\u521b\u5efa\u5e16\u5b50"
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
        val displayName = resolvePickedDisplayName(context, index)
        val (width, height, durationMillis) = resolvePickedMediaMetadata(context, this, type)
        val aspectRatio = resolvePickedMediaAspectRatio(width, height, type)
        val displayTimeMillis = resolvePickedMediaDisplayTimeMillis(context, index)
        val calendar = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = displayTimeMillis
        }

        return SystemMediaItem(
            id = "picked-${displayTimeMillis}-${index + 1}-${displayName.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }}",
            mediaStoreId = displayName.hashCode().toLong().and(Long.MAX_VALUE),
            uri = this,
            type = type,
            mimeType = mimeType.ifBlank { if (type == SystemMediaType.VIDEO) "video/mp4" else "image/jpeg" },
            displayName = displayName,
            bucketName = "系统选择器",
            displayTimeMillis = displayTimeMillis,
            displayYear = calendar.get(java.util.Calendar.YEAR),
            displayMonth = calendar.get(java.util.Calendar.MONTH) + 1,
            displayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH),
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            palette = pickedPaletteFor(index, type),
            linkedPostIds = emptyList(),
            videoDurationMillis = durationMillis,
        )
    }

    private fun Uri.resolvePickedDisplayName(
        context: Context,
        index: Int,
    ): String {
        return context.contentResolver.query(
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
    }

    private fun Uri.resolvePickedMediaDisplayTimeMillis(
        context: Context,
        index: Int,
    ): Long {
        val fallbackTimeMillis = System.currentTimeMillis() - (index * 1_000L)
        val (dateTakenMillis, dateModifiedSeconds) = queryPickedMediaTimes(context)
        return when {
            dateTakenMillis != null && dateTakenMillis > 0L -> dateTakenMillis
            dateModifiedSeconds != null && dateModifiedSeconds > 0L -> dateModifiedSeconds * 1000L
            else -> fallbackTimeMillis
        }
    }

    private fun Uri.queryPickedMediaTimes(
        context: Context,
    ): Pair<Long?, Long?> {
        return runCatching {
            context.contentResolver.query(
                this,
                arrayOf("datetaken", MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateTakenMillis = cursor.getLongOrNull(cursor.getColumnIndex("datetaken"))
                    val dateModifiedSeconds = cursor.getLongOrNull(
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED),
                    )
                    dateTakenMillis to dateModifiedSeconds
                } else {
                    null to null
                }
            } ?: (null to null)
        }.getOrDefault(null to null)
    }

    private fun resolveUploadDimensions(
        mediaItem: SystemMediaItem,
        resolvedWidth: Int?,
        resolvedHeight: Int?,
    ): Pair<Int, Int> {
        if (resolvedWidth != null && resolvedHeight != null && resolvedWidth > 0 && resolvedHeight > 0) {
            return resolvedWidth to resolvedHeight
        }
        if (mediaItem.width != null && mediaItem.height != null && mediaItem.width > 0 && mediaItem.height > 0) {
            return mediaItem.width to mediaItem.height
        }

        val ratio = mediaItem.aspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.15f, 6f)
            ?: if (mediaItem.type == SystemMediaType.VIDEO) 16f / 9f else 1f
        val longEdge = if (mediaItem.type == SystemMediaType.VIDEO) 1920 else 1600
        return if (ratio >= 1f) {
            longEdge to (longEdge / ratio).roundToInt().coerceAtLeast(1)
        } else {
            (longEdge * ratio).roundToInt().coerceAtLeast(1) to longEdge
        }
    }

    private fun normalizeUploadTimeMillis(timeMillis: Long): Long {
        return timeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        if (columnIndex < 0 || isNull(columnIndex)) return null
        return getLong(columnIndex)
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
                val orientationDegrees = resolveImageOrientationDegrees(context, uri)
                val rawWidth = bounds.outWidth.takeIf { it > 0 }
                val rawHeight = bounds.outHeight.takeIf { it > 0 }
                val (resolvedWidth, resolvedHeight) = if (orientationDegrees == 90 || orientationDegrees == 270) {
                    rawHeight to rawWidth
                } else {
                    rawWidth to rawHeight
                }
                Triple(
                    resolvedWidth,
                    resolvedHeight,
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

    private fun resolveImageOrientationDegrees(
        context: Context,
        uri: Uri,
    ): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                when (ExifInterface(inputStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
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
            publishOperationSummaryIfReady(
                operationId = operationId,
                operationType = result.operationType,
                postRoute = result.postRoute,
            )
        }
    }

    private fun publishOperationSummaryIfReady(
        operationId: String,
        operationType: OperationType? = null,
        postRoute: PostDetailPlaceholderRoute? = null,
    ) {
        if (publishedOperationSummaryIds.contains(operationId)) return
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        if (!operationTasks.all { it.isTerminal }) return

        val successCount = operationTasks.count { it.state == UploadState.SUCCESS }
        val failureCount = operationTasks.count { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED }
        val resultMediaIds = operationTasks
            .filter { it.state == UploadState.SUCCESS }
            .mapNotNull { it.resultMediaId?.takeIf { mediaId -> mediaId.isNotBlank() } }
            .distinct()
        val request = operationRequestsById[operationId]
        val resolvedOperationType = operationType ?: request?.operationType ?: OperationType.IMPORT_TO_APP
        publishedOperationSummaryIds += operationId
        publishOperationResult(
            OperationResultEvent(
                eventId = "$operationId-summary-${System.currentTimeMillis()}",
                operationId = operationId,
                operationType = resolvedOperationType,
                succeeded = failureCount == 0,
                message = uploadSummaryMessage(successCount = successCount, failureCount = failureCount),
                postRoute = postRoute,
                resultMediaIds = resultMediaIds,
                successCount = successCount,
                failureCount = failureCount,
                totalCount = operationTasks.size,
            ),
        )
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
        publishedOperationSummaryIds.remove(operationId)
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

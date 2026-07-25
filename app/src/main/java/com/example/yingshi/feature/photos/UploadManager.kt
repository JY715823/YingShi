package com.example.yingshi.feature.photos

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.MutationKind
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationResultEvent
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject

internal sealed interface PendingOperationRequest {
    val mediaItems: List<SystemMediaItem>
    val operationType: OperationType
    val targetLabel: String
}

internal data class CreatePostOperationRequest(
    val draft: CreatePostDraft,
    override val mediaItems: List<SystemMediaItem>,
    val additionalAppMediaIds: List<String> = emptyList(),
    val additionalAppCoverMediaId: String? = null,
) : PendingOperationRequest {
    override val operationType: OperationType = OperationType.CREATE_POST
    override val targetLabel: String = "Create post"
}

internal data class ImportToAppOperationRequest(
    override val mediaItems: List<SystemMediaItem>,
) : PendingOperationRequest {
    override val operationType: OperationType = OperationType.IMPORT_TO_APP
    override val targetLabel: String = "导入照片流"
}

internal data class AddToExistingPostOperationRequest(
    val postId: String,
    val postTitle: String = "",
    override val mediaItems: List<SystemMediaItem>,
) : PendingOperationRequest {
    override val operationType: OperationType = OperationType.ADD_TO_EXISTING_POST
    override val targetLabel: String = if (postTitle.isBlank()) "Add to post" else "Add to $postTitle"
}

internal object UploadManager {
    private const val UploadTasksPreferencesName = "system_media_upload_tasks"
    private const val UploadTasksItemsKey = "tasks_json"
    private const val MaxConcurrentRealUploads = 2

    /** FR-7: Minimum interval (ms) between upload enqueue calls to prevent double-click duplicates. */
    private const val ENQUEUE_DEBOUNCE_MS = 1000L
    private var lastEnqueueTimestamp = 0L

    internal val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val uploadSemaphore = Semaphore(MaxConcurrentRealUploads)
    internal val uploadTasksState = mutableStateListOf<SystemMediaUploadTaskUiModel>()
    // FR-3: 分页状态字段，UI 通过 LocalSystemMediaBridgeRepository 读取
    internal val remoteHistoryCursor = mutableStateOf<String?>(null)
    internal val remoteHistoryHasMore = mutableStateOf(false)
    internal val remoteHistoryLoadingMore = mutableStateOf(false)
    // FR-3 AC-7: loadMore 失败标记，UI 据此显示"加载失败，点击重试"
    internal val remoteHistoryLoadMoreFailed = mutableStateOf(false)
    internal val uploadJobsByTaskId = linkedMapOf<String, Job>()
    internal val realUploadedMediaIdsByOperationId = linkedMapOf<String, LinkedHashMap<String, String>>()
    internal val operationRequestsById = linkedMapOf<String, PendingOperationRequest>()
    internal var uploadTaskPreferences: SharedPreferences? = null
    internal var persistentUploadTasksLoaded = false

    val uploadTasks: List<SystemMediaUploadTaskUiModel>
        get() = uploadTasksState

    init {
        OperationBus.uploadTasksProvider = { uploadTasksState.toList() }
        OperationBus.operationRequestProvider = { operationRequestsById[it] }
    }

    internal fun warmPersistentTransferCenter(context: Context) {
        warmPersistentUploadTasks(context)
        refreshRemoteUploadHistory()
    }

    internal fun refreshRemoteUploadHistory() {
        uploadScope.launch {
            remoteHistoryLoadingMore.value = true
            remoteHistoryLoadMoreFailed.value = false
            when (val result = RepositoryProvider.uploadRepository.getUploadHistory(pageSize = 100)) {
                is ApiResult.Success -> {
                    remoteHistoryCursor.value = result.data.nextCursor
                    remoteHistoryHasMore.value = result.data.hasMore
                    mergeRemoteUploadHistory(result.data.tasks)
                }
                is ApiResult.Error -> {
                    debugUploadLog("history refresh failed: ${result.message}", result.throwable)
                    remoteHistoryLoadMoreFailed.value = true
                }
                ApiResult.Loading -> Unit
            }
            remoteHistoryLoadingMore.value = false
        }
    }

    // FR-3: 加载下一页历史任务。守卫：isLoadingMore / !hasMore / cursor=null 时直接返回。
    internal fun loadMoreUploadHistory() {
        if (remoteHistoryLoadingMore.value) return
        if (!remoteHistoryHasMore.value) return
        val currentCursor = remoteHistoryCursor.value ?: return
        uploadScope.launch {
            remoteHistoryLoadingMore.value = true
            remoteHistoryLoadMoreFailed.value = false
            when (val result = RepositoryProvider.uploadRepository.getUploadHistory(
                pageSize = 100,
                cursor = currentCursor,
            )) {
                is ApiResult.Success -> {
                    remoteHistoryCursor.value = result.data.nextCursor
                    remoteHistoryHasMore.value = result.data.hasMore
                    mergeRemoteUploadHistory(result.data.tasks)
                }
                is ApiResult.Error -> {
                    debugUploadLog("history load more failed: ${result.message}", result.throwable)
                    // FR-3 AC-7: 标记失败，UI 显示"加载失败，点击重试"，不影响已有列表
                    remoteHistoryLoadMoreFailed.value = true
                }
                ApiResult.Loading -> Unit
            }
            remoteHistoryLoadingMore.value = false
        }
    }

    internal fun remainingUploadTaskCount(): Int {
        return uploadTasksState.count { task ->
            task.state == UploadState.WAITING ||
                task.state == UploadState.UPLOADING ||
                (task.state == UploadState.FAILED && task.canRetry)
        }
    }

    internal fun warmPersistentUploadTasks(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            UploadTasksPreferencesName,
            Context.MODE_PRIVATE,
        )
        uploadTaskPreferences = prefs
        if (persistentUploadTasksLoaded) return
        persistentUploadTasksLoaded = true
        val raw = prefs.getString(UploadTasksItemsKey, null)?.takeIf { it.isNotBlank() }
            ?: return
        runCatching {
            val array = JSONArray(raw)
            val restoredTasks = buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val restored = entry.toPersistedUploadTask() ?: continue
                    add(restored)
                }
            }
            if (restoredTasks.isNotEmpty()) {
                uploadTasksState.removeAll { existing ->
                    restoredTasks.any { it.taskId == existing.taskId }
                }
                uploadTasksState.addAll(restoredTasks)
                restoredTasks.map { it.operationId }.distinct().forEach(::refreshOperationTaskMeta)
            }
        }
    }

    internal fun mergeRemoteUploadHistory(tasks: List<RemoteUploadTask>) {
        if (tasks.isEmpty()) return
        var changed = false
        tasks.forEach { remoteTask ->
            val uiTask = remoteTask.toUploadTaskUiModel()
            val existingIndex = uploadTasksState.indexOfFirst { it.taskId == uiTask.taskId }
            if (existingIndex >= 0) {
                val current = uploadTasksState[existingIndex]
                if (current.state == UploadState.CANCELLED) {
                    uploadTasksState[existingIndex] = current.copy(
                        thumbnailUrl = current.thumbnailUrl ?: uiTask.thumbnailUrl,
                        previewUri = current.previewUri ?: uiTask.previewUri,
                        updatedAtMillis = maxOf(current.updatedAtMillis, uiTask.updatedAtMillis),
                    )
                    changed = true
                } else if (!current.state.isActivelyUploading()) {
                    uploadTasksState[existingIndex] = uiTask.copy(
                        previewUri = current.previewUri ?: uiTask.previewUri,
                        thumbnailUrl = uiTask.thumbnailUrl ?: current.thumbnailUrl,
                        canRetry = current.canRetry,
                    )
                    changed = true
                }
            } else {
                uploadTasksState.add(uiTask)
                changed = true
            }
        }
        if (changed) {
            tasks.mapNotNull { it.operationId ?: it.uploadId }.distinct().forEach(::refreshOperationTaskMeta)
            persistUploadTasks()
        }
    }

    internal fun createPostFromSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        return createPostFromSystemMediaDraft(
            draft = defaultCreatePostDraft(mediaItems),
            mediaItems = mediaItems,
        )
    }

    internal fun createPostFromSystemMediaDraft(
        draft: CreatePostDraft,
        mediaItems: List<SystemMediaItem>,
        additionalAppMediaItems: List<PhotoFeedItem> = emptyList(),
    ): AlbumPostCardUiModel? {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        val post = if (additionalAppMediaItems.isEmpty()) {
            FakeAlbumRepository.createConfiguredLocalPostFromSystemMedia(
                draft = draft,
                mediaItems = normalizedItems,
            )
        } else {
            FakeAlbumRepository.createConfiguredLocalPostFromMixedMedia(
                draft = draft,
                systemMediaItems = normalizedItems,
                appMediaItems = additionalAppMediaItems,
            )
        } ?: return null
        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = post.id,
        )
        if (additionalAppMediaItems.isNotEmpty()) {
            FakePhotoFeedRepository.importSystemMediaToFeed(
                mediaItems = additionalAppMediaItems.map { it.toCreatePostSystemMediaItem() },
                postId = post.id,
            )
        }
        ImportOverlayStore.linkMediaToPost(
            mediaIds = normalizedItems.map { it.id } + additionalAppMediaItems.map { it.mediaId },
            postId = post.id,
        )
        normalizedItems.forEach { item ->
            ImportOverlayStore.rememberAppMediaIdForSource(item, item.id)
        }
        additionalAppMediaItems.forEach { item ->
            ImportOverlayStore.linkMediaToPost(
                mediaIds = listOf(item.mediaId),
                postId = post.id,
            )
        }
        return post
    }

    internal fun enqueueCreatePostUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
        draft: CreatePostDraft = defaultCreatePostDraft(mediaItems),
        additionalAppMediaIds: List<String> = emptyList(),
        additionalAppCoverMediaId: String? = null,
    ): Int {
        // FR-7: Debounce to prevent double-click duplicate uploads
        val now = System.currentTimeMillis()
        if (now - lastEnqueueTimestamp < ENQUEUE_DEBOUNCE_MS) {
            return 0
        }
        lastEnqueueTimestamp = now

        ImportOverlayStore.warmPersistentImportOverlay(context)
        val normalizedItems = normalizeSystemMedia(mediaItems)
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.CREATE_POST,
            skippedCount = mediaItems.size - normalizedItems.size,
        )
        if (normalizedItems.isEmpty()) return 0
        return enqueueCreatePostUploadReal(
            context = context,
            mediaItems = normalizedItems,
            draft = draft,
            additionalAppMediaIds = additionalAppMediaIds,
            additionalAppCoverMediaId = additionalAppCoverMediaId,
        )
    }

    internal fun importSystemMediaToApp(
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val dedupedItems = deduplicateImportCandidates(mediaItems)
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.IMPORT_TO_APP,
            skippedCount = dedupedItems.skippedCount,
        )
        val normalizedItems = dedupedItems.items
        if (normalizedItems.isEmpty()) return 0
        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = null,
        )
        ImportOverlayStore.publishMutation(
            kind = MutationKind.OVERLAY_ONLY,
            mediaIds = normalizedItems.map { it.id },
        )
        normalizedItems.forEach { item ->
            ImportOverlayStore.rememberAppMediaIdForSource(item, item.id)
        }
        return normalizedItems.size
    }

    internal fun enqueueImportToAppUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        ImportOverlayStore.warmPersistentImportOverlay(context)
        val dedupedItems = deduplicateImportCandidates(mediaItems)
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.IMPORT_TO_APP,
            skippedCount = dedupedItems.skippedCount,
        )
        val normalizedItems = dedupedItems.items
        if (normalizedItems.isEmpty()) return 0
        return enqueueImportToAppUploadReal(
            context = context,
            mediaItems = normalizedItems,
        )
    }

    internal fun enqueueImportPickedMediaToAppUpload(
        context: Context,
        mediaUris: List<Uri>,
    ): Int {
        ImportOverlayStore.warmPersistentImportOverlay(context)
        val items = mediaUris.toPickedSystemMediaItems(context)
        if (items.isEmpty()) return 0
        return enqueueImportToAppUpload(
            context = context,
            mediaItems = items,
        )
    }

    internal fun buildImportPickedMediaPreview(
        context: Context,
        mediaUris: List<Uri>,
    ): SystemMediaImportPreview {
        ImportOverlayStore.warmPersistentImportOverlay(context)
        return buildImportToAppPreview(
            mediaItems = mediaUris.toPickedSystemMediaItems(context),
        )
    }

    internal fun addSystemMediaToExistingPost(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = normalizeSystemMedia(mediaItems)
            .filterNot { it.linkedSmallAlbumIds.contains(postId) || it.linkedPostIds.contains(postId) }
        val addedCount = FakeAlbumRepository.appendSystemMediaToPost(
            postId = postId,
            mediaItems = normalizedItems,
        )
        if (addedCount <= 0) return 0

        FakePhotoFeedRepository.importSystemMediaToFeed(
            mediaItems = normalizedItems,
            postId = postId,
        )
        ImportOverlayStore.linkMediaToPost(
            mediaIds = normalizedItems.map { it.id },
            postId = postId,
        )
        normalizedItems.forEach { item ->
            ImportOverlayStore.rememberAppMediaIdForSource(item, item.id)
        }
        return addedCount
    }

    internal fun enqueueAddToExistingPostUpload(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
        postTitle: String = "",
    ): Int {
        ImportOverlayStore.warmPersistentImportOverlay(context)
        val normalizedItems = normalizeSystemMedia(mediaItems)
            .filterNot { it.linkedSmallAlbumIds.contains(postId) || it.linkedPostIds.contains(postId) }
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.ADD_TO_EXISTING_POST,
            skippedCount = mediaItems.size - normalizeSystemMedia(mediaItems).size,
        )
        if (normalizedItems.isEmpty()) return 0
        return enqueueAddToExistingPostUploadReal(
            context = context,
            postId = postId,
            mediaItems = normalizedItems,
            postTitle = postTitle,
        )
    }

    internal fun buildImportToAppPreview(
        mediaItems: List<SystemMediaItem>,
    ): SystemMediaImportPreview {
        return buildSystemMediaImportPreview(
            mediaItems = mediaItems,
            preference = SettingsRepository.getSettingsState().mediaTimePreference,
            importedAtBaseMillis = System.currentTimeMillis(),
            knownAppMediaIdForSource = ImportOverlayStore::knownAppMediaIdForSource,
        )
    }

    internal fun pauseUploadTask(taskId: String) {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return
        if (!task.canPause) return
        updateUploadTask(
            taskId = taskId,
            state = UploadState.CANCELLED,
            progressPercent = task.progressPercent,
            statusMessage = "已暂停，可重试",
            canRetry = true,
        )
        cancelUploadJob(task)
        OperationBus.publishOperationSummaryIfReady(task.operationId)
        uploadScope.launch {
            RepositoryProvider.uploadRepository.cancelUpload(taskId)
        }
    }

    internal fun cancelUploadTask(taskId: String) {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return
        if (task.state == UploadState.SUCCESS || (task.state == UploadState.CANCELLED && !task.canRetry)) return
        updateUploadTask(
            taskId = taskId,
            state = UploadState.CANCELLED,
            progressPercent = task.progressPercent,
            statusMessage = "已取消",
            canRetry = false,
        )
        cancelUploadJob(task)
        OperationBus.publishOperationSummaryIfReady(task.operationId)
        uploadScope.launch {
            RepositoryProvider.uploadRepository.cancelUpload(taskId)
        }
    }

    internal fun cancelUploadOperation(operationId: String) {
        uploadTasksState
            .filter { task -> task.operationId == operationId && task.canCancel }
            .map { it.taskId }
            .forEach(::cancelUploadTask)
    }

    internal fun pauseUploadOperation(operationId: String) {
        uploadTasksState
            .filter { task -> task.operationId == operationId && task.canPause }
            .map { it.taskId }
            .forEach(::pauseUploadTask)
    }

    internal fun dismissUploadTask(taskId: String) {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return
        uploadTasksState.removeAll { it.taskId == taskId }
        cleanupOperationIfIdle(task.operationId)
        persistUploadTasks()
        uploadScope.launch {
            when (val result = RepositoryProvider.uploadRepository.dismissUpload(task.taskId)) {
                is ApiResult.Error -> debugUploadLog("dismiss failed task=${task.taskId}: ${result.message}", result.throwable)
                is ApiResult.Success,
                ApiResult.Loading,
                -> Unit
            }
        }
    }

    internal fun retryUploadTask(
        context: Context,
        taskId: String,
    ): Boolean {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return false
        val request = operationRequestsById[task.operationId] ?: return false
        val retryItem = request.mediaItems.firstOrNull { it.id == task.mediaId } ?: return false
        val existingPostRoute = uploadTasksState
            .firstOrNull { it.operationId == task.operationId && it.resultPostRoute != null }
            ?.resultPostRoute
        uploadJobsByTaskId.remove(taskId)?.cancel(CancellationException("Upload task retry requested"))
        uploadTasksState.removeAll { it.taskId == taskId }
        OperationBus.finalizedOperationIds.remove(task.operationId)
        OperationBus.publishedOperationSummaryIds.remove(task.operationId)
        OperationBus.publishedFirstSuccessOperationIds.remove(task.operationId)
        return when (request) {
            is ImportToAppOperationRequest -> {
                retryImportToAppUpload(
                    context = context,
                    operationId = task.operationId,
                    request = request,
                    retryItem = retryItem,
                )
            }
            is CreatePostOperationRequest -> {
                retryCreatePostUpload(
                    context = context,
                    operationId = task.operationId,
                    request = request,
                    retryItem = retryItem,
                    existingPostRoute = existingPostRoute,
                )
            }
            is AddToExistingPostOperationRequest -> {
                retryAddToExistingPostUpload(
                    context = context,
                    operationId = task.operationId,
                    request = request,
                    retryItem = retryItem,
                )
            }
        }
    }

    internal fun retryUploadOperation(
        context: Context,
        operationId: String,
    ): Int {
        val retryableTaskIds = uploadTasksState
            .filter { task -> task.operationId == operationId && task.canRetry }
            .map(SystemMediaUploadTaskUiModel::taskId)
        var retriedCount = 0
        retryableTaskIds.forEach { taskId ->
            if (retryUploadTask(context, taskId)) {
                retriedCount += 1
            }
        }
        return retriedCount
    }

    private fun retryImportToAppUpload(
        context: Context,
        operationId: String,
        request: ImportToAppOperationRequest,
        retryItem: SystemMediaItem,
    ): Boolean {
        enqueueRealUploadTask(
            context = context,
            operationId = operationId,
            mediaItem = retryItem,
            targetLabel = request.targetLabel,
            sourceItems = request.mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeImportToAppReal(uploadedMedia)
            },
        )
        return true
    }

    private fun retryCreatePostUpload(
        context: Context,
        operationId: String,
        request: CreatePostOperationRequest,
        retryItem: SystemMediaItem,
        existingPostRoute: PostDetailPlaceholderRoute?,
    ): Boolean {
        enqueueRealUploadTask(
            context = context,
            operationId = operationId,
            mediaItem = retryItem,
            targetLabel = request.targetLabel,
            sourceItems = listOf(retryItem),
            finalizeAction = { uploadedMedia ->
                if (existingPostRoute != null) {
                    finalizeAppendToPostReal(
                        postId = existingPostRoute.postId,
                        sourceItems = listOf(retryItem),
                        uploadedMedia = uploadedMedia,
                    )
                } else {
                    finalizeCreatePostReal(
                        draft = request.draft,
                        sourceItems = request.mediaItems,
                        uploadedMedia = uploadedMedia,
                        additionalAppMediaIds = request.additionalAppMediaIds,
                        additionalAppCoverMediaId = request.additionalAppCoverMediaId,
                    )
                }
            },
        )
        return true
    }

    private fun retryAddToExistingPostUpload(
        context: Context,
        operationId: String,
        request: AddToExistingPostOperationRequest,
        retryItem: SystemMediaItem,
    ): Boolean {
        enqueueRealUploadTask(
            context = context,
            operationId = operationId,
            mediaItem = retryItem,
            targetLabel = request.targetLabel,
            sourceItems = request.mediaItems,
            finalizeAction = { uploadedMedia ->
                finalizeAppendToPostReal(
                    postId = request.postId,
                    sourceItems = request.mediaItems,
                    uploadedMedia = uploadedMedia,
                )
            },
        )
        return true
    }

    internal fun rememberUploadedMediaId(
        operationId: String,
        sourceItem: SystemMediaItem,
        uploadedMediaId: String,
    ) {
        rememberUploadedMediaIds(
            operationId = operationId,
            sourceMediaIdsToUploadedMediaIds = mapOf(sourceItem.id to uploadedMediaId),
        )
        ImportOverlayStore.rememberAppMediaIdForSource(sourceItem, uploadedMediaId)
    }

    internal fun rememberUploadedMediaIds(
        operationId: String,
        sourceMediaIdsToUploadedMediaIds: Map<String, String>,
    ) {
        if (sourceMediaIdsToUploadedMediaIds.isEmpty()) return
        val uploadedIds = realUploadedMediaIdsByOperationId.getOrPut(operationId) { linkedMapOf() }
        sourceMediaIdsToUploadedMediaIds.forEach { (sourceMediaId, uploadedMediaId) ->
            if (sourceMediaId.isNotBlank() && uploadedMediaId.isNotBlank()) {
                uploadedIds[sourceMediaId] = uploadedMediaId
            }
        }
    }

    internal fun isUploadTaskCancelled(taskId: String): Boolean {
        return uploadTasksState.firstOrNull { it.taskId == taskId }?.state == UploadState.CANCELLED
    }

    internal fun cancelUploadJob(task: SystemMediaUploadTaskUiModel) {
        val fallbackTaskId = "${task.operationId}-${task.mediaId}"
        val cancellation = CancellationException("Upload task canceled by user")
        uploadJobsByTaskId.remove(task.taskId)?.cancel(cancellation)
        uploadJobsByTaskId.remove(fallbackTaskId)?.cancel(cancellation)
    }

    internal fun replaceUploadTaskId(
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
            updatedAtMillis = System.currentTimeMillis(),
        )
        persistUploadTasks()
        uploadJobsByTaskId.remove(oldTaskId)?.let { job ->
            uploadJobsByTaskId[newTaskId] = job
        }
        return true
    }

    internal fun updateUploadTask(
        taskId: String,
        state: UploadState,
        progressPercent: Int,
        statusMessage: String? = null,
        errorMessage: String? = null,
        canRetry: Boolean = false,
        resultMediaId: String? = null,
        previewUri: String? = null,
        thumbnailUrl: String? = null,
    ) {
        val currentIndex = uploadTasksState.indexOfFirst { it.taskId == taskId }
        if (currentIndex < 0) return
        val current = uploadTasksState[currentIndex]
        val nowMillis = System.currentTimeMillis()
        uploadTasksState[currentIndex] = current.copy(
            state = state,
            progressPercent = progressPercent,
            statusMessage = statusMessage ?: current.statusMessage,
            errorMessage = errorMessage,
            canRetry = canRetry,
            resultMediaId = resultMediaId ?: current.resultMediaId,
            previewUri = previewUri ?: current.previewUri,
            thumbnailUrl = thumbnailUrl ?: current.thumbnailUrl,
            updatedAtMillis = nowMillis,
            completedAtMillis = if (state.isTerminalUploadState()) current.completedAtMillis ?: nowMillis else current.completedAtMillis,
        )
        refreshOperationTaskMeta(current.operationId)
    }

    internal fun updateOperationTasks(
        operationId: String,
        state: UploadState,
        statusMessage: String,
        errorMessage: String? = null,
        canRetry: Boolean = false,
        resultPostRoute: PostDetailPlaceholderRoute? = null,
    ) {
        uploadTasksState.indices.forEach { index ->
            val task = uploadTasksState[index]
            if (task.operationId == operationId) {
                uploadTasksState[index] = task.copy(
                    state = state,
                    progressPercent = if (state == UploadState.FAILED || state == UploadState.CANCELLED) {
                        task.progressPercent.coerceAtLeast(0)
                    } else {
                        task.progressPercent.coerceAtLeast(100)
                    },
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    canRetry = canRetry,
                    resultPostRoute = resultPostRoute ?: task.resultPostRoute,
                    updatedAtMillis = System.currentTimeMillis(),
                    completedAtMillis = if (state.isTerminalUploadState()) task.completedAtMillis ?: System.currentTimeMillis() else task.completedAtMillis,
                )
            }
        }
        refreshOperationTaskMeta(operationId, resultPostRoute = resultPostRoute)
    }

    internal fun updateSuccessfulOperationTasks(
        operationId: String,
        state: UploadState,
        statusMessage: String,
        errorMessage: String? = null,
        canRetry: Boolean = false,
        resultPostRoute: PostDetailPlaceholderRoute? = null,
    ) {
        uploadTasksState.indices.forEach { index ->
            val task = uploadTasksState[index]
            if (task.operationId == operationId && task.resultMediaId?.isNotBlank() == true) {
                uploadTasksState[index] = task.copy(
                    state = state,
                    progressPercent = if (state == UploadState.FAILED || state == UploadState.CANCELLED) {
                        task.progressPercent.coerceAtLeast(0)
                    } else {
                        task.progressPercent.coerceAtLeast(100)
                    },
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    canRetry = canRetry,
                    resultPostRoute = resultPostRoute ?: task.resultPostRoute,
                    updatedAtMillis = System.currentTimeMillis(),
                    completedAtMillis = if (state.isTerminalUploadState()) task.completedAtMillis ?: System.currentTimeMillis() else task.completedAtMillis,
                )
            }
        }
        refreshOperationTaskMeta(operationId, resultPostRoute = resultPostRoute)
    }

    internal fun markFailedOperationTasksAfterPartialPost(operationId: String) {
        uploadTasksState.indices.forEach { index ->
            val task = uploadTasksState[index]
            if (
                task.operationId == operationId &&
                (task.state == UploadState.FAILED || task.state == UploadState.CANCELLED)
            ) {
                uploadTasksState[index] = task.copy(
                    statusMessage = "未加入新小相册",
                    errorMessage = task.errorMessage ?: "该媒体未上传成功，可稍后重试。",
                    canRetry = true,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
        }
        refreshOperationTaskMeta(operationId)
    }

    internal fun addFailedUploadTask(
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
                state = UploadState.FAILED,
                statusMessage = statusMessage,
                errorMessage = errorMessage,
                canRetry = true,
            ),
        )
    }

    internal fun refreshOperationTaskMeta(
        operationId: String,
        resultPostRoute: PostDetailPlaceholderRoute? = null,
    ) {
        val meta = OperationBus.operationTaskMeta(operationId)
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        val successCount = operationTasks.count { it.state == UploadState.SUCCESS }
        val failureCount = operationTasks.count { it.state == UploadState.FAILED }
        val cancelledCount = operationTasks.count { it.state == UploadState.CANCELLED }
        uploadTasksState.indices.forEach { index ->
            val task = uploadTasksState[index]
            if (task.operationId == operationId) {
                uploadTasksState[index] = task.copy(
                    operationType = meta.operationType,
                    targetLabel = meta.targetLabel,
                    operationTitle = meta.operationTitle,
                    operationMediaCount = meta.mediaCount,
                    operationSuccessCount = successCount,
                    operationFailureCount = failureCount,
                    operationCancelledCount = cancelledCount,
                    resultPostRoute = resultPostRoute ?: task.resultPostRoute,
                )
            }
        }
        persistUploadTasks()
    }

    internal fun clearOperationState(
        operationId: String,
        keepRequest: Boolean,
    ) {
        uploadTasksState.removeAll { it.operationId == operationId }
        OperationBus.finalizedOperationIds.remove(operationId)
        OperationBus.publishedOperationSummaryIds.remove(operationId)
        OperationBus.publishedFirstSuccessOperationIds.remove(operationId)
        realUploadedMediaIdsByOperationId.remove(operationId)
        if (!keepRequest) {
            operationRequestsById.remove(operationId)
        }
        persistUploadTasks()
    }

    internal fun cleanupOperationIfIdle(operationId: String) {
        if (uploadTasksState.none { it.operationId == operationId }) {
            clearOperationState(operationId, keepRequest = false)
        }
    }

    internal fun persistUploadTasks() {
        val prefs = uploadTaskPreferences ?: return
        val array = JSONArray()
        uploadTasksState
            .filterNot { task ->
                task.isTerminal &&
                    task.resultMediaId.isNullOrBlank() &&
                    task.errorMessage.isNullOrBlank() &&
                    task.statusMessage.isNullOrBlank()
            }
            .forEach { task ->
                array.put(task.toPersistedUploadTaskJson())
            }
        prefs.edit().putString(UploadTasksItemsKey, array.toString()).apply()
    }

    private fun JSONObject.toPersistedUploadTask(): SystemMediaUploadTaskUiModel? {
        val cutoffMillis = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        val updatedAtMillis = optLong("updatedAtMillis", System.currentTimeMillis())
        if (updatedAtMillis < cutoffMillis) return null
        val taskId = optString("taskId").takeIf { it.isNotBlank() } ?: return null
        val operationId = optString("operationId").takeIf { it.isNotBlank() } ?: return null
        val mediaId = optString("mediaId").takeIf { it.isNotBlank() } ?: taskId
        val restoredState = optString("state").toPersistedUploadState()
        val state = if (restoredState == UploadState.WAITING || restoredState == UploadState.UPLOADING) {
            UploadState.CANCELLED
        } else {
            restoredState
        }
        val resumedMessage = if (state == UploadState.CANCELLED && restoredState != UploadState.CANCELLED) {
            "应用关闭后已暂停，可重新选择媒体继续上传"
        } else {
            optString("statusMessage").takeIf { it.isNotBlank() }
        }
        return SystemMediaUploadTaskUiModel(
            taskId = taskId,
            operationId = operationId,
            mediaId = mediaId,
            fileName = optString("fileName").takeIf { it.isNotBlank() } ?: mediaId,
            targetLabel = optString("targetLabel").takeIf { it.isNotBlank() } ?: "传输任务",
            mediaType = runCatching {
                SystemMediaType.valueOf(optString("mediaType").ifBlank { SystemMediaType.IMAGE.name })
            }.getOrDefault(SystemMediaType.IMAGE),
            previewUri = optString("previewUri").takeIf { it.isNotBlank() },
            thumbnailUrl = optString("thumbnailUrl").takeIf { it.isNotBlank() },
            resultMediaId = optString("resultMediaId").takeIf { it.isNotBlank() },
            progressPercent = if (state.isTerminalUploadState()) {
                optInt("progressPercent", 100).coerceIn(0, 100)
            } else {
                optInt("progressPercent", 0).coerceIn(0, 100)
            },
            state = state,
            statusMessage = resumedMessage,
            errorMessage = optString("errorMessage").takeIf { it.isNotBlank() },
            canRetry = optBoolean("canRetry", false) &&
                restoredState != UploadState.SUCCESS &&
                operationRequestsById.containsKey(operationId),
            operationType = runCatching {
                OperationType.valueOf(optString("operationType").ifBlank { OperationType.IMPORT_TO_APP.name })
            }.getOrDefault(OperationType.IMPORT_TO_APP),
            operationTitle = optString("operationTitle").takeIf { it.isNotBlank() },
            operationMediaCount = optInt("operationMediaCount", 1).coerceAtLeast(1),
            operationSuccessCount = optInt("operationSuccessCount", 0).coerceAtLeast(0),
            operationFailureCount = optInt("operationFailureCount", 0).coerceAtLeast(0),
            operationCancelledCount = optInt("operationCancelledCount", 0).coerceAtLeast(0),
            createdAtMillis = optLong("createdAtMillis", updatedAtMillis),
            updatedAtMillis = updatedAtMillis,
            completedAtMillis = optLong("completedAtMillis", 0L).takeIf { it > 0L },
        )
    }
}

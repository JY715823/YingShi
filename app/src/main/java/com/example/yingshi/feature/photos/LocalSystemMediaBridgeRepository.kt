package com.example.yingshi.feature.photos

import android.content.Context
import android.content.SharedPreferences
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
import com.example.yingshi.data.model.RemoteUploadTask
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

object LocalSystemMediaBridgeRepository {
    private const val UploadLogTag = "SystemMediaUpload"
    private const val ReadLocalMediaTimeoutMillis = 180_000L
    private const val CreateUploadTokenTimeoutMillis = 15_000L
    private const val UploadFileTimeoutMillis = 10 * 60_000L
    private const val MaxConcurrentRealUploads = 2
    private const val ImportOverlayPreferencesName = "system_media_import_overlay"
    private const val ImportOverlayItemsKey = "items_json"
    private const val UploadTasksPreferencesName = "system_media_upload_tasks"
    private const val UploadTasksItemsKey = "tasks_json"

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
        val cancelledCount: Int = 0,
        val totalCount: Int = 0,
        val shouldAutoOpenResult: Boolean = false,
    )

    private data class RealUploadMetadata(
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
    )

    private data class UploadedOperationMedia(
        val orderedUploadedMediaIds: List<String>,
        val uploadedMediaIdBySourceId: Map<String, String>,
    )

    private data class OperationTaskMeta(
        val operationType: OperationType,
        val targetLabel: String,
        val operationTitle: String,
        val mediaCount: Int,
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
        val additionalAppMediaIds: List<String> = emptyList(),
        val additionalAppCoverMediaId: String? = null,
    ) : PendingOperationRequest {
        override val operationType: OperationType = OperationType.CREATE_POST
        override val targetLabel: String = "Create post"
    }

    private data class ImportToAppOperationRequest(
        override val mediaItems: List<SystemMediaItem>,
    ) : PendingOperationRequest {
        override val operationType: OperationType = OperationType.IMPORT_TO_APP
        override val targetLabel: String = "导入照片流"
    }

    private data class AddToExistingPostOperationRequest(
        val postId: String,
        val postTitle: String = "",
        override val mediaItems: List<SystemMediaItem>,
    ) : PendingOperationRequest {
        override val operationType: OperationType = OperationType.ADD_TO_EXISTING_POST
        override val targetLabel: String = if (postTitle.isBlank()) "Add to post" else "Add to $postTitle"
    }
    var mutationVersion by mutableIntStateOf(0)
        private set
    var latestMutationEvent by mutableStateOf(MutationEvent())
        private set

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val uploadSemaphore = Semaphore(MaxConcurrentRealUploads)
    private val uploadTasksState = mutableStateListOf<SystemMediaUploadTaskUiModel>()
    private val operationResultsState = mutableStateListOf<OperationResultEvent>()
    private val finalizedOperationIds = linkedSetOf<String>()
    private val publishedOperationSummaryIds = linkedSetOf<String>()
    private val hiddenMediaIds = linkedSetOf<String>()
    private val invalidatedAppMediaIds = linkedSetOf<String>()
    private val linkedPostIdsByMediaId = linkedMapOf<String, LinkedHashSet<String>>()
    private val realUploadedMediaIdsByOperationId = linkedMapOf<String, LinkedHashMap<String, String>>()
    private val appMediaIdBySystemSourceKey = linkedMapOf<String, String>()
    private val operationRequestsById = linkedMapOf<String, PendingOperationRequest>()
    private val uploadJobsByTaskId = linkedMapOf<String, Job>()
    private val publishedFirstSuccessOperationIds = linkedSetOf<String>()
    private var overlayPreferences: SharedPreferences? = null
    private var uploadTaskPreferences: SharedPreferences? = null
    private var persistentOverlayLoaded = false
    private var persistentUploadTasksLoaded = false

    val uploadTasks: List<SystemMediaUploadTaskUiModel>
        get() = uploadTasksState

    val operationResults: List<OperationResultEvent>
        get() = operationResultsState

    fun warmPersistentTransferCenter(context: Context) {
        warmPersistentUploadTasks(context)
        refreshRemoteUploadHistory()
    }

    fun refreshRemoteUploadHistory() {
        if (RepositoryProvider.currentMode != RepositoryMode.REAL) return
        uploadScope.launch {
            when (val result = RepositoryProvider.uploadRepository.getUploadHistory(pageSize = 100)) {
                is ApiResult.Success -> mergeRemoteUploadHistory(result.data)
                is ApiResult.Error -> debugUploadLog("history refresh failed: ${result.message}", result.throwable)
                ApiResult.Loading -> Unit
            }
        }
    }

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
                val importedMediaId = knownAppMediaIdForSource(item)
                    ?: item.importedAppMediaId?.takeIf { it.isNotBlank() && it !in invalidatedAppMediaIds }
                val linkedPostIds = linkedPostIdsByMediaId[item.id]
                    ?: importedMediaId?.let { linkedPostIdsByMediaId[it] }
                item.copy(
                    importedAppMediaId = importedMediaId,
                    linkedSmallAlbumIds = linkedPostIds?.toList() ?: item.linkedSmallAlbumIds,
                    linkedPostIds = linkedPostIds?.toList() ?: item.linkedPostIds,
                )
            }
    }

    fun warmPersistentImportOverlay(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            ImportOverlayPreferencesName,
            Context.MODE_PRIVATE,
        )
        overlayPreferences = prefs
        warmPersistentUploadTasks(context)
        if (persistentOverlayLoaded) return
        persistentOverlayLoaded = true
        val raw = prefs.getString(ImportOverlayItemsKey, null)?.takeIf { it.isNotBlank() }
            ?: return
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val sourceKey = entry.optString("sourceKey").takeIf { it.isNotBlank() } ?: continue
                val appMediaId = entry.optString("appMediaId").takeIf { it.isNotBlank() } ?: continue
                val smallAlbumIds = entry.optJSONArray("smallAlbumIds").toStringSet()
                appMediaIdBySystemSourceKey[sourceKey] = appMediaId
                if (smallAlbumIds.isNotEmpty()) {
                    linkedPostIdsByMediaId[appMediaId] = linkedSetOf<String>().apply {
                        addAll(smallAlbumIds)
                    }
                }
            }
        }
    }

    private fun warmPersistentUploadTasks(context: Context) {
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

    private fun mergeRemoteUploadHistory(tasks: List<RemoteUploadTask>) {
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

    private fun RemoteUploadTask.toUploadTaskUiModel(): SystemMediaUploadTaskUiModel {
        val type = if (mediaType.equals("video", ignoreCase = true)) {
            SystemMediaType.VIDEO
        } else {
            SystemMediaType.IMAGE
        }
        val opType = runCatching {
            OperationType.valueOf(operationType.orEmpty().ifBlank { OperationType.IMPORT_TO_APP.name })
        }.getOrDefault(OperationType.IMPORT_TO_APP)
        return SystemMediaUploadTaskUiModel(
            taskId = uploadId,
            operationId = operationId?.takeIf { it.isNotBlank() } ?: uploadId,
            mediaId = sourceItemId?.takeIf { it.isNotBlank() } ?: mediaId ?: uploadId,
            fileName = fileName,
            targetLabel = opType.defaultTargetLabel(),
            mediaType = type,
            thumbnailUrl = media?.coverUrl
                ?: media?.previewUrl
                ?: media?.thumbnailUrl
                ?: media?.mediaUrl,
            resultMediaId = mediaId,
            progressPercent = progressPercent.coerceIn(0, 100),
            state = state,
            statusMessage = when (state) {
                UploadState.WAITING -> "等待上传"
                UploadState.UPLOADING -> "正在上传 $progressPercent%"
                UploadState.SUCCESS -> "上传完成"
                UploadState.FAILURE -> "上传失败"
                UploadState.CANCELLED -> "已取消"
            },
            errorMessage = errorMessage,
            canRetry = false,
            operationType = opType,
            operationTitle = operationTitle,
            operationMediaCount = operationMediaCount ?: 1,
            createdAtMillis = createdAtMillis ?: updatedAtMillis ?: completedAtMillis ?: System.currentTimeMillis(),
            updatedAtMillis = updatedAtMillis ?: completedAtMillis ?: createdAtMillis ?: System.currentTimeMillis(),
            completedAtMillis = completedAtMillis,
        )
    }

    private fun OperationType.defaultTargetLabel(): String {
        return when (this) {
            OperationType.IMPORT_TO_APP -> "导入照片流"
            OperationType.CREATE_POST -> "新建小相册"
            OperationType.ADD_TO_EXISTING_POST -> "加入已有小相册"
        }
    }

    private fun UploadState.isActivelyUploading(): Boolean {
        return this == UploadState.WAITING || this == UploadState.UPLOADING
    }

    fun rememberImportStatus(
        item: SystemMediaItem,
        appMediaId: String,
        smallAlbumIds: List<String>,
    ) {
        rememberAppMediaIdForSource(item, appMediaId)
        linkedPostIdsByMediaId[item.id] = linkedSetOf<String>().apply {
            addAll(smallAlbumIds.filter { it.isNotBlank() })
        }
        linkedPostIdsByMediaId[appMediaId] = linkedSetOf<String>().apply {
            addAll(smallAlbumIds.filter { it.isNotBlank() })
        }
        invalidatedAppMediaIds.remove(appMediaId)
        persistImportOverlay()
        publishMutation(MutationKind.OVERLAY_ONLY, setOf(item.id))
    }

    fun forgetImportStatus(item: SystemMediaItem) {
        var changed = false
        item.stableImportSourceKeys().forEach { sourceKey ->
            if (appMediaIdBySystemSourceKey.remove(sourceKey) != null) {
                changed = true
            }
        }
        if (changed) {
            persistImportOverlay()
            publishMutation(MutationKind.OVERLAY_ONLY, setOf(item.id))
        }
    }

    fun forgetImportStatusByAppMediaId(appMediaId: String) {
        if (appMediaId.isBlank()) return
        invalidatedAppMediaIds += appMediaId
        val keysToRemove = appMediaIdBySystemSourceKey.entries
            .filter { it.value == appMediaId }
            .map { it.key }
        linkedPostIdsByMediaId.remove(appMediaId)
        if (keysToRemove.isEmpty()) {
            publishMutation(MutationKind.OVERLAY_ONLY)
            return
        }
        keysToRemove.forEach { appMediaIdBySystemSourceKey.remove(it) }
        linkedPostIdsByMediaId.entries.removeAll { (mediaId, _) ->
            mediaId == appMediaId || mediaId in keysToRemove
        }
        persistImportOverlay()
        publishMutation(MutationKind.OVERLAY_ONLY)
    }

    fun forgetImportStatusByAppMediaIds(appMediaIds: Collection<String>) {
        appMediaIds.filter { it.isNotBlank() }.distinct().forEach(::forgetImportStatusByAppMediaId)
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
        linkMediaToPost(
            mediaIds = normalizedItems.map { it.id } + additionalAppMediaItems.map { it.mediaId },
            postId = post.id,
        )
        normalizedItems.forEach { item ->
            rememberAppMediaIdForSource(item, item.id)
        }
        additionalAppMediaItems.forEach { item ->
            linkMediaToPost(
                mediaIds = listOf(item.mediaId),
                postId = post.id,
            )
        }
        return post
    }

    fun enqueueCreatePostUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
        draft: CreatePostDraft = defaultCreatePostDraft(mediaItems),
        additionalAppMediaIds: List<String> = emptyList(),
        additionalAppCoverMediaId: String? = null,
    ): Int {
        warmPersistentImportOverlay(context)
        val normalizedItems = normalizeSystemMedia(mediaItems)
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.CREATE_POST,
            skippedCount = mediaItems.size - normalizedItems.size,
        )
        if (normalizedItems.isEmpty()) return 0
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            enqueueCreatePostUploadReal(
                context = context,
                mediaItems = normalizedItems,
                draft = draft,
                additionalAppMediaIds = additionalAppMediaIds,
                additionalAppCoverMediaId = additionalAppCoverMediaId,
            )
        } else {
            enqueueCreatePostUploadFake(
                mediaItems = normalizedItems,
                draft = draft,
                additionalAppMediaIds = additionalAppMediaIds,
                additionalAppCoverMediaId = additionalAppCoverMediaId,
            )
        }
    }

    fun importSystemMediaToApp(
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
        publishMutation(
            kind = MutationKind.OVERLAY_ONLY,
            mediaIds = normalizedItems.map { it.id },
        )
        normalizedItems.forEach { item ->
            rememberAppMediaIdForSource(item, item.id)
        }
        return normalizedItems.size
    }

    fun enqueueImportToAppUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        warmPersistentImportOverlay(context)
        val dedupedItems = deduplicateImportCandidates(mediaItems)
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.IMPORT_TO_APP,
            skippedCount = dedupedItems.skippedCount,
        )
        val normalizedItems = dedupedItems.items
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
        warmPersistentImportOverlay(context)
        val items = mediaUris.toPickedSystemMediaItems(context)
        if (items.isEmpty()) return 0
        return enqueueImportToAppUpload(
            context = context,
            mediaItems = items,
        )
    }

    fun buildImportPickedMediaPreview(
        context: Context,
        mediaUris: List<Uri>,
    ): SystemMediaImportPreview {
        warmPersistentImportOverlay(context)
        return buildImportToAppPreview(
            mediaItems = mediaUris.toPickedSystemMediaItems(context),
        )
    }

    fun addSystemMediaToExistingPost(
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
        linkMediaToPost(
            mediaIds = normalizedItems.map { it.id },
            postId = postId,
        )
        normalizedItems.forEach { item ->
            rememberAppMediaIdForSource(item, item.id)
        }
        return addedCount
    }

    fun enqueueAddToExistingPostUpload(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
        postTitle: String = "",
    ): Int {
        warmPersistentImportOverlay(context)
        val normalizedItems = normalizeSystemMedia(mediaItems)
            .filterNot { it.linkedSmallAlbumIds.contains(postId) || it.linkedPostIds.contains(postId) }
        publishDuplicateNoticeIfNeeded(
            operationType = OperationType.ADD_TO_EXISTING_POST,
            skippedCount = mediaItems.size - normalizeSystemMedia(mediaItems).size,
        )
        if (normalizedItems.isEmpty()) return 0
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            enqueueAddToExistingPostUploadReal(
                context = context,
                postId = postId,
                mediaItems = normalizedItems,
                postTitle = postTitle,
            )
        } else {
            enqueueAddToExistingPostUploadFake(
                postId = postId,
                mediaItems = normalizedItems,
                postTitle = postTitle,
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

    fun pauseUploadTask(taskId: String) {
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
        publishOperationSummaryIfReady(task.operationId)
        uploadScope.launch {
            RepositoryProvider.uploadRepository.cancelUpload(taskId)
        }
    }

    fun cancelUploadTask(taskId: String) {
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
        publishOperationSummaryIfReady(task.operationId)
        uploadScope.launch {
            RepositoryProvider.uploadRepository.cancelUpload(taskId)
        }
    }

    fun buildImportToAppPreview(
        mediaItems: List<SystemMediaItem>,
    ): SystemMediaImportPreview {
        return buildSystemMediaImportPreview(
            mediaItems = mediaItems,
            preference = SettingsRepository.getSettingsState().mediaTimePreference,
            importedAtBaseMillis = System.currentTimeMillis(),
            knownAppMediaIdForSource = ::knownAppMediaIdForSource,
        )
    }

    fun cancelUploadOperation(operationId: String) {
        uploadTasksState
            .filter { task -> task.operationId == operationId && task.canCancel }
            .map { it.taskId }
            .forEach(::cancelUploadTask)
    }

    fun pauseUploadOperation(operationId: String) {
        uploadTasksState
            .filter { task -> task.operationId == operationId && task.canPause }
            .map { it.taskId }
            .forEach(::pauseUploadTask)
    }

    fun dismissUploadTask(taskId: String) {
        val task = uploadTasksState.firstOrNull { it.taskId == taskId } ?: return
        uploadTasksState.removeAll { it.taskId == taskId }
        cleanupOperationIfIdle(task.operationId)
        persistUploadTasks()
        if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            uploadScope.launch {
                when (val result = RepositoryProvider.uploadRepository.dismissUpload(task.taskId)) {
                    is ApiResult.Error -> debugUploadLog("dismiss failed task=${task.taskId}: ${result.message}", result.throwable)
                    is ApiResult.Success,
                    ApiResult.Loading,
                    -> Unit
                }
            }
        }
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
        val retryItem = request.mediaItems.firstOrNull { it.id == task.mediaId } ?: return false
        val existingPostRoute = uploadTasksState
            .firstOrNull { it.operationId == task.operationId && it.resultPostRoute != null }
            ?.resultPostRoute
        uploadJobsByTaskId.remove(taskId)?.cancel(CancellationException("Upload task retry requested"))
        uploadTasksState.removeAll { it.taskId == taskId }
        finalizedOperationIds.remove(task.operationId)
        publishedOperationSummaryIds.remove(task.operationId)
        publishedFirstSuccessOperationIds.remove(task.operationId)
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

    fun retryUploadOperation(
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
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
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
            true
        } else {
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = retryItem,
                targetLabel = request.targetLabel,
                onOperationSuccess = {
                    val importedCount = importSystemMediaToApp(request.mediaItems)
                    OperationResultEvent(
                        eventId = if (importedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.IMPORT_TO_APP,
                        succeeded = importedCount > 0,
                        message = if (importedCount > 0) {
                            "导入完成"
                        } else {
                            "没有可导入的媒体"
                        },
                    )
                },
            )
            true
        }
    }

    private fun retryCreatePostUpload(
        context: Context,
        operationId: String,
        request: CreatePostOperationRequest,
        retryItem: SystemMediaItem,
        existingPostRoute: PostDetailPlaceholderRoute?,
    ): Boolean {
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
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
            true
        } else {
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = retryItem,
                targetLabel = request.targetLabel,
                onOperationSuccess = {
                    val createdPost = createPostFromSystemMediaDraft(
                        draft = request.draft,
                        mediaItems = request.mediaItems,
                        additionalAppMediaItems = request.additionalAppMediaIds
                            .mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem),
                    )
                    if (createdPost == null) {
                        OperationResultEvent(
                            eventId = "$operationId-failure",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = false,
                            message = "小相册创建失败，可重试。",
                        )
                    } else {
                        OperationResultEvent(
                            eventId = "$operationId-success",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = true,
                            message = "小相册创建完成",
                            postRoute = FakeAlbumRepository.toPostDetailRoute(createdPost),
                        )
                    }
                },
            )
            true
        }
    }

    private fun retryAddToExistingPostUpload(
        context: Context,
        operationId: String,
        request: AddToExistingPostOperationRequest,
        retryItem: SystemMediaItem,
    ): Boolean {
        return if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
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
            true
        } else {
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = retryItem,
                targetLabel = request.targetLabel,
                onOperationSuccess = {
                    val addedCount = addSystemMediaToExistingPost(request.postId, request.mediaItems)
                    val postRoute = FakeAlbumRepository.getPost(request.postId)
                        ?.let(FakeAlbumRepository::toPostDetailRoute)
                    OperationResultEvent(
                        eventId = if (addedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.ADD_TO_EXISTING_POST,
                        succeeded = addedCount > 0,
                        message = if (addedCount > 0) {
                            "已加入小相册"
                        } else {
                            "这些媒体已在目标小相册中"
                        },
                        postRoute = postRoute.takeIf { addedCount > 0 },
                    )
                },
            )
            true
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
                targetLabel = "导入照片流",
                onOperationSuccess = {
                    val importedCount = importSystemMediaToApp(mediaItems)
                    OperationResultEvent(
                        eventId = if (importedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.IMPORT_TO_APP,
                        succeeded = importedCount > 0,
                        message = if (importedCount > 0) {
                            "导入完成"
                        } else {
                            "没有可导入的媒体"
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
        additionalAppMediaIds: List<String>,
        additionalAppCoverMediaId: String?,
    ): Int {
        val additionalAppItems = additionalAppMediaIds.distinct().mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem)
        val finalDraft = draft.copy(coverSourceMediaId = draft.coverSourceMediaId ?: additionalAppCoverMediaId)
        val operationId = "create-post-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = CreatePostOperationRequest(
            draft = finalDraft,
            mediaItems = mediaItems,
            additionalAppMediaIds = additionalAppMediaIds.distinct(),
            additionalAppCoverMediaId = additionalAppCoverMediaId,
        )
        mediaItems.forEach { item ->
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "新建小相册",
                onOperationSuccess = {
                    val createdPost = createPostFromSystemMediaDraft(
                        draft = finalDraft,
                        mediaItems = mediaItems,
                        additionalAppMediaItems = additionalAppItems,
                    )
                    if (createdPost == null) {
                        OperationResultEvent(
                            eventId = "$operationId-failure",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = false,
                            message = "小相册创建失败，可重试。",
                        )
                    } else {
                        OperationResultEvent(
                            eventId = "$operationId-success",
                            operationId = operationId,
                            operationType = OperationType.CREATE_POST,
                            succeeded = true,
                            message = "小相册创建完成",
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
        postTitle: String,
    ): Int {
        val operationId = "append-post-$postId-${System.currentTimeMillis()}"
        operationRequestsById[operationId] = AddToExistingPostOperationRequest(
            postId = postId,
            postTitle = postTitle.ifBlank { FakeAlbumRepository.getPost(postId)?.title.orEmpty() },
            mediaItems = mediaItems,
        )
        mediaItems.forEach { item ->
            enqueueFakeUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "加入已有小相册",
                onOperationSuccess = {
                    val addedCount = addSystemMediaToExistingPost(postId, mediaItems)
                    val postRoute = FakeAlbumRepository.getPost(postId)
                        ?.let(FakeAlbumRepository::toPostDetailRoute)
                    OperationResultEvent(
                        eventId = if (addedCount > 0) "$operationId-success" else "$operationId-failure",
                        operationId = operationId,
                        operationType = OperationType.ADD_TO_EXISTING_POST,
                        succeeded = addedCount > 0,
                        message = if (addedCount > 0) {
                            "已加入小相册"
                        } else {
                            "这些媒体已在目标小相册中"
                        },
                        postRoute = postRoute.takeIf { addedCount > 0 },
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
                resolvedCreateUploadTokenPayload(
                    fileName = mediaItem.displayName.ifBlank { "${mediaItem.id}.jpg" },
                    mimeType = fakeMimeType(mediaItem.type),
                    fileSizeBytes = fakeFileSizeBytes(mediaItem),
                    mediaType = mediaItem.type.name.lowercase(),
                    width = mediaItem.width?.coerceAtLeast(1) ?: 1,
                    height = mediaItem.height?.coerceAtLeast(1) ?: 1,
                    durationMillis = null,
                    mediaItem = mediaItem,
                    operationId = operationId,
                    operationType = operationRequestsById[operationId]?.operationType,
                    operationTitle = operationTaskMeta(operationId).operationTitle,
                    operationMediaCount = operationTaskMeta(operationId).mediaCount,
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
                            statusMessage = "上传失败",
                            errorMessage = tokenResult.message,
                            canRetry = true,
                        ),
                    )
                    refreshOperationTaskMeta(operationId)
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
                    statusMessage = "等待上传",
                ),
            )
            refreshOperationTaskMeta(operationId)

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
                    updatedAtMillis = System.currentTimeMillis(),
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
                        statusMessage = "上传失败",
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
                targetLabel = "导入照片流",
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
        additionalAppMediaIds: List<String>,
        additionalAppCoverMediaId: String?,
    ): Int {
        val operationId = "real-create-post-${System.currentTimeMillis()}"
        val normalizedAdditionalAppMediaIds = additionalAppMediaIds.distinct()
        val finalDraft = draft.copy(coverSourceMediaId = draft.coverSourceMediaId ?: additionalAppCoverMediaId)
        val reusableMediaIdsBySourceId = reusableAppMediaIdsBySourceId(mediaItems)
        val uploadItems = mediaItems.filterNot { reusableMediaIdsBySourceId.containsKey(it.id) }
        operationRequestsById[operationId] = CreatePostOperationRequest(
            draft = finalDraft,
            mediaItems = mediaItems,
            additionalAppMediaIds = normalizedAdditionalAppMediaIds,
            additionalAppCoverMediaId = additionalAppCoverMediaId,
        )
        rememberUploadedMediaIds(
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

    private fun enqueueAddToExistingPostUploadReal(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
        postTitle: String,
    ): Int {
        val operationId = "real-append-post-$postId-${System.currentTimeMillis()}"
        val reusableMediaIdsBySourceId = reusableAppMediaIdsBySourceId(mediaItems)
        val uploadItems = mediaItems.filterNot { reusableMediaIdsBySourceId.containsKey(it.id) }
        operationRequestsById[operationId] = AddToExistingPostOperationRequest(
            postId = postId,
            postTitle = postTitle,
            mediaItems = mediaItems,
        )
        rememberUploadedMediaIds(
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
        refreshOperationTaskMeta(operationId)

        val job = uploadScope.launch {
            var activeTaskId = initialTaskId
            var uploadPermitAcquired = false
            try {
                uploadSemaphore.acquire()
                uploadPermitAcquired = true
                if (isUploadTaskCancelled(activeTaskId)) return@launch
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
                        fallback = "无法读取所选媒体，请重新选择。",
                    )
                    addFailedUploadTask(
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
                    publishOperationSummaryIfReady(operationId)
                    debugUploadLog("read failed operation=$operationId mediaId=${mediaItem.id}: $message", throwable)
                    return@launch
                }
                if (isUploadTaskCancelled(activeTaskId)) return@launch

                debugUploadLog(
                    "metadata operation=$operationId uri=${metadata.sourceUri} file=${metadata.fileName} " +
                        "mime=${metadata.mimeType} size=${metadata.fileSizeBytes} " +
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
                    operationType = operationRequestsById[operationId]?.operationType?.name,
                    operationTitle = operationTaskMeta(operationId).operationTitle,
                    operationMediaCount = operationTaskMeta(operationId).mediaCount,
                    sourceItemId = mediaItem.id,
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
                        addFailedUploadTask(
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
                            uploadScope.launch {
                                if (!isUploadTaskCancelled(uploadId)) {
                                    val message = if (progress >= 100) "服务器正在确认接收" else "正在上传 $mappedProgress%"
                                    updateUploadTask(
                                        taskId = uploadId,
                                        state = UploadState.UPLOADING,
                                        progressPercent = mappedProgress,
                                        statusMessage = message,
                                    )
                                }
                            }
                        },
                        shouldCancel = { isUploadTaskCancelled(uploadId) },
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
                            progressPercent = 99,
                            statusMessage = "上传完成，正在处理",
                        )
                        val uploadedMediaId = uploadResult.data.mediaId
                        if (uploadedMediaId.isBlank()) {
                            val message = "上传已完成，但服务器没有返回媒体编号。"
                            updateUploadTask(
                                taskId = uploadId,
                                state = UploadState.FAILURE,
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
                            thumbnailUrl = uploadResult.data.coverUrl
                                ?: uploadResult.data.previewUrl
                                ?: uploadResult.data.thumbnailUrl
                                ?: uploadResult.data.mediaUrl,
                        )
                        rememberUploadedMediaId(
                            operationId = operationId,
                            sourceItem = mediaItem,
                            uploadedMediaId = uploadedMediaId,
                        )
                        publishMutation(
                            kind = MutationKind.OVERLAY_ONLY,
                            mediaIds = listOf(mediaItem.id),
                        )
                        notifyRealBackendContentChanged(mediaIds = setOf(uploadedMediaId))
                        publishFirstSuccessIfNeeded(
                            operationId = operationId,
                            operationType = operationRequestsById[operationId]?.operationType ?: OperationType.IMPORT_TO_APP,
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
                        publishOperationSummaryIfReady(operationId)
                    }
                    is ApiResult.Error -> {
                        if (uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
                            return@launch
                        }
                        val message = uploadResult.message.ifBlank { "上传失败，请稍后重试。" }
                        updateUploadTask(
                            taskId = uploadId,
                            state = UploadState.FAILURE,
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
                finalizeRealOperationIfReady(
                    operationId = operationId,
                    sourceItems = sourceItems,
                    finalizeAction = finalizeAction,
                )
                publishOperationSummaryIfReady(operationId)
            } catch (throwable: Throwable) {
                val message = friendlyUploadError(
                    throwable = throwable,
                    fallback = "上传任务异常中断，请稍后重试。",
                )
                if (uploadTasksState.any { it.taskId == activeTaskId }) {
                    updateUploadTask(
                        taskId = activeTaskId,
                        state = UploadState.FAILURE,
                        progressPercent = 0,
                        statusMessage = "上传失败",
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
                        statusMessage = "上传失败",
                        errorMessage = message,
                    )
                }
                finalizeRealOperationIfReady(
                    operationId = operationId,
                    sourceItems = sourceItems,
                    finalizeAction = finalizeAction,
                )
                publishOperationSummaryIfReady(operationId)
                debugUploadLog("upload crashed operation=$operationId task=$activeTaskId", throwable)
            } finally {
                if (uploadPermitAcquired) {
                    uploadSemaphore.release()
                }
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

    private suspend fun finalizeCreatePostReal(
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
                linkMediaToPost(
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

    private suspend fun finalizeRealOperationIfReady(
        operationId: String,
        sourceItems: List<SystemMediaItem>,
        finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
    ) {
        if (finalizedOperationIds.contains(operationId)) return
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        val request = operationRequestsById[operationId]
        val hasFailedTasks = operationTasks.any { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED }
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

        val uploadedMap = realUploadedMediaIdsByOperationId[operationId].orEmpty()
        val orderedIds = sourceItems.mapNotNull { uploadedMap[it.id] }
        if (allTasksSucceeded && orderedIds.distinct().size != sourceItems.distinctBy { it.id }.size) return
        if (orderedIds.isEmpty()) return

        finalizedOperationIds += operationId
        if (canFinalizePartialCreatePost || canFinalizePartialAddToPost) {
            updateSuccessfulOperationTasks(
                operationId = operationId,
                state = UploadState.UPLOADING,
                statusMessage = "部分上传完成，正在用成功项创建小相册",
            )
        } else {
            updateOperationTasks(
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
                publishMutation(
                    kind = MutationKind.OVERLAY_ONLY,
                    mediaIds = sourceItems.map { it.id },
                )
                notifyRealBackendContentChanged(
                    postIds = result.data.affectedPostIds,
                    mediaIds = orderedIds.toSet(),
                )
                if (canFinalizePartialCreatePost || canFinalizePartialAddToPost) {
                    updateSuccessfulOperationTasks(
                        operationId = operationId,
                        state = UploadState.SUCCESS,
                        statusMessage = result.data.successMessage,
                        resultPostRoute = result.data.postRoute,
                    )
                    markFailedOperationTasksAfterPartialPost(operationId)
                } else {
                    updateOperationTasks(
                        operationId = operationId,
                        state = UploadState.SUCCESS,
                        statusMessage = result.data.successMessage,
                        resultPostRoute = result.data.postRoute,
                    )
                }
                publishOperationSummaryIfReady(
                    operationId = operationId,
                    operationType = result.data.operationType,
                    postRoute = result.data.postRoute,
                )
            }
            is ApiResult.Error -> {
                finalizedOperationIds.remove(operationId)
                if (canFinalizePartialCreatePost || canFinalizePartialAddToPost) {
                    updateSuccessfulOperationTasks(
                        operationId = operationId,
                        state = UploadState.FAILURE,
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
                    updateOperationTasks(
                        operationId = operationId,
                        state = UploadState.FAILURE,
                        statusMessage = when (request?.operationType) {
                            OperationType.ADD_TO_EXISTING_POST -> "加入小相册失败"
                            else -> "小相册创建失败"
                        },
                        errorMessage = result.message.ifBlank { "上传完成，但收尾处理失败，可重试。" },
                        canRetry = true,
                    )
                }
                publishOperationSummaryIfReady(
                    operationId = operationId,
                    operationType = request?.operationType ?: OperationType.CREATE_POST,
                )
            }
            ApiResult.Loading -> Unit
        }
    }

    private fun finalizeRealOperationWithKnownMedia(
        operationId: String,
        sourceItems: List<SystemMediaItem>,
        finalizeAction: suspend (UploadedOperationMedia) -> ApiResult<RealFinalizeResult>,
    ) {
        if (finalizedOperationIds.contains(operationId)) return
        uploadScope.launch {
            val uploadedMap = realUploadedMediaIdsByOperationId[operationId].orEmpty()
            val orderedIds = sourceItems.mapNotNull { uploadedMap[it.id] }.distinct()
            if (orderedIds.isEmpty()) {
                publishOperationResult(
                    OperationResultEvent(
                        eventId = "$operationId-duplicate-${System.currentTimeMillis()}",
                        operationId = operationId,
                        operationType = operationRequestsById[operationId]?.operationType ?: OperationType.IMPORT_TO_APP,
                        succeeded = false,
                        message = "已跳过重复媒体。",
                        totalCount = sourceItems.size,
                    ),
                )
                return@launch
            }

            finalizedOperationIds += operationId
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
                            rememberAppMediaIdForSource(item, appMediaId)
                        }
                    }
                    publishMutation(
                        kind = MutationKind.OVERLAY_ONLY,
                        mediaIds = sourceItems.map { it.id },
                    )
                    notifyRealBackendContentChanged(
                        postIds = result.data.affectedPostIds,
                        mediaIds = orderedIds.toSet(),
                    )
                    publishOperationResult(
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
                    finalizedOperationIds.remove(operationId)
                    publishOperationResult(
                        OperationResultEvent(
                            eventId = "$operationId-reused-failure-${System.currentTimeMillis()}",
                            operationId = operationId,
                            operationType = operationRequestsById[operationId]?.operationType ?: OperationType.CREATE_POST,
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
        sourceItem: SystemMediaItem,
        uploadedMediaId: String,
    ) {
        rememberUploadedMediaIds(
            operationId = operationId,
            sourceMediaIdsToUploadedMediaIds = mapOf(sourceItem.id to uploadedMediaId),
        )
        rememberAppMediaIdForSource(sourceItem, uploadedMediaId)
    }

    private fun rememberUploadedMediaIds(
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

    private fun isUploadTaskCancelled(taskId: String): Boolean {
        return uploadTasksState.firstOrNull { it.taskId == taskId }?.state == UploadState.CANCELLED
    }

    private fun cancelUploadJob(task: SystemMediaUploadTaskUiModel) {
        val fallbackTaskId = "${task.operationId}-${task.mediaId}"
        val cancellation = CancellationException("Upload task canceled by user")
        uploadJobsByTaskId.remove(task.taskId)?.cancel(cancellation)
        uploadJobsByTaskId.remove(fallbackTaskId)?.cancel(cancellation)
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
            updatedAtMillis = System.currentTimeMillis(),
        )
        persistUploadTasks()
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

    private fun updateOperationTasks(
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
                    progressPercent = if (state == UploadState.FAILURE || state == UploadState.CANCELLED) {
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

    private fun updateSuccessfulOperationTasks(
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
                    progressPercent = if (state == UploadState.FAILURE || state == UploadState.CANCELLED) {
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

    private fun markFailedOperationTasksAfterPartialPost(operationId: String) {
        uploadTasksState.indices.forEach { index ->
            val task = uploadTasksState[index]
            if (
                task.operationId == operationId &&
                (task.state == UploadState.FAILURE || task.state == UploadState.CANCELLED)
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
                message = friendlyUploadError(throwable, "上传请求失败，请检查网络后重试。"),
                throwable = throwable,
            )
        }
    }

    private fun friendlyUploadError(
        throwable: Throwable,
        fallback: String,
    ): String {
        return when (throwable) {
            is TimeoutCancellationException -> "操作超时，请检查网络后重试。"
            is SecurityException -> "没有读取所选媒体的权限，请授权后重试。"
            is java.io.FileNotFoundException -> "选中的媒体文件不存在或已被系统移除。"
            is java.net.ConnectException -> "无法连接服务，请检查网络和服务地址。"
            is java.net.SocketTimeoutException -> "网络请求超时，请稍后重试。"
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
        )
    }

    private fun finalizeWaitingMessage(targetLabel: String): String {
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

    private fun normalizeSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): List<SystemMediaItem> {
        return normalizeSystemMediaForImport(
            mediaItems = mediaItems,
            preference = SettingsRepository.getSettingsState().mediaTimePreference,
            importedAtBaseMillis = System.currentTimeMillis(),
        ).distinctBy(SystemMediaItem::stableImportSourceKey)
    }

    private data class DeduplicatedImportItems(
        val items: List<SystemMediaItem>,
        val skippedCount: Int,
    )

    private fun deduplicateImportCandidates(
        mediaItems: List<SystemMediaItem>,
    ): DeduplicatedImportItems {
        val normalizedItems = normalizeSystemMedia(mediaItems)
        val newItems = normalizedItems.filter { item ->
            knownAppMediaIdForSource(item) == null
        }
        return DeduplicatedImportItems(
            items = newItems,
            skippedCount = (mediaItems.size - normalizedItems.size) + (normalizedItems.size - newItems.size),
        )
    }

    private fun reusableAppMediaIdsBySourceId(
        mediaItems: List<SystemMediaItem>,
    ): Map<String, String> {
        return mediaItems.mapNotNull { item ->
            val knownMediaId = knownAppMediaIdForSource(item) ?: return@mapNotNull null
            item.id to knownMediaId
        }.toMap()
    }

    private fun knownAppMediaIdForSource(
        item: SystemMediaItem,
    ): String? {
        item.importedAppMediaId
            ?.takeIf { it.isNotBlank() && it !in invalidatedAppMediaIds }
            ?.let { return it }
        item.stableImportSourceKeys().forEach { sourceKey ->
            appMediaIdBySystemSourceKey[sourceKey]
                ?.takeIf { it !in invalidatedAppMediaIds }
                ?.let { return it }
        }
        if (RepositoryProvider.currentMode != RepositoryMode.REAL) {
            FakePhotoFeedRepository.findPhotoFeedItem(item.id)?.mediaId?.let { mediaId ->
                rememberAppMediaIdForSource(item, mediaId)
                return mediaId
            }
        }
        return null
    }

    private fun rememberAppMediaIdForSource(
        item: SystemMediaItem,
        appMediaId: String,
    ) {
        if (appMediaId.isBlank()) return
        invalidatedAppMediaIds.remove(appMediaId)
        item.stableImportSourceKeys().forEach { sourceKey ->
            appMediaIdBySystemSourceKey[sourceKey] = appMediaId
        }
        persistImportOverlay()
    }

    private fun persistImportOverlay() {
        val prefs = overlayPreferences ?: return
        val sourceKeyByAppMediaId = appMediaIdBySystemSourceKey.entries
            .groupBy({ it.value }, { it.key })
        val array = JSONArray()
        appMediaIdBySystemSourceKey.forEach { (sourceKey, appMediaId) ->
            val smallAlbumIds = linkedPostIdsByMediaId[appMediaId]
                ?: sourceKeyByAppMediaId[appMediaId]
                    ?.asSequence()
                    ?.mapNotNull { linkedPostIdsByMediaId[it] }
                    ?.firstOrNull()
                ?: emptySet()
            array.put(
                JSONObject()
                    .put("sourceKey", sourceKey)
                    .put("appMediaId", appMediaId)
                    .put("smallAlbumIds", JSONArray(smallAlbumIds.toList())),
            )
        }
        prefs.edit().putString(ImportOverlayItemsKey, array.toString()).apply()
    }

    private fun persistUploadTasks() {
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

    private fun SystemMediaUploadTaskUiModel.toPersistedUploadTaskJson(): JSONObject {
        return JSONObject()
            .put("taskId", taskId)
            .put("operationId", operationId)
            .put("mediaId", mediaId)
            .put("fileName", fileName)
            .put("targetLabel", targetLabel)
            .put("mediaType", mediaType.name)
            .put("previewUri", previewUri)
            .put("thumbnailUrl", thumbnailUrl)
            .put("resultMediaId", resultMediaId)
            .put("progressPercent", progressPercent)
            .put("state", state.name)
            .put("statusMessage", statusMessage)
            .put("errorMessage", errorMessage)
            .put("canRetry", canRetry)
            .put("operationType", operationType.name)
            .put("operationTitle", operationTitle)
            .put("operationMediaCount", operationMediaCount)
            .put("operationSuccessCount", operationSuccessCount)
            .put("operationFailureCount", operationFailureCount)
            .put("operationCancelledCount", operationCancelledCount)
            .put("createdAtMillis", createdAtMillis)
            .put("completedAtMillis", completedAtMillis)
            .put("updatedAtMillis", updatedAtMillis)
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

    private fun String.toPersistedUploadState(): UploadState {
        return runCatching { UploadState.valueOf(ifBlank { UploadState.FAILURE.name }) }
            .getOrDefault(UploadState.FAILURE)
    }

    private fun UploadState.isTerminalUploadState(): Boolean {
        return this == UploadState.SUCCESS || this == UploadState.FAILURE || this == UploadState.CANCELLED
    }

    private fun JSONArray?.toStringSet(): LinkedHashSet<String> {
        val values = linkedSetOf<String>()
        if (this == null) return values
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(values::add)
        }
        return values
    }

    private fun PhotoFeedItem.toCreatePostSystemMediaItem(): SystemMediaItem {
        val uriString = mediaSource?.originalUrl
            ?: mediaSource?.mediaUrl
            ?: mediaSource?.thumbnailUrl
            ?: "content://app-feed/$mediaId"
        val calendar = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = mediaDisplayTimeMillis
        }
        return SystemMediaItem(
            id = mediaId,
            mediaStoreId = mediaId.hashCode().toLong().and(Long.MAX_VALUE),
            uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: Uri.parse("content://app-feed/$mediaId"),
            type = when (mediaType) {
                AppMediaType.IMAGE -> SystemMediaType.IMAGE
                AppMediaType.VIDEO -> SystemMediaType.VIDEO
            },
            mimeType = mediaSource?.mimeType ?: when (mediaType) {
                AppMediaType.IMAGE -> "image/jpeg"
                AppMediaType.VIDEO -> "video/mp4"
            },
            displayName = mediaId,
            bucketName = "照片流",
            displayTimeMillis = mediaDisplayTimeMillis,
            capturedAtMillis = capturedAtMillis,
            fileModifiedAtMillis = null,
            displayTimeSource = displayTimeSource ?: DisplayTimeSourceOriginal,
            displayYear = calendar.get(java.util.Calendar.YEAR),
            displayMonth = calendar.get(java.util.Calendar.MONTH) + 1,
            displayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH),
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            palette = palette,
            linkedPostIds = postIds,
            videoDurationMillis = videoDurationMillis,
            uploadedByUserId = uploadedByUserId,
        )
    }

    private fun publishDuplicateNoticeIfNeeded(
        operationType: OperationType,
        skippedCount: Int,
    ) {
        if (skippedCount <= 0) return
        publishOperationResult(
            OperationResultEvent(
                eventId = "duplicate-${operationType.name.lowercase(Locale.ROOT)}-${System.currentTimeMillis()}",
                operationId = "duplicate-${System.currentTimeMillis()}",
                operationType = operationType,
                succeeded = false,
                message = "已跳过 $skippedCount 个重复媒体。",
                successCount = 0,
                failureCount = 0,
                totalCount = skippedCount,
            ),
        )
    }

    private fun Uri.toPickedSystemMediaItem(
        context: Context,
        index: Int,
    ): SystemMediaItem? {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(this).orEmpty()
        val displayName = resolvePickedDisplayName(context, index)
        val type = resolvePickedMediaType(
            uri = this,
            displayName = displayName,
            mimeType = mimeType,
            fallback = SystemMediaType.IMAGE,
        )
        val sizeBytes = resolvePickedSizeBytes(context)
        val (width, height, durationMillis) = resolvePickedMediaMetadata(context, this, type)
        val aspectRatio = resolvePickedMediaAspectRatio(width, height, type)
        val importedAtMillis = System.currentTimeMillis() - (index * 1_000L)
        val timeMetadata = queryDeviceMediaTimeMetadata(
            context = context,
            uri = this,
            mediaType = type,
        )
        val resolvedTime = resolvePreferredMediaDisplayTime(
            metadata = timeMetadata,
            importedAtMillis = importedAtMillis,
            preference = SettingsRepository.getSettingsState().mediaTimePreference,
        )
        val calendar = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = resolvedTime.displayTimeMillis
        }

        return SystemMediaItem(
            id = buildPickedMediaId(
                uri = this,
                displayName = displayName,
                displayTimeMillis = resolvedTime.displayTimeMillis,
                sizeBytes = sizeBytes,
                type = type,
            ),
            mediaStoreId = this.toString().hashCode().toLong().and(Long.MAX_VALUE),
            uri = this,
            type = type,
            mimeType = mimeType.ifBlank { if (type == SystemMediaType.VIDEO) "video/mp4" else "image/jpeg" },
            displayName = displayName,
            bucketName = "系统选择器",
            displayTimeMillis = resolvedTime.displayTimeMillis,
            capturedAtMillis = resolvedTime.capturedAtMillis,
            fileModifiedAtMillis = resolvedTime.fileModifiedAtMillis,
            displayTimeSource = resolvedTime.displayTimeSource,
            displayYear = calendar.get(java.util.Calendar.YEAR),
            displayMonth = calendar.get(java.util.Calendar.MONTH) + 1,
            displayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH),
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            palette = pickedPaletteFor(index, type),
            linkedPostIds = emptyList(),
            videoDurationMillis = durationMillis,
            sizeBytes = sizeBytes,
        )
    }

    private fun buildPickedMediaId(
        uri: Uri,
        displayName: String,
        displayTimeMillis: Long,
        sizeBytes: Long?,
        type: SystemMediaType,
    ): String {
        val rawKey = listOf(
            uri.toString(),
            displayName,
            displayTimeMillis.toString(),
            sizeBytes?.toString().orEmpty(),
            type.name,
        ).joinToString("|")
        val stableHash = rawKey.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
        return "picked-$stableHash"
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

    private fun Uri.resolvePickedSizeBytes(
        context: Context,
    ): Long? {
        return context.contentResolver.query(
            this,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (columnIndex >= 0) cursor.getLongOrNull(columnIndex) else null
            } else {
                null
            }
        }
    }

    private fun resolveUploadDimensions(
        mediaType: SystemMediaType,
        aspectRatio: Float,
        width: Int?,
        height: Int?,
        resolvedWidth: Int?,
        resolvedHeight: Int?,
    ): Pair<Int, Int> {
        if (resolvedWidth != null && resolvedHeight != null && resolvedWidth > 0 && resolvedHeight > 0) {
            return resolvedWidth to resolvedHeight
        }
        if (width != null && height != null && width > 0 && height > 0) {
            return width to height
        }

        val ratio = aspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.15f, 6f)
            ?: if (mediaType == SystemMediaType.VIDEO) 16f / 9f else 1f
        val longEdge = if (mediaType == SystemMediaType.VIDEO) 1920 else 1600
        return if (ratio >= 1f) {
            longEdge to (longEdge / ratio).roundToInt().coerceAtLeast(1)
        } else {
            (longEdge * ratio).roundToInt().coerceAtLeast(1) to longEdge
        }
    }

    private fun resolvePickedMediaType(
        uri: Uri,
        displayName: String,
        mimeType: String,
        fallback: SystemMediaType,
    ): SystemMediaType {
        if (mimeType.startsWith("video/", ignoreCase = true)) {
            return SystemMediaType.VIDEO
        }
        if (mimeType.startsWith("image/", ignoreCase = true)) {
            return SystemMediaType.IMAGE
        }
        val normalizedName = displayName.trim().lowercase(Locale.ROOT)
        if (normalizedName.endsWith(".mp4") ||
            normalizedName.endsWith(".mov") ||
            normalizedName.endsWith(".m4v") ||
            normalizedName.endsWith(".3gp") ||
            normalizedName.endsWith(".webm") ||
            normalizedName.endsWith(".mkv")
        ) {
            return SystemMediaType.VIDEO
        }
        val normalizedUri = uri.toString().trim().lowercase(Locale.ROOT)
        if (normalizedUri.contains("/video/") ||
            normalizedUri.endsWith(".mp4") ||
            normalizedUri.endsWith(".mov") ||
            normalizedUri.endsWith(".m4v") ||
            normalizedUri.endsWith(".3gp") ||
            normalizedUri.endsWith(".webm") ||
            normalizedUri.endsWith(".mkv")
        ) {
            return SystemMediaType.VIDEO
        }
        return fallback
    }

    private fun resolvedCreateUploadTokenPayload(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        mediaType: String,
        width: Int,
        height: Int,
        durationMillis: Long?,
        mediaItem: SystemMediaItem,
        operationId: String? = null,
        operationType: OperationType? = null,
        operationTitle: String? = null,
        operationMediaCount: Int? = null,
    ): CreateUploadTokenPayload {
        val resolvedTime = resolvePreferredMediaDisplayTime(
            metadata = DeviceMediaTimeMetadata(
                capturedAtMillis = mediaItem.capturedAtMillis,
                fileModifiedAtMillis = mediaItem.fileModifiedAtMillis,
            ),
            importedAtMillis = System.currentTimeMillis(),
            preference = SettingsRepository.getSettingsState().mediaTimePreference,
        )
        return CreateUploadTokenPayload(
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            mediaType = mediaType,
            width = width,
            height = height,
            durationMillis = durationMillis,
            displayTimeMillis = resolvedTime.displayTimeMillis,
            capturedAtMillis = resolvedTime.capturedAtMillis,
            importedAtMillis = resolvedTime.importedAtMillis,
            displayTimeSource = resolvedTime.displayTimeSource,
            sourceFingerprint = mediaItem.stableImportMetadataSourceFingerprint(),
            operationId = operationId,
            operationType = operationType?.name,
            operationTitle = operationTitle,
            operationMediaCount = operationMediaCount,
            sourceItemId = mediaItem.id,
        )
    }

    private fun List<Uri>.toPickedSystemMediaItems(
        context: Context,
    ): List<SystemMediaItem> {
        return distinct()
            .mapIndexedNotNull { index, uri ->
                uri.toPickedSystemMediaItem(
                    context = context,
                    index = index,
                )
            }
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
                resultPostRoute = result.postRoute.takeIf { result.succeeded },
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
        val failureCount = operationTasks.count { it.state == UploadState.FAILURE }
        val cancelledCount = operationTasks.count { it.state == UploadState.CANCELLED }
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

    private fun operationTaskMeta(operationId: String): OperationTaskMeta {
        val request = operationRequestsById[operationId]
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
                    mediaCount = uploadTasksState.count { it.operationId == operationId }.coerceAtLeast(1),
                )
            }
        }
    }

    private fun refreshOperationTaskMeta(
        operationId: String,
        resultPostRoute: PostDetailPlaceholderRoute? = null,
    ) {
        val meta = operationTaskMeta(operationId)
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        val successCount = operationTasks.count { it.state == UploadState.SUCCESS }
        val failureCount = operationTasks.count { it.state == UploadState.FAILURE }
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

    private fun publishFirstSuccessIfNeeded(
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
                totalCount = uploadTasksState.count { it.operationId == operationId }.coerceAtLeast(1),
                shouldAutoOpenResult = false,
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
        publishedFirstSuccessOperationIds.remove(operationId)
        realUploadedMediaIdsByOperationId.remove(operationId)
        if (!keepRequest) {
            operationRequestsById.remove(operationId)
        }
        persistUploadTasks()
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

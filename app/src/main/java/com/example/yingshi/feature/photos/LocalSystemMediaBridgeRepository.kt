package com.example.yingshi.feature.photos

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var mutationVersion by mutableIntStateOf(0)
        private set

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val uploadTasksState = mutableStateListOf<SystemMediaUploadTaskUiModel>()
    private val finalizedOperationIds = linkedSetOf<String>()

    private val hiddenMediaIds = linkedSetOf<String>()
    private val linkedPostIdsByMediaId = linkedMapOf<String, LinkedHashSet<String>>()

    val uploadTasks: List<SystemMediaUploadTaskUiModel>
        get() = uploadTasksState

    fun applyOverlay(items: List<SystemMediaItem>): List<SystemMediaItem> {
        return items
            .filterNot { hiddenMediaIds.contains(it.id) }
            .map { item ->
                item.copy(
                    linkedPostIds = linkedPostIdsByMediaId[item.id]
                        ?.toList()
                        .orEmpty(),
                )
            }
    }

    fun createPostFromSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        val normalizedItems = mediaItems.distinctBy { it.id }
        val post = FakeAlbumRepository.createLocalPostFromSystemMedia(normalizedItems) ?: return null
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
    ): Int {
        if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            return enqueueCreatePostUploadReal(context, mediaItems)
        }
        val normalizedItems = mediaItems.distinctBy { it.id }
        if (normalizedItems.isEmpty()) return 0
        val operationId = "create-post-${System.currentTimeMillis()}"
        normalizedItems.forEach { item ->
            enqueueUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "发成新帖子",
                onOperationSuccess = {
                    createPostFromSystemMedia(normalizedItems)
                },
            )
        }
        return normalizedItems.size
    }

    fun addSystemMediaToExistingPost(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = mediaItems.distinctBy { it.id }
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
        if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
            return enqueueAddToExistingPostUploadReal(context, postId, mediaItems)
        }
        val normalizedItems = mediaItems.distinctBy { it.id }
        if (normalizedItems.isEmpty()) return 0
        val operationId = "append-post-$postId-${System.currentTimeMillis()}"
        normalizedItems.forEach { item ->
            enqueueUploadTask(
                operationId = operationId,
                mediaItem = item,
                targetLabel = "加入已有帖子",
                onOperationSuccess = {
                    addSystemMediaToExistingPost(postId, normalizedItems)
                },
            )
        }
        return normalizedItems.size
    }

    fun moveToSimulatedSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        var changedCount = 0
        mediaIds.distinct().forEach { mediaId ->
            if (hiddenMediaIds.add(mediaId)) {
                changedCount += 1
            }
        }
        if (changedCount > 0) {
            mutationVersion += 1
        }
        return changedCount
    }

    fun markMovedToSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        return moveToSimulatedSystemTrash(mediaIds)
    }

    private fun linkMediaToPost(
        mediaIds: Collection<String>,
        postId: String,
    ) {
        var changed = false
        mediaIds.distinct().forEach { mediaId ->
            val postIds = linkedPostIdsByMediaId.getOrPut(mediaId) {
                linkedSetOf()
            }
            changed = postIds.add(postId) || changed
        }
        if (changed) {
            mutationVersion += 1
        }
    }

    fun cancelUploadTask(taskId: String) {
        val index = uploadTasksState.indexOfFirst { it.taskId == taskId }
        if (index < 0) return
        val current = uploadTasksState[index]
        if (current.isTerminal) return
        uploadTasksState[index] = current.copy(state = UploadState.CANCELLED)
        uploadScope.launch {
            RepositoryProvider.uploadRepository.cancelUpload(taskId)
        }
    }

    fun dismissUploadTask(taskId: String) {
        uploadTasksState.removeAll { it.taskId == taskId }
    }

    private fun enqueueUploadTask(
        operationId: String,
        mediaItem: SystemMediaItem,
        targetLabel: String,
        onOperationSuccess: () -> Unit,
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
                            errorMessage = tokenResult.message,
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
                )
            }

            val confirmResult = RepositoryProvider.uploadRepository.confirmUpload(
                uploadId = uploadId,
                payload = ConfirmUploadPayload(
                    etag = "fake-etag-$uploadId",
                    objectKey = "uploads/fake/${mediaItem.id}",
                ),
            )
            val currentIndex = uploadTasksState.indexOfFirst { it.taskId == uploadId }
            if (currentIndex < 0) return@launch
            val current = uploadTasksState[currentIndex]
            uploadTasksState[currentIndex] = when (confirmResult) {
                is ApiResult.Success -> current.copy(
                    progressPercent = 100,
                    state = UploadState.SUCCESS,
                )
                is ApiResult.Error -> current.copy(
                    state = UploadState.FAILURE,
                    errorMessage = confirmResult.message,
                )
                ApiResult.Loading -> current
            }
            finalizeOperationIfReady(operationId, onOperationSuccess)
        }
    }

    private fun enqueueCreatePostUploadReal(
        context: Context,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = mediaItems.distinctBy { it.id }
        if (normalizedItems.isEmpty()) return 0
        val operationId = "real-create-post-${System.currentTimeMillis()}"
        normalizedItems.forEach { item ->
            enqueueRealUploadTask(
                context = context,
                operationId = operationId,
                mediaItem = item,
                targetLabel = "发成新帖子",
                allSourceItems = normalizedItems,
                finalizeAction = { uploadedMediaIds ->
                    finalizeCreatePostReal(
                        sourceItems = normalizedItems,
                        uploadedMediaIds = uploadedMediaIds,
                    )
                },
            )
        }
        return normalizedItems.size
    }

    private fun enqueueAddToExistingPostUploadReal(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val normalizedItems = mediaItems.distinctBy { it.id }
        if (normalizedItems.isEmpty()) return 0
        val operationId = "real-append-post-$postId-${System.currentTimeMillis()}"
        normalizedItems.forEach { item ->
            enqueueRealUploadTask(
                context = context,
                operationId = operationId,
                mediaItem = item,
                targetLabel = "加入已有帖子",
                allSourceItems = normalizedItems,
                finalizeAction = { uploadedMediaIds ->
                    finalizeAppendToPostReal(
                        postId = postId,
                        sourceItems = normalizedItems,
                        uploadedMediaIds = uploadedMediaIds,
                    )
                },
            )
        }
        return normalizedItems.size
    }

    private fun enqueueRealUploadTask(
        context: Context,
        operationId: String,
        mediaItem: SystemMediaItem,
        targetLabel: String,
        allSourceItems: List<SystemMediaItem>,
        finalizeAction: suspend (List<String>) -> ApiResult<*>,
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
                        errorMessage = throwable.message ?: "读取本地媒体失败。",
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
                            errorMessage = tokenResult.message,
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
                ),
            )
            updateUploadTask(uploadId, UploadState.UPLOADING, 42)

            when (
                val uploadResult = RepositoryProvider.uploadRepository.uploadLocalFile(
                    uploadId = uploadId,
                    fileName = metadata.fileName,
                    mimeType = metadata.mimeType,
                    fileBytes = metadata.fileBytes,
                )
            ) {
                is ApiResult.Success -> {
                    updateUploadTask(uploadId, UploadState.SUCCESS, 100)
                    rememberUploadedMediaId(operationId, uploadResult.data.mediaId)
                    finalizeRealOperationIfReady(
                        operationId = operationId,
                        sourceItems = allSourceItems,
                        targetLabel = targetLabel,
                        finalizeAction = finalizeAction,
                    )
                }
                is ApiResult.Error -> {
                    updateUploadTask(
                        taskId = uploadId,
                        state = UploadState.FAILURE,
                        progressPercent = 42,
                        errorMessage = uploadResult.message,
                    )
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private suspend fun finalizeCreatePostReal(
        sourceItems: List<SystemMediaItem>,
        uploadedMediaIds: List<String>,
    ): ApiResult<*> {
        val albums = when (val result = RepositoryProvider.albumRepository.getAlbums()) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return ApiResult.Error(
                code = result.code,
                message = result.message.ifBlank { "读取相册失败，无法创建帖子。" },
                throwable = result.throwable,
            )
            ApiResult.Loading -> return ApiResult.Error(
                code = "ALBUMS_LOADING",
                message = "相册仍在加载，请稍后再试。",
            )
        }
        val defaultAlbumId = albums.firstOrNull()?.albumId
            ?: return ApiResult.Error(
                code = "ALBUM_REQUIRED",
                message = "后端当前没有可用相册，无法发成新帖子。",
            )

        return RepositoryProvider.postRepository.createPost(
            CreatePostPayload(
                title = buildRealPostTitle(sourceItems),
                summary = buildRealPostSummary(sourceItems),
                displayTimeMillis = sourceItems.maxOfOrNull { it.displayTimeMillis } ?: System.currentTimeMillis(),
                albumIds = listOf(defaultAlbumId),
                initialMediaIds = uploadedMediaIds,
            ),
        ).also { result ->
            if (result is ApiResult.Success) {
                linkMediaToPost(
                    mediaIds = sourceItems.map { it.id },
                    postId = result.data.postId,
                )
            }
        }
    }

    private suspend fun finalizeAppendToPostReal(
        postId: String,
        sourceItems: List<SystemMediaItem>,
        uploadedMediaIds: List<String>,
    ): ApiResult<*> {
        return RepositoryProvider.postRepository.addMediaToPost(
            postId = postId,
            mediaIds = uploadedMediaIds,
        ).also { result ->
            if (result is ApiResult.Success) {
                linkMediaToPost(
                    mediaIds = sourceItems.map { it.id },
                    postId = postId,
                )
            }
        }
    }

    private suspend fun finalizeRealOperationIfReady(
        operationId: String,
        sourceItems: List<SystemMediaItem>,
        targetLabel: String,
        finalizeAction: suspend (List<String>) -> ApiResult<*>,
    ) {
        if (finalizedOperationIds.contains(operationId)) return
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        if (operationTasks.any { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED }) return
        if (!operationTasks.all { it.state == UploadState.SUCCESS }) return

        val uploadedMediaIds = realUploadedMediaIdsByOperationId[operationId]
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (uploadedMediaIds.size != operationTasks.size) return

        finalizedOperationIds += operationId
        when (val result = finalizeAction(uploadedMediaIds)) {
            is ApiResult.Success -> {
                mutationVersion += 1
                RealBackendMutationBus.notifyChanged()
            }
            is ApiResult.Error -> {
                finalizedOperationIds.remove(operationId)
                uploadTasksState.add(
                    SystemMediaUploadTaskUiModel(
                        taskId = "finalize-$operationId",
                        operationId = operationId,
                        mediaId = sourceItems.firstOrNull()?.id ?: operationId,
                        fileName = "帖子整理",
                        targetLabel = targetLabel,
                        progressPercent = 100,
                        state = UploadState.FAILURE,
                        errorMessage = result.message.ifBlank { "上传完成，但整理帖子失败。" },
                    ),
                )
            }
            ApiResult.Loading -> Unit
        }
    }

    private fun rememberUploadedMediaId(
        operationId: String,
        uploadedMediaId: String,
    ) {
        val mediaIds = realUploadedMediaIdsByOperationId.getOrPut(operationId) { linkedSetOf() }
        mediaIds += uploadedMediaId
    }

    private fun updateUploadTask(
        taskId: String,
        state: UploadState,
        progressPercent: Int,
        errorMessage: String? = null,
    ) {
        val currentIndex = uploadTasksState.indexOfFirst { it.taskId == taskId }
        if (currentIndex < 0) return
        val current = uploadTasksState[currentIndex]
        uploadTasksState[currentIndex] = current.copy(
            state = state,
            progressPercent = progressPercent,
            errorMessage = errorMessage,
        )
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
            durationMillis = null,
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

    private fun finalizeOperationIfReady(
        operationId: String,
        onOperationSuccess: () -> Unit,
    ) {
        if (finalizedOperationIds.contains(operationId)) return
        val operationTasks = uploadTasksState.filter { it.operationId == operationId }
        if (operationTasks.isEmpty()) return
        if (operationTasks.any { it.state == UploadState.FAILURE || it.state == UploadState.CANCELLED }) return
        if (operationTasks.all { it.state == UploadState.SUCCESS }) {
            finalizedOperationIds += operationId
            onOperationSuccess()
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

    private data class RealUploadMetadata(
        val fileName: String,
        val mimeType: String,
        val fileBytes: ByteArray,
        val width: Int,
        val height: Int,
        val durationMillis: Long?,
    )

    private val realUploadedMediaIdsByOperationId = linkedMapOf<String, LinkedHashSet<String>>()
}

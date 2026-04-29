package com.example.yingshi.feature.photos

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        mediaItems: List<SystemMediaItem>,
    ): Int {
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
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
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
}

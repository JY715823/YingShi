package com.example.yingshi.feature.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.yingshi.BuildConfig
import com.example.yingshi.data.model.ConfirmUploadPayload
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationResultEvent
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository.OperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val UploadLogTag = "SystemMediaUpload"

internal data class DeduplicatedImportItems(
    val items: List<SystemMediaItem>,
    val skippedCount: Int,
)

internal fun enqueueImportToAppUploadFake(
    mediaItems: List<SystemMediaItem>,
): Int {
    val operationId = "import-app-${System.currentTimeMillis()}"
    UploadManager.operationRequestsById[operationId] = ImportToAppOperationRequest(mediaItems = mediaItems)
    mediaItems.forEach { item ->
        enqueueFakeUploadTask(
            operationId = operationId,
            mediaItem = item,
            targetLabel = "导入照片流",
            onOperationSuccess = {
                val importedCount = UploadManager.importSystemMediaToApp(mediaItems)
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

internal fun enqueueCreatePostUploadFake(
    mediaItems: List<SystemMediaItem>,
    draft: CreatePostDraft,
    additionalAppMediaIds: List<String>,
    additionalAppCoverMediaId: String?,
): Int {
    val additionalAppItems = additionalAppMediaIds.distinct().mapNotNull(FakePhotoFeedRepository::findPhotoFeedItem)
    val finalDraft = draft.copy(coverSourceMediaId = draft.coverSourceMediaId ?: additionalAppCoverMediaId)
    val operationId = "create-post-${System.currentTimeMillis()}"
    UploadManager.operationRequestsById[operationId] = CreatePostOperationRequest(
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
                val createdPost = UploadManager.createPostFromSystemMediaDraft(
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

internal fun enqueueAddToExistingPostUploadFake(
    postId: String,
    mediaItems: List<SystemMediaItem>,
    postTitle: String,
): Int {
    val operationId = "append-post-$postId-${System.currentTimeMillis()}"
    UploadManager.operationRequestsById[operationId] = AddToExistingPostOperationRequest(
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
                val addedCount = UploadManager.addSystemMediaToExistingPost(postId, mediaItems)
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

internal fun enqueueFakeUploadTask(
    operationId: String,
    mediaItem: SystemMediaItem,
    targetLabel: String,
    onOperationSuccess: () -> OperationResultEvent,
) {
    UploadManager.uploadScope.launch {
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
                operationType = UploadManager.operationRequestsById[operationId]?.operationType,
                operationTitle = OperationBus.operationTaskMeta(operationId).operationTitle,
                operationMediaCount = OperationBus.operationTaskMeta(operationId).mediaCount,
            ),
        )
        val uploadId = when (tokenResult) {
            is ApiResult.Success -> tokenResult.data.uploadId
            is ApiResult.Error -> {
                UploadManager.uploadTasksState.add(
                    SystemMediaUploadTaskUiModel(
                        taskId = "${operationId}-${mediaItem.id}",
                        operationId = operationId,
                        mediaId = mediaItem.id,
                        fileName = mediaItem.displayName.ifBlank { mediaItem.id },
                        targetLabel = targetLabel,
                        mediaType = mediaItem.type,
                        previewUri = mediaItem.uri.toString(),
                        progressPercent = 0,
                        state = UploadState.FAILED,
                        statusMessage = "上传失败",
                        errorMessage = tokenResult.message,
                        canRetry = true,
                    ),
                )
                UploadManager.refreshOperationTaskMeta(operationId)
                OperationBus.publishOperationSummaryIfReady(operationId)
                return@launch
            }
            ApiResult.Loading -> return@launch
        }

        UploadManager.uploadTasksState.add(
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
        UploadManager.refreshOperationTaskMeta(operationId)

        val progressSteps = listOf(12, 28, 46, 63, 81, 100)
        progressSteps.forEachIndexed { index, progress ->
            delay(220L + (index * 30L))
            val currentIndex = UploadManager.uploadTasksState.indexOfFirst { it.taskId == uploadId }
            if (currentIndex < 0) return@launch
            val current = UploadManager.uploadTasksState[currentIndex]
            if (current.state == UploadState.CANCELLED) return@launch
            UploadManager.uploadTasksState[currentIndex] = current.copy(
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
        if (UploadManager.uploadTasksState.firstOrNull { it.taskId == uploadId }?.state == UploadState.CANCELLED) {
            return@launch
        }
        when (confirmResult) {
            is ApiResult.Success -> {
                UploadManager.updateUploadTask(
                    taskId = uploadId,
                    state = UploadState.SUCCESS,
                    progressPercent = 100,
                    statusMessage = finalizeWaitingMessage(targetLabel),
                    resultMediaId = mediaItem.id,
                )
                finalizeOperationIfReady(operationId, onOperationSuccess)
                invalidateSystemMediaMetadataCache(clearDisk = true)
                OperationBus.publishOperationSummaryIfReady(operationId)
            }
            is ApiResult.Error -> {
                UploadManager.updateUploadTask(
                    taskId = uploadId,
                    state = UploadState.FAILED,
                    progressPercent = 100,
                    statusMessage = "上传失败",
                    errorMessage = confirmResult.message,
                    canRetry = true,
                )
                OperationBus.publishOperationSummaryIfReady(operationId)
            }
            ApiResult.Loading -> Unit
        }
    }
}

internal fun finalizeOperationIfReady(
    operationId: String,
    onOperationSuccess: () -> OperationResultEvent,
) {
    if (OperationBus.finalizedOperationIds.contains(operationId)) return
    val operationTasks = UploadManager.uploadTasksState.filter { it.operationId == operationId }
    if (operationTasks.isEmpty()) return
    if (operationTasks.any { it.state == UploadState.FAILED || it.state == UploadState.CANCELLED }) return
    if (operationTasks.all { it.state == UploadState.SUCCESS }) {
        OperationBus.finalizedOperationIds += operationId
        val result = onOperationSuccess()
        UploadManager.updateOperationTasks(
            operationId = operationId,
            state = if (result.succeeded) UploadState.SUCCESS else UploadState.FAILED,
            statusMessage = result.message,
            errorMessage = if (result.succeeded) null else result.message,
            canRetry = !result.succeeded,
            resultPostRoute = result.postRoute.takeIf { result.succeeded },
        )
        OperationBus.publishOperationSummaryIfReady(
            operationId = operationId,
            operationType = result.operationType,
            postRoute = result.postRoute,
        )
    }
}

internal suspend fun <T> runUploadApiWithTimeout(
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

internal fun friendlyUploadError(
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

internal fun debugUploadLog(
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

internal fun normalizeSystemMedia(
    mediaItems: List<SystemMediaItem>,
): List<SystemMediaItem> {
    return normalizeSystemMediaForImport(
        mediaItems = mediaItems,
        preference = SettingsRepository.getSettingsState().mediaTimePreference,
        importedAtBaseMillis = System.currentTimeMillis(),
    ).distinctBy(SystemMediaItem::stableImportSourceKey)
}

internal fun deduplicateImportCandidates(
    mediaItems: List<SystemMediaItem>,
): DeduplicatedImportItems {
    val normalizedItems = normalizeSystemMedia(mediaItems)
    val newItems = normalizedItems.filter { item ->
        ImportOverlayStore.knownAppMediaIdForSource(item) == null
    }
    return DeduplicatedImportItems(
        items = newItems,
        skippedCount = (mediaItems.size - normalizedItems.size) + (normalizedItems.size - newItems.size),
    )
}

internal fun reusableAppMediaIdsBySourceId(
    mediaItems: List<SystemMediaItem>,
): Map<String, String> {
    return mediaItems.mapNotNull { item ->
        val knownMediaId = ImportOverlayStore.knownAppMediaIdForSource(item) ?: return@mapNotNull null
        item.id to knownMediaId
    }.toMap()
}

internal fun publishDuplicateNoticeIfNeeded(
    operationType: OperationType,
    skippedCount: Int,
) {
    if (skippedCount <= 0) return
    OperationBus.publishOperationResult(
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

internal fun RemoteUploadTask.toUploadTaskUiModel(): SystemMediaUploadTaskUiModel {
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
            UploadState.FAILED -> "上传失败"
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

internal fun OperationType.defaultTargetLabel(): String {
    return when (this) {
        OperationType.IMPORT_TO_APP -> "导入照片流"
        OperationType.CREATE_POST -> "新建小相册"
        OperationType.ADD_TO_EXISTING_POST -> "加入已有小相册"
    }
}

internal fun UploadState.isActivelyUploading(): Boolean {
    return this == UploadState.WAITING || this == UploadState.UPLOADING
}

internal fun SystemMediaUploadTaskUiModel.toPersistedUploadTaskJson(): JSONObject {
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

internal fun String.toPersistedUploadState(): UploadState {
    return runCatching { UploadState.valueOf(ifBlank { UploadState.FAILED.name }) }
        .getOrDefault(UploadState.FAILED)
}

internal fun UploadState.isTerminalUploadState(): Boolean {
    return this == UploadState.SUCCESS || this == UploadState.FAILED || this == UploadState.CANCELLED
}

internal fun JSONArray?.toStringSet(): LinkedHashSet<String> {
    val values = linkedSetOf<String>()
    if (this == null) return values
    for (index in 0 until length()) {
        optString(index).takeIf { it.isNotBlank() }?.let(values::add)
    }
    return values
}

internal fun PhotoFeedItem.toCreatePostSystemMediaItem(): SystemMediaItem {
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

internal fun Uri.toPickedSystemMediaItem(
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
        palette = paletteForPickedMedia(index, type),
        linkedPostIds = emptyList(),
        videoDurationMillis = durationMillis,
        sizeBytes = sizeBytes,
    )
}

internal fun buildPickedMediaId(
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
    val stableHash = rawKey.hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }
    return "picked-$stableHash"
}

internal fun Uri.resolvePickedDisplayName(
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

internal fun Uri.resolvePickedSizeBytes(
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

internal fun resolveUploadDimensions(
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

internal fun resolvePickedMediaType(
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

internal fun resolvedCreateUploadTokenPayload(
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

internal fun List<Uri>.toPickedSystemMediaItems(
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

internal fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getLong(columnIndex)
}

internal fun resolvePickedMediaMetadata(
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

internal fun resolveImageOrientationDegrees(
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

internal fun resolvePickedMediaAspectRatio(
    width: Int?,
    height: Int?,
    type: SystemMediaType,
): Float {
    if (width != null && height != null && width > 0 && height > 0) {
        return (width.toFloat() / height.toFloat()).coerceIn(0.56f, 1.8f)
    }
    return if (type == SystemMediaType.VIDEO) 1.33f else 1f
}

internal fun fakeMimeType(type: SystemMediaType): String {
    return when (type) {
        SystemMediaType.IMAGE -> "image/jpeg"
        SystemMediaType.VIDEO -> "video/mp4"
    }
}

internal fun fakeFileSizeBytes(item: SystemMediaItem): Long {
    val base = if (item.type == SystemMediaType.VIDEO) 12_000_000L else 3_000_000L
    return base + (item.id.hashCode().toLong().and(0xFFFF))
}

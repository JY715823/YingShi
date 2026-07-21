package com.example.yingshi.feature.photos

import android.content.Context
import android.net.Uri

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
        val resultMediaIds: List<String> = emptyList(),
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val cancelledCount: Int = 0,
        val totalCount: Int = 0,
        val shouldAutoOpenResult: Boolean = false,
    )

    val uploadTasks: List<SystemMediaUploadTaskUiModel>
        get() = UploadManager.uploadTasks

    val operationResults: List<OperationResultEvent>
        get() = OperationBus.operationResults

    val mutationVersion: Int
        get() = ImportOverlayStore.mutationVersion

    val latestMutationEvent: MutationEvent
        get() = ImportOverlayStore.latestMutationEvent

    fun warmPersistentTransferCenter(context: Context) {
        UploadManager.warmPersistentTransferCenter(context)
    }

    fun refreshRemoteUploadHistory() {
        UploadManager.refreshRemoteUploadHistory()
    }

    fun remainingUploadTaskCount(): Int {
        return UploadManager.remainingUploadTaskCount()
    }

    fun applyOverlay(items: List<SystemMediaItem>): List<SystemMediaItem> {
        return ImportOverlayStore.applyOverlay(items)
    }

    fun warmPersistentImportOverlay(context: Context) {
        ImportOverlayStore.warmPersistentImportOverlay(context)
    }

    fun rememberImportStatus(
        item: SystemMediaItem,
        appMediaId: String,
        smallAlbumIds: List<String>,
    ) {
        ImportOverlayStore.rememberImportStatus(item, appMediaId, smallAlbumIds)
    }

    fun forgetImportStatus(item: SystemMediaItem) {
        ImportOverlayStore.forgetImportStatus(item)
    }

    /** 查询本地 overlay 缓存中的导入状态，不修改缓存。返回 (appMediaId, smallAlbumIds) 或 null */
    fun peekImportStatus(item: SystemMediaItem): Pair<String, List<String>>? {
        return ImportOverlayStore.peekImportStatus(item)
    }

    fun forgetImportStatusByAppMediaId(appMediaId: String) {
        ImportOverlayStore.forgetImportStatusByAppMediaId(appMediaId)
    }

    fun forgetImportStatusByAppMediaIds(appMediaIds: Collection<String>) {
        ImportOverlayStore.forgetImportStatusByAppMediaIds(appMediaIds)
    }

    fun createPostFromSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        return UploadManager.createPostFromSystemMedia(mediaItems)
    }

    fun createPostFromSystemMediaDraft(
        draft: CreatePostDraft,
        mediaItems: List<SystemMediaItem>,
        additionalAppMediaItems: List<PhotoFeedItem> = emptyList(),
    ): AlbumPostCardUiModel? {
        return UploadManager.createPostFromSystemMediaDraft(
            draft = draft,
            mediaItems = mediaItems,
            additionalAppMediaItems = additionalAppMediaItems,
        )
    }

    fun enqueueCreatePostUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
        draft: CreatePostDraft = defaultCreatePostDraft(mediaItems),
        additionalAppMediaIds: List<String> = emptyList(),
        additionalAppCoverMediaId: String? = null,
    ): Int {
        return UploadManager.enqueueCreatePostUpload(
            context = context,
            mediaItems = mediaItems,
            draft = draft,
            additionalAppMediaIds = additionalAppMediaIds,
            additionalAppCoverMediaId = additionalAppCoverMediaId,
        )
    }

    fun importSystemMediaToApp(
        mediaItems: List<SystemMediaItem>,
    ): Int {
        return UploadManager.importSystemMediaToApp(mediaItems)
    }

    fun enqueueImportToAppUpload(
        context: Context,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        return UploadManager.enqueueImportToAppUpload(
            context = context,
            mediaItems = mediaItems,
        )
    }

    fun enqueueImportPickedMediaToAppUpload(
        context: Context,
        mediaUris: List<Uri>,
    ): Int {
        return UploadManager.enqueueImportPickedMediaToAppUpload(
            context = context,
            mediaUris = mediaUris,
        )
    }

    fun buildImportPickedMediaPreview(
        context: Context,
        mediaUris: List<Uri>,
    ): SystemMediaImportPreview {
        return UploadManager.buildImportPickedMediaPreview(
            context = context,
            mediaUris = mediaUris,
        )
    }

    fun addSystemMediaToExistingPost(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        return UploadManager.addSystemMediaToExistingPost(postId, mediaItems)
    }

    fun enqueueAddToExistingPostUpload(
        context: Context,
        postId: String,
        mediaItems: List<SystemMediaItem>,
        postTitle: String = "",
    ): Int {
        return UploadManager.enqueueAddToExistingPostUpload(
            context = context,
            postId = postId,
            mediaItems = mediaItems,
            postTitle = postTitle,
        )
    }

    fun moveToSimulatedSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        return ImportOverlayStore.moveToSimulatedSystemTrash(mediaIds)
    }

    fun markMovedToSystemTrash(
        mediaIds: Collection<String>,
    ): Int {
        return ImportOverlayStore.markMovedToSystemTrash(mediaIds)
    }

    fun pauseUploadTask(taskId: String) {
        UploadManager.pauseUploadTask(taskId)
    }

    fun cancelUploadTask(taskId: String) {
        UploadManager.cancelUploadTask(taskId)
    }

    fun buildImportToAppPreview(
        mediaItems: List<SystemMediaItem>,
    ): SystemMediaImportPreview {
        return UploadManager.buildImportToAppPreview(mediaItems)
    }

    fun cancelUploadOperation(operationId: String) {
        UploadManager.cancelUploadOperation(operationId)
    }

    fun pauseUploadOperation(operationId: String) {
        UploadManager.pauseUploadOperation(operationId)
    }

    fun dismissUploadTask(taskId: String) {
        UploadManager.dismissUploadTask(taskId)
    }

    fun dismissOperationResult(eventId: String) {
        OperationBus.dismissOperationResult(eventId)
    }

    fun retryUploadTask(
        context: Context,
        taskId: String,
    ): Boolean {
        return UploadManager.retryUploadTask(context, taskId)
    }

    fun retryUploadOperation(
        context: Context,
        operationId: String,
    ): Int {
        return UploadManager.retryUploadOperation(context, operationId)
    }
}

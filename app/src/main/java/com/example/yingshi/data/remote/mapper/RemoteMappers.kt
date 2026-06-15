package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.model.RemoteAlbum
import com.example.yingshi.data.model.RemoteComment
import com.example.yingshi.data.model.RemoteCommentPage
import com.example.yingshi.data.model.RemoteLifeConsoleBowelMutation
import com.example.yingshi.data.model.RemoteLifeConsoleBowelHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleBowelSummary
import com.example.yingshi.data.model.RemoteLifeConsoleBowelUserSummary
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteLifeConsoleUser
import com.example.yingshi.data.model.RemoteMediaAccess
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.model.RemotePostDetail
import com.example.yingshi.data.model.RemotePostMedia
import com.example.yingshi.data.model.RemotePostSummary
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.model.RemoteUploadToken
import com.example.yingshi.data.model.RemoteUploadTask
import com.example.yingshi.data.model.UploadState
import com.example.yingshi.data.remote.dto.AlbumDto
import com.example.yingshi.data.remote.dto.CommentDto
import com.example.yingshi.data.remote.dto.CommentListResponseDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelMutationResponseDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelHistoryDayDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelSummaryDto
import com.example.yingshi.data.remote.dto.LifeConsoleBowelUserSummaryDto
import com.example.yingshi.data.remote.dto.LifeConsoleHistoryDto
import com.example.yingshi.data.remote.dto.LifeConsoleHistoryDayDto
import com.example.yingshi.data.remote.dto.LifeConsoleMediaSlotDto
import com.example.yingshi.data.remote.dto.LifeConsoleTodayDto
import com.example.yingshi.data.remote.dto.LifeConsoleUserDto
import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.MediaAccessDto
import com.example.yingshi.data.remote.dto.PostDetailDto
import com.example.yingshi.data.remote.dto.PostMediaDto
import com.example.yingshi.data.remote.dto.PostSummaryDto
import com.example.yingshi.data.remote.dto.PendingCleanupDto
import com.example.yingshi.data.remote.dto.TrashDetailDto
import com.example.yingshi.data.remote.dto.TrashItemDto
import com.example.yingshi.data.remote.dto.UploadCompleteResponseDto
import com.example.yingshi.data.remote.dto.UploadTaskDto
import com.example.yingshi.data.remote.dto.UploadTokenDto

fun MediaDto.toRemoteModel(): RemoteMedia {
    val normalizedDisplayTime = displayTimeMillis.takeIf { it > 0L } ?: createdAtMillis ?: 0L
    val accessItems = access.orEmpty().map(MediaAccessDto::toRemoteModel)
    val previewAccess = accessItems.accessFor("preview")
    val originalAccess = accessItems.accessFor("original")
    val videoAccess = accessItems.accessFor("video")
    val coverAccess = accessItems.accessFor("cover")
    return RemoteMedia(
        mediaId = mediaId,
        mediaType = mediaType?.ifBlank { type.orEmpty() }.orEmpty().ifBlank { "image" },
        previewUrl = previewAccess?.requestUrl ?: previewUrl ?: thumbnailUrl,
        originalUrl = originalAccess?.requestUrl ?: originalUrl,
        videoUrl = videoAccess?.requestUrl ?: videoUrl,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        displayTimeMillis = normalizedDisplayTime,
        commentCount = 0,
        smallAlbumIds = smallAlbumIds.orEmpty(),
        thumbnailUrl = previewAccess?.requestUrl ?: thumbnailUrl ?: previewUrl,
        mediaUrl = originalAccess?.requestUrl ?: videoAccess?.requestUrl ?: mediaUrl ?: url,
        coverUrl = coverAccess?.requestUrl ?: coverUrl,
        mimeType = mimeType,
        durationMillis = durationMillis ?: duration,
        createdAtMillis = createdAtMillis,
        capturedAtMillis = capturedAtMillis,
        importedAtMillis = importedAtMillis,
        displayTimeSource = displayTimeSource,
        recordOwnerUserId = recordOwnerUserId,
        uploadedByUserId = uploadedByUserId,
        access = accessItems,
    )
}

fun LifeConsoleUserDto.toRemoteModel(): RemoteLifeConsoleUser {
    return RemoteLifeConsoleUser(
        userId = userId,
        account = account,
        displayName = displayName,
        avatarUrl = avatarUrl,
    )
}

fun LifeConsoleMediaSlotDto.toRemoteModel(): RemoteLifeConsoleMediaSlot {
    return RemoteLifeConsoleMediaSlot(
        category = category,
        ownerUserId = ownerUserId,
        editable = editable,
        mediaItems = mediaItems.map(MediaDto::toRemoteModel),
    )
}

fun LifeConsoleBowelUserSummaryDto.toRemoteModel(): RemoteLifeConsoleBowelUserSummary {
    return RemoteLifeConsoleBowelUserSummary(
        userId = userId,
        count = count,
        latestOccurredAtMillis = latestOccurredAtMillis,
        eventTimesMillis = eventTimesMillis,
    )
}

fun LifeConsoleBowelSummaryDto.toRemoteModel(): RemoteLifeConsoleBowelSummary {
    return RemoteLifeConsoleBowelSummary(
        users = users.map(LifeConsoleBowelUserSummaryDto::toRemoteModel),
    )
}

fun LifeConsoleTodayDto.toRemoteModel(): RemoteLifeConsoleToday {
    return RemoteLifeConsoleToday(
        date = date,
        zoneId = zoneId,
        currentUser = currentUser.toRemoteModel(),
        partner = partner?.toRemoteModel(),
        personSelf = personSelf.toRemoteModel(),
        personPartner = personPartner.toRemoteModel(),
        mealSelf = mealSelf.toRemoteModel(),
        mealPartner = mealPartner.toRemoteModel(),
        bowel = bowel.toRemoteModel(),
    )
}

fun LifeConsoleHistoryDayDto.toRemoteModel(): RemoteLifeConsoleHistoryDay {
    return RemoteLifeConsoleHistoryDay(
        date = date,
        displayLabel = displayLabel,
        selfMedia = selfMedia.map(MediaDto::toRemoteModel),
        partnerMedia = partnerMedia.map(MediaDto::toRemoteModel),
    )
}

fun LifeConsoleBowelHistoryDayDto.toRemoteModel(): RemoteLifeConsoleBowelHistoryDay {
    return RemoteLifeConsoleBowelHistoryDay(
        date = date,
        displayLabel = displayLabel,
        users = users.map(LifeConsoleBowelUserSummaryDto::toRemoteModel),
    )
}

fun LifeConsoleHistoryDto.toRemoteModel(): RemoteLifeConsoleHistory {
    return RemoteLifeConsoleHistory(
        zoneId = zoneId,
        currentUser = currentUser.toRemoteModel(),
        partner = partner?.toRemoteModel(),
        personDays = personDays.map(LifeConsoleHistoryDayDto::toRemoteModel),
        mealDays = mealDays.map(LifeConsoleHistoryDayDto::toRemoteModel),
        bowelDays = bowelDays.map(LifeConsoleBowelHistoryDayDto::toRemoteModel),
    )
}

fun LifeConsoleBowelMutationResponseDto.toRemoteModel(): RemoteLifeConsoleBowelMutation {
    return RemoteLifeConsoleBowelMutation(
        eventId = event?.bowelEventId,
        bowel = bowel.toRemoteModel(),
    )
}

fun AlbumDto.toRemoteModel(): RemoteAlbum {
    return RemoteAlbum(
        albumId = albumId,
        title = title,
        subtitle = subtitle,
        coverMediaId = coverMediaId,
        smallAlbumCount = smallAlbumCount,
        systemKey = systemKey,
        includeInPhotoFeed = includeInPhotoFeed,
    )
}

fun PostSummaryDto.toRemoteSummary(): RemotePostSummary {
    return RemotePostSummary(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        creatorUserId = creatorUserId,
        participantUserIds = participantUserIds.orEmpty(),
        displayTimeMillis = displayTimeMillis,
        eventStartedAtMillis = eventStartedAtMillis,
        eventEndedAtMillis = eventEndedAtMillis,
        displayTimeSource = displayTimeSource,
        albumId = albumId,
        coverMediaId = coverMediaId,
        mediaCount = mediaCount,
    )
}

fun PostMediaDto.toRemotePostMedia(): RemotePostMedia {
    val normalizedDisplayTime = media.displayTimeMillis.takeIf { it > 0L } ?: media.createdAtMillis ?: 0L
    val accessItems = media.access.orEmpty().map(MediaAccessDto::toRemoteModel)
    val previewAccess = accessItems.accessFor("preview")
    val originalAccess = accessItems.accessFor("original")
    val videoAccess = accessItems.accessFor("video")
    val coverAccess = accessItems.accessFor("cover")
    return RemotePostMedia(
        mediaId = media.mediaId,
        mediaType = media.mediaType?.ifBlank { media.type.orEmpty() }.orEmpty().ifBlank { "image" },
        previewUrl = previewAccess?.requestUrl ?: media.previewUrl ?: media.thumbnailUrl,
        originalUrl = originalAccess?.requestUrl ?: media.originalUrl,
        videoUrl = videoAccess?.requestUrl ?: media.videoUrl,
        width = media.width,
        height = media.height,
        aspectRatio = media.aspectRatio,
        displayTimeMillis = normalizedDisplayTime,
        commentCount = 0,
        isCover = isCover,
        videoDurationMillis = media.durationMillis ?: media.duration,
        thumbnailUrl = previewAccess?.requestUrl ?: media.thumbnailUrl ?: media.previewUrl,
        mediaUrl = originalAccess?.requestUrl ?: videoAccess?.requestUrl ?: media.mediaUrl ?: media.url,
        coverUrl = coverAccess?.requestUrl ?: media.coverUrl,
        mimeType = media.mimeType,
        createdAtMillis = media.createdAtMillis,
        capturedAtMillis = media.capturedAtMillis,
        importedAtMillis = media.importedAtMillis,
        displayTimeSource = media.displayTimeSource,
        uploadedByUserId = media.uploadedByUserId,
        access = accessItems,
    )
}

fun PostDetailDto.toRemoteDetail(): RemotePostDetail {
    return RemotePostDetail(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        creatorUserId = creatorUserId,
        participantUserIds = participantUserIds.orEmpty(),
        displayTimeMillis = displayTimeMillis,
        eventStartedAtMillis = eventStartedAtMillis,
        eventEndedAtMillis = eventEndedAtMillis,
        displayTimeSource = displayTimeSource,
        albumId = albumId,
        coverMediaId = coverMediaId,
        mediaItems = mediaItems.map(PostMediaDto::toRemotePostMedia),
    )
}

fun PostDetailDto.toRemoteSummary(): RemotePostSummary {
    return RemotePostSummary(
        postId = postId,
        title = title,
        summary = summary,
        contributorLabel = contributorLabel,
        creatorUserId = creatorUserId,
        participantUserIds = participantUserIds.orEmpty(),
        displayTimeMillis = displayTimeMillis,
        eventStartedAtMillis = eventStartedAtMillis,
        eventEndedAtMillis = eventEndedAtMillis,
        displayTimeSource = displayTimeSource,
        albumId = albumId,
        coverMediaId = coverMediaId,
        mediaCount = mediaCount,
    )
}

fun CommentDto.toRemoteModel(): RemoteComment {
    return RemoteComment(
        commentId = commentId,
        targetType = targetType,
        targetId = smallAlbumId ?: mediaId.orEmpty(),
        authorId = authorId,
        authorName = authorName,
        content = content.orEmpty(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        isDeleted = isDeleted,
    )
}

fun CommentListResponseDto.toRemotePage(): RemoteCommentPage {
    return RemoteCommentPage(
        comments = comments.map(CommentDto::toRemoteModel),
        page = page,
        size = size,
        hasMore = hasMore,
    )
}

fun TrashItemDto.toRemoteModel(): RemoteTrashItem {
    return RemoteTrashItem(
        trashItemId = trashItemId,
        itemType = itemType,
        state = state,
        actorUserId = actorUserId,
        sourceSmallAlbumId = sourceSmallAlbumId,
        sourceMediaId = sourceMediaId,
        commentTargetMediaId = commentTargetMediaId,
        title = title,
        previewInfo = previewInfo,
        deletedAtMillis = deletedAtMillis,
        relatedSmallAlbumIds = relatedSmallAlbumIds,
        relatedMediaIds = relatedMediaIds,
        sourceMediaType = sourceMediaType,
        sourceMediaWidth = sourceMediaWidth,
        sourceMediaHeight = sourceMediaHeight,
        sourceMediaAspectRatio = sourceMediaAspectRatio,
        sourceMediaDurationMillis = sourceMediaDurationMillis,
        sourceMediaMimeType = sourceMediaMimeType,
    )
}

fun PendingCleanupDto.toRemoteModel(): RemotePendingCleanup {
    return RemotePendingCleanup(
        trashItemId = trashItemId,
        removedAtMillis = removedAtMillis,
        undoDeadlineMillis = undoDeadlineMillis,
        item = item.toRemoteModel(),
    )
}

fun TrashDetailDto.toRemoteDetail(): RemoteTrashDetail {
    return RemoteTrashDetail(
        item = item.toRemoteModel(),
        canRestore = canRestore,
        canMoveOutOfTrash = canMoveOutOfTrash,
        pendingCleanup = pendingCleanup?.toRemoteModel(),
    )
}

fun UploadTokenDto.toRemoteModel(): RemoteUploadToken {
    return RemoteUploadToken(
        uploadId = uploadId,
        provider = provider,
        uploadUrl = uploadUrl,
        expireAtMillis = expireAtMillis,
        state = state,
        uploadMethod = uploadMethod ?: "multipart",
        objectKey = objectKey,
        headers = headers,
        confirmUrl = confirmUrl,
    )
}

fun UploadCompleteResponseDto.toRemoteModel(): RemoteUploadTask {
    return RemoteUploadTask(
        uploadId = uploadId,
        fileName = media.mediaId,
        mediaType = media.mediaType?.ifBlank { media.type.orEmpty() }.orEmpty().ifBlank { "image" },
        objectKey = media.url,
        mediaId = media.mediaId,
        state = state.toUploadState(),
        progressPercent = if (state.equals("success", ignoreCase = true)) 100 else 0,
        errorMessage = null,
        media = media.toRemoteModel(),
    )
}

fun UploadTaskDto.toRemoteModel(): RemoteUploadTask {
    return RemoteUploadTask(
        uploadId = uploadId,
        fileName = fileName,
        mediaType = mediaType,
        objectKey = objectKey,
        mediaId = mediaId,
        state = state.toUploadState(),
        progressPercent = progressPercent,
        errorMessage = errorMessage,
        media = media?.toRemoteModel(),
    )
}

private fun MediaAccessDto.toRemoteModel(): RemoteMediaAccess {
    return RemoteMediaAccess(
        variant = variant,
        url = url,
        signedUrl = signedUrl,
        expiresAtMillis = expiresAtMillis,
        cacheKey = cacheKey,
        revision = revision,
    )
}

private fun List<RemoteMediaAccess>.accessFor(variant: String): RemoteMediaAccess? {
    return firstOrNull { it.variant.equals(variant, ignoreCase = true) }
}

private fun String.toUploadState(): UploadState {
    return when (lowercase()) {
        "waiting" -> UploadState.WAITING
        "uploading" -> UploadState.UPLOADING
        "success" -> UploadState.SUCCESS
        "failure" -> UploadState.FAILURE
        "cancelled" -> UploadState.CANCELLED
        else -> UploadState.FAILURE
    }
}

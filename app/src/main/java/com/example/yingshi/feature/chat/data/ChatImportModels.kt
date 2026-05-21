package com.example.yingshi.feature.chat.data

import androidx.room.Embedded
import androidx.room.Relation

enum class ImportedChatType {
    PRIVATE,
    GROUP,
    UNKNOWN,
}

enum class ImportedResourceType {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    UNKNOWN,
}

enum class ImportedResourceRenderKind {
    IMAGE,
    STICKER,
    VIDEO,
    AUDIO,
    FILE,
    UNKNOWN,
}

enum class ImportedResourceDetectedFormat {
    GIF,
    JPEG,
    PNG,
    WEBP,
    SILK,
    AMR,
    MP3,
    WAV,
    AAC,
    MP4,
    UNKNOWN,
}

enum class ImportedViewerMode {
    MEDIA,
    STICKER,
}

data class ImportedMessageIdentityRow(
    val messageLocalId: Long,
    val messageStableKey: String,
    val sourceMessageId: String?,
    val fallbackSignature: String,
)

data class ImportedChatSummary(
    val chatId: Long,
    val chatStableKey: String,
    val displayName: String,
    val chatType: ImportedChatType,
    val messageCount: Int,
    val lastMessagePreview: String,
    val lastMessageType: String,
    val lastMessageAtMillis: Long,
    val lastImportedAtMillis: Long,
    val avatarLocalPath: String?,
    val sourceFileName: String?,
    val lastImportAddedMessageCount: Int,
    val lastImportMergedMessageCount: Int,
    val lastImportResourceCount: Int,
    val lastImportAvatarCount: Int,
)

data class ImportedParticipant(
    val participantId: Long,
    val chatId: Long,
    val participantStableKey: String,
    val uid: String?,
    val uin: String?,
    val displayName: String,
    val avatarLocalPath: String?,
    val avatarMimeType: String?,
    val isSelf: Boolean,
    val lastSeenAtMillis: Long,
)

data class ImportedResource(
    val resourceLocalId: Long,
    val ordinal: Int,
    val type: ImportedResourceType,
    val renderKind: ImportedResourceRenderKind,
    val originalFileName: String?,
    val storedFileName: String,
    val originalRelativePath: String?,
    val storedRelativePath: String,
    val mimeType: String?,
    val md5: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
    val localFilePath: String,
    val detectedFormat: ImportedResourceDetectedFormat,
    val resolvedMimeType: String?,
    val isAnimatedImage: Boolean,
)

data class ImportedMessage(
    val messageLocalId: Long,
    val chatId: Long,
    val messageStableKey: String,
    val sourceMessageId: String?,
    val fallbackSignature: String,
    val sourceSeq: String?,
    val timestamp: Long,
    val timeIso: String?,
    val senderUid: String?,
    val senderUin: String?,
    val senderDisplayName: String,
    val senderNickname: String?,
    val senderRemark: String?,
    val isSelf: Boolean,
    val type: String,
    val text: String,
    val html: String?,
    val rawContentJson: String,
    val rawMessageJson: String?,
    val replyToSourceMessageId: String?,
    val replyReferenceMessageId: String?,
    val replyPreviewText: String?,
    val replyReferenceSenderUin: String?,
    val replyReferenceSenderName: String?,
    val replyReferenceTimestampSeconds: Long?,
    val replyReferenceContent: String?,
    val jsonTitle: String?,
    val jsonSummary: String?,
    val jsonPreviewUrl: String?,
    val callSummary: String?,
    val recalled: Boolean,
    val system: Boolean,
    val searchText: String,
    val resources: List<ImportedResource>,
)

data class ImportedRenderableMessage(
    val message: ImportedMessage,
    val segments: List<ImportedMessageSegment>,
)

sealed interface ImportedMessageSegment {
    data class Text(
        val text: String,
    ) : ImportedMessageSegment

    data class Face(
        val faceId: String,
        val faceName: String,
    ) : ImportedMessageSegment

    data class Resource(
        val resource: ImportedResource,
        val originalElementType: String,
        val label: String?,
    ) : ImportedMessageSegment

    data class Reply(
        val sourceMessageId: String?,
        val referencedMessageId: String?,
        val senderUin: String?,
        val senderName: String?,
        val content: String?,
        val timestampSeconds: Long?,
    ) : ImportedMessageSegment

    data class JsonCard(
        val title: String?,
        val summary: String?,
        val previewUrl: String?,
    ) : ImportedMessageSegment

    data class CallRecord(
        val summary: String?,
    ) : ImportedMessageSegment

    data class Unknown(
        val type: String,
        val rawJson: String,
    ) : ImportedMessageSegment
}

data class ImportedChatDetail(
    val chatId: Long,
    val chatStableKey: String,
    val displayName: String,
    val chatType: ImportedChatType,
    val peerUid: String?,
    val selfUid: String?,
    val selfUin: String?,
    val selfName: String?,
    val sourceFileName: String?,
    val messageCount: Int,
    val lastMessageAtMillis: Long,
    val lastImportedAtMillis: Long,
    val lastImportAddedMessageCount: Int,
    val lastImportMergedMessageCount: Int,
    val lastImportResourceCount: Int,
    val lastImportAvatarCount: Int,
    val participants: List<ImportedParticipant>,
)

data class ImportedChatImportInfo(
    val chatId: Long,
    val displayName: String,
    val sourceFileName: String?,
    val lastImportedAtMillis: Long,
    val messageCount: Int,
    val lastImportAddedMessageCount: Int,
    val lastImportMergedMessageCount: Int,
    val lastImportResourceCount: Int,
    val lastImportAvatarCount: Int,
    val storageBytes: Long,
)

data class ImportedMessageSearchResult(
    val messageLocalId: Long,
    val messageStableKey: String,
    val timestamp: Long,
    val senderDisplayName: String,
    val previewText: String,
    val fullPreviewText: String = previewText,
)

data class ImportedMessageWindow(
    val items: List<ImportedRenderableMessage>,
    val startAnchor: MessageWindowAnchor?,
    val endAnchor: MessageWindowAnchor?,
    val hasOlder: Boolean,
    val hasNewer: Boolean,
)

data class MessageWindowAnchor(
    val timestamp: Long,
    val messageLocalId: Long,
)

data class ChatReadingAnchor(
    val messageLocalId: Long,
    val scrollOffset: Int,
    val savedAtMillis: Long,
)

data class ImportedMessageWithResources(
    @Embedded
    val message: ImportedMessageEntity,
    @Relation(
        parentColumn = "messageLocalId",
        entityColumn = "messageLocalId",
    )
    val resources: List<ImportedResourceEntity>,
)

data class ChatImportProgress(
    val stage: String,
    val message: String,
    val current: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else current.toFloat() / total.toFloat()
}

data class ChatImportResult(
    val chatId: Long,
    val importedMessageCount: Int,
    val mergedMessageCount: Int,
    val copiedResourceCount: Int,
    val copiedAvatarCount: Int,
    val skippedMessageCount: Int,
    val missingResourceCount: Int,
    val failedAvatarCount: Int,
    val warnings: List<String>,
)

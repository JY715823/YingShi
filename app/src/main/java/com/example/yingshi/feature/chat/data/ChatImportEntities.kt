package com.example.yingshi.feature.chat.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "imported_chats",
    indices = [
        Index(value = ["chatStableKey"], unique = true),
        Index(value = ["lastMessageAtMillis"]),
    ],
)
data class ImportedChatEntity(
    @PrimaryKey(autoGenerate = true)
    val chatId: Long = 0,
    val chatStableKey: String,
    val displayName: String,
    val chatType: String,
    val peerUid: String?,
    val sourceFileName: String?,
    val selfUid: String?,
    val selfUin: String?,
    val selfName: String?,
    val createdAtMillis: Long,
    val lastImportedAtMillis: Long,
    val lastMessageAtMillis: Long,
    val messageCount: Int,
    val lastMessagePreview: String,
    val lastMessageType: String,
    val lastImportAddedMessageCount: Int,
    val lastImportMergedMessageCount: Int,
    val lastImportResourceCount: Int,
    val lastImportAvatarCount: Int,
)

@Entity(
    tableName = "imported_participants",
    foreignKeys = [
        ForeignKey(
            entity = ImportedChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["chatId", "participantStableKey"], unique = true),
        Index(value = ["chatId", "uin"]),
    ],
)
data class ImportedParticipantEntity(
    @PrimaryKey(autoGenerate = true)
    val participantId: Long = 0,
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

@Entity(
    tableName = "imported_messages",
    foreignKeys = [
        ForeignKey(
            entity = ImportedChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["chatId", "messageStableKey"], unique = true),
        Index(value = ["chatId", "sourceMessageId"]),
        Index(value = ["chatId", "fallbackSignature"]),
        Index(value = ["chatId", "timestamp"]),
    ],
)
data class ImportedMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val messageLocalId: Long = 0,
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
)

@Entity(
    tableName = "imported_resources",
    foreignKeys = [
        ForeignKey(
            entity = ImportedMessageEntity::class,
            parentColumns = ["messageLocalId"],
            childColumns = ["messageLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["messageLocalId"]),
    ],
)
data class ImportedResourceEntity(
    @PrimaryKey(autoGenerate = true)
    val resourceLocalId: Long = 0,
    val messageLocalId: Long,
    val ordinal: Int,
    val type: String,
    val renderKind: String,
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
)

@Entity(
    tableName = "imported_message_search",
    foreignKeys = [
        ForeignKey(
            entity = ImportedMessageEntity::class,
            parentColumns = ["messageLocalId"],
            childColumns = ["messageLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId"]),
    ],
)
data class ImportedMessageSearchEntity(
    @PrimaryKey
    val messageLocalId: Long,
    val chatId: Long,
    val messageStableKey: String,
    val searchText: String,
)

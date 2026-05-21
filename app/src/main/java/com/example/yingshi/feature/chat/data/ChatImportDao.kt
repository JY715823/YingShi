package com.example.yingshi.feature.chat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatImportDao {
    @Query(
        """
        SELECT c.*, p.avatarLocalPath AS avatarLocalPath
        FROM imported_chats c
        LEFT JOIN imported_participants p
          ON p.chatId = c.chatId
         AND p.isSelf = 0
        GROUP BY c.chatId
        ORDER BY c.lastMessageAtMillis DESC, c.lastImportedAtMillis DESC
        """,
    )
    @RewriteQueriesToDropUnusedColumns
    fun observeChatSummaries(): Flow<List<ImportedChatSummaryRow>>

    @Query("SELECT * FROM imported_chats WHERE chatId = :chatId LIMIT 1")
    fun observeChat(chatId: Long): Flow<ImportedChatEntity?>

    @Query("SELECT * FROM imported_participants WHERE chatId = :chatId ORDER BY isSelf DESC, lastSeenAtMillis DESC, displayName ASC")
    fun observeParticipants(chatId: Long): Flow<List<ImportedParticipantEntity>>

    @Transaction
    @Query("SELECT * FROM imported_messages WHERE chatId = :chatId ORDER BY timestamp ASC, messageLocalId ASC")
    fun observeMessages(chatId: Long): Flow<List<ImportedMessageWithResources>>

    @Transaction
    @Query(
        """
        SELECT * FROM imported_messages
        WHERE chatId = :chatId
        ORDER BY timestamp DESC, messageLocalId DESC
        LIMIT :limit
        """,
    )
    suspend fun getLatestMessages(
        chatId: Long,
        limit: Int,
    ): List<ImportedMessageWithResources>

    @Transaction
    @Query(
        """
        SELECT * FROM imported_messages
        WHERE chatId = :chatId
          AND (
            timestamp < :anchorTimestamp
            OR (timestamp = :anchorTimestamp AND messageLocalId < :anchorMessageLocalId)
          )
        ORDER BY timestamp DESC, messageLocalId DESC
        LIMIT :limit
        """,
    )
    suspend fun getOlderMessagesBefore(
        chatId: Long,
        anchorTimestamp: Long,
        anchorMessageLocalId: Long,
        limit: Int,
    ): List<ImportedMessageWithResources>

    @Transaction
    @Query(
        """
        SELECT * FROM imported_messages
        WHERE chatId = :chatId
          AND (
            timestamp > :anchorTimestamp
            OR (timestamp = :anchorTimestamp AND messageLocalId > :anchorMessageLocalId)
          )
        ORDER BY timestamp ASC, messageLocalId ASC
        LIMIT :limit
        """,
    )
    suspend fun getNewerMessagesAfter(
        chatId: Long,
        anchorTimestamp: Long,
        anchorMessageLocalId: Long,
        limit: Int,
    ): List<ImportedMessageWithResources>

    @Query("SELECT * FROM imported_chats WHERE chatStableKey = :chatStableKey LIMIT 1")
    suspend fun findChatByStableKey(chatStableKey: String): ImportedChatEntity?

    @Query(
        """
        SELECT *
        FROM imported_chats
        WHERE chatType = :chatType
          AND selfUid = :selfUid
          AND peerUid = :peerUid
        ORDER BY messageCount DESC, lastImportedAtMillis DESC, chatId ASC
        LIMIT 1
        """,
    )
    suspend fun findChatByTypeAndSelfUidAndPeerUid(
        chatType: String,
        selfUid: String,
        peerUid: String,
    ): ImportedChatEntity?

    @Query(
        """
        SELECT c.*
        FROM imported_chats c
        INNER JOIN imported_participants p
           ON p.chatId = c.chatId
        WHERE c.chatType = :chatType
          AND c.selfUid = :selfUid
          AND p.isSelf = 0
          AND p.uid = :participantUid
        ORDER BY c.messageCount DESC, c.lastImportedAtMillis DESC, c.chatId ASC
        LIMIT 1
        """,
    )
    suspend fun findPrivateChatByParticipantUid(
        chatType: String,
        selfUid: String,
        participantUid: String,
    ): ImportedChatEntity?

    @Query(
        """
        SELECT c.*
        FROM imported_chats c
        INNER JOIN imported_participants p
           ON p.chatId = c.chatId
        WHERE c.chatType = :chatType
          AND c.selfUid = :selfUid
          AND p.isSelf = 0
          AND p.uin = :participantUin
        ORDER BY c.messageCount DESC, c.lastImportedAtMillis DESC, c.chatId ASC
        LIMIT 1
        """,
    )
    suspend fun findPrivateChatByParticipantUin(
        chatType: String,
        selfUid: String,
        participantUin: String,
    ): ImportedChatEntity?

    @Query("SELECT * FROM imported_chats WHERE chatId = :chatId LIMIT 1")
    suspend fun findChatById(chatId: Long): ImportedChatEntity?

    @Query("DELETE FROM imported_chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChat(chat: ImportedChatEntity): Long

    @Update
    suspend fun updateChat(chat: ImportedChatEntity)

    @Query(
        """
        SELECT messageLocalId, messageStableKey, sourceMessageId, fallbackSignature
        FROM imported_messages
        WHERE chatId = :chatId
        """,
    )
    suspend fun getMessageIdentityRows(chatId: Long): List<ImportedMessageIdentityRow>

    @Query(
        """
        SELECT messageLocalId, messageStableKey, sourceMessageId, fallbackSignature
        FROM imported_messages
        WHERE chatId = :chatId AND messageStableKey = :messageStableKey
        LIMIT 1
        """,
    )
    suspend fun findMessageIdentityByStableKey(
        chatId: Long,
        messageStableKey: String,
    ): ImportedMessageIdentityRow?

    @Query(
        """
        SELECT messageLocalId, messageStableKey, sourceMessageId, fallbackSignature
        FROM imported_messages
        WHERE chatId = :chatId AND sourceMessageId = :sourceMessageId
        LIMIT 1
        """,
    )
    suspend fun findMessageIdentityBySourceMessageId(
        chatId: Long,
        sourceMessageId: String,
    ): ImportedMessageIdentityRow?

    @Query(
        """
        SELECT messageLocalId, messageStableKey, sourceMessageId, fallbackSignature
        FROM imported_messages
        WHERE chatId = :chatId AND fallbackSignature = :fallbackSignature
        LIMIT 1
        """,
    )
    suspend fun findMessageIdentityByFallbackSignature(
        chatId: Long,
        fallbackSignature: String,
    ): ImportedMessageIdentityRow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: ImportedMessageEntity): Long

    @Update
    suspend fun updateMessage(message: ImportedMessageEntity)

    @Update
    suspend fun updateMessages(messages: List<ImportedMessageEntity>)

    @Query("SELECT * FROM imported_messages WHERE messageLocalId = :messageLocalId LIMIT 1")
    suspend fun getMessageByLocalId(messageLocalId: Long): ImportedMessageEntity?

    @Transaction
    @Query("SELECT * FROM imported_messages WHERE messageLocalId = :messageLocalId LIMIT 1")
    suspend fun getMessageWithResourcesByLocalId(messageLocalId: Long): ImportedMessageWithResources?

    @Query("DELETE FROM imported_resources WHERE messageLocalId = :messageLocalId")
    suspend fun deleteResourcesForMessage(messageLocalId: Long)

    @Query("SELECT * FROM imported_resources WHERE messageLocalId = :messageLocalId ORDER BY ordinal ASC, resourceLocalId ASC")
    suspend fun getResourcesForMessage(messageLocalId: Long): List<ImportedResourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<ImportedResourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceMessageSearch(search: ImportedMessageSearchEntity)

    @Query("DELETE FROM imported_message_search WHERE messageLocalId = :messageLocalId")
    suspend fun deleteMessageSearch(messageLocalId: Long)

    @Query(
        """
        SELECT participantId, chatId, participantStableKey, uid, uin, displayName, avatarLocalPath, avatarMimeType, isSelf, lastSeenAtMillis
        FROM imported_participants
        WHERE chatId = :chatId AND participantStableKey = :participantStableKey
        LIMIT 1
        """,
    )
    suspend fun findParticipant(
        chatId: Long,
        participantStableKey: String,
    ): ImportedParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParticipant(participant: ImportedParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: ImportedParticipantEntity)

    @Query("SELECT COUNT(*) FROM imported_messages WHERE chatId = :chatId")
    suspend fun countMessages(chatId: Long): Int

    @Query(
        """
        SELECT * FROM imported_messages
        WHERE chatId = :chatId
        ORDER BY timestamp DESC, messageLocalId DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestMessage(chatId: Long): ImportedMessageEntity?

    @Query(
        """
        SELECT m.messageLocalId AS messageLocalId,
               m.messageStableKey AS messageStableKey,
               m.timestamp AS timestamp,
               m.senderDisplayName AS senderDisplayName,
               m.type AS type,
               m.text AS text,
               m.rawContentJson AS rawContentJson,
               m.rawMessageJson AS rawMessageJson,
               m.replyPreviewText AS replyPreviewText,
               m.jsonTitle AS jsonTitle,
               m.jsonSummary AS jsonSummary,
               m.callSummary AS callSummary,
               m.system AS system,
               m.recalled AS recalled
        FROM imported_message_search s
        INNER JOIN imported_messages m
           ON m.messageLocalId = s.messageLocalId
        WHERE s.chatId = :chatId
          AND s.searchText LIKE '%' || :query || '%'
        ORDER BY m.timestamp ASC, m.messageLocalId ASC
        LIMIT 200
        """,
    )
    fun observeSearchResultRows(
        chatId: Long,
        query: String,
    ): Flow<List<ImportedMessageSearchRow>>

    @Query("SELECT * FROM imported_chats ORDER BY chatId ASC")
    suspend fun getAllChats(): List<ImportedChatEntity>

    @Query(
        """
        SELECT * FROM imported_messages
        WHERE chatId = :chatId
        ORDER BY timestamp ASC, messageLocalId ASC
        """,
    )
    suspend fun getMessagesForChat(chatId: Long): List<ImportedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceMessageSearchEntries(entries: List<ImportedMessageSearchEntity>)

    @Query("DELETE FROM imported_message_search WHERE chatId = :chatId")
    suspend fun deleteMessageSearchForChat(chatId: Long)

    @Query(
        """
        SELECT messageLocalId
        FROM imported_messages
        WHERE chatId = :chatId
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY timestamp ASC, messageLocalId ASC
        LIMIT 1
        """,
    )
    suspend fun findFirstMessageInRange(
        chatId: Long,
        startMillis: Long,
        endMillis: Long,
    ): Long?

    @Query(
        """
        SELECT messageLocalId
        FROM imported_messages
        WHERE chatId = :chatId
          AND sourceMessageId = :sourceMessageId
        ORDER BY timestamp ASC, messageLocalId ASC
        LIMIT 1
        """,
    )
    suspend fun findMessageLocalIdBySourceMessageId(
        chatId: Long,
        sourceMessageId: String,
    ): Long?

    @Query(
        """
        SELECT messageLocalId
        FROM imported_messages
        WHERE chatId = :chatId
          AND replyReferenceMessageId = :replyReferenceMessageId
        ORDER BY timestamp ASC, messageLocalId ASC
        LIMIT 1
        """,
    )
    suspend fun findMessageLocalIdByReplyReferenceMessageId(
        chatId: Long,
        replyReferenceMessageId: String,
    ): Long?

    @Query(
        """
        SELECT messageLocalId
        FROM imported_messages
        WHERE chatId = :chatId
          AND senderUin = :senderUin
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY ABS(timestamp - :targetMillis) ASC, messageLocalId ASC
        LIMIT 10
        """,
    )
    suspend fun findNearbyMessagesBySenderAndTime(
        chatId: Long,
        senderUin: String,
        startMillis: Long,
        endMillis: Long,
        targetMillis: Long,
    ): List<Long>

    @Query(
        """
        SELECT m.messageLocalId
        FROM imported_messages m
        WHERE m.chatId = :chatId
          AND m.senderUin = :senderUin
          AND m.timestamp BETWEEN :startMillis AND :endMillis
          AND (
            m.text LIKE '%' || :content || '%'
            OR m.replyPreviewText LIKE '%' || :content || '%'
            OR m.rawContentJson LIKE '%' || :content || '%'
          )
        ORDER BY ABS(m.timestamp - :targetMillis) ASC, m.messageLocalId ASC
        LIMIT 10
        """,
    )
    suspend fun findNearbyMessagesBySenderTimeAndContent(
        chatId: Long,
        senderUin: String,
        startMillis: Long,
        endMillis: Long,
        targetMillis: Long,
        content: String,
    ): List<Long>

    @Transaction
    @Query(
        """
        SELECT *
        FROM imported_messages
        WHERE chatId = :chatId
          AND senderUin = :senderUin
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY ABS(timestamp - :targetMillis) ASC, messageLocalId ASC
        LIMIT :limit
        """,
    )
    suspend fun getNearbyMessageRowsBySenderAndTime(
        chatId: Long,
        senderUin: String,
        startMillis: Long,
        endMillis: Long,
        targetMillis: Long,
        limit: Int,
    ): List<ImportedMessageWithResources>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM imported_messages
            WHERE chatId = :chatId
              AND (
                timestamp < :anchorTimestamp
                OR (timestamp = :anchorTimestamp AND messageLocalId < :anchorMessageLocalId)
              )
        )
        """,
    )
    suspend fun hasOlderMessages(
        chatId: Long,
        anchorTimestamp: Long,
        anchorMessageLocalId: Long,
    ): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM imported_messages
            WHERE chatId = :chatId
              AND (
                timestamp > :anchorTimestamp
                OR (timestamp = :anchorTimestamp AND messageLocalId > :anchorMessageLocalId)
              )
        )
        """,
    )
    suspend fun hasNewerMessages(
        chatId: Long,
        anchorTimestamp: Long,
        anchorMessageLocalId: Long,
    ): Boolean
}

data class ImportedChatSummaryRow(
    val chatId: Long,
    val chatStableKey: String,
    val displayName: String,
    val chatType: String,
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

data class ImportedMessageSearchRow(
    val messageLocalId: Long,
    val messageStableKey: String,
    val timestamp: Long,
    val senderDisplayName: String,
    val type: String,
    val text: String,
    val rawContentJson: String,
    val rawMessageJson: String?,
    val replyPreviewText: String?,
    val jsonTitle: String?,
    val jsonSummary: String?,
    val callSummary: String?,
    val system: Boolean,
    val recalled: Boolean,
)

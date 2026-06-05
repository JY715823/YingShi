package com.example.yingshi.feature.chat.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.yingshi.feature.chat.QFaceCatalog
import com.example.yingshi.feature.chat.QFaceCatalogStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ImportedChatRepository(
    private val appContext: Context,
    private val database: ChatImportDatabase = ChatImportDatabase.getInstance(appContext),
    private val dao: ChatImportDao = database.chatImportDao(),
    private val gson: Gson = Gson(),
    private val syncBridge: ChatSyncBridge = NoOpChatSyncBridge,
    baseDir: File? = null,
    tempDir: File? = null,
) {
    private val latestWindowSize = 80
    private val pageLoadSize = 50
    private val maintenancePrefs = appContext.getSharedPreferences("chat_import_maintenance", Context.MODE_PRIVATE)
    private val readingAnchorPrefs = appContext.getSharedPreferences("chat_import_reading_anchor", Context.MODE_PRIVATE)
    private val importsBaseDir = (baseDir ?: appContext.filesDir.resolve("chat-imports")).apply { mkdirs() }
    private val importsTempDir = (tempDir ?: appContext.cacheDir.resolve("chat-imports-temp")).apply { mkdirs() }
    @Volatile
    private var didEnsureMaintenance = false
    private val qFaceCatalog: QFaceCatalog? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        QFaceCatalogStore.getOrLoad(appContext)
    }

    suspend fun hydrateFromRemoteIfNeeded() {
        syncBridge.hydrate(this)
    }

    suspend fun exportLocalSnapshot(): ChatLocalSnapshot {
        return withContext(Dispatchers.IO) {
            ChatLocalSnapshot(
                chats = dao.getAllChats(),
                participants = dao.getAllParticipants(),
                messages = dao.getAllMessages(),
                resources = dao.getAllResources(),
                messageSearchEntries = dao.getAllMessageSearchEntries(),
                readingAnchors = exportReadingAnchors(),
            )
        }
    }

    suspend fun replaceLocalSnapshot(snapshot: ChatLocalSnapshot) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.clearMessageSearch()
                dao.clearResources()
                dao.clearMessages()
                dao.clearParticipants()
                dao.clearChats()
                if (snapshot.chats.isNotEmpty()) dao.insertChats(snapshot.chats)
                if (snapshot.participants.isNotEmpty()) dao.insertParticipants(snapshot.participants)
                if (snapshot.messages.isNotEmpty()) dao.insertMessages(snapshot.messages)
                if (snapshot.resources.isNotEmpty()) dao.insertResources(snapshot.resources)
                if (snapshot.messageSearchEntries.isNotEmpty()) {
                    dao.replaceMessageSearchEntries(snapshot.messageSearchEntries)
                }
            }
            replaceReadingAnchors(snapshot.readingAnchors)
            Unit
        }
    }

    fun observeChatSummaries(): Flow<List<ImportedChatSummary>> {
        return dao.observeChatSummaries().map { rows ->
            rows.map { row ->
                ImportedChatSummary(
                    chatId = row.chatId,
                    chatStableKey = row.chatStableKey,
                    displayName = row.displayName,
                    chatType = row.chatType.toImportedChatType(),
                    messageCount = row.messageCount,
                    lastMessagePreview = row.lastMessagePreview,
                    lastMessageType = row.lastMessageType,
                    lastMessageAtMillis = row.lastMessageAtMillis,
                    lastImportedAtMillis = row.lastImportedAtMillis,
                    avatarLocalPath = row.avatarLocalPath,
                    sourceFileName = row.sourceFileName,
                    lastImportAddedMessageCount = row.lastImportAddedMessageCount,
                    lastImportMergedMessageCount = row.lastImportMergedMessageCount,
                    lastImportResourceCount = row.lastImportResourceCount,
                    lastImportAvatarCount = row.lastImportAvatarCount,
                )
            }
        }
    }

    fun observeChatDetail(chatId: Long): Flow<ImportedChatDetail?> {
        return combine(
            dao.observeChat(chatId),
            dao.observeParticipants(chatId),
        ) { chat, participants ->
            chat?.toImportedChatDetail(participants.map(::toImportedParticipant))
        }
    }

    suspend fun getImportedChatImportInfo(chatId: Long): ImportedChatImportInfo? {
        return withContext(Dispatchers.IO) {
            dao.findChatById(chatId)?.let { chat ->
                ImportedChatImportInfo(
                    chatId = chat.chatId,
                    displayName = chat.displayName,
                    sourceFileName = chat.sourceFileName,
                    lastImportedAtMillis = chat.lastImportedAtMillis,
                    messageCount = chat.messageCount,
                    lastImportAddedMessageCount = chat.lastImportAddedMessageCount,
                    lastImportMergedMessageCount = chat.lastImportMergedMessageCount,
                    lastImportResourceCount = chat.lastImportResourceCount,
                    lastImportAvatarCount = chat.lastImportAvatarCount,
                    storageBytes = chatStorageDir(chat.chatStableKey).directorySizeBytes(),
                )
            }
        }
    }

    suspend fun hasImportedChat(chatId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            dao.findChatById(chatId) != null
        }
    }

    suspend fun deleteImportedChat(chatId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val chat = dao.findChatById(chatId) ?: return@withContext false
            database.withTransaction {
                dao.deleteChatById(chatId)
            }
            clearReadingAnchor(chatId)
            chatStorageDir(chat.chatStableKey).deleteRecursively()
            syncBridge.afterMutation(this@ImportedChatRepository)
            true
        }
    }

    fun observeChatMessages(chatId: Long): Flow<List<ImportedMessage>> {
        return combine(
            dao.observeChat(chatId),
            dao.observeMessages(chatId),
        ) { chat, messages ->
            val chatStableKey = chat?.chatStableKey ?: return@combine emptyList()
            messages.map { row ->
                row.message.toImportedMessage(
                    resources = row.resources.map { resource ->
                        resource.toImportedResource(
                            absoluteFilePath = chatStorageDir(chatStableKey)
                                .resolve(resource.storedRelativePath)
                                .absolutePath,
                        )
                    },
                )
            }
        }
    }

    suspend fun loadLatestMessageWindow(chatId: Long): ImportedMessageWindow {
        return withContext(Dispatchers.IO) {
            buildMessageWindowFromRows(
                chatId = chatId,
                rows = dao.getLatestMessages(chatId, latestWindowSize),
            )
        }
    }

    suspend fun loadWindowAroundMessage(
        chatId: Long,
        messageLocalId: Long,
        windowSize: Int = latestWindowSize,
    ): ImportedMessageWindow {
        return withContext(Dispatchers.IO) {
            val center = dao.getMessageWithResourcesByLocalId(messageLocalId)
                ?: return@withContext ImportedMessageWindow(
                    items = emptyList(),
                    startAnchor = null,
                    endAnchor = null,
                    hasOlder = false,
                    hasNewer = false,
                )
            val before = dao.getOlderMessagesBefore(
                chatId = chatId,
                anchorTimestamp = center.message.timestamp,
                anchorMessageLocalId = center.message.messageLocalId,
                limit = windowSize / 2,
            ).asReversed()
            val after = dao.getNewerMessagesAfter(
                chatId = chatId,
                anchorTimestamp = center.message.timestamp,
                anchorMessageLocalId = center.message.messageLocalId,
                limit = windowSize / 2,
            )
            val rows = buildList {
                addAll(before)
                add(center)
                addAll(after)
            }.takeLast(windowSize)
            buildMessageWindowFromRows(chatId, rows)
        }
    }

    suspend fun loadOlderMessages(
        chatId: Long,
        anchor: MessageWindowAnchor,
    ): List<ImportedRenderableMessage> {
        return withContext(Dispatchers.IO) {
            val chat = dao.findChatById(chatId) ?: return@withContext emptyList()
            dao.getOlderMessagesBefore(
                chatId = chatId,
                anchorTimestamp = anchor.timestamp,
                anchorMessageLocalId = anchor.messageLocalId,
                limit = pageLoadSize,
            ).asReversed().map { row -> row.toRenderableMessage(chat.chatStableKey) }
        }
    }

    suspend fun loadNewerMessages(
        chatId: Long,
        anchor: MessageWindowAnchor,
    ): List<ImportedRenderableMessage> {
        return withContext(Dispatchers.IO) {
            val chat = dao.findChatById(chatId) ?: return@withContext emptyList()
            dao.getNewerMessagesAfter(
                chatId = chatId,
                anchorTimestamp = anchor.timestamp,
                anchorMessageLocalId = anchor.messageLocalId,
                limit = pageLoadSize,
            ).map { row -> row.toRenderableMessage(chat.chatStableKey) }
        }
    }

    suspend fun hasOlderMessages(
        chatId: Long,
        anchor: MessageWindowAnchor,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.hasOlderMessages(
            chatId = chatId,
            anchorTimestamp = anchor.timestamp,
            anchorMessageLocalId = anchor.messageLocalId,
        )
    }

    suspend fun hasNewerMessages(
        chatId: Long,
        anchor: MessageWindowAnchor,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.hasNewerMessages(
            chatId = chatId,
            anchorTimestamp = anchor.timestamp,
            anchorMessageLocalId = anchor.messageLocalId,
        )
    }

    fun searchChatMessages(chatId: Long, query: String): Flow<List<ImportedMessageSearchResult>> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return flowOf(emptyList())
        return dao.observeSearchResultRows(chatId, normalized).map { rows ->
            rows.map { row ->
                val presentation = buildImportedMessagePresentation(
                    type = row.type,
                    text = row.text,
                    rawContentJson = row.rawContentJson,
                    rawMessageJson = row.rawMessageJson,
                    replyPreviewText = row.replyPreviewText,
                    jsonTitle = row.jsonTitle,
                    jsonSummary = row.jsonSummary,
                    callSummary = row.callSummary,
                    system = row.system,
                    recalled = row.recalled,
                    qFaceCatalog = qFaceCatalog,
                )
                ImportedMessageSearchResult(
                    messageLocalId = row.messageLocalId,
                    messageStableKey = row.messageStableKey,
                    timestamp = row.timestamp,
                    senderDisplayName = row.senderDisplayName,
                    previewText = presentation.previewText,
                    fullPreviewText = presentation.previewText,
                )
            }
        }
    }

    suspend fun ensurePresentationMaintenance() {
        if (didEnsureMaintenance || currentMaintenanceVersion() >= ChatImportPresentationMaintenanceVersion) {
            didEnsureMaintenance = true
            return
        }
        withContext(Dispatchers.IO) {
            if (didEnsureMaintenance || currentMaintenanceVersion() >= ChatImportPresentationMaintenanceVersion) {
                didEnsureMaintenance = true
                return@withContext
            }
            val chats = dao.getAllChats()
            chats.forEach { chat ->
                val messages = dao.getMessagesForChat(chat.chatId)
                if (messages.isEmpty()) return@forEach
                val updatedMessages = messages.map { message ->
                    val presentation = buildImportedMessagePresentation(
                        type = message.type,
                        text = message.text,
                        rawContentJson = message.rawContentJson,
                        rawMessageJson = message.rawMessageJson,
                        replyPreviewText = message.replyPreviewText,
                        jsonTitle = message.jsonTitle,
                        jsonSummary = message.jsonSummary,
                        callSummary = message.callSummary,
                        system = message.system,
                        recalled = message.recalled,
                        qFaceCatalog = qFaceCatalog,
                    )
                    message.copy(searchText = presentation.searchText)
                }
                val searchEntries = updatedMessages.map { message ->
                    ImportedMessageSearchEntity(
                        messageLocalId = message.messageLocalId,
                        chatId = message.chatId,
                        messageStableKey = message.messageStableKey,
                        searchText = message.searchText,
                    )
                }
                val latestMessage = updatedMessages.lastOrNull()
                val latestPreview = latestMessage?.let { latest ->
                    buildImportedMessagePresentation(
                        type = latest.type,
                        text = latest.text,
                        rawContentJson = latest.rawContentJson,
                        rawMessageJson = latest.rawMessageJson,
                        replyPreviewText = latest.replyPreviewText,
                        jsonTitle = latest.jsonTitle,
                        jsonSummary = latest.jsonSummary,
                        callSummary = latest.callSummary,
                        system = latest.system,
                        recalled = latest.recalled,
                        qFaceCatalog = qFaceCatalog,
                    ).previewText
                }.orEmpty()
                database.withTransaction {
                    dao.updateMessages(updatedMessages)
                    dao.deleteMessageSearchForChat(chat.chatId)
                    dao.replaceMessageSearchEntries(searchEntries)
                    dao.updateChat(chat.copy(lastMessagePreview = latestPreview))
                }
            }
            setMaintenanceVersion(ChatImportPresentationMaintenanceVersion)
            didEnsureMaintenance = true
        }
    }

    private fun currentMaintenanceVersion(): Int {
        return maintenancePrefs.getInt("presentation_version", 0)
    }

    private fun setMaintenanceVersion(version: Int) {
        maintenancePrefs.edit {
            putInt("presentation_version", version)
        }
    }

    suspend fun jumpToDate(chatId: Long, date: LocalDate): Long? {
        return withContext(Dispatchers.IO) {
            val zoneId = ZoneId.systemDefault()
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
            dao.findFirstMessageInRange(chatId, start, end)
        }
    }

    suspend fun jumpToSourceMessageId(chatId: Long, sourceMessageId: String): Long? {
        return withContext(Dispatchers.IO) {
            dao.findMessageLocalIdBySourceMessageId(chatId, sourceMessageId)
        }
    }

    suspend fun saveReadingAnchor(
        chatId: Long,
        anchor: ChatReadingAnchor,
    ) = withContext(Dispatchers.IO) {
        readingAnchorPrefs.edit {
            putLong("chat_${chatId}_message_id", anchor.messageLocalId)
            putInt("chat_${chatId}_offset", anchor.scrollOffset)
            putLong("chat_${chatId}_saved_at", anchor.savedAtMillis)
        }
        syncBridge.afterMutation(this@ImportedChatRepository)
    }

    suspend fun loadReadingAnchor(chatId: Long): ChatReadingAnchor? = withContext(Dispatchers.IO) {
        val messageLocalId = readingAnchorPrefs.getLong("chat_${chatId}_message_id", -1L)
        if (messageLocalId <= 0L) return@withContext null
        ChatReadingAnchor(
            messageLocalId = messageLocalId,
            scrollOffset = readingAnchorPrefs.getInt("chat_${chatId}_offset", 0),
            savedAtMillis = readingAnchorPrefs.getLong("chat_${chatId}_saved_at", 0L),
        )
    }

    suspend fun clearReadingAnchor(chatId: Long) = withContext(Dispatchers.IO) {
        readingAnchorPrefs.edit {
            remove("chat_${chatId}_message_id")
            remove("chat_${chatId}_offset")
            remove("chat_${chatId}_saved_at")
        }
        syncBridge.afterMutation(this@ImportedChatRepository)
    }

    suspend fun resolveReplyTarget(
        chatId: Long,
        sourceMessageId: String?,
        replyReferenceMessageId: String?,
        replyReferenceSenderUin: String?,
        replyReferenceTimestampSeconds: Long?,
        replyReferenceContent: String?,
    ): Long? {
        return withContext(Dispatchers.IO) {
            if (!sourceMessageId.isNullOrBlank()) {
                dao.findMessageLocalIdBySourceMessageId(chatId, sourceMessageId)?.let { return@withContext it }
            }
            if (!replyReferenceMessageId.isNullOrBlank()) {
                dao.findMessageLocalIdByReplyReferenceMessageId(chatId, replyReferenceMessageId)?.let { return@withContext it }
                dao.findMessageLocalIdBySourceMessageId(chatId, replyReferenceMessageId)?.let { return@withContext it }
            }
            if (!replyReferenceSenderUin.isNullOrBlank() && replyReferenceTimestampSeconds != null) {
                val targetMillis = replyReferenceTimestampSeconds * 1000L
                val window = 12_000L
                val start = targetMillis - window
                val end = targetMillis + window
                val nearbyRows = dao.getNearbyMessageRowsBySenderAndTime(
                    chatId = chatId,
                    senderUin = replyReferenceSenderUin,
                    startMillis = start,
                    endMillis = end,
                    targetMillis = targetMillis,
                    limit = 16,
                )
                parseReplyReferenceMarker(replyReferenceContent)?.let { marker ->
                    nearbyRows.firstOrNull { row -> row.matchesReplyMarker(marker) }
                        ?.message
                        ?.messageLocalId
                        ?.let { return@withContext it }
                }
                if (!replyReferenceContent.isNullOrBlank()) {
                    val byContent = dao.findNearbyMessagesBySenderTimeAndContent(
                        chatId = chatId,
                        senderUin = replyReferenceSenderUin,
                        startMillis = start,
                        endMillis = end,
                        targetMillis = targetMillis,
                        content = replyReferenceContent,
                    )
                    if (byContent.isNotEmpty()) return@withContext byContent.first()
                }
                val nearby = dao.findNearbyMessagesBySenderAndTime(
                    chatId = chatId,
                    senderUin = replyReferenceSenderUin,
                    startMillis = start,
                    endMillis = end,
                    targetMillis = targetMillis,
                )
                if (nearby.isNotEmpty()) return@withContext nearby.first()
                nearbyRows.firstOrNull()?.message?.messageLocalId?.let { return@withContext it }
            }
            null
        }
    }

    suspend fun importFromZip(
        uri: Uri,
        onProgress: (ChatImportProgress) -> Unit = {},
    ): ChatImportResult = withContext(Dispatchers.IO) {
        val importToken = UUID.randomUUID().toString()
        val unzipDir = importsTempDir.resolve(importToken)
        unzipDir.mkdirs()
        try {
            onProgress(ChatImportProgress("prepare", "准备读取 ZIP", 0, 1))
            val sourceDisplayName = queryDisplayName(uri)
            val zipSizeBytes = queryDocumentSize(uri)
            ensureUsableImportSpace(zipSizeBytes)
            unzipZip(uri, unzipDir)
            onProgress(ChatImportProgress("extract", "ZIP 解压完成", 1, 1))

            val importRoot = findImportRoot(unzipDir)
                ?: error("未在 ZIP 中找到 manifest.json")
            val manifestFile = importRoot.resolve("manifest.json")
            val manifest = gson.fromJson(
                manifestFile.readText(Charsets.UTF_8),
                QceManifest::class.java,
            ) ?: error("无法解析 manifest.json")
            val chunkFiles = resolveChunkFiles(importRoot, manifest)
            if (chunkFiles.isEmpty()) error("未找到 chunks/*.jsonl")

            val parsedFileInfo = sourceDisplayName?.let(::parseQceZipDisplayName)
            val totalMessages = manifest.statistics?.totalMessages
                ?: manifest.chunked?.chunks?.sumOf { it.count ?: 0 }
                ?: 0

            onProgress(ChatImportProgress("parse", "正在读取聊天消息", 0, totalMessages))

            val senderUidSet = linkedSetOf<String>()
            val nonSelfParticipantUids = linkedSetOf<String>()
            val nonSelfParticipantUins = linkedSetOf<String>()
            val warnings = linkedSetOf<String>()
            var skippedMessageCount = 0
            var processed = 0
            chunkFiles.sortedBy { it.name }.forEach { chunkFile ->
                chunkFile.useLines(Charsets.UTF_8) { lines ->
                    lines.filter { it.isNotBlank() }.forEachIndexed { index, line ->
                        runCatching {
                            parsePreparedMessage(
                                line = line,
                                selfUid = manifest.chatInfo?.selfUid,
                                selfUin = manifest.chatInfo?.selfUin,
                            )
                        }.onSuccess { prepared ->
                            senderUidSet += listOfNotNull(prepared.senderUid)
                            if (!prepared.isSelf) {
                                prepared.senderUid?.takeIf { it.isNotBlank() }?.let(nonSelfParticipantUids::add)
                                prepared.senderUin?.takeIf { it.isNotBlank() }?.let(nonSelfParticipantUins::add)
                            }
                        }.onFailure { throwable ->
                            skippedMessageCount += 1
                            warnings += buildWarning(
                                kind = "message_parse",
                                detail = "分片 ${chunkFile.name} 第 ${index + 1} 条消息解析失败，已跳过",
                                cause = throwable,
                            )
                        }
                        processed += 1
                        if (processed % 200 == 0 || processed == totalMessages) {
                            onProgress(
                                ChatImportProgress(
                                    stage = "parse",
                                    message = "正在解析消息",
                                    current = processed,
                                    total = totalMessages,
                                ),
                            )
                        }
                    }
                }
            }

            val chatType = manifest.chatInfo?.type?.toImportedChatType() ?: ImportedChatType.UNKNOWN
            val privatePeerIdentity = resolvePrivatePeerIdentity(
                chatType = chatType,
                participantUids = nonSelfParticipantUids,
                participantUins = nonSelfParticipantUins,
            )
            val stableKey = buildChatStableKey(
                sourceDisplayName = sourceDisplayName,
                parsedFileInfo = parsedFileInfo,
                chatInfo = manifest.chatInfo,
                chatType = chatType,
                senderUidSet = senderUidSet,
                privatePeerIdentity = privatePeerIdentity,
            )
            val resolvedChatTarget = resolveExistingChatTarget(
                chatType = chatType,
                proposedStableKey = stableKey,
                selfUid = manifest.chatInfo?.selfUid,
                parsedPeerUid = parsedFileInfo?.chatId?.takeIf { it.isNotBlank() },
                privatePeerIdentity = privatePeerIdentity,
            )
            val resolvedStableKey = resolvedChatTarget.chatStableKey
            val chatStorageDir = resolvedChatTarget.storageDir.apply {
                resolve("resources").mkdirs()
                resolve("avatars").mkdirs()
            }

            val avatarMap = runCatching {
                loadAvatarPayloads(importRoot, manifest)
            }.onFailure { throwable ->
                warnings += buildWarning(
                    kind = "avatar_manifest",
                    detail = "头像数据读取失败，已回退为默认头像",
                    cause = throwable,
                )
            }.getOrDefault(emptyMap())
            onProgress(ChatImportProgress("import", "正在合并到本地数据库", 0, totalMessages))

            var insertedCount = 0
            var mergedCount = 0
            var copiedResourceCount = 0
            var copiedAvatarCount = 0
            var missingResourceCount = 0
            var failedAvatarCount = 0

            val now = System.currentTimeMillis()
            val chatId = database.withTransaction {
                val existingChat = resolvedChatTarget.existingChat ?: dao.findChatByStableKey(resolvedStableKey)
                val importedDisplayName = resolveImportedChatDisplayName(
                    manifestName = manifest.chatInfo?.name,
                    parsedFileInfo = parsedFileInfo,
                    fallbackSourceDisplayName = sourceDisplayName,
                    existingDisplayName = existingChat?.displayName,
                )
                val importedPeerUid = resolveImportedChatPeerUid(
                    chatType = chatType,
                    parsedPeerUid = parsedFileInfo?.chatId,
                    privatePeerIdentity = privatePeerIdentity,
                    existingPeerUid = existingChat?.peerUid,
                )
                if (existingChat == null) {
                    dao.insertChat(
                        ImportedChatEntity(
                            chatStableKey = resolvedStableKey,
                            displayName = importedDisplayName,
                            chatType = chatType.name,
                            peerUid = importedPeerUid,
                            sourceFileName = sourceDisplayName,
                            selfUid = manifest.chatInfo?.selfUid,
                            selfUin = manifest.chatInfo?.selfUin,
                            selfName = manifest.chatInfo?.selfName,
                            createdAtMillis = now,
                            lastImportedAtMillis = now,
                            lastMessageAtMillis = 0L,
                            messageCount = 0,
                            lastMessagePreview = "",
                            lastMessageType = "",
                            lastImportAddedMessageCount = 0,
                            lastImportMergedMessageCount = 0,
                            lastImportResourceCount = 0,
                            lastImportAvatarCount = 0,
                        ),
                    )
                } else {
                    dao.updateChat(
                        existingChat.copy(
                            displayName = importedDisplayName,
                            peerUid = importedPeerUid,
                            sourceFileName = sourceDisplayName ?: existingChat.sourceFileName,
                            selfUid = manifest.chatInfo?.selfUid ?: existingChat.selfUid,
                            selfUin = manifest.chatInfo?.selfUin ?: existingChat.selfUin,
                            selfName = manifest.chatInfo?.selfName ?: existingChat.selfName,
                            lastImportedAtMillis = now,
                        ),
                    )
                    existingChat.chatId
                }
            }

            val storageAvatarPaths = mutableMapOf<String, StoredAvatarPayload>()
            var importedProgress = 0
            chunkFiles.sortedBy { it.name }.forEach { chunkFile ->
                chunkFile.useLines(Charsets.UTF_8) { lines ->
                    lines.filter { it.isNotBlank() }.forEachIndexed { index, line ->
                        val prepared = runCatching {
                            parsePreparedMessage(
                                line = line,
                                selfUid = manifest.chatInfo?.selfUid,
                                selfUin = manifest.chatInfo?.selfUin,
                            )
                        }.getOrElse { throwable ->
                            importedProgress += 1
                            if (importedProgress % 100 == 0 || importedProgress == totalMessages) {
                                onProgress(
                                    ChatImportProgress(
                                        stage = "import",
                                        message = "正在写入本地数据库",
                                        current = importedProgress,
                                        total = totalMessages,
                                    ),
                                )
                            }
                            if (warnings.none { it.contains("[message_parse]") }) {
                                warnings += buildWarning(
                                    kind = "message_parse",
                                    detail = "有部分消息在写入阶段再次解析失败，已跳过",
                                    cause = throwable,
                                )
                            }
                            return@forEachIndexed
                        }

                        database.withTransaction {
                            val identity = resolveExistingMessageIdentity(
                                chatId = chatId,
                                prepared = prepared,
                                chatStableKey = resolvedStableKey,
                            )
                            val messageStableKey = buildMessageStableKey(resolvedStableKey, prepared.sourceMessageId, prepared.fallbackSignature)
                            val existingMessage = identity?.messageLocalId?.let { existingMessageLocalId ->
                                dao.getMessageByLocalId(existingMessageLocalId)
                            }
                            val previewPresentation = buildImportedMessagePresentation(
                                type = prepared.type,
                                text = prepared.text,
                                rawContentJson = prepared.rawContentJson,
                                rawMessageJson = prepared.rawMessageJson,
                                replyPreviewText = prepared.replyPreviewText,
                                jsonTitle = prepared.jsonTitle,
                                jsonSummary = prepared.jsonSummary,
                                callSummary = prepared.callSummary,
                                system = prepared.system,
                                recalled = prepared.recalled,
                                qFaceCatalog = qFaceCatalog,
                            )
                            val messageEntity = ImportedMessageEntity(
                                messageLocalId = identity?.messageLocalId ?: 0L,
                                chatId = chatId,
                                messageStableKey = messageStableKey,
                                sourceMessageId = preferString(prepared.sourceMessageId, existingMessage?.sourceMessageId),
                                fallbackSignature = prepared.fallbackSignature.ifBlank { existingMessage?.fallbackSignature.orEmpty() },
                                sourceSeq = preferString(prepared.seq, existingMessage?.sourceSeq),
                                timestamp = prepared.timestamp.takeIf { it > 0L } ?: existingMessage?.timestamp ?: 0L,
                                timeIso = preferString(prepared.timeIso, existingMessage?.timeIso),
                                senderUid = preferString(prepared.senderUid, existingMessage?.senderUid),
                                senderUin = preferString(prepared.senderUin, existingMessage?.senderUin),
                                senderDisplayName = preferString(prepared.senderDisplayName, existingMessage?.senderDisplayName).orEmpty(),
                                senderNickname = preferString(prepared.senderNickname, existingMessage?.senderNickname),
                                senderRemark = preferString(prepared.senderRemark, existingMessage?.senderRemark),
                                isSelf = prepared.isSelf || (existingMessage?.isSelf == true),
                                type = preferString(prepared.type, existingMessage?.type).orEmpty(),
                                text = preferString(prepared.text, existingMessage?.text).orEmpty(),
                                html = preferString(prepared.html, existingMessage?.html),
                                rawContentJson = preferJson(prepared.rawContentJson, existingMessage?.rawContentJson),
                                rawMessageJson = preferJson(prepared.rawMessageJson, existingMessage?.rawMessageJson),
                                replyToSourceMessageId = preferString(prepared.replyToSourceMessageId, existingMessage?.replyToSourceMessageId),
                                replyReferenceMessageId = preferString(prepared.replyReferenceMessageId, existingMessage?.replyReferenceMessageId),
                                replyPreviewText = preferString(prepared.replyPreviewText, existingMessage?.replyPreviewText),
                                replyReferenceSenderUin = preferString(prepared.replyReferenceSenderUin, existingMessage?.replyReferenceSenderUin),
                                replyReferenceSenderName = preferString(prepared.replyReferenceSenderName, existingMessage?.replyReferenceSenderName),
                                replyReferenceTimestampSeconds = prepared.replyReferenceTimestampSeconds ?: existingMessage?.replyReferenceTimestampSeconds,
                                replyReferenceContent = preferString(prepared.replyReferenceContent, existingMessage?.replyReferenceContent),
                                jsonTitle = preferString(prepared.jsonTitle, existingMessage?.jsonTitle),
                                jsonSummary = preferString(prepared.jsonSummary, existingMessage?.jsonSummary),
                                jsonPreviewUrl = preferString(prepared.jsonPreviewUrl, existingMessage?.jsonPreviewUrl),
                                callSummary = preferString(prepared.callSummary, existingMessage?.callSummary),
                                recalled = prepared.recalled || (existingMessage?.recalled == true),
                                system = prepared.system || (existingMessage?.system == true),
                                searchText = mergeSearchText(prepared.searchText, existingMessage?.searchText),
                            )
                            val messageLocalId = if (identity == null) {
                                insertedCount += 1
                                dao.insertMessage(messageEntity)
                            } else {
                                mergedCount += 1
                                dao.updateMessage(messageEntity)
                                messageEntity.messageLocalId
                            }

                            val participantResult = upsertParticipant(
                                chatId = chatId,
                                prepared = prepared,
                                avatars = avatarMap,
                                avatarCache = storageAvatarPaths,
                                chatStorageDir = chatStorageDir,
                            )
                            if (participantResult.avatarCopied) {
                                copiedAvatarCount += 1
                            }
                            if (participantResult.avatarFailed) {
                                failedAvatarCount += 1
                                warnings += buildWarning(
                                    kind = "avatar_copy",
                                    detail = "发送者 ${prepared.senderDisplayName.ifBlank { prepared.senderUin ?: "未知" }} 的头像处理失败，已回退默认头像",
                                    cause = null,
                                )
                            }

                            val existingResources = if (identity == null) {
                                emptyList()
                            } else {
                                dao.getResourcesForMessage(messageLocalId)
                            }
                            val reusedExistingResourceIds = mutableSetOf<Long>()
                            val copiedResources = prepared.resources.mapIndexedNotNull { ordinal, resource ->
                                val reusedResource = findReusableResourceEntity(
                                    existing = existingResources,
                                    usedResourceIds = reusedExistingResourceIds,
                                    prepared = resource,
                                    ordinal = ordinal,
                                    chatStorageDir = chatStorageDir,
                                )
                                if (reusedResource != null) {
                                    reusedExistingResourceIds += reusedResource.resourceLocalId
                                    return@mapIndexedNotNull reusedResource
                                }
                                val copyAttempt = runCatching {
                                    copyResourceFile(
                                        importRoot = importRoot,
                                        chatStorageDir = chatStorageDir,
                                        prepared = prepared,
                                        resource = resource,
                                        ordinal = ordinal,
                                    )
                                }
                                copyAttempt.exceptionOrNull()?.let { throwable ->
                                    missingResourceCount += 1
                                    warnings += buildWarning(
                                        kind = "resource_copy",
                                        detail = "消息资源复制失败，已按缺失资源导入",
                                        cause = throwable,
                                    )
                                }
                                val copied = copyAttempt.getOrNull()
                                if (copied == null) {
                                    if (copyAttempt.isSuccess) {
                                        missingResourceCount += 1
                                        warnings += buildWarning(
                                            kind = "resource_missing",
                                            detail = "有资源文件缺失，相关消息将以降级形式显示",
                                            cause = null,
                                        )
                                    }
                                    return@mapIndexedNotNull null
                                }
                                copiedResourceCount += 1
                                ImportedResourceEntity(
                                    messageLocalId = messageLocalId,
                                    ordinal = ordinal,
                                    type = copied.type.name,
                                    renderKind = copied.renderKind.name,
                                    originalFileName = copied.originalFileName,
                                    storedFileName = copied.storedFileName,
                                    originalRelativePath = copied.originalRelativePath,
                                    storedRelativePath = copied.storedRelativePath,
                                    mimeType = copied.mimeType,
                                    md5 = copied.md5,
                                    sizeBytes = copied.sizeBytes,
                                    width = copied.width,
                                    height = copied.height,
                                    durationSeconds = copied.durationSeconds,
                                )
                            }
                            val resourceEntities = mergeResourceEntities(
                                messageLocalId = messageLocalId,
                                fresh = copiedResources,
                                existing = existingResources,
                            )
                            dao.deleteResourcesForMessage(messageLocalId)
                            if (resourceEntities.isNotEmpty()) {
                                dao.insertResources(resourceEntities)
                            }

                            dao.deleteMessageSearch(messageLocalId)
                            dao.replaceMessageSearch(
                                ImportedMessageSearchEntity(
                                    messageLocalId = messageLocalId,
                                    chatId = chatId,
                                    messageStableKey = messageStableKey,
                                    searchText = previewPresentation.searchText,
                                ),
                            )
                        }

                        importedProgress += 1
                        if (importedProgress % 100 == 0 || importedProgress == totalMessages) {
                            onProgress(
                                ChatImportProgress(
                                    stage = "import",
                                    message = "正在写入本地数据库",
                                    current = importedProgress,
                                    total = totalMessages,
                                ),
                            )
                        }
                    }
                }
            }

            database.withTransaction {
                val latestMessage = dao.getLatestMessage(chatId)
                val updatedChat = requireNotNull(dao.findChatById(chatId)).copy(
                    lastImportedAtMillis = now,
                    lastMessageAtMillis = latestMessage?.timestamp ?: 0L,
                    messageCount = dao.countMessages(chatId),
                    lastMessagePreview = latestMessage?.let { latest ->
                        buildImportedMessagePresentation(
                            type = latest.type,
                            text = latest.text,
                            rawContentJson = latest.rawContentJson,
                            rawMessageJson = latest.rawMessageJson,
                            replyPreviewText = latest.replyPreviewText,
                            jsonTitle = latest.jsonTitle,
                            jsonSummary = latest.jsonSummary,
                            callSummary = latest.callSummary,
                            system = latest.system,
                            recalled = latest.recalled,
                            qFaceCatalog = qFaceCatalog,
                        ).previewText
                    }.orEmpty(),
                    lastMessageType = latestMessage?.type.orEmpty(),
                    lastImportAddedMessageCount = insertedCount,
                    lastImportMergedMessageCount = mergedCount,
                    lastImportResourceCount = copiedResourceCount,
                    lastImportAvatarCount = copiedAvatarCount,
                )
                dao.updateChat(updatedChat)
            }

            onProgress(ChatImportProgress("done", "??????", importedProgress, totalMessages))
            val result = ChatImportResult(
                chatId = chatId,
                importedMessageCount = insertedCount,
                mergedMessageCount = mergedCount,
                copiedResourceCount = copiedResourceCount,
                copiedAvatarCount = copiedAvatarCount,
                skippedMessageCount = skippedMessageCount,
                missingResourceCount = missingResourceCount,
                failedAvatarCount = failedAvatarCount,
                warnings = warnings.toList(),
            )
            syncBridge.afterMutation(this@ImportedChatRepository)
            result
        } finally {
            unzipDir.deleteRecursively()
        }
    }

    private fun exportReadingAnchors(): List<StoredChatReadingAnchor> {
        return readingAnchorPrefs.all.keys
            .mapNotNull { key ->
                if (!key.startsWith("chat_") || !key.endsWith("_message_id")) return@mapNotNull null
                val chatId = key.removePrefix("chat_").removeSuffix("_message_id").toLongOrNull()
                    ?: return@mapNotNull null
                val messageLocalId = readingAnchorPrefs.getLong(key, -1L)
                if (messageLocalId <= 0L) return@mapNotNull null
                StoredChatReadingAnchor(
                    chatId = chatId,
                    messageLocalId = messageLocalId,
                    scrollOffset = readingAnchorPrefs.getInt("chat_${chatId}_offset", 0),
                    savedAtMillis = readingAnchorPrefs.getLong("chat_${chatId}_saved_at", 0L),
                )
            }
            .sortedBy { it.chatId }
    }

    private fun replaceReadingAnchors(anchors: List<StoredChatReadingAnchor>) {
        readingAnchorPrefs.edit {
            readingAnchorPrefs.all.keys
                .filter { it.startsWith("chat_") }
                .forEach(::remove)
            anchors.forEach { anchor ->
                putLong("chat_${anchor.chatId}_message_id", anchor.messageLocalId)
                putInt("chat_${anchor.chatId}_offset", anchor.scrollOffset)
                putLong("chat_${anchor.chatId}_saved_at", anchor.savedAtMillis)
            }
        }
    }

    private suspend fun upsertParticipant(
        chatId: Long,
        prepared: PreparedMessage,
        avatars: Map<String, AvatarPayload>,
        avatarCache: MutableMap<String, StoredAvatarPayload>,
        chatStorageDir: File,
    ): ParticipantUpsertResult {
        val participantStableKey = buildParticipantStableKey(
            uid = prepared.senderUid,
            uin = prepared.senderUin,
            displayName = prepared.senderDisplayName,
        )
        val existing = dao.findParticipant(chatId, participantStableKey)
        val avatarKey = prepared.senderUin?.takeIf { avatars.containsKey(it) }
        var copiedAvatar = false
        var avatarFailed = false
        val storedAvatar = avatarKey?.let { uin ->
            avatarCache[uin] ?: copyAvatarPayload(
                chatStorageDir = chatStorageDir,
                uin = uin,
                payload = avatars.getValue(uin),
            )?.also {
                avatarCache[uin] = it
                copiedAvatar = true
            } ?: run {
                avatarFailed = true
                null
            }
        }
        val avatarPath = storedAvatar?.absolutePath ?: existing?.avatarLocalPath
        val avatarMime = storedAvatar?.mimeType ?: existing?.avatarMimeType
        val entity = ImportedParticipantEntity(
            participantId = existing?.participantId ?: 0L,
            chatId = chatId,
            participantStableKey = participantStableKey,
            uid = prepared.senderUid,
            uin = prepared.senderUin,
            displayName = prepared.senderDisplayName,
            avatarLocalPath = avatarPath,
            avatarMimeType = avatarMime,
            isSelf = prepared.isSelf,
            lastSeenAtMillis = maxOf(existing?.lastSeenAtMillis ?: 0L, prepared.timestamp),
        )
        if (existing == null) {
            dao.insertParticipant(entity)
        } else {
            dao.updateParticipant(entity)
        }
        return ParticipantUpsertResult(
            avatarCopied = copiedAvatar,
            avatarFailed = avatarFailed,
        )
    }

    private suspend fun resolveExistingMessageIdentity(
        chatId: Long,
        prepared: PreparedMessage,
        chatStableKey: String,
    ): ImportedMessageIdentityRow? {
        val stableKey = buildMessageStableKey(chatStableKey, prepared.sourceMessageId, prepared.fallbackSignature)
        return dao.findMessageIdentityByStableKey(chatId, stableKey)
            ?: prepared.sourceMessageId?.let { dao.findMessageIdentityBySourceMessageId(chatId, it) }
            ?: dao.findMessageIdentityByFallbackSignature(chatId, prepared.fallbackSignature)
    }

    private suspend fun resolveExistingChatTarget(
        chatType: ImportedChatType,
        proposedStableKey: String,
        selfUid: String?,
        parsedPeerUid: String?,
        privatePeerIdentity: PrivatePeerIdentity?,
    ): ResolvedChatTarget {
        dao.findChatByStableKey(proposedStableKey)?.let { existing ->
            return ResolvedChatTarget(
                existingChat = existing,
                chatStableKey = existing.chatStableKey,
                storageDir = chatStorageDir(existing.chatStableKey),
            )
        }
        val normalizedSelfUid = selfUid?.takeIf { it.isNotBlank() }
        if (!normalizedSelfUid.isNullOrBlank()) {
            when (chatType) {
                ImportedChatType.PRIVATE -> {
                    parsedPeerUid?.takeIf { it.isNotBlank() }?.let { peerUid ->
                        dao.findChatByTypeAndSelfUidAndPeerUid(
                            chatType = chatType.name,
                            selfUid = normalizedSelfUid,
                            peerUid = peerUid,
                        )?.let { existing ->
                            return ResolvedChatTarget(
                                existingChat = existing,
                                chatStableKey = existing.chatStableKey,
                                storageDir = chatStorageDir(existing.chatStableKey),
                            )
                        }
                    }
                    privatePeerIdentity?.uid?.takeIf { it.isNotBlank() }?.let { participantUid ->
                        dao.findPrivateChatByParticipantUid(
                            chatType = ImportedChatType.PRIVATE.name,
                            selfUid = normalizedSelfUid,
                            participantUid = participantUid,
                        )?.let { existing ->
                            return ResolvedChatTarget(
                                existingChat = existing,
                                chatStableKey = existing.chatStableKey,
                                storageDir = chatStorageDir(existing.chatStableKey),
                            )
                        }
                    }
                    privatePeerIdentity?.uin?.takeIf { it.isNotBlank() }?.let { participantUin ->
                        dao.findPrivateChatByParticipantUin(
                            chatType = ImportedChatType.PRIVATE.name,
                            selfUid = normalizedSelfUid,
                            participantUin = participantUin,
                        )?.let { existing ->
                            return ResolvedChatTarget(
                                existingChat = existing,
                                chatStableKey = existing.chatStableKey,
                                storageDir = chatStorageDir(existing.chatStableKey),
                            )
                        }
                    }
                }
                ImportedChatType.GROUP -> {
                    parsedPeerUid?.takeIf { it.isNotBlank() }?.let { peerUid ->
                        dao.findChatByTypeAndSelfUidAndPeerUid(
                            chatType = chatType.name,
                            selfUid = normalizedSelfUid,
                            peerUid = peerUid,
                        )?.let { existing ->
                            return ResolvedChatTarget(
                                existingChat = existing,
                                chatStableKey = existing.chatStableKey,
                                storageDir = chatStorageDir(existing.chatStableKey),
                            )
                        }
                    }
                }
                ImportedChatType.UNKNOWN -> Unit
            }
        }
        return ResolvedChatTarget(
            existingChat = null,
            chatStableKey = proposedStableKey,
            storageDir = chatStorageDir(proposedStableKey),
        )
    }

    private fun resolvePrivatePeerIdentity(
        chatType: ImportedChatType,
        participantUids: Set<String>,
        participantUins: Set<String>,
    ): PrivatePeerIdentity? {
        if (chatType != ImportedChatType.PRIVATE) return null
        val uniqueUid = participantUids.map(String::trim).filter(String::isNotBlank).distinct().singleOrNull()
        val uniqueUin = participantUins.map(String::trim).filter(String::isNotBlank).distinct().singleOrNull()
        if (uniqueUid == null && uniqueUin == null) return null
        return PrivatePeerIdentity(
            uid = uniqueUid,
            uin = uniqueUin,
        )
    }

    private fun buildChatStableKey(
        sourceDisplayName: String?,
        parsedFileInfo: ParsedQceZipInfo?,
        chatInfo: QceChatInfo?,
        chatType: ImportedChatType,
        senderUidSet: Set<String>,
        privatePeerIdentity: PrivatePeerIdentity?,
    ): String {
        val selfUid = chatInfo?.selfUid.orEmpty()
        parsedFileInfo?.chatId?.takeIf { it.isNotBlank() }?.let { peerUid ->
            return "${chatType.name.lowercase(Locale.ROOT)}:$selfUid:$peerUid"
        }
        if (chatType == ImportedChatType.PRIVATE) {
            privatePeerIdentity?.canonicalPeerKey?.let { peerKey ->
                return "private:$selfUid:$peerKey"
            }
            val otherUid = senderUidSet.firstOrNull { it.isNotBlank() && it != selfUid }
            if (!otherUid.isNullOrBlank()) {
                return "private:$selfUid:$otherUid"
            }
        }
        val fallbackName = chatInfo?.name?.ifBlank { sourceDisplayName.orEmpty() }
            ?: sourceDisplayName
            ?: "unknown"
        return "${chatType.name.lowercase(Locale.ROOT)}:$selfUid:${fallbackName.lowercase(Locale.ROOT)}"
    }

    private fun resolveImportedChatDisplayName(
        manifestName: String?,
        parsedFileInfo: ParsedQceZipInfo?,
        fallbackSourceDisplayName: String?,
        existingDisplayName: String?,
    ): String {
        return manifestName?.trim()?.takeIf { it.isNotBlank() }
            ?: parsedFileInfo?.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: existingDisplayName?.trim()?.takeIf { it.isNotBlank() }
            ?: parsedFileInfo?.chatId?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackSourceDisplayName?.removeSuffix(".zip")?.trim()?.takeIf { it.isNotBlank() }
            ?: "未命名聊天"
    }

    private fun resolveImportedChatPeerUid(
        chatType: ImportedChatType,
        parsedPeerUid: String?,
        privatePeerIdentity: PrivatePeerIdentity?,
        existingPeerUid: String?,
    ): String? {
        return when (chatType) {
            ImportedChatType.PRIVATE -> parsedPeerUid?.takeIf { it.isNotBlank() }
                ?: privatePeerIdentity?.canonicalPeerKey
                ?: existingPeerUid?.takeIf { it.isNotBlank() }
            ImportedChatType.GROUP -> parsedPeerUid?.takeIf { it.isNotBlank() }
                ?: existingPeerUid?.takeIf { it.isNotBlank() }
            ImportedChatType.UNKNOWN -> parsedPeerUid?.takeIf { it.isNotBlank() }
                ?: existingPeerUid?.takeIf { it.isNotBlank() }
        }
    }

    private fun chatStorageDir(chatStableKey: String): File {
        val dirName = "chat_" + sha256(chatStableKey).take(24)
        return importsBaseDir.resolve(dirName)
    }

    private fun findImportRoot(unzipDir: File): File? {
        return unzipDir.walkTopDown()
            .firstOrNull { it.isFile && it.name == "manifest.json" }
            ?.parentFile
    }

    private fun resolveChunkFiles(importRoot: File, manifest: QceManifest): List<File> {
        val declared = manifest.chunked?.chunks.orEmpty().mapNotNull { chunk ->
            val relative = chunk.relativePath?.ifBlank { null }
                ?: chunk.fileName?.takeIf { it.isNotBlank() }?.let { "chunks/$it" }
            relative?.let { importRoot.resolve(it) }
        }.filter { it.exists() }
        if (declared.isNotEmpty()) return declared
        return importRoot.resolve("chunks")
            .takeIf { it.exists() && it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) == "jsonl" }
            .orEmpty()
    }

    private fun unzipZip(uri: Uri, targetDir: File) {
        openInputStream(uri).use { input ->
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val safeFile = targetDir.resolve(entry.name).normalize()
                    require(safeFile.path.startsWith(targetDir.normalize().path)) {
                        "ZIP 条目非法: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        safeFile.mkdirs()
                    } else {
                        safeFile.parentFile?.mkdirs()
                        FileOutputStream(safeFile).use { output ->
                            zip.copyTo(output)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun openInputStream(uri: Uri): InputStream {
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            FileInputStream(requireNotNull(uri.path) { "无效文件路径" })
        } else {
            requireNotNull(appContext.contentResolver.openInputStream(uri)) {
                "无法读取选中的 ZIP 文件"
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.let(::File)?.name
        }
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun queryDocumentSize(uri: Uri): Long? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.let(::File)?.takeIf(File::exists)?.length()
        }
        val projection = arrayOf(android.provider.OpenableColumns.SIZE)
        return runCatching {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else null
            }
        }.getOrNull()
    }

    private fun ensureUsableImportSpace(zipSizeBytes: Long?) {
        val tempFree = importsTempDir.usableSpace
        val baseFree = importsBaseDir.usableSpace
        val estimatedNeed = (zipSizeBytes ?: 0L) * 2L
        if (zipSizeBytes != null && tempFree < estimatedNeed) {
            error("可用临时空间不足，当前大约剩余 ${formatBytes(tempFree)}，建议先清理空间后再导入。")
        }
        if (baseFree < 512L * 1024L * 1024L) {
            error("应用可用存储空间过低，当前大约剩余 ${formatBytes(baseFree)}，建议先清理空间后再导入。")
        }
    }

    private fun loadAvatarPayloads(
        importRoot: File,
        manifest: QceManifest,
    ): Map<String, AvatarPayload> {
        val avatarFile = manifest.avatars?.file?.takeIf { it.isNotBlank() }?.let(importRoot::resolve)
            ?: importRoot.resolve("avatars.json").takeIf { it.exists() }
            ?: return emptyMap()
        if (!avatarFile.exists()) return emptyMap()
        val root = JsonParser.parseString(avatarFile.readText(Charsets.UTF_8)).asJsonObject
        return root.entrySet().mapNotNull { (uin, element) ->
            val dataUri = element.asStringOrNull()?.takeIf { it.startsWith("data:", ignoreCase = true) } ?: return@mapNotNull null
            parseAvatarPayload(uin, dataUri)
        }.associateBy { it.uin }
    }

    private fun parseAvatarPayload(
        uin: String,
        dataUri: String,
    ): AvatarPayload? {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex <= 0) return null
        val header = dataUri.substring(5, commaIndex)
        val base64Part = dataUri.substring(commaIndex + 1)
        val mimeType = header.substringBefore(';').ifBlank { "image/jpeg" }
        return AvatarPayload(uin = uin, dataUri = dataUri, mimeType = mimeType, base64 = base64Part)
    }

    private fun copyAvatarPayload(
        chatStorageDir: File,
        uin: String,
        payload: AvatarPayload,
    ): StoredAvatarPayload? {
        return runCatching {
            val ext = fileExtensionFromMime(payload.mimeType) ?: "jpg"
            val avatarFile = chatStorageDir.resolve("avatars").resolve("$uin.$ext")
            val bytes = Base64.decode(payload.base64, Base64.DEFAULT)
            avatarFile.parentFile?.mkdirs()
            avatarFile.writeBytes(bytes)
            StoredAvatarPayload(
                absolutePath = avatarFile.absolutePath,
                mimeType = payload.mimeType,
            )
        }.getOrNull()
    }

    private fun copyResourceFile(
        importRoot: File,
        chatStorageDir: File,
        prepared: PreparedMessage,
        resource: PreparedResource,
        ordinal: Int,
    ): CopiedResource? {
        val sourceFile = resolveSourceResourceFile(importRoot, resource) ?: return null
        val typeDir = resource.type.toResourceDirectoryName()
        val extension = inferResourceExtension(resource, sourceFile)
        val targetBaseName = resource.md5?.ifBlank { null }
            ?: prepared.sourceMessageId?.takeIf { it.isNotBlank() }?.let { sourceMessageId ->
                "${sourceMessageId}_${ordinal}"
            }
            ?: "msg_${prepared.timestamp}_${ordinal}_${sha256(prepared.rawContentJson).take(8)}"
        val targetName = "$targetBaseName.$extension"
        val relativePath = "resources/$typeDir/$targetName"
        val targetFile = chatStorageDir.resolve(relativePath)
        targetFile.parentFile?.mkdirs()
        if (!targetFile.exists() || targetFile.length() != sourceFile.length()) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }
        return CopiedResource(
            type = resource.type,
            renderKind = resource.renderKind,
            originalFileName = resource.fileName,
            storedFileName = targetName,
            originalRelativePath = resource.originalRelativePath,
            storedRelativePath = relativePath,
            mimeType = resource.mimeType,
            md5 = resource.md5,
            sizeBytes = resource.sizeBytes ?: sourceFile.length(),
            width = resource.width,
            height = resource.height,
            durationSeconds = resource.durationSeconds,
        )
    }

    private fun findReusableResourceEntity(
        existing: List<ImportedResourceEntity>,
        usedResourceIds: Set<Long>,
        prepared: PreparedResource,
        ordinal: Int,
        chatStorageDir: File,
    ): ImportedResourceEntity? {
        val match = existing.firstOrNull { candidate ->
            candidate.resourceLocalId !in usedResourceIds &&
                resourceLogicalKey(candidate.ordinal, candidate) == resourceLogicalKey(ordinal, prepared)
        } ?: return null
        val targetFile = chatStorageDir.resolve(match.storedRelativePath)
        return match.copy(
            messageLocalId = match.messageLocalId,
            ordinal = ordinal,
            renderKind = if (prepared.renderKind == ImportedResourceRenderKind.UNKNOWN) {
                match.renderKind
            } else {
                prepared.renderKind.name
            },
            originalFileName = preferString(prepared.fileName, match.originalFileName),
            storedFileName = match.storedFileName,
            originalRelativePath = preferString(prepared.originalRelativePath, match.originalRelativePath),
            storedRelativePath = match.storedRelativePath,
            mimeType = preferString(prepared.mimeType, match.mimeType),
            md5 = preferString(prepared.md5, match.md5),
            sizeBytes = prepared.sizeBytes ?: match.sizeBytes ?: targetFile.takeIf(File::exists)?.length(),
            width = prepared.width ?: match.width,
            height = prepared.height ?: match.height,
            durationSeconds = prepared.durationSeconds ?: match.durationSeconds,
        )
    }

    private fun resolveSourceResourceFile(importRoot: File, resource: PreparedResource): File? {
        val candidates = linkedSetOf<File>()
        collectImportRelativePathCandidates(resource.originalRelativePath).forEach { relative ->
            candidates += importRoot.resolve(relative)
            relative.removePrefix("resources/").takeIf { it != relative }?.let {
                candidates += importRoot.resolve("resources").resolve(it)
            }
        }
        collectImportRelativePathCandidates(resource.localPath).forEach { relative ->
            candidates += importRoot.resolve(relative)
            relative.removePrefix("resources/").takeIf { it != relative }?.let {
                candidates += importRoot.resolve("resources").resolve(it)
            }
            candidates += importRoot.resolve("resources").resolve(relative)
        }
        resource.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
            candidates += importRoot.resolve("resources")
                .resolve(resource.type.toResourceDirectoryName())
                .resolve(fileName)
        }
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    private fun inferResourceExtension(resource: PreparedResource, sourceFile: File): String {
        resource.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }?.let {
            return it.lowercase(Locale.ROOT)
        }
        sourceFile.extension.takeIf { it.isNotBlank() }?.let {
            return it.lowercase(Locale.ROOT)
        }
        return fileExtensionFromMime(resource.mimeType) ?: when (resource.type) {
            ImportedResourceType.IMAGE -> "jpg"
            ImportedResourceType.VIDEO -> "mp4"
            ImportedResourceType.AUDIO -> "amr"
            ImportedResourceType.FILE,
            ImportedResourceType.UNKNOWN,
            -> "bin"
        }
    }

    private fun buildWarning(
        kind: String,
        detail: String,
        cause: Throwable?,
    ): String {
        val suffix = cause?.message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
        return "[$kind] $detail$suffix"
    }

    private fun File.directorySizeBytes(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = 1024L * 1024L
        val gb = mb * 1024L
        return when {
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / gb.toDouble())
            bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes.toDouble() / mb.toDouble())
            else -> "$bytes B"
        }
    }

    private fun parsePreparedMessage(
        line: String,
        selfUid: String?,
        selfUin: String?,
    ): PreparedMessage {
        val root = JsonParser.parseString(line).asJsonObject
        val sender = root.getAsJsonObject("sender")
        val content = root.getAsJsonObject("content")
        val senderUid = sender?.string("uid")
        val senderUin = sender?.string("uin")
        val senderName = sender?.string("name").orEmpty()
            .ifBlank { sender?.string("nickname").orEmpty() }
            .ifBlank { senderUid.orEmpty() }
        val text = content?.string("text").orEmpty()
        val html = content?.string("html")
        val type = root.string("type").orEmpty()
        val rawContentJson = gson.toJson(content ?: JsonObject())
        val rawMessageJson = line
        val elements = content?.getAsJsonArray("elements")
        val resources = extractPreparedResources(
            content = content,
            elements = elements,
        )
        val replyElement = elements?.firstObjectOfType("reply")
        val jsonElement = elements?.firstObjectOfType("json")
        val avRecordElement = elements?.firstObjectOfType("av_record")
        val replyData = replyElement?.getAsJsonObject("data")
        val jsonData = jsonElement?.getAsJsonObject("data")
        val avRecordData = avRecordElement?.getAsJsonObject("data")
        val normalizedSearchText = buildList {
            add(text)
            add(replyData?.string("content").orEmpty())
            add(jsonData?.string("title").orEmpty())
            add(jsonData?.string("summary").orEmpty())
            add(jsonData?.string("description").orEmpty())
            add(avRecordData?.string("summary").orEmpty())
            add(avRecordData?.string("text").orEmpty())
        }.joinToString(" ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .lowercase(Locale.ROOT)
        val timestamp = root.long("timestamp") ?: 0L
        val semanticContentHash = buildSemanticMessageFingerprint(
            type = type,
            text = text,
            resources = resources,
            replyData = replyData,
            jsonData = jsonData,
            avRecordData = avRecordData,
        )
        val fallbackSignature = listOf(
            root.string("seq").orEmpty(),
            timestamp.toString(),
            senderUid.orEmpty(),
            senderUin.orEmpty(),
            type,
            semanticContentHash.take(16),
        ).joinToString("|")
        val presentation = buildImportedMessagePresentation(
            type = type,
            text = text,
            rawContentJson = rawContentJson,
            rawMessageJson = rawMessageJson,
            replyPreviewText = replyData?.string("content"),
            jsonTitle = jsonData?.string("title"),
            jsonSummary = jsonData?.string("summary")
                ?: jsonData?.string("description"),
            callSummary = avRecordData?.string("summary")
                ?: avRecordData?.string("text"),
            system = root.bool("system"),
            recalled = root.bool("recalled"),
            qFaceCatalog = qFaceCatalog,
        )
        return PreparedMessage(
            sourceMessageId = root.string("id"),
            seq = root.string("seq"),
            timestamp = timestamp,
            timeIso = root.string("time"),
            senderUid = senderUid,
            senderUin = senderUin,
            senderDisplayName = senderName,
            senderNickname = sender?.string("nickname"),
            senderRemark = sender?.string("remark"),
            isSelf = (!selfUid.isNullOrBlank() && senderUid == selfUid) ||
                (!selfUin.isNullOrBlank() && senderUin == selfUin),
            type = type,
            text = text,
            html = html,
            rawContentJson = rawContentJson,
            rawMessageJson = rawMessageJson,
            replyToSourceMessageId = replyData?.string("referencedMessageId")
                ?: replyData?.string("messageId"),
            replyReferenceMessageId = replyData?.string("messageId"),
            replyPreviewText = replyData?.string("content"),
            replyReferenceSenderUin = replyData?.string("senderUin"),
            replyReferenceSenderName = replyData?.string("senderName"),
            replyReferenceTimestampSeconds = replyData?.long("timestamp"),
            replyReferenceContent = replyData?.string("content"),
            jsonTitle = jsonData?.string("title"),
            jsonSummary = jsonData?.string("summary")
                ?: jsonData?.string("description"),
            jsonPreviewUrl = jsonData?.string("preview"),
            callSummary = avRecordData?.string("summary")
                ?: avRecordData?.string("text"),
            recalled = root.bool("recalled"),
            system = root.bool("system"),
            searchText = mergeSearchText(presentation.searchText, normalizedSearchText),
            previewText = presentation.previewText,
            fallbackSignature = fallbackSignature,
            resources = resources,
        )
    }

    private fun extractPreparedResources(
        content: JsonObject?,
        elements: JsonArray?,
    ): List<PreparedResource> {
        val resourceArray = content?.getAsJsonArray("resources").orEmptyJsonArray()
        if (resourceArray.size() > 0) {
            return resourceArray.mapIndexedNotNull { index, element ->
                val resourceObject = element.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
                val elementData = elements?.mediaElementDataAt(index)
                buildPreparedResource(resourceObject, elementData)
            }
        }
        return elements.orEmptyJsonArray()
            .mapNotNull { element ->
                val elementObject = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val type = elementObject.string("type").orEmpty()
                if (type !in setOf("image", "video", "audio", "file")) return@mapNotNull null
                buildPreparedResource(
                    resourceObject = elementObject.getAsJsonObject("data") ?: JsonObject(),
                    elementData = null,
                    declaredType = type,
                )
            }
    }

    private fun buildPreparedResource(
        resourceObject: JsonObject,
        elementData: JsonObject?,
        declaredType: String? = null,
    ): PreparedResource {
        val type = (resourceObject.string("type")
            ?: elementData?.string("type")
            ?: declaredType)
            .toImportedResourceType()
        val localPath = resourceObject.string("localPath")
            ?: elementData?.string("localPath")
        val url = resourceObject.string("url")
            ?: elementData?.string("url")
        val normalizedUrlPath = normalizeImportRelativePath(url)
        val normalizedLocalPath = normalizeImportRelativePath(localPath)
        val originalRelativePath = normalizedUrlPath
            ?: normalizedLocalPath
            ?: localPath?.replace('\\', '/')
        return PreparedResource(
            type = type,
            renderKind = inferRenderKind(
                declaredType = declaredType,
                resourceObject = resourceObject,
                elementData = elementData,
            ),
            fileName = resourceObject.string("filename")
                ?: elementData?.string("filename"),
            localPath = localPath,
            originalRelativePath = originalRelativePath,
            mimeType = resourceObject.string("mimeType")
                ?: elementData?.string("mimeType"),
            md5 = resourceObject.string("md5")
                ?: elementData?.string("md5"),
            sizeBytes = resourceObject.long("size")
                ?: elementData?.long("size"),
            width = resourceObject.int("width")
                ?: elementData?.int("width"),
            height = resourceObject.int("height")
                ?: elementData?.int("height"),
            durationSeconds = resourceObject.int("duration")
                ?: elementData?.int("duration"),
        )
    }

    private fun buildSemanticMessageFingerprint(
        type: String,
        text: String,
        resources: List<PreparedResource>,
        replyData: JsonObject?,
        jsonData: JsonObject?,
        avRecordData: JsonObject?,
    ): String {
        val normalizedText = text.normalizeSemanticText()
        val replyFingerprint = listOfNotNull(
            replyData?.string("referencedMessageId")?.normalizeSemanticText(),
            replyData?.string("messageId")?.normalizeSemanticText(),
            replyData?.string("senderUin")?.normalizeSemanticText(),
            replyData?.long("timestamp")?.toString(),
            replyData?.string("content")?.normalizeSemanticText(),
        ).joinToString("|")
        val jsonFingerprint = listOfNotNull(
            jsonData?.string("title")?.normalizeSemanticText(),
            jsonData?.string("summary")?.normalizeSemanticText(),
            jsonData?.string("description")?.normalizeSemanticText(),
            jsonData?.string("prompt")?.normalizeSemanticText(),
            jsonData?.string("text")?.normalizeSemanticText(),
        ).joinToString("|")
        val callFingerprint = listOfNotNull(
            avRecordData?.string("summary")?.normalizeSemanticText(),
            avRecordData?.string("text")?.normalizeSemanticText(),
            avRecordData?.string("status")?.normalizeSemanticText(),
        ).joinToString("|")
        val resourceFingerprint = resources.joinToString("||") { resource ->
            listOf(
                resource.type.name,
                resource.md5?.normalizeSemanticText().orEmpty(),
                resource.fileName?.normalizeSemanticText().orEmpty(),
                resource.sizeBytes?.toString().orEmpty(),
                resource.width?.toString().orEmpty(),
                resource.height?.toString().orEmpty(),
                resource.durationSeconds?.toString().orEmpty(),
            ).joinToString("|")
        }
        return sha256(
            listOf(
                type.normalizeSemanticText(),
                normalizedText,
                replyFingerprint,
                jsonFingerprint,
                callFingerprint,
                resourceFingerprint,
            ).joinToString("::"),
        )
    }

    private fun inferRenderKind(
        declaredType: String?,
        resourceObject: JsonObject,
        elementData: JsonObject?,
    ): ImportedResourceRenderKind {
        val rawType = (declaredType
            ?: resourceObject.string("type")
            ?: elementData?.string("type"))
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return when (rawType) {
            "market_face" -> ImportedResourceRenderKind.STICKER
            "video" -> ImportedResourceRenderKind.VIDEO
            "audio" -> ImportedResourceRenderKind.AUDIO
            "file" -> ImportedResourceRenderKind.FILE
            "image" -> {
                val width = resourceObject.int("width") ?: elementData?.int("width")
                val height = resourceObject.int("height") ?: elementData?.int("height")
                val sizeBytes = resourceObject.long("size") ?: elementData?.long("size")
                if (looksLikeSticker(width, height, sizeBytes)) {
                    ImportedResourceRenderKind.STICKER
                } else {
                    ImportedResourceRenderKind.IMAGE
                }
            }
            else -> ImportedResourceRenderKind.UNKNOWN
        }
    }

    private fun looksLikeSticker(
        width: Int?,
        height: Int?,
        sizeBytes: Long?,
    ): Boolean {
        if (width == null || height == null) return false
        val longest = maxOf(width, height)
        val shortest = minOf(width, height)
        val ratio = if (shortest > 0) longest.toFloat() / shortest.toFloat() else Float.MAX_VALUE
        if (longest <= 96 && ratio <= 1.25f) return true
        if (longest <= 180 && shortest >= 44 && ratio <= 1.25f && (sizeBytes ?: Long.MAX_VALUE) <= 24_000L) return true
        if (longest <= 320 && shortest >= 120 && ratio <= 1.15f && (sizeBytes ?: Long.MAX_VALUE) <= 14_000L) return true
        return false
    }

    private fun parseQceZipDisplayName(displayName: String): ParsedQceZipInfo? {
        val trimmed = displayName.removeSuffix(".zip")
        val match = Regex("^(friend|group)_(.+)_(\\d{8})_(\\d{6})(?:_.+)?$").matchEntire(trimmed)
            ?: return null
        val type = match.groupValues[1]
        val middlePart = match.groupValues[2]
        val exportDate = match.groupValues[3]
        val exportTime = match.groupValues[4]
        val lastUnderscore = middlePart.lastIndexOf('_')
        if (lastUnderscore > 0) {
            val possibleId = middlePart.substring(lastUnderscore + 1)
            val possibleName = middlePart.substring(0, lastUnderscore)
            if (possibleId.all(Char::isDigit) && possibleName.isNotBlank()) {
                return ParsedQceZipInfo(
                    chatType = if (type == "group") ImportedChatType.GROUP else ImportedChatType.PRIVATE,
                    chatId = possibleId,
                    displayName = possibleName.replace('_', ' '),
                    exportDate = exportDate,
                    exportTime = exportTime,
                )
            }
            val secondLastUnderscore = possibleName.lastIndexOf('_')
            if (secondLastUnderscore > 0) {
                val possiblePrefix = possibleName.substring(secondLastUnderscore + 1)
                if (possiblePrefix == "u") {
                    return ParsedQceZipInfo(
                        chatType = if (type == "group") ImportedChatType.GROUP else ImportedChatType.PRIVATE,
                        chatId = "u_$possibleId",
                        displayName = possibleName.substring(0, secondLastUnderscore).replace('_', ' '),
                        exportDate = exportDate,
                        exportTime = exportTime,
                    )
                }
            }
        }
        return ParsedQceZipInfo(
            chatType = if (type == "group") ImportedChatType.GROUP else ImportedChatType.PRIVATE,
            chatId = middlePart,
            displayName = null,
            exportDate = exportDate,
            exportTime = exportTime,
        )
    }

    private fun buildMessageStableKey(
        chatStableKey: String,
        sourceMessageId: String?,
        fallbackSignature: String,
    ): String {
        return if (!sourceMessageId.isNullOrBlank()) {
            "$chatStableKey:$sourceMessageId"
        } else {
            "$chatStableKey:fallback:$fallbackSignature"
        }
    }

    private fun buildParticipantStableKey(
        uid: String?,
        uin: String?,
        displayName: String,
    ): String {
        return when {
            !uid.isNullOrBlank() -> "uid:$uid"
            !uin.isNullOrBlank() -> "uin:$uin"
            else -> "name:${displayName.trim().lowercase(Locale.ROOT)}"
        }
    }

    private fun ImportedChatEntity.toImportedChatDetail(
        participants: List<ImportedParticipant>,
    ): ImportedChatDetail {
        return ImportedChatDetail(
            chatId = chatId,
            chatStableKey = chatStableKey,
            displayName = displayName,
            chatType = chatType.toImportedChatType(),
            peerUid = peerUid,
            selfUid = selfUid,
            selfUin = selfUin,
            selfName = selfName,
            sourceFileName = sourceFileName,
            messageCount = messageCount,
            lastMessageAtMillis = lastMessageAtMillis,
            lastImportedAtMillis = lastImportedAtMillis,
            lastImportAddedMessageCount = lastImportAddedMessageCount,
            lastImportMergedMessageCount = lastImportMergedMessageCount,
            lastImportResourceCount = lastImportResourceCount,
            lastImportAvatarCount = lastImportAvatarCount,
            participants = participants,
        )
    }

    private fun ImportedMessageEntity.toImportedMessage(
        resources: List<ImportedResource>,
    ): ImportedMessage {
        return ImportedMessage(
            messageLocalId = messageLocalId,
            chatId = chatId,
            messageStableKey = messageStableKey,
            sourceMessageId = sourceMessageId,
            fallbackSignature = fallbackSignature,
            sourceSeq = sourceSeq,
            timestamp = timestamp,
            timeIso = timeIso,
            senderUid = senderUid,
            senderUin = senderUin,
            senderDisplayName = senderDisplayName,
            senderNickname = senderNickname,
            senderRemark = senderRemark,
            isSelf = isSelf,
            type = type,
            text = text,
            html = html,
            rawContentJson = rawContentJson,
            rawMessageJson = rawMessageJson,
            replyToSourceMessageId = replyToSourceMessageId,
            replyReferenceMessageId = replyReferenceMessageId,
            replyPreviewText = replyPreviewText,
            replyReferenceSenderUin = replyReferenceSenderUin,
            replyReferenceSenderName = replyReferenceSenderName,
            replyReferenceTimestampSeconds = replyReferenceTimestampSeconds,
            replyReferenceContent = replyReferenceContent,
            jsonTitle = jsonTitle,
            jsonSummary = jsonSummary,
            jsonPreviewUrl = jsonPreviewUrl,
            callSummary = callSummary,
            recalled = recalled,
            system = system,
            searchText = searchText,
            resources = resources,
        )
    }

    private fun ImportedResourceEntity.toImportedResource(
        absoluteFilePath: String,
    ): ImportedResource {
        val baseType = type.toImportedResourceType()
        val storedRenderKind = renderKind.toImportedResourceRenderKind()
        val probe = probeImportedResource(
            absoluteFilePath = absoluteFilePath,
            type = baseType,
            declaredMimeType = mimeType,
        )
        return ImportedResource(
            resourceLocalId = resourceLocalId,
            ordinal = ordinal,
            type = baseType,
            renderKind = deriveRenderKind(
                type = baseType,
                storedRenderKind = storedRenderKind,
                width = width,
                height = height,
                sizeBytes = sizeBytes,
            ),
            originalFileName = originalFileName,
            storedFileName = storedFileName,
            originalRelativePath = originalRelativePath,
            storedRelativePath = storedRelativePath,
            mimeType = mimeType,
            md5 = md5,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            durationSeconds = durationSeconds,
            localFilePath = absoluteFilePath,
            detectedFormat = probe.detectedFormat,
            resolvedMimeType = probe.resolvedMimeType,
            isAnimatedImage = probe.isAnimatedImage,
        )
    }

    private fun deriveRenderKind(
        type: ImportedResourceType,
        storedRenderKind: ImportedResourceRenderKind,
        width: Int?,
        height: Int?,
        sizeBytes: Long?,
    ): ImportedResourceRenderKind {
        if (storedRenderKind != ImportedResourceRenderKind.UNKNOWN) return storedRenderKind
        return when (type) {
            ImportedResourceType.IMAGE -> {
                if (looksLikeSticker(width, height, sizeBytes)) ImportedResourceRenderKind.STICKER
                else ImportedResourceRenderKind.IMAGE
            }
            ImportedResourceType.VIDEO -> ImportedResourceRenderKind.VIDEO
            ImportedResourceType.AUDIO -> ImportedResourceRenderKind.AUDIO
            ImportedResourceType.FILE -> ImportedResourceRenderKind.FILE
            ImportedResourceType.UNKNOWN -> ImportedResourceRenderKind.UNKNOWN
        }
    }

    private fun toImportedParticipant(entity: ImportedParticipantEntity): ImportedParticipant {
        return ImportedParticipant(
            participantId = entity.participantId,
            chatId = entity.chatId,
            participantStableKey = entity.participantStableKey,
            uid = entity.uid,
            uin = entity.uin,
            displayName = entity.displayName,
            avatarLocalPath = entity.avatarLocalPath,
            avatarMimeType = entity.avatarMimeType,
            isSelf = entity.isSelf,
            lastSeenAtMillis = entity.lastSeenAtMillis,
        )
    }

    private suspend fun buildMessageWindowFromRows(
        chatId: Long,
        rows: List<ImportedMessageWithResources>,
    ): ImportedMessageWindow {
        if (rows.isEmpty()) {
            return ImportedMessageWindow(
                items = emptyList(),
                startAnchor = null,
                endAnchor = null,
                hasOlder = false,
                hasNewer = false,
            )
        }
        val chat = requireNotNull(dao.findChatById(chatId))
        val orderedRows = rows.sortedWith(compareBy({ it.message.timestamp }, { it.message.messageLocalId }))
        val items = orderedRows.map { it.toRenderableMessage(chat.chatStableKey) }
        val startMessage = orderedRows.first().message
        val endMessage = orderedRows.last().message
        return ImportedMessageWindow(
            items = items,
            startAnchor = MessageWindowAnchor(
                timestamp = startMessage.timestamp,
                messageLocalId = startMessage.messageLocalId,
            ),
            endAnchor = MessageWindowAnchor(
                timestamp = endMessage.timestamp,
                messageLocalId = endMessage.messageLocalId,
            ),
            hasOlder = dao.hasOlderMessages(
                chatId = chatId,
                anchorTimestamp = startMessage.timestamp,
                anchorMessageLocalId = startMessage.messageLocalId,
            ),
            hasNewer = dao.hasNewerMessages(
                chatId = chatId,
                anchorTimestamp = endMessage.timestamp,
                anchorMessageLocalId = endMessage.messageLocalId,
            ),
        )
    }

    private fun ImportedMessageWithResources.toRenderableMessage(
        chatStableKey: String,
    ): ImportedRenderableMessage {
        val importedMessage = message.toImportedMessage(
            resources = resources.map { resource ->
                resource.toImportedResource(
                    absoluteFilePath = chatStorageDir(chatStableKey)
                        .resolve(resource.storedRelativePath)
                        .absolutePath,
                )
            },
        )
        return ImportedRenderableMessage(
            message = importedMessage,
            segments = parseMessageSegments(importedMessage),
        )
    }

    private fun parseMessageSegments(message: ImportedMessage): List<ImportedMessageSegment> {
        val root = runCatching {
            JsonParser.parseString(message.rawContentJson).asJsonObject
        }.getOrNull() ?: return fallbackSegments(message)
        val elements = root.getAsJsonArray("elements").orEmptyJsonArray()
        if (elements.size() == 0) return fallbackSegments(message)
        val resourcesByOrdinal = message.resources.sortedBy { it.ordinal }
        var resourceIndex = 0
        val segments = mutableListOf<ImportedMessageSegment>()
        elements.forEach { element ->
            val objectValue = element.asJsonObjectOrNull() ?: return@forEach
            val type = objectValue.string("type").orEmpty()
            val data = objectValue.getAsJsonObject("data") ?: JsonObject()
            when (type) {
                "text", "at" -> {
                    data.string("text")?.takeIf { it.isNotBlank() }?.let {
                        segments += ImportedMessageSegment.Text(it)
                    }
                }
                "face" -> {
                    val faceId = data.string("id").orEmpty()
                    val faceName = data.string("name").orEmpty().ifBlank { "表情" }
                    segments += ImportedMessageSegment.Face(faceId = faceId, faceName = faceName)
                }
                "reply" -> {
                    segments += ImportedMessageSegment.Reply(
                        sourceMessageId = data.string("referencedMessageId"),
                        referencedMessageId = data.string("messageId"),
                        senderUin = data.string("senderUin"),
                        senderName = data.string("senderName"),
                        content = data.string("content"),
                        timestampSeconds = data.long("timestamp"),
                    )
                }
                "json" -> {
                    segments += ImportedMessageSegment.JsonCard(
                        title = data.string("title"),
                        summary = data.string("summary") ?: data.string("description"),
                        previewUrl = data.string("preview"),
                    )
                }
                "av_record" -> {
                    segments += ImportedMessageSegment.CallRecord(
                        summary = data.string("summary") ?: data.string("text"),
                    )
                }
                "image", "video", "audio", "file", "market_face" -> {
                    val resource = resourcesByOrdinal.getOrNull(resourceIndex)
                    resourceIndex += 1
                    if (resource != null) {
                        segments += ImportedMessageSegment.Resource(
                            resource = resource,
                            originalElementType = type,
                            label = data.string("filename") ?: data.string("name"),
                        )
                    } else if (type == "market_face") {
                        segments += ImportedMessageSegment.Unknown(
                            type = type,
                            rawJson = gson.toJson(objectValue),
                        )
                    }
                }
                else -> {
                    segments += ImportedMessageSegment.Unknown(
                        type = type.ifBlank { "unknown" },
                        rawJson = gson.toJson(objectValue),
                    )
                }
            }
        }
        return if (segments.isEmpty()) fallbackSegments(message) else mergeAdjacentTextSegments(segments)
    }

    private fun fallbackSegments(message: ImportedMessage): List<ImportedMessageSegment> {
        val fallback = mutableListOf<ImportedMessageSegment>()
        message.resources.forEach { resource ->
            fallback += ImportedMessageSegment.Resource(
                resource = resource,
                originalElementType = resource.type.name.lowercase(Locale.ROOT),
                label = resource.originalFileName,
            )
        }
        if (!message.jsonTitle.isNullOrBlank() || !message.jsonSummary.isNullOrBlank()) {
            fallback += ImportedMessageSegment.JsonCard(
                title = message.jsonTitle,
                summary = message.jsonSummary,
                previewUrl = message.jsonPreviewUrl,
            )
        } else if (!message.callSummary.isNullOrBlank()) {
            fallback += ImportedMessageSegment.CallRecord(message.callSummary)
        } else if (message.text.isNotBlank()) {
            if (isSerializedPayloadLike(message.text)) {
                fallback += ImportedMessageSegment.Unknown(
                    type = message.type.ifBlank { "unknown" },
                    rawJson = message.text.take(240),
                )
            } else {
                fallback += ImportedMessageSegment.Text(message.text)
            }
        }
        return fallback.ifEmpty {
            listOf(
                ImportedMessageSegment.Unknown(
                    type = message.type.ifBlank { "unknown" },
                    rawJson = message.rawContentJson.take(240),
                ),
            )
        }
    }

    private fun mergeAdjacentTextSegments(
        segments: List<ImportedMessageSegment>,
    ): List<ImportedMessageSegment> {
        val merged = mutableListOf<ImportedMessageSegment>()
        segments.forEach { segment ->
            val previous = merged.lastOrNull()
            if (segment is ImportedMessageSegment.Text && previous is ImportedMessageSegment.Text) {
                merged[merged.lastIndex] = previous.copy(text = previous.text + segment.text)
            } else {
                merged += segment
            }
        }
        return merged
    }
}

private const val ChatImportPresentationMaintenanceVersion = 1

private fun isSerializedPayloadLike(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.length < 40) return false
    return (normalized.startsWith("{") && normalized.endsWith("}")) ||
        (normalized.startsWith("[") && normalized.endsWith("]")) ||
        normalized.contains("\"app\"") ||
        normalized.contains("\"prompt\"") ||
        normalized.contains("\"type\"")
}

private enum class ReplyReferenceMarker {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    FACE,
}

private fun parseReplyReferenceMarker(content: String?): ReplyReferenceMarker? {
    val normalized = content?.trim().orEmpty()
    return when {
        normalized.startsWith("[图片") -> ReplyReferenceMarker.IMAGE
        normalized.startsWith("[视频") -> ReplyReferenceMarker.VIDEO
        normalized.startsWith("[语音") -> ReplyReferenceMarker.AUDIO
        normalized.startsWith("[文件") -> ReplyReferenceMarker.FILE
        normalized.startsWith("[表情") -> ReplyReferenceMarker.FACE
        else -> null
    }
}

private fun ImportedMessageWithResources.matchesReplyMarker(marker: ReplyReferenceMarker): Boolean {
    return when (marker) {
        ReplyReferenceMarker.IMAGE -> {
            resources.any { it.type.toImportedResourceType() == ImportedResourceType.IMAGE }
        }
        ReplyReferenceMarker.VIDEO -> {
            resources.any { it.type.toImportedResourceType() == ImportedResourceType.VIDEO }
        }
        ReplyReferenceMarker.AUDIO -> {
            resources.any { it.type.toImportedResourceType() == ImportedResourceType.AUDIO } ||
                message.type.equals("audio", ignoreCase = true)
        }
        ReplyReferenceMarker.FILE -> {
            resources.any { it.type.toImportedResourceType() == ImportedResourceType.FILE } ||
                message.type.equals("file", ignoreCase = true)
        }
        ReplyReferenceMarker.FACE -> {
            message.text.contains("[表情") ||
                message.rawContentJson.contains("\"type\":\"face\"")
        }
    }
}

private data class QceManifest(
    val chatInfo: QceChatInfo? = null,
    val statistics: QceStatistics? = null,
    val chunked: QceChunked? = null,
    val avatars: QceAvatarsRef? = null,
)

private data class QceChatInfo(
    val name: String? = null,
    val type: String? = null,
    val selfUid: String? = null,
    val selfUin: String? = null,
    val selfName: String? = null,
)

private data class QceStatistics(
    val totalMessages: Int? = null,
)

private data class QceChunked(
    val chunks: List<QceChunkEntry> = emptyList(),
)

private data class QceChunkEntry(
    val relativePath: String? = null,
    val fileName: String? = null,
    val count: Int? = null,
)

private data class QceAvatarsRef(
    val file: String? = null,
)

private data class ParsedQceZipInfo(
    val chatType: ImportedChatType,
    val chatId: String,
    val displayName: String?,
    val exportDate: String,
    val exportTime: String,
)

private data class PrivatePeerIdentity(
    val uid: String?,
    val uin: String?,
) {
    val canonicalPeerKey: String?
        get() = uid?.takeIf { it.isNotBlank() } ?: uin?.takeIf { it.isNotBlank() }
}

private data class ResolvedChatTarget(
    val existingChat: ImportedChatEntity?,
    val chatStableKey: String,
    val storageDir: File,
)

private data class PreparedMessage(
    val sourceMessageId: String?,
    val seq: String?,
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
    val previewText: String,
    val fallbackSignature: String,
    val resources: List<PreparedResource>,
)

private data class PreparedResource(
    val type: ImportedResourceType,
    val renderKind: ImportedResourceRenderKind,
    val fileName: String?,
    val localPath: String?,
    val originalRelativePath: String?,
    val mimeType: String?,
    val md5: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
)

private data class AvatarPayload(
    val uin: String,
    val dataUri: String,
    val mimeType: String,
    val base64: String,
)

private data class StoredAvatarPayload(
    val absolutePath: String,
    val mimeType: String,
)

private data class ParticipantUpsertResult(
    val avatarCopied: Boolean,
    val avatarFailed: Boolean,
)

private data class CopiedResource(
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
)

private fun JsonObject.string(name: String): String? = get(name).asStringOrNull()

private fun JsonObject.long(name: String): Long? = get(name).asLongOrNull()

private fun JsonObject.int(name: String): Int? = get(name).asIntOrNull()

private fun JsonObject.bool(name: String): Boolean = get(name)?.let {
    if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) it.asBoolean else false
} ?: false

private fun JsonElement?.asStringOrNull(): String? {
    if (this == null || isJsonNull) return null
    return runCatching { asString }.getOrNull()
}

private fun JsonElement?.asLongOrNull(): Long? {
    if (this == null || isJsonNull) return null
    return runCatching { asLong }.getOrNull()
}

private fun JsonElement?.asIntOrNull(): Int? {
    if (this == null || isJsonNull) return null
    return runCatching { asInt }.getOrNull()
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
    if (this == null || isJsonNull || !isJsonObject) return null
    return asJsonObject
}

private fun JsonArray?.orEmptyJsonArray(): JsonArray {
    return this ?: JsonArray()
}

private fun JsonArray.firstObjectOfType(type: String): JsonObject? {
    return firstOrNull { element ->
        element.asJsonObjectOrNull()?.string("type") == type
    }?.asJsonObjectOrNull()
}

private fun JsonArray.mediaElementDataAt(index: Int): JsonObject? {
    var mediaIndex = 0
    forEach { element ->
        val objectValue = element.asJsonObjectOrNull() ?: return@forEach
        val type = objectValue.string("type").orEmpty()
        if (type in setOf("image", "video", "audio", "file")) {
            if (mediaIndex == index) {
                return objectValue.getAsJsonObject("data")
            }
            mediaIndex += 1
        }
    }
    return null
}

private fun normalizeImportRelativePath(rawPath: String?): String? {
    val normalized = rawPath
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replace('\\', '/')
        ?.removePrefix("./")
        ?.removePrefix("/")
        ?: return null
    if (normalized.startsWith("http://", ignoreCase = true) ||
        normalized.startsWith("https://", ignoreCase = true) ||
        normalized.startsWith("data:", ignoreCase = true)
    ) {
        return null
    }
    return when {
        normalized.startsWith("resources/", ignoreCase = true) -> "resources/" + normalized.removePrefix("resources/")
        normalized.startsWith("images/", ignoreCase = true) ||
            normalized.startsWith("videos/", ignoreCase = true) ||
            normalized.startsWith("audios/", ignoreCase = true) ||
            normalized.startsWith("files/", ignoreCase = true) -> "resources/$normalized"
        else -> normalized
    }
}

private fun collectImportRelativePathCandidates(rawPath: String?): List<String> {
    val normalized = normalizeImportRelativePath(rawPath) ?: return emptyList()
    val candidates = linkedSetOf(normalized)
    normalized.removePrefix("resources/").takeIf { it != normalized }?.let(candidates::add)
    return candidates.toList()
}

private fun String?.toImportedChatType(): ImportedChatType {
    return when (this?.trim()?.lowercase(Locale.ROOT)) {
        "private", "friend" -> ImportedChatType.PRIVATE
        "group" -> ImportedChatType.GROUP
        else -> ImportedChatType.UNKNOWN
    }
}

private fun String?.toImportedResourceType(): ImportedResourceType {
    return when (this?.trim()?.lowercase(Locale.ROOT)) {
        "image" -> ImportedResourceType.IMAGE
        "video" -> ImportedResourceType.VIDEO
        "audio" -> ImportedResourceType.AUDIO
        "file" -> ImportedResourceType.FILE
        else -> ImportedResourceType.UNKNOWN
    }
}

private fun String?.toImportedResourceRenderKind(): ImportedResourceRenderKind {
    return when (this?.trim()?.lowercase(Locale.ROOT)) {
        "image" -> ImportedResourceRenderKind.IMAGE
        "sticker" -> ImportedResourceRenderKind.STICKER
        "video" -> ImportedResourceRenderKind.VIDEO
        "audio" -> ImportedResourceRenderKind.AUDIO
        "file" -> ImportedResourceRenderKind.FILE
        else -> ImportedResourceRenderKind.UNKNOWN
    }
}

private fun ImportedResourceType.toResourceDirectoryName(): String {
    return when (this) {
        ImportedResourceType.IMAGE -> "images"
        ImportedResourceType.VIDEO -> "videos"
        ImportedResourceType.AUDIO -> "audios"
        ImportedResourceType.FILE,
        ImportedResourceType.UNKNOWN,
        -> "files"
    }
}

private fun fileExtensionFromMime(mimeType: String?): String? {
    return when (mimeType?.lowercase(Locale.ROOT)) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        "audio/amr" -> "amr"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/aac" -> "m4a"
        "application/pdf" -> "pdf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/msword" -> "doc"
        else -> null
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun preferString(primary: String?, fallback: String?): String? {
    return primary?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() }
}

private fun preferJson(primary: String?, fallback: String?): String {
    val preferred = primary?.takeIf { it.isNotBlank() && it != "{}" && it != "[]" }
        ?: fallback?.takeIf { it.isNotBlank() }
    return preferred ?: "{}"
}

private fun mergeSearchText(primary: String, fallback: String?): String {
    return listOfNotNull(
        primary.takeIf { it.isNotBlank() },
        fallback?.takeIf { it.isNotBlank() && it != primary },
    ).joinToString(" ").replace("\\s+".toRegex(), " ").trim()
}

private fun String?.normalizeSemanticText(): String {
    return this?.trim()
        ?.replace("\\s+".toRegex(), " ")
        ?.lowercase(Locale.ROOT)
        .orEmpty()
}

private fun resourceLogicalKey(
    ordinal: Int,
    type: String,
    md5: String?,
    fileName: String?,
    sizeBytes: Long?,
    width: Int?,
    height: Int?,
    durationSeconds: Int?,
): String {
    val normalizedMd5 = md5.normalizeSemanticText()
    return if (normalizedMd5.isNotBlank()) {
        listOf(
            ordinal.toString(),
            type.normalizeSemanticText(),
            normalizedMd5,
        ).joinToString("|")
    } else {
        listOf(
            ordinal.toString(),
            type.normalizeSemanticText(),
            fileName.normalizeSemanticText(),
            sizeBytes?.toString().orEmpty(),
            width?.toString().orEmpty(),
            height?.toString().orEmpty(),
            durationSeconds?.toString().orEmpty(),
        ).joinToString("|")
    }
}

private fun resourceLogicalKey(
    ordinal: Int,
    resource: PreparedResource,
): String {
    return resourceLogicalKey(
        ordinal = ordinal,
        type = resource.type.name,
        md5 = resource.md5,
        fileName = resource.fileName,
        sizeBytes = resource.sizeBytes,
        width = resource.width,
        height = resource.height,
        durationSeconds = resource.durationSeconds,
    )
}

private fun resourceLogicalKey(
    ordinal: Int,
    resource: ImportedResourceEntity,
): String {
    return resourceLogicalKey(
        ordinal = ordinal,
        type = resource.type,
        md5 = resource.md5,
        fileName = resource.originalFileName ?: resource.storedFileName,
        sizeBytes = resource.sizeBytes,
        width = resource.width,
        height = resource.height,
        durationSeconds = resource.durationSeconds,
    )
}

private fun mergeResourceEntities(
    messageLocalId: Long,
    fresh: List<ImportedResourceEntity>,
    existing: List<ImportedResourceEntity>,
): List<ImportedResourceEntity> {
    if (fresh.isEmpty()) return existing.mapIndexed { index, resource ->
        resource.copy(messageLocalId = messageLocalId, ordinal = index)
    }
    val existingByLogicalKey = existing.groupBy { resourceLogicalKey(it.ordinal, it) }
    val usedResourceIds = mutableSetOf<Long>()
    return fresh.mapIndexed { index, resource ->
        val logicalKey = resourceLogicalKey(index, resource)
        val previous = existingByLogicalKey[logicalKey]
            ?.firstOrNull { candidate -> usedResourceIds.add(candidate.resourceLocalId) }
            ?: existing.firstOrNull { candidate ->
                candidate.resourceLocalId !in usedResourceIds &&
                    candidate.storedRelativePath == resource.storedRelativePath
            }?.also { usedResourceIds += it.resourceLocalId }
        resource.copy(
            resourceLocalId = previous?.resourceLocalId ?: 0L,
            messageLocalId = messageLocalId,
            ordinal = index,
            renderKind = if (resource.renderKind.isBlank() || resource.renderKind == ImportedResourceRenderKind.UNKNOWN.name) {
                previous?.renderKind ?: ImportedResourceRenderKind.UNKNOWN.name
            } else {
                resource.renderKind
            },
            originalFileName = preferString(resource.originalFileName, previous?.originalFileName),
            storedFileName = preferString(previous?.storedFileName, resource.storedFileName).orEmpty(),
            originalRelativePath = preferString(resource.originalRelativePath, previous?.originalRelativePath),
            storedRelativePath = preferString(previous?.storedRelativePath, resource.storedRelativePath).orEmpty(),
            mimeType = preferString(resource.mimeType, previous?.mimeType),
            md5 = preferString(resource.md5, previous?.md5),
            sizeBytes = resource.sizeBytes ?: previous?.sizeBytes,
            width = resource.width ?: previous?.width,
            height = resource.height ?: previous?.height,
            durationSeconds = resource.durationSeconds ?: previous?.durationSeconds,
        )
    }
}

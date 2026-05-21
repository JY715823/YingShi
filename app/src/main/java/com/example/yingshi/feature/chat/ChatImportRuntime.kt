package com.example.yingshi.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.yingshi.feature.chat.data.ChatImportProgress
import com.example.yingshi.feature.chat.data.ChatImportResult
import com.example.yingshi.feature.chat.data.ImportedChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ChatImportRuntimeState(
    val isRunning: Boolean = false,
    val progress: ChatImportProgress? = null,
    val sourceDisplayName: String? = null,
    val expectedChatId: Long? = null,
    val result: ChatImportResult? = null,
    val importedChatId: Long? = null,
    val message: String? = null,
    val error: String? = null,
)

object ChatImportRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private val _state = MutableStateFlow(ChatImportRuntimeState())
    val state: StateFlow<ChatImportRuntimeState> = _state.asStateFlow()

    fun startImport(
        context: Context,
        uri: Uri,
        expectedChatId: Long? = null,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            startMutex.withLock {
                if (_state.value.isRunning) return@withLock
                _state.value = ChatImportRuntimeState(
                    isRunning = true,
                    expectedChatId = expectedChatId,
                    sourceDisplayName = queryDisplayName(appContext, uri),
                    progress = ChatImportProgress(
                        stage = "prepare",
                        message = "准备导入聊天记录",
                        current = 0,
                        total = 1,
                    ),
                )
                runCatching {
                    ImportedChatRepository(appContext).importFromZip(uri) { progress ->
                        _state.value = _state.value.copy(
                            isRunning = true,
                            progress = progress,
                            result = null,
                            message = null,
                            error = null,
                        )
                    }
                }.onSuccess { result ->
                    _state.value = _state.value.copy(
                        isRunning = false,
                        progress = null,
                        result = result,
                        importedChatId = result.chatId,
                        message = buildImportSummary(result, expectedChatId),
                        error = null,
                    )
                }.onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isRunning = false,
                        progress = null,
                        result = null,
                        importedChatId = null,
                        message = null,
                        error = throwable.message ?: "导入失败，请检查 ZIP 内容是否完整。",
                    )
                }
            }
        }
    }

    fun clearTerminalState() {
        val current = _state.value
        if (current.isRunning) return
        _state.value = current.copy(
            result = null,
            importedChatId = null,
            message = null,
            error = null,
        )
    }

    fun buildStartIntent(
        context: Context,
        uri: Uri,
        expectedChatId: Long? = null,
    ): Intent {
        return Intent(context, ChatImportForegroundService::class.java).apply {
            action = ChatImportForegroundService.ACTION_START_IMPORT
            putExtra(ChatImportForegroundService.EXTRA_URI, uri.toString())
            putExtra(ChatImportForegroundService.EXTRA_EXPECTED_CHAT_ID, expectedChatId)
        }
    }

    private fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.substringAfterLast('/')
        }
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun buildImportSummary(
        result: ChatImportResult,
        expectedChatId: Long?,
    ): String {
        return buildString {
            append("导入完成")
            append("，新增 ${result.importedMessageCount} 条")
            if (result.mergedMessageCount > 0) {
                append("，合并 ${result.mergedMessageCount} 条")
            }
            if (result.copiedResourceCount > 0) {
                append("，资源 ${result.copiedResourceCount} 个")
            }
            if (result.copiedAvatarCount > 0) {
                append("，头像 ${result.copiedAvatarCount} 个")
            }
            if (result.skippedMessageCount > 0) {
                append("，跳过 ${result.skippedMessageCount} 条")
            }
            if (result.missingResourceCount > 0) {
                append("，缺失资源 ${result.missingResourceCount} 个")
            }
            if (result.failedAvatarCount > 0) {
                append("，头像降级 ${result.failedAvatarCount} 个")
            }
            if (expectedChatId != null && expectedChatId != result.chatId) {
                append("。这次导入识别为另一会话，已按普通导入处理。")
            }
            if (result.skippedMessageCount > 0 || result.missingResourceCount > 0 || result.failedAvatarCount > 0) {
                append("。部分内容已跳过或降级处理。")
            }
        }
    }
}

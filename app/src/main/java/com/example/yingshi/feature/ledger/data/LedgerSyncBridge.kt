package com.example.yingshi.feature.ledger.data

import com.example.yingshi.data.remote.api.LedgerApi
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.DeletedRowRefDto
import com.example.yingshi.data.remote.dto.LedgerChangesDto
import com.example.yingshi.data.remote.dto.LedgerSyncRequestDto
import com.example.yingshi.data.remote.dto.LedgerSyncResponseDto
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

data class LedgerLocalSnapshot(
    val books: List<LedgerBookEntity> = emptyList(),
    val categories: List<LedgerCategoryEntity> = emptyList(),
    val accounts: List<LedgerAccountEntity> = emptyList(),
    val transactions: List<LedgerTransactionEntity> = emptyList(),
    val budgets: List<LedgerBudgetEntity> = emptyList(),
    val categoryBudgets: List<LedgerCategoryBudgetEntity> = emptyList(),
    val deletedItems: List<LedgerDeletedItemEntity> = emptyList(),
    val recurringRules: List<LedgerRecurringRuleEntity> = emptyList(),
    val recurringOccurrences: List<LedgerRecurringOccurrenceEntity> = emptyList(),
)

interface LedgerSyncBridge {
    val shouldSeedDemoData: Boolean
        get() = true

    val syncErrors: Flow<String>
        get() = emptyFlow()

    suspend fun hydrate(repository: LedgerRepository) {}

    suspend fun afterMutation(repository: LedgerRepository) {}

    suspend fun backfillSeedChangelogIfNeeded(repository: LedgerRepository) {}
}

object NoOpLedgerSyncBridge : LedgerSyncBridge

class RemoteLedgerSyncBridge(
    private val ledgerApi: LedgerApi = RemoteServiceFactory.ledgerApi,
    private val gson: Gson = Gson(),
    private val prefs: LedgerPreferencesStore,
) : LedgerSyncBridge {
    override val shouldSeedDemoData: Boolean = false

    private val _syncErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val syncErrors: SharedFlow<String> = _syncErrors

    private val syncMutex = Mutex()

    @Volatile
    private var hydrated = false

    private fun isTransientError(throwable: Throwable): Boolean {
        if (throwable is IOException) return true
        if (throwable is HttpException) {
            val code = throwable.code()
            return code in 500..599 || code == 429
        }
        return false
    }

    /**
     * 从 HttpException 响应体中提取服务端返回的实际错误信息。
     * 服务端 ApiResponse 结构: { "error": { "code": "...", "message": "..." } }
     */
    private fun extractServerError(throwable: Throwable): String? {
        if (throwable !is HttpException) return null
        return try {
            val raw = throwable.response()?.errorBody()?.string()
            if (raw.isNullOrBlank()) return null
            val parsed = JsonParser.parseString(raw)
            if (!parsed.isJsonObject) return null
            val errorObj = parsed.asJsonObject.get("error")
            if (errorObj != null && errorObj.isJsonObject) {
                val msg = errorObj.asJsonObject.get("message")?.asString
                if (!msg.isNullOrBlank()) return msg
            }
            // 兜底：尝试直接读取顶层 message
            parsed.asJsonObject.get("message")?.asString
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun backfillSeedChangelogIfNeeded(repository: LedgerRepository) {
        if (!AuthSessionManager.isLoggedIn) return
        if (prefs.isSeedChangelogBackfilled()) return
        // 一次性补全所有本地种子数据的 changelog，确保它们能同步到服务端
        // 修复：旧版本 ensureSeedData 不记 changelog，导致种子 books/accounts/categories 从未同步
        // 新增账单时服务端 FK 约束违反 → 500
        val dao = repository.dao
        // 修复 2：扫描本地账号表中 bookId 为空字符串的记录，回填为默认账本 ID。
        // 旧版本 saveAccount 将 bookId 设为 ""（V5_6 解耦误以为服务端也解耦），
        // 但服务端 ledger_accounts.book_id 仍为 NOT NULL + FK 约束，导致同步 500。
        val defaultBookId = repository.defaultBookId()
        dao.getAllAccounts().forEach { account ->
            if (account.bookId.isBlank()) {
                dao.updateAccount(account.copy(bookId = defaultBookId, updatedAtMillis = System.currentTimeMillis()))
            }
        }
        dao.getAllBooks().forEach { book ->
            dao.insertChangelogEntry(LedgerSyncChangelogEntity(tableName = "books", rowId = book.id))
        }
        dao.getAllAccounts().forEach { account ->
            dao.insertChangelogEntry(LedgerSyncChangelogEntity(tableName = "accounts", rowId = account.id))
        }
        dao.getAllCategories().forEach { category ->
            dao.insertChangelogEntry(LedgerSyncChangelogEntity(tableName = "categories", rowId = category.id))
        }
        prefs.setSeedChangelogBackfilled(true)
    }

    override suspend fun hydrate(repository: LedgerRepository) {
        if (hydrated || !AuthSessionManager.isLoggedIn) {
            if (!AuthSessionManager.isLoggedIn) hydrated = false
            return
        }
        syncMutex.withLock {
            if (hydrated || !AuthSessionManager.isLoggedIn) return
            var lastError: Throwable? = null
            for (attempt in 0..1) {
                if (attempt > 0) delay(1000L)
                val response = runCatching {
                    ledgerApi.syncLedger(
                        LedgerSyncRequestDto(
                            lastSyncVersionMillis = 0L,
                            changes = LedgerChangesDto(),
                        ),
                    ).data
                }.getOrElse { throwable ->
                    if (throwable is HttpException && throwable.code() == 401) {
                        hydrated = false
                        prefs.clearLastSyncVersion()
                        return
                    }
                    lastError = throwable
                    if (isTransientError(throwable) && attempt < 1) {
                        null // retry
                    } else {
                        null // stop retrying, fall through to post-loop emission
                    }
                }
                if (response != null) {
                    applySyncResponse(repository, response)
                    hydrated = true
                    return
                }
            }
            lastError?.let { _syncErrors.tryEmit("首次同步失败: ${it.message ?: "未知错误"}") }
            hydrated = true
        }
    }

    override suspend fun afterMutation(repository: LedgerRepository) {
        if (!hydrated || !AuthSessionManager.isLoggedIn) return
        syncMutex.withLock {
            if (!hydrated || !AuthSessionManager.isLoggedIn) return
            val dao = repository.dao
            val changelog = dao.getAllChangelogEntries()
            if (changelog.isEmpty()) return

            val request = buildSyncRequest(dao, changelog)
            var lastError: Throwable? = null
            var lastErrorCode: Int? = null
            var lastServerErrorMsg: String? = null
            for (attempt in 0..1) {
                if (attempt > 0) delay(1000L * (attempt + 1))
                val response = runCatching {
                    ledgerApi.syncLedger(request).data
                }.getOrElse { throwable ->
                    if (throwable is HttpException && throwable.code() == 401) {
                        hydrated = false
                        prefs.clearLastSyncVersion()
                        return
                    }
                    lastError = throwable
                    if (throwable is HttpException) {
                        lastErrorCode = throwable.code()
                        lastServerErrorMsg = extractServerError(throwable)
                    }
                    if (isTransientError(throwable) && attempt < 1) {
                        null // retry
                    } else {
                        null // stop retrying, fall through to post-loop emission
                    }
                }
                if (response != null) {
                    applySyncResponse(repository, response)
                    // P2 修复：服务端可能因 FK 预检跳过部分 row，只删除被接受的 changelog，
                    // 被拒绝的保留以便下次重试，避免数据永久丢失。
                    val rejectedRowIds = response.rejectedRowIds
                        ?.mapNotNull { it.id }
                        ?.toSet()
                        ?: emptySet()
                    val acceptedChangelog = if (rejectedRowIds.isEmpty()) {
                        changelog
                    } else {
                        changelog.filter { it.rowId !in rejectedRowIds }
                    }
                    if (acceptedChangelog.isNotEmpty()) {
                        dao.deleteChangelogEntries(acceptedChangelog.map { it.id })
                    }
                    if (rejectedRowIds.isNotEmpty()) {
                        val reasons = response.rejectedRowIds!!
                            .take(3)
                            .joinToString("; ") { "${it.table}/${it.id ?: "null"}: ${it.reason}" }
                        _syncErrors.tryEmit("部分变更被服务端拒绝（将保留以重试）: $reasons")
                    }
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    return
                }
            }
            // 400 (非 401) 表示客户端数据格式错误，重试也不会成功 → 清除 changelog 避免死锁阻塞后续同步
            if (lastErrorCode != null && lastErrorCode in 400..499 && lastErrorCode != 401 && lastErrorCode != 429) {
                if (lastErrorCode == 409) {
                    // P3 修复：409 = 服务端状态冲突（如 FK 父表未到位），与 500 路径对称，
                    // 只清子表 changelog，保留父表（books/accounts/categories）以重试。
                    // 避免误导性的"格式异常"提示和一刀切清掉全部 changelog 导致数据丢失。
                    val parentTables = setOf("books", "accounts", "categories")
                    val safeToDelete = changelog.filter { it.tableName !in parentTables }
                    if (safeToDelete.isNotEmpty()) {
                        dao.deleteChangelogEntries(safeToDelete.map { it.id })
                    }
                    val detail = lastServerErrorMsg ?: "HTTP 409"
                    _syncErrors.tryEmit("同步冲突（可能是依赖数据未到位），已保留父表变更以重试。详情: $detail")
                    return
                }
                // 其他 4xx（400/422 等）才是真正的格式错误，清掉全部 changelog
                dao.deleteChangelogEntries(changelog.map { it.id })
                val detail = lastServerErrorMsg ?: "HTTP $lastErrorCode"
                _syncErrors.tryEmit("本地变更格式异常，已跳过以避免阻塞后续同步。详情: $detail")
                return
            }
            // 500 错误：重试仍失败。将服务端返回的真实异常信息透传给用户，便于诊断根因。
            if (lastErrorCode != null && lastErrorCode >= 500) {
                // 判断是否为 schema mismatch 类错误（BadSqlGrammar / column not found 等）
                val isSchemaError = lastServerErrorMsg?.let { msg ->
                    msg.contains("schema mismatch", ignoreCase = true) ||
                    msg.contains("Column", ignoreCase = true) ||
                    msg.contains("not found", ignoreCase = true) ||
                    msg.contains("BadSqlGrammar", ignoreCase = true) ||
                    msg.contains("PersistenceException", ignoreCase = true)
                } ?: false

                if (isSchemaError) {
                    // schema 问题：清除全部 changelog 避免反复打 500，并提示用户重启服务端
                    dao.deleteChangelogEntries(changelog.map { it.id })
                    _syncErrors.tryEmit("同步失败：服务端数据库 schema 异常。$lastServerErrorMsg。请重启服务端后再试。")
                    return
                }

                // 非 schema 错误（FK 约束、字段值非法等）：
                // 只清除子表 changelog（transactions/budgets 等），保留父表。
                val parentTables = setOf("books", "accounts", "categories")
                val safeToDelete = changelog.filter { it.tableName !in parentTables }
                if (safeToDelete.isNotEmpty()) {
                    dao.deleteChangelogEntries(safeToDelete.map { it.id })
                }
                val detail = lastServerErrorMsg ?: "未知服务器错误"
                _syncErrors.tryEmit("同步失败（服务器错误），已跳过本次变更。详情: $detail")
                return
            }
            lastError?.let { _syncErrors.tryEmit("同步失败，变更将在下次操作时重试: ${it.message ?: "网络错误"}") }
        }
    }

    private suspend fun buildSyncRequest(
        dao: LedgerDao,
        changelog: List<LedgerSyncChangelogEntity>,
    ): LedgerSyncRequestDto {
        val updates = changelog.filter { !it.isDelete }
        val deletes = changelog.filter { it.isDelete }

        val changes = LedgerChangesDto(
            books = updates.filter { it.tableName == "books" }
                .mapNotNull { dao.getBook(it.rowId) }
                .map { gson.toJsonTree(it) },
            categories = updates.filter { it.tableName == "categories" }
                .mapNotNull { dao.getCategory(it.rowId) }
                .map { gson.toJsonTree(it) },
            accounts = updates.filter { it.tableName == "accounts" }
                .mapNotNull { dao.getAccount(it.rowId) }
                .map { gson.toJsonTree(it) },
            transactions = updates.filter { it.tableName == "transactions" }
                .mapNotNull { dao.getTransaction(it.rowId) }
                .map { gson.toJsonTree(it) },
            budgets = updates.filter { it.tableName == "budgets" }
                .mapNotNull { dao.getBudget(it.rowId) }
                .map { gson.toJsonTree(it) },
            categoryBudgets = updates.filter { it.tableName == "category_budgets" }
                .mapNotNull { dao.getCategoryBudget(it.rowId) }
                .map { gson.toJsonTree(it) },
            deletedItems = updates.filter { it.tableName == "deleted_items" }
                .mapNotNull { dao.getDeletedItem(it.rowId) }
                .map { gson.toJsonTree(it) },
            recurringRules = updates.filter { it.tableName == "recurring_rules" }
                .mapNotNull { dao.getRecurringRule(it.rowId) }
                .map { gson.toJsonTree(it) },
            recurringOccurrences = updates.filter { it.tableName == "recurring_occurrences" }
                .mapNotNull { dao.getRecurringOccurrenceById(it.rowId) }
                .map { gson.toJsonTree(it) },
            deletedRowIds = deletes.map { DeletedRowRefDto(table = it.tableName, id = it.rowId) },
        )
        return LedgerSyncRequestDto(
            lastSyncVersionMillis = prefs.getLastSyncVersionMillis(),
            changes = changes,
        )
    }

    private suspend fun applySyncResponse(
        repository: LedgerRepository,
        response: LedgerSyncResponseDto,
    ) {
        val snapshot = LedgerLocalSnapshot(
            books = response.changes.books.map { gson.fromJson(it, LedgerBookEntity::class.java) },
            categories = response.changes.categories.map { gson.fromJson(it, LedgerCategoryEntity::class.java) },
            accounts = response.changes.accounts.map { gson.fromJson(it, LedgerAccountEntity::class.java) },
            transactions = response.changes.transactions.map { gson.fromJson(it, LedgerTransactionEntity::class.java) },
            budgets = response.changes.budgets.map { gson.fromJson(it, LedgerBudgetEntity::class.java) },
            categoryBudgets = response.changes.categoryBudgets.map { gson.fromJson(it, LedgerCategoryBudgetEntity::class.java) },
            deletedItems = response.changes.deletedItems.map { gson.fromJson(it, LedgerDeletedItemEntity::class.java) },
            recurringRules = response.changes.recurringRules.map { gson.fromJson(it, LedgerRecurringRuleEntity::class.java) },
            recurringOccurrences = response.changes.recurringOccurrences.map { gson.fromJson(it, LedgerRecurringOccurrenceEntity::class.java) },
        )
        repository.upsertSyncResponse(snapshot)
        repository.deleteSyncRows(response.changes.deletedRowIds)
        prefs.setLastSyncVersionMillis(response.versionMillis)
    }
}

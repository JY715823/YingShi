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
                        hydrated = true
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
                    if (isTransientError(throwable) && attempt < 1) {
                        null // retry
                    } else {
                        null // stop retrying, fall through to post-loop emission
                    }
                }
                if (response != null) {
                    applySyncResponse(repository, response)
                    dao.deleteChangelogEntries(changelog.map { it.id })
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    return
                }
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

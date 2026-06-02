package com.example.yingshi.feature.ledger.data

import com.example.yingshi.data.remote.api.LedgerApi
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.UpsertLedgerSnapshotRequestDto
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

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

    suspend fun hydrate(repository: LedgerRepository) {}

    suspend fun afterMutation(repository: LedgerRepository) {}
}

object NoOpLedgerSyncBridge : LedgerSyncBridge

class RemoteLedgerSyncBridge(
    private val ledgerApi: LedgerApi = RemoteServiceFactory.ledgerApi,
    private val gson: Gson = Gson(),
) : LedgerSyncBridge {
    override val shouldSeedDemoData: Boolean = false

    private val hydrationMutex = Mutex()
    private var hydrated = false

    override suspend fun hydrate(repository: LedgerRepository) {
        if (hydrated || !AuthSessionManager.isLoggedIn) {
            hydrated = true
            return
        }
        hydrationMutex.withLock {
            if (hydrated || !AuthSessionManager.isLoggedIn) return
            val remoteSnapshot = runCatching { ledgerApi.getSnapshot().data }
                .getOrElse {
                    hydrated = true
                    return
                }
            val payload = remoteSnapshot.payload
            if (payload == null || payload.isJsonNull) {
                pushSnapshot(repository)
            } else {
                val snapshot = gson.fromJson(payload, LedgerLocalSnapshot::class.java)
                repository.replaceLocalSnapshot(snapshot)
            }
            hydrated = true
        }
    }

    override suspend fun afterMutation(repository: LedgerRepository) {
        if (!hydrated || !AuthSessionManager.isLoggedIn) return
        pushSnapshot(repository)
    }

    private suspend fun pushSnapshot(repository: LedgerRepository) {
        val snapshot = repository.exportLocalSnapshot()
        val payload = gson.toJsonTree(snapshot)
        runCatching {
            ledgerApi.putSnapshot(
                UpsertLedgerSnapshotRequestDto(
                    payload = payload,
                ),
            )
        }.getOrElse { throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                hydrated = false
            }
        }
    }
}

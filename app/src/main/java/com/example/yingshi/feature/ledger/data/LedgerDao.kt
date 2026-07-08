package com.example.yingshi.feature.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_books ORDER BY isDeleted ASC, sortOrder ASC")
    suspend fun getAllBooks(): List<LedgerBookEntity>

    @Query("SELECT * FROM ledger_categories ORDER BY bookId ASC, type ASC, sortOrder ASC")
    suspend fun getAllCategories(): List<LedgerCategoryEntity>

    @Query("SELECT * FROM ledger_accounts ORDER BY bookId ASC, sortOrder ASC")
    suspend fun getAllAccounts(): List<LedgerAccountEntity>

    @Query("SELECT * FROM ledger_transactions ORDER BY occurredAtMillis DESC, createdAtMillis DESC")
    suspend fun getAllTransactions(): List<LedgerTransactionEntity>

    @Query("SELECT * FROM ledger_budgets ORDER BY startMillis ASC, createdAtMillis ASC")
    suspend fun getAllBudgets(): List<LedgerBudgetEntity>

    @Query("SELECT * FROM ledger_category_budgets ORDER BY budgetId ASC, createdAtMillis ASC")
    suspend fun getAllCategoryBudgets(): List<LedgerCategoryBudgetEntity>

    @Query("SELECT * FROM ledger_deleted_items ORDER BY deletedAtMillis DESC")
    suspend fun getAllDeletedItems(): List<LedgerDeletedItemEntity>

    @Query("SELECT * FROM ledger_recurring_rules ORDER BY createdAtMillis ASC")
    suspend fun getAllRecurringRules(): List<LedgerRecurringRuleEntity>

    @Query("SELECT * FROM ledger_recurring_occurrences ORDER BY createdAtMillis ASC")
    suspend fun getAllRecurringOccurrences(): List<LedgerRecurringOccurrenceEntity>

    @Query("SELECT COUNT(*) FROM ledger_books WHERE isDeleted = 0")
    suspend fun activeBookCount(): Int

    @Query("SELECT * FROM ledger_books WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeBooks(): Flow<List<LedgerBookEntity>>

    @Query("SELECT * FROM ledger_books WHERE isDeleted = 1 ORDER BY sortOrder ASC")
    fun observeArchivedBooks(): Flow<List<LedgerBookEntity>>

    @Query("SELECT * FROM ledger_books ORDER BY isDeleted ASC, sortOrder ASC")
    fun observeAllBooks(): Flow<List<LedgerBookEntity>>

    @Query("SELECT * FROM ledger_books WHERE isDeleted = 0 ORDER BY sortOrder ASC LIMIT 1")
    fun observePrimaryBook(): Flow<LedgerBookEntity?>

    @Query("SELECT * FROM ledger_books WHERE id = :bookId AND isDeleted = 0 LIMIT 1")
    fun observeBook(bookId: String): Flow<LedgerBookEntity?>

    @Query("SELECT * FROM ledger_books WHERE id = :bookId LIMIT 1")
    suspend fun getBook(bookId: String): LedgerBookEntity?

    @Query("SELECT * FROM ledger_books WHERE isDeleted = 0 AND lower(trim(name)) = lower(trim(:name)) LIMIT 1")
    suspend fun findBookByName(name: String): LedgerBookEntity?

    @Query("SELECT * FROM ledger_categories WHERE bookId = :bookId AND hidden = 0 ORDER BY type ASC, sortOrder ASC")
    fun observeVisibleCategories(bookId: String): Flow<List<LedgerCategoryEntity>>

    @Query("SELECT * FROM ledger_categories WHERE bookId = :bookId ORDER BY type ASC, sortOrder ASC")
    fun observeAllCategories(bookId: String): Flow<List<LedgerCategoryEntity>>

    @Query("SELECT * FROM ledger_categories WHERE bookId = :bookId AND type = :type AND hidden = 0 ORDER BY sortOrder ASC")
    fun observeCategoriesByType(bookId: String, type: LedgerCategoryType): Flow<List<LedgerCategoryEntity>>

    @Query("SELECT * FROM ledger_categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategory(categoryId: String): LedgerCategoryEntity?

    @Query("SELECT * FROM ledger_categories WHERE bookId = :bookId AND type = :type AND name = :name LIMIT 1")
    suspend fun findCategoryByName(bookId: String, type: LedgerCategoryType, name: String): LedgerCategoryEntity?

    @Query("SELECT * FROM ledger_accounts WHERE bookId = :bookId AND hidden = 0 ORDER BY sortOrder ASC")
    fun observeVisibleAccounts(bookId: String): Flow<List<LedgerAccountEntity>>

    @Query(
        """
        SELECT a.*
        FROM ledger_accounts a
        INNER JOIN ledger_books b ON b.id = a.bookId
        WHERE b.isDeleted = 0 AND a.hidden = 0
        ORDER BY a.bookId ASC, a.sortOrder ASC
        """,
    )
    fun observeVisibleAccountsAcrossBooks(): Flow<List<LedgerAccountEntity>>

    @Query("SELECT * FROM ledger_accounts WHERE bookId = :bookId ORDER BY sortOrder ASC")
    fun observeAllAccounts(bookId: String): Flow<List<LedgerAccountEntity>>

    @Query("SELECT * FROM ledger_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE bookId = :bookId AND name = :name LIMIT 1")
    suspend fun findAccountByName(bookId: String, name: String): LedgerAccountEntity?

    @Query(
        """
        SELECT * FROM ledger_recurring_rules
        WHERE bookId = :bookId
        ORDER BY enabled DESC, nextOccurrenceAtMillis ASC, createdAtMillis ASC
        """,
    )
    fun observeRecurringRules(bookId: String): Flow<List<LedgerRecurringRuleEntity>>

    @Query("SELECT * FROM ledger_recurring_rules WHERE id = :ruleId LIMIT 1")
    suspend fun getRecurringRule(ruleId: String): LedgerRecurringRuleEntity?

    @Query(
        """
        SELECT r.*
        FROM ledger_recurring_rules r
        INNER JOIN ledger_books b ON b.id = r.bookId
        WHERE b.isDeleted = 0
        AND r.enabled = 1
        AND r.nextOccurrenceAtMillis <= :nowMillis
        AND (r.endAtMillis IS NULL OR r.nextOccurrenceAtMillis <= r.endAtMillis)
        ORDER BY r.nextOccurrenceAtMillis ASC
        """,
    )
    suspend fun getDueRecurringRules(nowMillis: Long): List<LedgerRecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRule(rule: LedgerRecurringRuleEntity)

    @Update
    suspend fun updateRecurringRule(rule: LedgerRecurringRuleEntity)

    @Query("DELETE FROM ledger_recurring_rules WHERE id = :ruleId")
    suspend fun deleteRecurringRule(ruleId: String)

    @Query("DELETE FROM ledger_recurring_occurrences WHERE ruleId = :ruleId")
    suspend fun deleteRecurringOccurrencesByRuleId(ruleId: String)

    @Query("SELECT * FROM ledger_recurring_occurrences WHERE ruleId = :ruleId AND occurrenceAtMillis = :occurrenceAtMillis LIMIT 1")
    suspend fun getRecurringOccurrence(ruleId: String, occurrenceAtMillis: Long): LedgerRecurringOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecurringOccurrence(occurrence: LedgerRecurringOccurrenceEntity): Long

    @Query("SELECT * FROM ledger_transactions WHERE bookId = :bookId AND deletedAtMillis IS NULL ORDER BY occurredAtMillis DESC")
    fun observeTransactions(bookId: String): Flow<List<LedgerTransactionEntity>>

    @Query(
        """
        SELECT * FROM ledger_transactions
        WHERE bookId = :bookId
        AND deletedAtMillis IS NULL
        AND occurredAtMillis >= :startMillis
        AND occurredAtMillis < :endMillis
        ORDER BY occurredAtMillis DESC
        """,
    )
    fun observeTransactionsInRange(
        bookId: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<LedgerTransactionEntity>>

    @Query(
        """
        SELECT * FROM ledger_transactions
        WHERE bookId = :bookId
        AND deletedAtMillis IS NULL
        AND occurredAtMillis >= :startMillis
        AND occurredAtMillis < :endMillis
        ORDER BY occurredAtMillis DESC
        """,
    )
    suspend fun getTransactionsInRange(
        bookId: String,
        startMillis: Long,
        endMillis: Long,
    ): List<LedgerTransactionEntity>

    @Query("SELECT * FROM ledger_transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransaction(transactionId: String): LedgerTransactionEntity?

    @Query("SELECT COUNT(*) FROM ledger_transactions WHERE bookId = :bookId")
    suspend fun countTransactionsByBook(bookId: String): Int

    @Query(
        """
        SELECT * FROM ledger_transactions
        WHERE bookId = :bookId
        AND deletedAtMillis IS NULL
        AND (
            remark LIKE '%' || :query || '%'
            OR CAST(amountCents AS TEXT) LIKE '%' || :query || '%'
        )
        ORDER BY occurredAtMillis DESC
        """,
    )
    fun searchTransactions(bookId: String, query: String): Flow<List<LedgerTransactionEntity>>

    @Query(
        """
        SELECT t.* FROM ledger_transactions t
        LEFT JOIN ledger_categories c ON t.categoryId = c.id
        LEFT JOIN ledger_accounts a ON t.accountId = a.id
        LEFT JOIN ledger_accounts ta ON t.toAccountId = ta.id
        WHERE t.bookId = :bookId
        AND t.deletedAtMillis IS NULL
        AND (
            :keyword = ''
            OR t.remark LIKE '%' || :keyword || '%'
            OR CAST(t.amountCents AS TEXT) LIKE '%' || :keyword || '%'
            OR c.name LIKE '%' || :keyword || '%'
            OR a.name LIKE '%' || :keyword || '%'
            OR IFNULL(ta.name, '') LIKE '%' || :keyword || '%'
        )
        AND (:typeFilter = '' OR t.type = :typeFilter)
        AND (:categoryId = '' OR t.categoryId = :categoryId)
        AND (:accountId = '' OR t.accountId = :accountId OR t.toAccountId = :accountId)
        AND (:startDateMillis = -1 OR t.occurredAtMillis >= :startDateMillis)
        AND (:endDateMillis = -1 OR t.occurredAtMillis <= :endDateMillis)
        AND (:minAmountCents = -1 OR t.amountCents >= :minAmountCents)
        AND (:maxAmountCents = -1 OR t.amountCents <= :maxAmountCents)
        ORDER BY t.occurredAtMillis DESC
        """,
    )
    fun searchTransactionsFiltered(
        bookId: String,
        keyword: String,
        typeFilter: String,
        categoryId: String,
        accountId: String,
        startDateMillis: Long,
        endDateMillis: Long,
        minAmountCents: Long,
        maxAmountCents: Long,
    ): Flow<List<LedgerTransactionEntity>>

    @Query(
        """
        SELECT * FROM ledger_budgets
        WHERE bookId = :bookId
        AND period = :period
        AND startMillis = :startMillis
        AND endMillis = :endMillis
        LIMIT 1
        """,
    )
    fun observeBudget(
        bookId: String,
        period: LedgerBudgetPeriod,
        startMillis: Long,
        endMillis: Long,
    ): Flow<LedgerBudgetEntity?>

    @Query("SELECT * FROM ledger_budgets WHERE id = :budgetId LIMIT 1")
    suspend fun getBudget(budgetId: String): LedgerBudgetEntity?

    @Query("SELECT * FROM ledger_category_budgets WHERE budgetId = :budgetId")
    fun observeCategoryBudgets(budgetId: String): Flow<List<LedgerCategoryBudgetEntity>>

    @Query("SELECT * FROM ledger_category_budgets WHERE budgetId = :budgetId")
    suspend fun getCategoryBudgets(budgetId: String): List<LedgerCategoryBudgetEntity>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM ledger_category_budgets WHERE budgetId = :budgetId")
    suspend fun categoryBudgetTotal(budgetId: String): Long

    @Query("SELECT * FROM ledger_deleted_items WHERE bookId = :bookId ORDER BY deletedAtMillis DESC")
    fun observeDeletedItems(bookId: String): Flow<List<LedgerDeletedItemEntity>>

    @Query("SELECT * FROM ledger_deleted_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getDeletedItemByItemId(itemId: String): LedgerDeletedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: LedgerBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<LedgerBookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<LedgerCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: LedgerCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<LedgerAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: LedgerAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionRaw(transaction: LedgerTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsRaw(transactions: List<LedgerTransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: LedgerBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<LedgerBudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryBudget(categoryBudget: LedgerCategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryBudgets(categoryBudgets: List<LedgerCategoryBudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItem(deletedItem: LedgerDeletedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItems(deletedItems: List<LedgerDeletedItemEntity>)

    @Update
    suspend fun updateBook(book: LedgerBookEntity)

    @Update
    suspend fun updateCategory(category: LedgerCategoryEntity)

    @Update
    suspend fun updateAccount(account: LedgerAccountEntity)

    @Update
    suspend fun updateTransactionRaw(transaction: LedgerTransactionEntity)

    @Update
    suspend fun updateBudget(budget: LedgerBudgetEntity)

    @Update
    suspend fun updateCategoryBudget(categoryBudget: LedgerCategoryBudgetEntity)

    @Query("DELETE FROM ledger_deleted_items WHERE itemId = :itemId")
    suspend fun deleteDeletedItemByItemId(itemId: String)

    @Query("DELETE FROM ledger_category_budgets WHERE id = :categoryBudgetId")
    suspend fun deleteCategoryBudget(categoryBudgetId: String)

    @Query("DELETE FROM ledger_category_budgets WHERE budgetId = :budgetId")
    suspend fun deleteCategoryBudgetsByBudgetId(budgetId: String)

    @Query("DELETE FROM ledger_category_budgets WHERE budgetId = :budgetId AND categoryId = :categoryId")
    suspend fun deleteCategoryBudgetByCategory(budgetId: String, categoryId: String)

    @Query("DELETE FROM ledger_budgets WHERE id = :budgetId")
    suspend fun deleteBudget(budgetId: String)

    @Query("DELETE FROM ledger_transactions WHERE id = :transactionId")
    suspend fun hardDeleteTransaction(transactionId: String)

    @Query("DELETE FROM ledger_recurring_occurrences")
    suspend fun clearRecurringOccurrences()

    @Query("DELETE FROM ledger_recurring_rules")
    suspend fun clearRecurringRules()

    @Query("DELETE FROM ledger_deleted_items")
    suspend fun clearDeletedItems()

    @Query("DELETE FROM ledger_category_budgets")
    suspend fun clearCategoryBudgets()

    @Query("DELETE FROM ledger_budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM ledger_transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM ledger_accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM ledger_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM ledger_books")
    suspend fun clearBooks()

    @Query("UPDATE ledger_categories SET hidden = :hidden, updatedAtMillis = :updatedAtMillis WHERE id = :categoryId")
    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean, updatedAtMillis: Long)

    @Query("UPDATE ledger_accounts SET hidden = :hidden, updatedAtMillis = :updatedAtMillis WHERE id = :accountId")
    suspend fun setAccountHidden(accountId: String, hidden: Boolean, updatedAtMillis: Long)

    @Query("UPDATE ledger_books SET isDeleted = :archived, updatedAtMillis = :updatedAtMillis WHERE id = :bookId")
    suspend fun setBookArchived(bookId: String, archived: Boolean, updatedAtMillis: Long)

    @Query("SELECT * FROM ledger_transactions WHERE id IN (:transactionIds)")
    suspend fun getTransactions(transactionIds: List<String>): List<LedgerTransactionEntity>

    @Transaction
    suspend fun insertTransaction(transaction: LedgerTransactionEntity) {
        applyTransactionBalance(transaction, direction = 1)
        insertTransactionRaw(transaction)
    }

    @Transaction
    suspend fun insertTransactions(transactions: List<LedgerTransactionEntity>) {
        transactions.forEach { insertTransaction(it) }
    }

    @Transaction
    suspend fun updateTransaction(updated: LedgerTransactionEntity) {
        val previous = getTransaction(updated.id) ?: return insertTransaction(updated)
        if (previous.deletedAtMillis == null) {
            applyTransactionBalance(previous, direction = -1)
        }
        if (updated.deletedAtMillis == null) {
            applyTransactionBalance(updated, direction = 1)
        }
        updateTransactionRaw(updated)
    }

    @Transaction
    suspend fun softDeleteTransaction(
        transactionId: String,
        deletedAtMillis: Long,
        expiresAtMillis: Long,
        deletedItemId: String,
        title: String,
    ) {
        val transaction = getTransaction(transactionId) ?: return
        if (transaction.deletedAtMillis == null) {
            applyTransactionBalance(transaction, direction = -1)
        }
        updateTransactionRaw(
            transaction.copy(
                deletedAtMillis = deletedAtMillis,
                updatedAtMillis = deletedAtMillis,
            ),
        )
        insertDeletedItem(
            LedgerDeletedItemEntity(
                id = deletedItemId,
                bookId = transaction.bookId,
                itemId = transaction.id,
                type = LedgerDeletedItemType.TRANSACTION,
                title = title,
                amountCents = transaction.amountCents,
                deletedAtMillis = deletedAtMillis,
                expiresAtMillis = expiresAtMillis,
            ),
        )
    }

    @Transaction
    suspend fun restoreTransaction(transactionId: String, restoredAtMillis: Long) {
        val transaction = getTransaction(transactionId) ?: return
        if (transaction.deletedAtMillis != null) {
            val restored = transaction.copy(
                deletedAtMillis = null,
                updatedAtMillis = restoredAtMillis,
            )
            applyTransactionBalance(restored, direction = 1)
            updateTransactionRaw(restored)
        }
        deleteDeletedItemByItemId(transactionId)
    }

    private suspend fun applyTransactionBalance(transaction: LedgerTransactionEntity, direction: Int) {
        val account = getAccount(transaction.accountId) ?: return
        val delta = when (transaction.type) {
            LedgerTransactionType.EXPENSE -> -transaction.amountCents
            LedgerTransactionType.INCOME -> transaction.amountCents
            LedgerTransactionType.TRANSFER -> -transaction.amountCents
        } * direction
        updateAccount(
            account.copy(
                balanceCents = account.balanceCents + delta,
                updatedAtMillis = transaction.updatedAtMillis,
            ),
        )
        if (transaction.type == LedgerTransactionType.TRANSFER) {
            val toAccountId = transaction.toAccountId ?: return
            val toAccount = getAccount(toAccountId) ?: return
            updateAccount(
                toAccount.copy(
                    balanceCents = toAccount.balanceCents + transaction.amountCents * direction,
                    updatedAtMillis = transaction.updatedAtMillis,
                ),
            )
        }
    }

    @Transaction
    suspend fun replaceAllData(snapshot: LedgerLocalSnapshot) {
        clearRecurringOccurrences()
        clearRecurringRules()
        clearDeletedItems()
        clearCategoryBudgets()
        clearBudgets()
        clearTransactions()
        clearAccounts()
        clearCategories()
        clearBooks()
        if (snapshot.books.isNotEmpty()) insertBooks(snapshot.books)
        if (snapshot.categories.isNotEmpty()) insertCategories(snapshot.categories)
        if (snapshot.accounts.isNotEmpty()) insertAccounts(snapshot.accounts)
        if (snapshot.transactions.isNotEmpty()) insertTransactionsRaw(snapshot.transactions)
        if (snapshot.budgets.isNotEmpty()) insertBudgets(snapshot.budgets)
        if (snapshot.categoryBudgets.isNotEmpty()) insertCategoryBudgets(snapshot.categoryBudgets)
        if (snapshot.deletedItems.isNotEmpty()) insertDeletedItems(snapshot.deletedItems)
        if (snapshot.recurringRules.isNotEmpty()) {
            snapshot.recurringRules.forEach { insertRecurringRule(it) }
        }
        if (snapshot.recurringOccurrences.isNotEmpty()) {
            snapshot.recurringOccurrences.forEach { insertRecurringOccurrence(it) }
        }
    }

    // ── Changelog operations ──────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChangelogEntry(entry: LedgerSyncChangelogEntity)

    @Query("SELECT * FROM ledger_sync_changelog ORDER BY changedAtMillis ASC")
    suspend fun getAllChangelogEntries(): List<LedgerSyncChangelogEntity>

    @Query("DELETE FROM ledger_sync_changelog WHERE id IN (:ids)")
    suspend fun deleteChangelogEntries(ids: List<Long>)

    @Query("DELETE FROM ledger_sync_changelog")
    suspend fun clearChangelog()

    // ── Upsert operations for sync (no balance tracking) ──────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: LedgerBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: LedgerCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: LedgerAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: LedgerTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: LedgerBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryBudget(categoryBudget: LedgerCategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeletedItem(deletedItem: LedgerDeletedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringRule(rule: LedgerRecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringOccurrence(occurrence: LedgerRecurringOccurrenceEntity)

    // ── Get-by-id for sync changelog building ─────────────────────────

    @Query("SELECT * FROM ledger_category_budgets WHERE id = :id LIMIT 1")
    suspend fun getCategoryBudget(id: String): LedgerCategoryBudgetEntity?

    @Query("SELECT * FROM ledger_deleted_items WHERE id = :id LIMIT 1")
    suspend fun getDeletedItem(id: String): LedgerDeletedItemEntity?

    @Query("SELECT * FROM ledger_recurring_occurrences WHERE id = :id LIMIT 1")
    suspend fun getRecurringOccurrenceById(id: String): LedgerRecurringOccurrenceEntity?

    // ── Hard-delete operations for sync ────────────────────────────────

    @Query("DELETE FROM ledger_books WHERE id = :bookId")
    suspend fun hardDeleteBook(bookId: String)

    @Query("DELETE FROM ledger_categories WHERE id = :categoryId")
    suspend fun hardDeleteCategory(categoryId: String)

    @Query("DELETE FROM ledger_accounts WHERE id = :accountId")
    suspend fun hardDeleteAccount(accountId: String)

    @Query("DELETE FROM ledger_deleted_items WHERE id = :id")
    suspend fun hardDeleteDeletedItem(id: String)

    @Query("DELETE FROM ledger_recurring_occurrences WHERE id = :id")
    suspend fun hardDeleteRecurringOccurrence(id: String)
}

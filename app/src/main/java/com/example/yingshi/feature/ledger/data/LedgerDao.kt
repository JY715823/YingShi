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
    @Query("SELECT COUNT(*) FROM ledger_books WHERE isDeleted = 0")
    suspend fun activeBookCount(): Int

    @Query("SELECT * FROM ledger_books WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeBooks(): Flow<List<LedgerBookEntity>>

    @Query("SELECT * FROM ledger_books WHERE isDeleted = 0 ORDER BY sortOrder ASC LIMIT 1")
    fun observePrimaryBook(): Flow<LedgerBookEntity?>

    @Query("SELECT * FROM ledger_books WHERE id = :bookId AND isDeleted = 0 LIMIT 1")
    fun observeBook(bookId: String): Flow<LedgerBookEntity?>

    @Query("SELECT * FROM ledger_books WHERE id = :bookId LIMIT 1")
    suspend fun getBook(bookId: String): LedgerBookEntity?

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

    @Query("SELECT * FROM ledger_accounts WHERE bookId = :bookId ORDER BY sortOrder ASC")
    fun observeAllAccounts(bookId: String): Flow<List<LedgerAccountEntity>>

    @Query("SELECT * FROM ledger_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE bookId = :bookId AND name = :name LIMIT 1")
    suspend fun findAccountByName(bookId: String, name: String): LedgerAccountEntity?

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
    suspend fun insertBudget(budget: LedgerBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryBudget(categoryBudget: LedgerCategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItem(deletedItem: LedgerDeletedItemEntity)

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

    @Query("DELETE FROM ledger_deleted_items WHERE itemId = :itemId")
    suspend fun permanentlyDeleteDeletedItem(itemId: String)

    @Query("UPDATE ledger_categories SET hidden = :hidden, updatedAtMillis = :updatedAtMillis WHERE id = :categoryId")
    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean, updatedAtMillis: Long)

    @Query("UPDATE ledger_accounts SET hidden = :hidden, updatedAtMillis = :updatedAtMillis WHERE id = :accountId")
    suspend fun setAccountHidden(accountId: String, hidden: Boolean, updatedAtMillis: Long)

    @Query("SELECT * FROM ledger_transactions WHERE id IN (:transactionIds)")
    suspend fun getTransactions(transactionIds: List<String>): List<LedgerTransactionEntity>

    @Transaction
    suspend fun insertTransaction(transaction: LedgerTransactionEntity) {
        applyTransactionBalance(transaction, direction = 1)
        insertTransactionRaw(transaction)
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
}

package com.example.yingshi.feature.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LedgerTransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

enum class LedgerCategoryType {
    EXPENSE,
    INCOME,
}

enum class LedgerAccountType {
    CASH,
    DEBIT_CARD,
    CREDIT,
    ALIPAY,
    WECHAT,
    INVESTMENT,
    DEBT,
    OTHER,
}

enum class LedgerBudgetPeriod {
    WEEK,
    MONTH,
    QUARTER,
    YEAR,
}

enum class LedgerDeletedItemType {
    TRANSACTION,
}

@Entity(tableName = "ledger_books")
data class LedgerBookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val template: String,
    val currencyCode: String,
    val currencySymbol: String,
    val coverColor: Long,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "ledger_categories",
    indices = [
        Index(value = ["bookId", "type", "sortOrder"]),
        Index(value = ["bookId", "name", "type"], unique = true),
    ],
)
data class LedgerCategoryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    val iconKey: String,
    val color: Long,
    val type: LedgerCategoryType,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "ledger_accounts",
    indices = [
        Index(value = ["bookId", "sortOrder"]),
        Index(value = ["bookId", "name"], unique = true),
    ],
)
data class LedgerAccountEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    val type: LedgerAccountType,
    val iconKey: String,
    val color: Long,
    val initialBalanceCents: Long,
    val balanceCents: Long,
    val creditLimitCents: Long? = null,
    val includeInTotal: Boolean = true,
    val hidden: Boolean = false,
    val note: String = "",
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "ledger_transactions",
    indices = [
        Index(value = ["bookId", "occurredAtMillis"]),
        Index(value = ["bookId", "type"]),
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["toAccountId"]),
    ],
)
data class LedgerTransactionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val amountCents: Long,
    val type: LedgerTransactionType,
    val occurredAtMillis: Long,
    val remark: String,
    val method: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
)

@Entity(
    tableName = "ledger_budgets",
    indices = [
        Index(value = ["bookId", "period", "startMillis", "endMillis"], unique = true),
    ],
)
data class LedgerBudgetEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val period: LedgerBudgetPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val totalAmountCents: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "ledger_category_budgets",
    indices = [
        Index(value = ["budgetId", "categoryId"], unique = true),
    ],
)
data class LedgerCategoryBudgetEntity(
    @PrimaryKey val id: String,
    val budgetId: String,
    val categoryId: String,
    val amountCents: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "ledger_deleted_items",
    indices = [
        Index(value = ["bookId", "deletedAtMillis"]),
        Index(value = ["itemId"], unique = true),
    ],
)
data class LedgerDeletedItemEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val itemId: String,
    val type: LedgerDeletedItemType,
    val title: String,
    val amountCents: Long,
    val deletedAtMillis: Long,
    val expiresAtMillis: Long,
)

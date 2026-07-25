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
    val creatorUserId: String? = null,
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
    val deletedAtMillis: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "ledger_accounts",
    indices = [
        Index(value = ["ownerUserId", "sortOrder"]),
    ],
)
data class LedgerAccountEntity(
    @PrimaryKey val id: String,
    val bookId: String = "",
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
    val ownerUserId: String? = null,
    val bankKey: String? = null,
    val bankName: String? = null,
    val cardNumberTail: String? = null,
    val sortOrder: Int,
    val deletedAtMillis: Long? = null,
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
    val deletedAtMillis: Long? = null,
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
    val deletedAtMillis: Long? = null,
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
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Entity(
    tableName = "ledger_recurring_rules",
    indices = [
        Index(value = ["bookId", "enabled", "nextOccurrenceAtMillis"]),
        Index(value = ["bookId", "type"]),
    ],
)
data class LedgerRecurringRuleEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val type: LedgerTransactionType,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String?,
    val amountCents: Long,
    val remark: String,
    val frequency: LedgerRecurringFrequency,
    val startAtMillis: Long,
    val endAtMillis: Long?,
    val nextOccurrenceAtMillis: Long,
    val enabled: Boolean,
    val deletedAtMillis: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "ledger_recurring_occurrences",
    indices = [
        Index(value = ["ruleId", "occurrenceAtMillis"], unique = true),
        Index(value = ["transactionId"], unique = true),
    ],
)
data class LedgerRecurringOccurrenceEntity(
    @PrimaryKey val id: String,
    val ruleId: String,
    val transactionId: String,
    val occurrenceAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = 0L,
)

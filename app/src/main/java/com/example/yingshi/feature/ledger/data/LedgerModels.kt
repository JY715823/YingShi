package com.example.yingshi.feature.ledger.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LedgerBook(
    val id: String,
    val name: String,
    val currencySymbol: String,
)

data class LedgerCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val color: Long,
    val type: LedgerCategoryType,
    val hidden: Boolean = false,
)

data class LedgerAccount(
    val id: String,
    val name: String,
    val type: LedgerAccountType,
    val iconKey: String,
    val color: Long,
    val initialBalanceCents: Long,
    val balanceCents: Long,
    val includeInTotal: Boolean,
    val hidden: Boolean,
    val note: String,
)

data class LedgerTransaction(
    val id: String,
    val bookId: String,
    val category: LedgerCategory?,
    val account: LedgerAccount?,
    val toAccount: LedgerAccount?,
    val amountCents: Long,
    val type: LedgerTransactionType,
    val occurredAtMillis: Long,
    val remark: String,
    val method: String,
)

data class LedgerBudget(
    val id: String,
    val period: LedgerBudgetPeriod,
    val startMillis: Long,
    val endMillis: Long,
    val totalAmountCents: Long,
)

data class LedgerCategoryBudget(
    val id: String,
    val budgetId: String,
    val category: LedgerCategory?,
    val amountCents: Long,
    val usedCents: Long,
    val transactionCount: Int,
)

data class LedgerDeletedItem(
    val id: String,
    val itemId: String,
    val title: String,
    val amountCents: Long,
    val deletedAtMillis: Long,
    val expiresAtMillis: Long,
)

data class LedgerPeriodStats(
    val expenseCents: Long,
    val incomeCents: Long,
    val balanceCents: Long,
    val transactions: List<LedgerTransaction>,
    val categoryStats: List<LedgerCategoryStat>,
    val dailyStats: List<LedgerDailyStat>,
)

data class LedgerCategoryStat(
    val category: LedgerCategory?,
    val amountCents: Long,
    val count: Int,
    val percent: Float,
)

data class LedgerDailyStat(
    val dayStartMillis: Long,
    val expenseCents: Long,
    val incomeCents: Long,
)

data class LedgerTransactionDraft(
    val id: String? = null,
    val bookId: String,
    val categoryId: String?,
    val accountId: String,
    val toAccountId: String? = null,
    val amountCents: Long,
    val type: LedgerTransactionType,
    val occurredAtMillis: Long,
    val remark: String,
)

data class LedgerCategoryDraft(
    val id: String? = null,
    val bookId: String,
    val name: String,
    val iconKey: String,
    val color: Long,
    val type: LedgerCategoryType,
)

data class LedgerAccountDraft(
    val id: String? = null,
    val bookId: String,
    val name: String,
    val type: LedgerAccountType,
    val initialBalanceCents: Long,
    val includeInTotal: Boolean,
    val note: String,
)

enum class LedgerSearchTransactionType {
    ALL,
    EXPENSE,
    INCOME,
    TRANSFER,
}

data class LedgerSearchFilter(
    val keyword: String = "",
    val type: LedgerSearchTransactionType = LedgerSearchTransactionType.ALL,
    val categoryId: String? = null,
    val accountId: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
) {
    fun isEmpty(): Boolean {
        return keyword.isBlank() &&
            type == LedgerSearchTransactionType.ALL &&
            categoryId.isNullOrBlank() &&
            accountId.isNullOrBlank() &&
            startDate == null &&
            endDate == null &&
            minAmountCents == null &&
            maxAmountCents == null
    }

    fun matches(transaction: LedgerTransaction): Boolean {
        val query = keyword.trim()
        if (query.isNotBlank()) {
            val amountText = transaction.amountCents.toString()
            val matched = transaction.remark.contains(query, ignoreCase = true) ||
                transaction.category?.name?.contains(query, ignoreCase = true) == true ||
                transaction.account?.name?.contains(query, ignoreCase = true) == true ||
                transaction.toAccount?.name?.contains(query, ignoreCase = true) == true ||
                amountText.contains(query)
            if (!matched) return false
        }
        if (type != LedgerSearchTransactionType.ALL) {
            val matchedType = when (type) {
                LedgerSearchTransactionType.ALL -> true
                LedgerSearchTransactionType.EXPENSE -> transaction.type == LedgerTransactionType.EXPENSE
                LedgerSearchTransactionType.INCOME -> transaction.type == LedgerTransactionType.INCOME
                LedgerSearchTransactionType.TRANSFER -> transaction.type == LedgerTransactionType.TRANSFER
            }
            if (!matchedType) return false
        }
        if (!categoryId.isNullOrBlank() && transaction.category?.id != categoryId) return false
        if (!accountId.isNullOrBlank()) {
            val matchedAccount = transaction.account?.id == accountId || transaction.toAccount?.id == accountId
            if (!matchedAccount) return false
        }
        val occurredDate = Instant.ofEpochMilli(transaction.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (startDate != null && occurredDate.isBefore(startDate)) return false
        if (endDate != null && occurredDate.isAfter(endDate)) return false
        if (minAmountCents != null && transaction.amountCents < minAmountCents) return false
        if (maxAmountCents != null && transaction.amountCents > maxAmountCents) return false
        return true
    }
}

fun defaultAccountIconKey(type: LedgerAccountType): String = when (type) {
    LedgerAccountType.CASH -> "wallet"
    LedgerAccountType.DEBIT_CARD -> "asset"
    LedgerAccountType.CREDIT -> "wallet"
    LedgerAccountType.ALIPAY -> "alipay"
    LedgerAccountType.WECHAT -> "wechat"
    LedgerAccountType.INVESTMENT -> "trending_up"
    LedgerAccountType.DEBT -> "transfer"
    LedgerAccountType.OTHER -> "more_horiz"
}

fun defaultAccountColor(type: LedgerAccountType): Long = when (type) {
    LedgerAccountType.CASH -> 0xFF4CAF50
    LedgerAccountType.DEBIT_CARD -> 0xFFE54D4D
    LedgerAccountType.CREDIT -> 0xFF9C6ADE
    LedgerAccountType.ALIPAY -> 0xFF248BFF
    LedgerAccountType.WECHAT -> 0xFF20B15A
    LedgerAccountType.INVESTMENT -> 0xFF3CB4A5
    LedgerAccountType.DEBT -> 0xFFF28C52
    LedgerAccountType.OTHER -> 0xFF8D99A6
}

fun LedgerBookEntity.toDomain() = LedgerBook(
    id = id,
    name = name,
    currencySymbol = currencySymbol,
)

fun LedgerCategoryEntity.toDomain() = LedgerCategory(
    id = id,
    name = name,
    iconKey = iconKey,
    color = color,
    type = type,
    hidden = hidden,
)

fun LedgerAccountEntity.toDomain() = LedgerAccount(
    id = id,
    name = name,
    type = type,
    iconKey = iconKey,
    color = color,
    initialBalanceCents = initialBalanceCents,
    balanceCents = balanceCents,
    includeInTotal = includeInTotal,
    hidden = hidden,
    note = note,
)

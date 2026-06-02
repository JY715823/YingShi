package com.example.yingshi.feature.ledger

import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerDateRange
import com.example.yingshi.feature.ledger.data.LedgerDateUtils
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionDraft
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

const val LedgerImportExportHeader = "日期,类型,金额,分类,账户,转入账户,备注"

data class LedgerImportPreview(
    val rows: List<LedgerImportPreviewRow>,
) {
    val validRows: List<LedgerImportPreviewRow>
        get() = rows.filter { it.draft != null }

    val invalidRows: List<LedgerImportPreviewRow>
        get() = rows.filter { it.draft == null }

    val validCount: Int
        get() = validRows.size

    val invalidCount: Int
        get() = invalidRows.size
}

data class LedgerImportPreviewRow(
    val lineNumber: Int,
    val rawText: String,
    val occurredAtMillis: Long?,
    val type: LedgerTransactionType?,
    val amountCents: Long?,
    val categoryName: String,
    val accountName: String,
    val toAccountName: String,
    val remark: String,
    val draft: LedgerTransactionDraft?,
    val error: String?,
)

fun buildLedgerImportPreview(
    text: String,
    bookId: String,
    categories: List<LedgerCategory>,
    accounts: List<LedgerAccount>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LedgerImportPreview {
    val delimiter = detectLedgerImportDelimiter(text)
    val records = parseLedgerRecords(text, delimiter)
        .filter { record -> record.any { it.isNotBlank() } }
    val dataRecords = if (records.firstOrNull()?.isLedgerImportHeader() == true) {
        records.drop(1)
    } else {
        records
    }
    val categoryLookup = categories.groupBy { normalizeImportKey(it.name) }
    val accountLookup = accounts.associateBy { normalizeImportKey(it.name) }
    val rows = dataRecords.mapIndexed { index, rawFields ->
        val fields = rawFields.toMutableList().apply {
            while (size < 7) add("")
        }
        val rawText = rawFields.joinToString(if (delimiter == '\t') "\t" else ",")
        val lineNumber = index + 1 + if (records.firstOrNull()?.isLedgerImportHeader() == true) 1 else 0
        val dateText = fields[0].trim()
        val typeText = fields[1].trim()
        val amountText = fields[2].trim()
        val categoryName = fields[3].trim()
        val accountName = fields[4].trim()
        val toAccountName = fields[5].trim()
        val remark = fields.drop(6).joinToString(",").trim()
        val type = parseLedgerImportType(typeText)
        val amountCents = parseLedgerImportAmount(amountText)
        val occurredAtMillis = parseLedgerImportDate(dateText, zoneId)
        val account = accountLookup[normalizeImportKey(accountName)]
        val toAccount = if (toAccountName.isBlank()) {
            null
        } else {
            accountLookup[normalizeImportKey(toAccountName)]
        }
        val categoryType = type?.toCategoryType()
        val category = if (categoryType == null || categoryName.isBlank()) {
            null
        } else {
            categoryLookup[normalizeImportKey(categoryName)]
                .orEmpty()
                .firstOrNull { it.type == categoryType }
        }
        val error = when {
            dateText.isBlank() -> "缺少日期"
            occurredAtMillis == null -> "日期格式需为 2026-06-02 或 2026-06-02 12:30"
            typeText.isBlank() -> "缺少类型"
            type == null -> "类型需为支出、收入或转账"
            amountText.isBlank() -> "缺少金额"
            amountCents == null || amountCents <= 0L -> "金额需大于 0"
            accountName.isBlank() -> "缺少账户"
            account == null -> "未找到账户：$accountName"
            type != LedgerTransactionType.TRANSFER && categoryName.isBlank() -> "缺少分类"
            type != LedgerTransactionType.TRANSFER && category == null -> "未找到${if (type == LedgerTransactionType.INCOME) "收入" else "支出"}分类：$categoryName"
            type == LedgerTransactionType.TRANSFER && toAccountName.isBlank() -> "缺少转入账户"
            type == LedgerTransactionType.TRANSFER && toAccount == null -> "未找到转入账户：$toAccountName"
            type == LedgerTransactionType.TRANSFER && account?.id == toAccount?.id -> "转出和转入账户不能相同"
            else -> null
        }
        val draft = if (error == null && type != null && amountCents != null && occurredAtMillis != null && account != null) {
            LedgerTransactionDraft(
                bookId = bookId,
                categoryId = category?.id,
                accountId = account.id,
                toAccountId = toAccount?.id,
                amountCents = amountCents,
                type = type,
                occurredAtMillis = occurredAtMillis,
                remark = remark,
            )
        } else {
            null
        }
        LedgerImportPreviewRow(
            lineNumber = lineNumber,
            rawText = rawText,
            occurredAtMillis = occurredAtMillis,
            type = type,
            amountCents = amountCents,
            categoryName = categoryName,
            accountName = accountName,
            toAccountName = toAccountName,
            remark = remark,
            draft = draft,
            error = error,
        )
    }
    return LedgerImportPreview(rows)
}

fun exportLedgerTransactionsCsv(
    transactions: List<LedgerTransaction>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val rows = transactions
        .sortedByDescending { it.occurredAtMillis }
        .map { transaction ->
            listOf(
                formatLedgerExportDate(transaction.occurredAtMillis, zoneId),
                transaction.type.toLedgerExportLabel(),
                formatLedgerPlainAmount(transaction.amountCents),
                if (transaction.type == LedgerTransactionType.TRANSFER) "" else transaction.category?.name.orEmpty(),
                transaction.account?.name.orEmpty(),
                transaction.toAccount?.name.orEmpty(),
                transaction.remark,
            ).joinToString(",") { it.csvEscaped() }
        }
    return buildString {
        appendLine(LedgerImportExportHeader)
        append(rows.joinToString("\n"))
    }.trimEnd()
}

fun ledgerStatsReportText(uiState: LedgerUiState): String {
    val previousExpense = ledgerComparablePreviousExpenseCents(uiState)
    val previousLabel = ledgerStatsPreviousShortLabel(uiState)
    return buildString {
        appendLine("${uiState.bookName} · ${ledgerStatsCurrentLabel(uiState)}")
        appendLine("收入：${formatAmountValue(uiState.stats.incomeCents)}")
        appendLine("支出：${formatAmountValue(uiState.stats.expenseCents)}")
        appendLine("结余：${formatAmountValue(uiState.stats.balanceCents, signed = true)}")
        if (previousExpense != null && previousLabel != null) {
            val delta = uiState.stats.expenseCents - previousExpense
            val direction = if (delta <= 0) "减少" else "增加"
            appendLine("较$previousLabel 支出$direction ${formatAmountValue(delta.absoluteValue)}")
        }
        val topCategories = uiState.stats.categoryStats.take(5)
        if (topCategories.isNotEmpty()) {
            appendLine()
            appendLine("分类支出")
            topCategories.forEachIndexed { index, stat ->
                appendLine("${index + 1}. ${stat.category?.name ?: "未分类"} ${formatAmountValue(stat.amountCents)} (${stat.count}笔)")
            }
        }
        val dailyStats = uiState.stats.dailyStats.sortedByDescending { it.dayStartMillis }.take(8)
        if (dailyStats.isNotEmpty()) {
            appendLine()
            appendLine("日报")
            dailyStats.forEach { stat ->
                val balance = stat.incomeCents - stat.expenseCents
                appendLine(
                    "${formatLedgerReportDate(stat.dayStartMillis)} 收 ${formatAmountValue(stat.incomeCents)} 支 ${formatAmountValue(stat.expenseCents)} 结余 ${formatAmountValue(balance, signed = true)}",
                )
            }
        }
    }.trimEnd()
}

fun ledgerStatsCurrentLabel(uiState: LedgerUiState): String = when (uiState.selectedStatsMode) {
    LedgerStatsMode.WEEK -> {
        val start = uiState.selectedDate.minusDays((uiState.selectedDate.dayOfWeek.value - 1).toLong())
        val end = start.plusDays(6)
        "${start.monthValue}/${start.dayOfMonth}-${end.monthValue}/${end.dayOfMonth}"
    }
    LedgerStatsMode.MONTH -> formatYearMonth(uiState.selectedMonth)
    LedgerStatsMode.YEAR -> uiState.selectedDate.year.toString()
    LedgerStatsMode.TOTAL -> "全部时间"
    LedgerStatsMode.CUSTOM -> {
        val start = uiState.customStatsStartDate
        val end = uiState.customStatsEndDate
        "%02d/%02d-%02d/%02d".format(start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth)
    }
}

fun ledgerStatsCurrentShortLabel(uiState: LedgerUiState): String = when (uiState.selectedStatsMode) {
    LedgerStatsMode.WEEK -> "本周"
    LedgerStatsMode.MONTH -> "本月"
    LedgerStatsMode.YEAR -> "本年"
    LedgerStatsMode.TOTAL -> "全部"
    LedgerStatsMode.CUSTOM -> "当前区间"
}

fun ledgerStatsPreviousShortLabel(uiState: LedgerUiState): String? = when (uiState.selectedStatsMode) {
    LedgerStatsMode.WEEK -> "上周"
    LedgerStatsMode.MONTH -> "上月"
    LedgerStatsMode.YEAR -> "上年"
    LedgerStatsMode.TOTAL -> null
    LedgerStatsMode.CUSTOM -> "上一等长区间"
}

fun ledgerComparablePreviousExpenseCents(
    uiState: LedgerUiState,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? {
    val range = previousComparableRange(uiState, zoneId) ?: return null
    return uiState.allTransactions
        .asSequence()
        .filter { it.type == LedgerTransactionType.EXPENSE }
        .filter { it.occurredAtMillis >= range.startMillis && it.occurredAtMillis < range.endMillis }
        .sumOf { it.amountCents }
}

private fun previousComparableRange(uiState: LedgerUiState, zoneId: ZoneId): LedgerDateRange? = when (uiState.selectedStatsMode) {
    LedgerStatsMode.WEEK -> {
        val start = uiState.selectedDate.with(java.time.DayOfWeek.MONDAY).minusWeeks(1)
        LedgerDateUtils.dateRange(start, start.plusDays(6), zoneId)
    }
    LedgerStatsMode.MONTH -> LedgerDateUtils.monthRange(uiState.selectedMonth.minusMonths(1), zoneId)
    LedgerStatsMode.YEAR -> {
        val start = LocalDate.of(uiState.selectedDate.year - 1, 1, 1)
        LedgerDateRange(
            startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = start.plusYears(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }
    LedgerStatsMode.CUSTOM -> {
        val start = uiState.customStatsStartDate
        val end = uiState.customStatsEndDate
        val days = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L) + 1L
        val previousEnd = start.minusDays(1)
        LedgerDateUtils.dateRange(previousEnd.minusDays(days - 1L), previousEnd, zoneId)
    }
    LedgerStatsMode.TOTAL -> null
}

private fun parseLedgerImportDate(text: String, zoneId: ZoneId): Long? {
    val normalized = text.trim().replace('/', '-')
    if (normalized.isBlank()) return null
    val dateTimePatterns = listOf(
        "yyyy-M-d H:m:s",
        "yyyy-M-d H:m",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
    )
    dateTimePatterns.forEach { pattern ->
        val parsed = runCatching {
            LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern))
        }.getOrNull()
        if (parsed != null) return parsed.atZone(zoneId).toInstant().toEpochMilli()
    }
    return try {
        LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d"))
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun parseLedgerImportType(text: String): LedgerTransactionType? = when (normalizeImportKey(text)) {
    "支出", "支", "消费", "expense", "out", "pay" -> LedgerTransactionType.EXPENSE
    "收入", "收", "income", "in", "receive" -> LedgerTransactionType.INCOME
    "转账", "转", "transfer", "move" -> LedgerTransactionType.TRANSFER
    else -> null
}

private fun parseLedgerImportAmount(text: String): Long? {
    val cleaned = text
        .trim()
        .replace("¥", "")
        .replace("￥", "")
        .replace("元", "")
        .replace(",", "")
        .replace(" ", "")
    return cleaned.toCentsOrNull()?.absoluteValue
}

private fun detectLedgerImportDelimiter(text: String): Char {
    val firstContentLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    return if (firstContentLine.count { it == '\t' } > firstContentLine.count { it == ',' }) '\t' else ','
}

private fun parseLedgerRecords(text: String, delimiter: Char): List<List<String>> {
    val records = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0
    val input = text.removePrefix("\uFEFF")
    while (index < input.length) {
        val char = input[index]
        when {
            inQuotes && char == '"' && index + 1 < input.length && input[index + 1] == '"' -> {
                field.append('"')
                index++
            }
            char == '"' -> inQuotes = !inQuotes
            !inQuotes && char == delimiter -> {
                row += field.toString()
                field.clear()
            }
            !inQuotes && char == '\n' -> {
                row += field.toString()
                field.clear()
                records += row.toList()
                row.clear()
            }
            !inQuotes && char == '\r' -> Unit
            else -> field.append(char)
        }
        index++
    }
    row += field.toString()
    if (row.any { it.isNotBlank() }) {
        records += row.toList()
    }
    return records
}

private fun List<String>.isLedgerImportHeader(): Boolean {
    val normalized = map(::normalizeImportKey)
    return normalized.any { it == "日期" || it == "date" } &&
        normalized.any { it == "类型" || it == "type" } &&
        normalized.any { it == "金额" || it == "amount" }
}

private fun normalizeImportKey(text: String): String = text.trim().lowercase()

private fun LedgerTransactionType.toCategoryType(): LedgerCategoryType? = when (this) {
    LedgerTransactionType.EXPENSE -> LedgerCategoryType.EXPENSE
    LedgerTransactionType.INCOME -> LedgerCategoryType.INCOME
    LedgerTransactionType.TRANSFER -> null
}

private fun LedgerTransactionType.toLedgerExportLabel(): String = when (this) {
    LedgerTransactionType.EXPENSE -> "支出"
    LedgerTransactionType.INCOME -> "收入"
    LedgerTransactionType.TRANSFER -> "转账"
}

private fun formatLedgerExportDate(millis: Long, zoneId: ZoneId): String {
    return Instant.ofEpochMilli(millis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun formatLedgerReportDate(dayStartMillis: Long): String {
    val date = Instant.ofEpochMilli(dayStartMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "%02d/%02d".format(date.monthValue, date.dayOfMonth)
}

private fun formatLedgerPlainAmount(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = cents.absoluteValue
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

private fun String.csvEscaped(): String {
    val shouldQuote = any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == '\t' }
    if (!shouldQuote) return this
    return "\"" + replace("\"", "\"\"") + "\""
}

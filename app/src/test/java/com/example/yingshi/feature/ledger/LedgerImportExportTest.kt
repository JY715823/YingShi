package com.example.yingshi.feature.ledger

import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountType
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerPeriodStats
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class LedgerImportExportTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val food = category("cat-food", "餐饮", LedgerCategoryType.EXPENSE)
    private val salary = category("cat-salary", "工资", LedgerCategoryType.INCOME)
    private val wechat = account("account-wechat", "微信")
    private val icbc = account("account-icbc", "工商卡")

    @Test
    fun parsesExpenseIncomeAndTransferRows() {
        val preview = buildLedgerImportPreview(
            text = """
                日期,类型,金额,分类,账户,转入账户,备注
                2026-06-02,支出,18.50,餐饮,微信,,午餐
                2026-06-03 09:30,收入,5200,工资,工商卡,,工资
                2026-06-04,转账,300,,微信,工商卡,转入储蓄
            """.trimIndent(),
            bookId = "book",
            categories = listOf(food, salary),
            accounts = listOf(wechat, icbc),
            zoneId = zone,
        )

        assertEquals(3, preview.validCount)
        assertEquals(0, preview.invalidCount)
        assertEquals(LedgerTransactionType.EXPENSE, preview.rows[0].draft?.type)
        assertEquals(food.id, preview.rows[0].draft?.categoryId)
        assertEquals(salary.id, preview.rows[1].draft?.categoryId)
        assertEquals(icbc.id, preview.rows[2].draft?.toAccountId)
    }

    @Test
    fun marksInvalidRowsWithReadableErrors() {
        val preview = buildLedgerImportPreview(
            text = "2026-06-02,支出,0,不存在,微信,,午餐\nbad-date,收入,100,工资,工商卡,,",
            bookId = "book",
            categories = listOf(food, salary),
            accounts = listOf(wechat, icbc),
            zoneId = zone,
        )

        assertEquals(0, preview.validCount)
        assertEquals(2, preview.invalidCount)
        assertTrue(preview.rows[0].error.orEmpty().contains("金额"))
        assertTrue(preview.rows[1].error.orEmpty().contains("日期"))
    }

    @Test
    fun exportsCsvWithEscapedFields() {
        val csv = exportLedgerTransactionsCsv(
            transactions = listOf(
                transaction(
                    id = "t1",
                    type = LedgerTransactionType.EXPENSE,
                    amountCents = 1850,
                    occurredAtMillis = atMillis(2026, 6, 2, 12, 30),
                    category = food.copy(name = "餐饮,外卖"),
                    account = wechat,
                    remark = "午餐 \"双拼\"\n好吃",
                ),
            ),
            zoneId = zone,
        )

        assertTrue(csv.startsWith(LedgerImportExportHeader))
        assertTrue(csv.contains("\"餐饮,外卖\""))
        assertTrue(csv.contains("\"午餐 \"\"双拼\"\"\n好吃\""))
    }

    @Test
    fun comparablePreviousExpenseUsesRealTransactions() {
        val uiState = LedgerUiState(
            selectedMonth = YearMonth.of(2026, 6),
            selectedStatsMode = LedgerStatsMode.MONTH,
            allTransactions = listOf(
                transaction("previous", LedgerTransactionType.EXPENSE, 1234, atMillis(2026, 5, 10, 8, 0)),
                transaction("current", LedgerTransactionType.EXPENSE, 5000, atMillis(2026, 6, 10, 8, 0)),
            ),
            stats = LedgerPeriodStats(
                expenseCents = 5000,
                incomeCents = 0,
                balanceCents = -5000,
                transactions = emptyList(),
                categoryStats = emptyList(),
                dailyStats = emptyList(),
            ),
        )

        assertEquals(1234L, ledgerComparablePreviousExpenseCents(uiState, zone))
    }

    private fun category(id: String, name: String, type: LedgerCategoryType): LedgerCategory {
        return LedgerCategory(
            id = id,
            bookId = "book",
            name = name,
            iconKey = "more_horiz",
            color = 0xFF26313A,
            type = type,
        )
    }

    private fun account(id: String, name: String): LedgerAccount {
        return LedgerAccount(
            id = id,
            name = name,
            type = LedgerAccountType.OTHER,
            iconKey = "wallet",
            color = 0xFF26313A,
            initialBalanceCents = 0,
            balanceCents = 0,
            includeInTotal = true,
            hidden = false,
            note = "",
        )
    }

    private fun transaction(
        id: String,
        type: LedgerTransactionType,
        amountCents: Long,
        occurredAtMillis: Long,
        category: LedgerCategory? = food,
        account: LedgerAccount? = wechat,
        toAccount: LedgerAccount? = null,
        remark: String = "",
    ): LedgerTransaction {
        return LedgerTransaction(
            id = id,
            bookId = "book",
            accountId = account?.id.orEmpty(),
            toAccountId = toAccount?.id,
            category = category,
            account = account,
            toAccount = toAccount,
            amountCents = amountCents,
            type = type,
            occurredAtMillis = occurredAtMillis,
            remark = remark,
            method = "manual",
        )
    }

    private fun atMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}

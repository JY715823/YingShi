package com.example.yingshi.feature.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yingshi.feature.ledger.data.LedgerDatabase
import com.example.yingshi.feature.ledger.data.LedgerBookDraft
import com.example.yingshi.feature.ledger.data.LedgerBookTemplateTravel
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerRecurringFrequency
import com.example.yingshi.feature.ledger.data.LedgerRecurringRuleDraft
import com.example.yingshi.feature.ledger.data.LedgerRepository
import com.example.yingshi.feature.ledger.data.LedgerSeedData
import com.example.yingshi.feature.ledger.data.LedgerTransferAccountSide
import com.example.yingshi.feature.ledger.data.LedgerTransactionDraft
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryInstrumentedTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LedgerDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LedgerRepository(database.ledgerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transferMovesBalanceWithoutChangingIncomeExpenseStats() = runBlocking {
        repository.ensureSeedData(nowMillis = 1_000L)
        repository.saveTransaction(
            LedgerTransactionDraft(
                bookId = LedgerSeedData.DefaultBookId,
                categoryId = "cat-salary",
                accountId = LedgerSeedData.DefaultCashAccountId,
                amountCents = 10_000,
                type = LedgerTransactionType.INCOME,
                occurredAtMillis = 2_000L,
                remark = "工资",
            ),
        )
        repository.saveTransaction(
            LedgerTransactionDraft(
                bookId = LedgerSeedData.DefaultBookId,
                categoryId = null,
                accountId = LedgerSeedData.DefaultCashAccountId,
                toAccountId = "account-wechat",
                amountCents = 3_000,
                type = LedgerTransactionType.TRANSFER,
                occurredAtMillis = 3_000L,
                remark = "转账",
            ),
        )

        val accounts = repository.observeAccounts(LedgerSeedData.DefaultBookId).first()
        val cash = accounts.first { it.id == LedgerSeedData.DefaultCashAccountId }
        val wechat = accounts.first { it.id == "account-wechat" }
        val stats = repository.observePeriodStats(
            bookId = LedgerSeedData.DefaultBookId,
            startMillis = 0L,
            endMillis = 10_000L,
        ).first()

        assertEquals(7_000, cash.balanceCents)
        assertEquals(3_000, wechat.balanceCents)
        assertEquals(10_000, stats.incomeCents)
        assertEquals(0, stats.expenseCents)
    }

    @Test
    fun saveBookSeedsTemplateCategoriesAndAccounts() = runBlocking {
        repository.ensureSeedData(nowMillis = 1_000L)

        val bookId = repository.saveBook(
            LedgerBookDraft(
                name = "厦门旅行",
                template = LedgerBookTemplateTravel,
                coverColor = LedgerSeedData.defaultCoverColor(LedgerBookTemplateTravel),
            ),
        )

        val books = repository.observeBooks().first()
        val accounts = repository.observeAccounts(bookId).first()
        val categories = repository.observeAllCategories(bookId).first()

        assertTrue(books.any { it.id == bookId && it.name == "厦门旅行" })
        assertEquals(2, accounts.size)
        assertTrue(accounts.all { it.initialBalanceCents == 0L && it.bookId == bookId })
        assertTrue(categories.any { it.name == "车票" })
        assertTrue(categories.any { it.name == "退款" })
    }

    @Test
    fun archiveBookMovesItOutOfVisibleBooksAndBackAgain() = runBlocking {
        repository.ensureSeedData(nowMillis = 1_000L)

        repository.setBookArchived(LedgerSeedData.TravelBookId, true)

        val activeBooksAfterArchive = repository.observeBooks().first()
        val archivedBooksAfterArchive = repository.observeArchivedBooks().first()

        assertTrue(activeBooksAfterArchive.none { it.id == LedgerSeedData.TravelBookId })
        assertTrue(archivedBooksAfterArchive.any { it.id == LedgerSeedData.TravelBookId })

        repository.setBookArchived(LedgerSeedData.TravelBookId, false)

        val activeBooksAfterRestore = repository.observeBooks().first()
        assertTrue(activeBooksAfterRestore.any { it.id == LedgerSeedData.TravelBookId })
    }

    @Test
    fun reorderCategoriesAndAccountsPersistsOrder() = runBlocking {
        repository.ensureSeedData(nowMillis = 1_000L)

        val originalExpenseCategories = repository.observeAllCategories(LedgerSeedData.DefaultBookId).first()
            .filter { it.type == LedgerCategoryType.EXPENSE }
        val reversedCategoryIds = originalExpenseCategories.take(4).map { it.id }.reversed()
        repository.reorderCategories(
            bookId = LedgerSeedData.DefaultBookId,
            type = LedgerCategoryType.EXPENSE,
            orderedIds = reversedCategoryIds + originalExpenseCategories.drop(4).map { it.id },
        )
        val reorderedExpenseCategories = repository.observeAllCategories(LedgerSeedData.DefaultBookId).first()
            .filter { it.type == LedgerCategoryType.EXPENSE }
        assertEquals(reversedCategoryIds, reorderedExpenseCategories.take(4).map { it.id })

        val originalAccounts = repository.observeAllAccounts(LedgerSeedData.DefaultBookId).first()
        val reversedAccountIds = originalAccounts.take(3).map { it.id }.reversed()
        repository.reorderAccounts(
            bookId = LedgerSeedData.DefaultBookId,
            orderedIds = reversedAccountIds + originalAccounts.drop(3).map { it.id },
        )
        val reorderedAccounts = repository.observeAllAccounts(LedgerSeedData.DefaultBookId).first()
        assertEquals(reversedAccountIds, reorderedAccounts.take(3).map { it.id })
    }

    @Test
    fun batchUpdateTransferAccountMovesBalancesForSelectedSide() = runBlocking {
        repository.ensureSeedData(nowMillis = 1_000L)

        repository.saveTransaction(
            LedgerTransactionDraft(
                id = "transfer-test",
                bookId = LedgerSeedData.DefaultBookId,
                categoryId = null,
                accountId = LedgerSeedData.DefaultCashAccountId,
                toAccountId = "account-wechat",
                amountCents = 1_000,
                type = LedgerTransactionType.TRANSFER,
                occurredAtMillis = 2_000L,
                remark = "转账",
            ),
        )

        repository.batchUpdateTransferAccount(
            transactionIds = listOf("transfer-test"),
            side = LedgerTransferAccountSide.TO,
            accountId = "account-alipay",
        )

        val accounts = repository.observeAccounts(LedgerSeedData.DefaultBookId).first()
        assertEquals(-1_000, accounts.first { it.id == LedgerSeedData.DefaultCashAccountId }.balanceCents)
        assertEquals(0, accounts.first { it.id == "account-wechat" }.balanceCents)
        assertEquals(1_000, accounts.first { it.id == "account-alipay" }.balanceCents)
    }

    @Test
    fun recurringRulesMaterializeOnceAndRespectPauseResumeDelete() = runBlocking {
        repository.ensureSeedData(nowMillis = 1_000L)

        val startAtMillis = millis(2026, 5, 18, 9, 30)
        val nowMillis = millis(2026, 6, 1, 0, 0)
        val ruleId = repository.saveRecurringRule(
            LedgerRecurringRuleDraft(
                bookId = LedgerSeedData.DefaultBookId,
                type = LedgerTransactionType.EXPENSE,
                categoryId = "cat-food",
                accountId = LedgerSeedData.DefaultCashAccountId,
                amountCents = 1_234,
                remark = "周期晚餐",
                frequency = LedgerRecurringFrequency.MONTHLY,
                startAtMillis = startAtMillis,
                enabled = false,
            ),
        )

        repository.materializeDueRecurringTransactions(nowMillis = nowMillis)
        assertEquals(
            0,
            repository.observeTransactions(LedgerSeedData.DefaultBookId).first()
                .count { it.method == "recurring" && it.remark == "周期晚餐" },
        )

        repository.setRecurringRuleEnabled(ruleId, true)
        repository.materializeDueRecurringTransactions(nowMillis = nowMillis)
        repository.materializeDueRecurringTransactions(nowMillis = nowMillis)

        val recurringTransactions = repository.observeTransactions(LedgerSeedData.DefaultBookId).first()
            .filter { it.method == "recurring" && it.remark == "周期晚餐" }
        assertEquals(1, recurringTransactions.size)

        repository.deleteRecurringRule(ruleId)
        repository.materializeDueRecurringTransactions(nowMillis = millis(2026, 7, 1, 0, 0))

        val recurringAfterDelete = repository.observeTransactions(LedgerSeedData.DefaultBookId).first()
            .filter { it.method == "recurring" && it.remark == "周期晚餐" }
        assertEquals(1, recurringAfterDelete.size)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}

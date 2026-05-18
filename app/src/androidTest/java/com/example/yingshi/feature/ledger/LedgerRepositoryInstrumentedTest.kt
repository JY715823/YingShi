package com.example.yingshi.feature.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yingshi.feature.ledger.data.LedgerDatabase
import com.example.yingshi.feature.ledger.data.LedgerRepository
import com.example.yingshi.feature.ledger.data.LedgerSeedData
import com.example.yingshi.feature.ledger.data.LedgerTransactionDraft
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}

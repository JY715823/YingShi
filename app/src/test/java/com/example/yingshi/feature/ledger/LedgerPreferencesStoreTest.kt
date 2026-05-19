package com.example.yingshi.feature.ledger

import com.example.yingshi.feature.ledger.data.LedgerPreferencesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerPreferencesStoreTest {
    @Test
    fun resolveDefaultBookFallsBackToFirstVisibleBook() {
        assertEquals(
            "book-b",
            LedgerPreferencesStore.resolveDefaultBookId(
                storedBookId = "missing-book",
                visibleBookIds = listOf("book-b", "book-c"),
            ),
        )
    }

    @Test
    fun resolveDefaultAccountFallsBackToFirstVisibleAccount() {
        assertEquals(
            "account-1",
            LedgerPreferencesStore.resolveDefaultAccountId(
                storedAccountId = null,
                visibleAccountIds = listOf("account-1", "account-2"),
            ),
        )
    }

    @Test
    fun resolveDefaultBookReturnsNullWhenNoVisibleBooks() {
        assertNull(
            LedgerPreferencesStore.resolveDefaultBookId(
                storedBookId = "book-a",
                visibleBookIds = emptyList(),
            ),
        )
    }
}

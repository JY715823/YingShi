package com.example.yingshi.feature.ledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerNavigationRequestTest {
    @Test
    fun firstWidgetOpenAddRequestIsHandled() {
        assertTrue(shouldOpenLedgerAdd(openAddNonce = 1, lastOpenAddNonce = 0))
    }

    @Test
    fun repeatedWidgetOpenAddRequestIsIgnored() {
        assertFalse(shouldOpenLedgerAdd(openAddNonce = 3, lastOpenAddNonce = 3))
    }

    @Test
    fun emptyWidgetOpenAddRequestIsIgnored() {
        assertFalse(shouldOpenLedgerAdd(openAddNonce = 0, lastOpenAddNonce = 0))
    }
}

package com.example.yingshi.feature.ledger

import com.example.yingshi.feature.ledger.data.LedgerBudgetPeriod
import com.example.yingshi.feature.ledger.data.LedgerDateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LedgerCalculatorTest {
    @Test
    fun evaluatesOperatorPrecedence() {
        assertEquals("10", LedgerCalculator.evaluate("2+3×4-4"))
    }

    @Test
    fun rejectsDivideByZero() {
        assertNull(LedgerCalculator.evaluate("8÷0"))
    }

    @Test
    fun formatsAndParsesCents() {
        assertEquals(129225L, "1292.25".toCentsOrNull())
        assertEquals("¥1,292.25", 129225L.formatMoney("¥"))
        assertEquals("-¥10.00", (-1000L).formatMoney("¥", signed = true))
    }

    @Test
    fun monthAndQuarterRangesStartAtExpectedDates() {
        val zone = ZoneId.of("Asia/Shanghai")
        val quarter = LedgerDateUtils.periodRange(
            date = LocalDate.of(2026, 5, 17),
            period = LedgerBudgetPeriod.QUARTER,
            zoneId = zone,
        )
        assertEquals(
            LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            quarter.startMillis,
        )
        assertEquals(
            LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            quarter.endMillis,
        )
    }
}

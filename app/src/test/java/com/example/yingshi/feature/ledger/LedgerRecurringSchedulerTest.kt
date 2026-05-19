package com.example.yingshi.feature.ledger

import com.example.yingshi.feature.ledger.data.LedgerRecurringFrequency
import com.example.yingshi.feature.ledger.data.LedgerRecurringScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LedgerRecurringSchedulerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun monthlyOccurrenceFallsBackToLastDayOfMonth() {
        val start = millis(2026, 1, 31, 9, 30)
        val reference = millis(2026, 2, 1, 0, 0)

        val next = LedgerRecurringScheduler.firstOccurrenceAtOrAfter(
            referenceMillis = reference,
            startAtMillis = start,
            frequency = LedgerRecurringFrequency.MONTHLY,
            zoneId = zone,
        )

        assertEquals(millis(2026, 2, 28, 9, 30), next)
    }

    @Test
    fun yearlyOccurrenceFallsBackFromLeapDay() {
        val start = millis(2024, 2, 29, 8, 15)
        val reference = millis(2025, 1, 1, 0, 0)

        val next = LedgerRecurringScheduler.firstOccurrenceAtOrAfter(
            referenceMillis = reference,
            startAtMillis = start,
            frequency = LedgerRecurringFrequency.YEARLY,
            zoneId = zone,
        )

        assertEquals(millis(2025, 2, 28, 8, 15), next)
    }

    @Test
    fun nextOccurrenceAfterAdvancesToTheFollowingMonth() {
        val start = millis(2026, 3, 31, 12, 0)
        val occurrence = millis(2026, 4, 30, 12, 0)

        val next = LedgerRecurringScheduler.nextOccurrenceAfter(
            occurrenceMillis = occurrence,
            startAtMillis = start,
            frequency = LedgerRecurringFrequency.MONTHLY,
            zoneId = zone,
        )

        assertEquals(millis(2026, 5, 31, 12, 0), next)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}

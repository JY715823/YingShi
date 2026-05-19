package com.example.yingshi.feature.ledger.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object LedgerRecurringScheduler {
    fun firstOccurrenceAtOrAfter(
        referenceMillis: Long,
        startAtMillis: Long,
        frequency: LedgerRecurringFrequency,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val start = localDateTime(startAtMillis, zoneId)
        val reference = localDateTime(referenceMillis, zoneId)
        if (!reference.isAfter(start)) return startAtMillis
        return when (frequency) {
            LedgerRecurringFrequency.DAILY -> firstDailyOccurrence(reference, start, zoneId)
            LedgerRecurringFrequency.WEEKLY -> firstWeeklyOccurrence(reference, start, zoneId)
            LedgerRecurringFrequency.MONTHLY -> firstMonthlyOccurrence(reference, start, zoneId)
            LedgerRecurringFrequency.YEARLY -> firstYearlyOccurrence(reference, start, zoneId)
        }
    }

    fun nextOccurrenceAfter(
        occurrenceMillis: Long,
        startAtMillis: Long,
        frequency: LedgerRecurringFrequency,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return firstOccurrenceAtOrAfter(occurrenceMillis + 1, startAtMillis, frequency, zoneId)
    }

    private fun firstDailyOccurrence(
        reference: LocalDateTime,
        start: LocalDateTime,
        zoneId: ZoneId,
    ): Long {
        val days = ChronoUnit.DAYS.between(start.toLocalDate(), reference.toLocalDate()).coerceAtLeast(0)
        val candidate = start.plusDays(days)
        return if (candidate.isBefore(reference)) {
            candidate.plusDays(1).toMillis(zoneId)
        } else {
            candidate.toMillis(zoneId)
        }
    }

    private fun firstWeeklyOccurrence(
        reference: LocalDateTime,
        start: LocalDateTime,
        zoneId: ZoneId,
    ): Long {
        val weeks = ChronoUnit.WEEKS.between(start.toLocalDate(), reference.toLocalDate()).coerceAtLeast(0)
        val candidate = start.plusWeeks(weeks)
        return if (candidate.isBefore(reference)) {
            candidate.plusWeeks(1).toMillis(zoneId)
        } else {
            candidate.toMillis(zoneId)
        }
    }

    private fun firstMonthlyOccurrence(
        reference: LocalDateTime,
        start: LocalDateTime,
        zoneId: ZoneId,
    ): Long {
        val startMonth = start.toYearMonth()
        val referenceMonth = reference.toYearMonth()
        val months = ChronoUnit.MONTHS.between(startMonth, referenceMonth).coerceAtLeast(0)
        val candidate = monthlyOccurrence(start, months.toInt(), zoneId)
        return if (localDateTime(candidate, zoneId).isBefore(reference)) {
            monthlyOccurrence(start, months.toInt() + 1, zoneId)
        } else {
            candidate
        }
    }

    private fun firstYearlyOccurrence(
        reference: LocalDateTime,
        start: LocalDateTime,
        zoneId: ZoneId,
    ): Long {
        val years = ChronoUnit.YEARS.between(start.toLocalDate(), reference.toLocalDate()).coerceAtLeast(0)
        val candidate = yearlyOccurrence(start, years.toInt(), zoneId)
        return if (localDateTime(candidate, zoneId).isBefore(reference)) {
            yearlyOccurrence(start, years.toInt() + 1, zoneId)
        } else {
            candidate
        }
    }

    private fun monthlyOccurrence(start: LocalDateTime, monthsToAdd: Int, zoneId: ZoneId): Long {
        val candidateMonth = start.toYearMonth().plusMonths(monthsToAdd.toLong())
        val day = start.dayOfMonth.coerceAtMost(candidateMonth.lengthOfMonth())
        return candidateMonth.atDay(day)
            .atTime(start.toLocalTime())
            .toMillis(zoneId)
    }

    private fun yearlyOccurrence(start: LocalDateTime, yearsToAdd: Int, zoneId: ZoneId): Long {
        val year = start.year + yearsToAdd
        val month = start.monthValue
        val day = when {
            month == 2 && start.dayOfMonth == 29 -> 29.coerceAtMost(java.time.YearMonth.of(year, 2).lengthOfMonth())
            else -> start.dayOfMonth.coerceAtMost(java.time.YearMonth.of(year, month).lengthOfMonth())
        }
        return LocalDateTime.of(year, month, day, start.hour, start.minute, start.second, start.nano).toMillis(zoneId)
    }

    private fun LocalDateTime.toYearMonth() = java.time.YearMonth.of(year, monthValue)

    private fun LocalDateTime.toMillis(zoneId: ZoneId): Long =
        atZone(zoneId).toInstant().toEpochMilli()

    private fun localDateTime(millis: Long, zoneId: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime()
}

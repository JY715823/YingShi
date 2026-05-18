package com.example.yingshi.feature.ledger.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class LedgerDateRange(
    val startMillis: Long,
    val endMillis: Long,
)

object LedgerDateUtils {
    fun dateRange(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LedgerDateRange {
        val safeEndDate = if (endDateInclusive.isBefore(startDate)) startDate else endDateInclusive
        return LedgerDateRange(
            startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = safeEndDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    fun monthRange(month: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): LedgerDateRange {
        val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return LedgerDateRange(start, end)
    }

    fun periodRange(
        date: LocalDate,
        period: LedgerBudgetPeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LedgerDateRange {
        val startDate = when (period) {
            LedgerBudgetPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            LedgerBudgetPeriod.MONTH -> date.withDayOfMonth(1)
            LedgerBudgetPeriod.QUARTER -> {
                val quarterStartMonth = ((date.monthValue - 1) / 3) * 3 + 1
                LocalDate.of(date.year, quarterStartMonth, 1)
            }
            LedgerBudgetPeriod.YEAR -> LocalDate.of(date.year, 1, 1)
        }
        val endDate = when (period) {
            LedgerBudgetPeriod.WEEK -> startDate.plusWeeks(1)
            LedgerBudgetPeriod.MONTH -> startDate.plusMonths(1)
            LedgerBudgetPeriod.QUARTER -> startDate.plusMonths(3)
            LedgerBudgetPeriod.YEAR -> startDate.plusYears(1)
        }
        return LedgerDateRange(
            startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    fun dayStartMillis(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return Instant.ofEpochMilli(millis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun toLocalDateTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime()
    }
}

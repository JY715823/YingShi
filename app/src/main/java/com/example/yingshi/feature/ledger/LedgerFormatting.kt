package com.example.yingshi.feature.ledger

import com.example.yingshi.feature.ledger.data.LedgerDateUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val AmountFormat = DecimalFormat("#,##0.00")
private val LedgerMonthFormatter = DateTimeFormatter.ofPattern("yyyy/MM")
private val LedgerDayFormatter = DateTimeFormatter.ofPattern("MM/dd E")
private val LedgerDateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
private val LedgerMonthLabelFormatter = DateTimeFormatter.ofPattern("MM")
private val LedgerYearLabelFormatter = DateTimeFormatter.ofPattern("yyyy")

fun Long.formatMoney(symbol: String = "¥", signed: Boolean = false): String {
    val sign = when {
        !signed -> ""
        this > 0 -> "+"
        this < 0 -> "-"
        else -> ""
    }
    val amount = BigDecimal(this.absoluteValue).divide(BigDecimal(100), 2, RoundingMode.DOWN)
    return "$sign$symbol${AmountFormat.format(amount)}"
}

fun String.toCentsOrNull(): Long? {
    val clean = trim()
    if (clean.isBlank()) return null
    return runCatching {
        BigDecimal(clean)
            .setScale(2, RoundingMode.DOWN)
            .multiply(BigDecimal(100))
            .longValueExact()
    }.getOrNull()
}

fun formatMonth(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(LedgerMonthFormatter)
}

fun formatAmountValue(cents: Long, signed: Boolean = false): String {
    val sign = when {
        !signed -> ""
        cents > 0 -> "+"
        cents < 0 -> "-"
        else -> ""
    }
    val amount = BigDecimal(cents.absoluteValue).divide(BigDecimal(100), 2, RoundingMode.DOWN)
    return "$sign${AmountFormat.format(amount)}"
}

fun formatYearMonth(month: YearMonth): String = month.format(LedgerMonthFormatter)

fun formatYearLabel(month: YearMonth): String = month.atDay(1).format(LedgerYearLabelFormatter)

fun formatMonthLabel(month: YearMonth): String = month.atDay(1).format(LedgerMonthLabelFormatter)

fun formatDayHeader(dayStartMillis: Long): String {
    return Instant.ofEpochMilli(dayStartMillis).atZone(ZoneId.systemDefault()).format(LedgerDayFormatter)
}

fun formatDateTime(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(LedgerDateTimeFormatter)
}

fun dayStart(millis: Long): Long = LedgerDateUtils.dayStartMillis(millis)

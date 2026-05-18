package com.example.yingshi.feature.ledger

import java.math.BigDecimal
import java.math.RoundingMode

object LedgerCalculator {
    fun evaluate(expression: String): String? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty()) return null
        val values = ArrayDeque<BigDecimal>()
        val ops = ArrayDeque<Char>()
        fun applyTop(): Boolean {
            if (values.size < 2 || ops.isEmpty()) return false
            val b = values.removeLast()
            val a = values.removeLast()
            val result = when (ops.removeLast()) {
                '+' -> a + b
                '-' -> a - b
                '×', '*' -> a * b
                '÷', '/' -> {
                    if (b.compareTo(BigDecimal.ZERO) == 0) return false
                    a.divide(b, 8, RoundingMode.HALF_UP)
                }
                else -> return false
            }
            values.addLast(result)
            return true
        }
        tokens.forEach { token ->
            when (token) {
                is CalculatorToken.Number -> values.addLast(token.value)
                is CalculatorToken.Operator -> {
                    while (ops.isNotEmpty() && precedence(ops.last()) >= precedence(token.value)) {
                        if (!applyTop()) return null
                    }
                    ops.addLast(token.value)
                }
            }
        }
        while (ops.isNotEmpty()) {
            if (!applyTop()) return null
        }
        val result = values.singleOrNull() ?: return null
        return result.setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun tokenize(expression: String): List<CalculatorToken>? {
        val tokens = mutableListOf<CalculatorToken>()
        val number = StringBuilder()
        fun flushNumber(): Boolean {
            if (number.isEmpty()) return true
            val value = runCatching { BigDecimal(number.toString()) }.getOrNull() ?: return false
            tokens += CalculatorToken.Number(value)
            number.clear()
            return true
        }
        expression.filterNot { it.isWhitespace() }.forEachIndexed { index, char ->
            when {
                char.isDigit() || char == '.' -> number.append(char)
                char in listOf('+', '-', '×', '*', '÷', '/') -> {
                    if (index == 0 && char == '-') {
                        number.append(char)
                    } else {
                        if (!flushNumber()) return null
                        tokens += CalculatorToken.Operator(char)
                    }
                }
                else -> return null
            }
        }
        if (!flushNumber()) return null
        return tokens
    }

    private fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '×', '*', '÷', '/' -> 2
        else -> 0
    }
}

private sealed interface CalculatorToken {
    data class Number(val value: BigDecimal) : CalculatorToken
    data class Operator(val value: Char) : CalculatorToken
}

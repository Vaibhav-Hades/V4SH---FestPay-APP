package com.festpay.app.data

import java.math.BigDecimal

data class BalanceResult(
    val balance: BigDecimal?,
    val rawText: String
)

object BalanceParser {

    fun parseBalance(screenText: String): BalanceResult {
        if (screenText.isBlank()) {
            return BalanceResult(null, screenText)
        }

        val lower = screenText
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        // Guard: Check if screen contains balance keywords
        val balanceKeywords = listOf("balance", "avail bal", "avail.bal", "available", "current bal", "ac bal")
        val hasKeyword = balanceKeywords.any { lower.contains(it) }

        // Guard: Reject PIN screens
        val sensitiveKeywords = listOf("pin", "enter upi", "6 digit", "password")
        if (sensitiveKeywords.any { lower.contains(it) } && !hasKeyword) {
            return BalanceResult(null, screenText)
        }

        if (!hasKeyword) {
            return BalanceResult(null, screenText)
        }

        // Extract currency amount matching pattern: e.g. "rs. 12,345.67", "inr 12345.67", "₹ 1,25,500.50", "12,345.67"
        val currencyRegex = Regex("(?:rs\\.?|inr|₹)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
        val matches = currencyRegex.findAll(lower)

        for (match in matches) {
            val numberGroup = match.groupValues.getOrNull(1)?.replace(",", "") ?: continue

            // Filter out account numbers or mobile numbers (e.g. 10+ digits or year-like integers without decimals or balance context)
            if (numberGroup.length >= 10 && !numberGroup.contains(".")) {
                continue // likely account number or mobile number
            }

            try {
                val parsedDecimal = BigDecimal(numberGroup)
                if (parsedDecimal >= BigDecimal.ZERO) {
                    return BalanceResult(parsedDecimal, screenText)
                }
            } catch (_: Exception) {}
        }

        return BalanceResult(null, screenText)
    }
}

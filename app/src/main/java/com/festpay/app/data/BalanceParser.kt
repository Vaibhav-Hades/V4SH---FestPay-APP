package com.festpay.app.data

import java.math.BigDecimal

data class BalanceResult(val balance: BigDecimal?, val rawText: String)

object BalanceParser {
    // Complete tokens: western grouping, Indian grouping, or ungrouped decimals.
    private const val NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3}|[0-9]+)(?:\\.[0-9]{1,2})?"
    private val amountAfterLabel = Regex(
        "\\b(?:balance|avail\\.?\\s*bal\\.?|current\\s+bal\\.?|ac\\s+bal\\.?)" +
            "\\s*(?:is\\b\\s*)?[:=]?\\s*(?:(?:rs\\.?|inr|₹)\\s*)?" +
            "($NUMBER)(?![0-9.,])", RegexOption.IGNORE_CASE
    )
    private val currencyAmount = Regex("(?:₹|\\brs\\.?|\\binr)\\s*($NUMBER)(?![0-9.,])", RegexOption.IGNORE_CASE)

    fun parseBalance(screenText: String): BalanceResult {
        fun unavailable() = BalanceResult(null, "")
        val text = screenText.replace('\u00A0', ' ').replace('\u202F', ' ')
            .replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return unavailable()
        if (Regex("\\b(pin|otp|password|passcode|cvv|failed|failure|error|unavailable|invalid)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return unavailable()
        if (Regex("\\b(check balance|select option|send money)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return unavailable()
        val matches = amountAfterLabel.findAll(text).toList()
        if (matches.size != 1) return unavailable()
        if (currencyAmount.findAll(text).count() > 1) return unavailable()
        val number = matches.single().groupValues[1].replace(",", "")
        if (number.substringBefore('.').length >= 10) return unavailable()
        val amount = number.toBigDecimalOrNull() ?: return unavailable()
        // Raw carrier text is intentionally not returned or retained.
        return if (amount >= BigDecimal.ZERO) BalanceResult(amount, "") else unavailable()
    }
}

package com.festpay.app.data

/** The payee name the CARRIER itself prints during the payment. Carrier-reported, never bank-verified. */
object CarrierPayee {
    private val patterns = listOf(
        Regex("""\bpayment\s+to\s+([A-Za-z][A-Za-z .'-]{1,58}?)\s*,""", RegexOption.IGNORE_CASE),
        Regex("""(?m)^\s*(?:Carrier info\s+)?([A-Za-z][A-Za-z .'-]{1,58}?)\s+has\s+been\s+successfully\s+added""", RegexOption.IGNORE_CASE)
    )

    fun parse(message: String): String? {
        for (p in patterns) {
            val name = p.find(message)?.groupValues?.get(1)?.trim()?.replace(Regex("""\s+"""), " ") ?: continue
            if (name.length in 2..60 && name.none { it.isDigit() || it == '@' }) return name
        }
        return null
    }
}

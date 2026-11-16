package com.festpay.app.data

/** The carrier's own words, cleaned for display. Never used to decide a payment outcome. */
object CarrierMessage {
    private val filler = Regex("""(?i)please\s+wait|ussd\s+code\s+running""")

    /** Text without the dialog title, one line, at most 300 characters; null for blank or filler frames. */
    fun plain(text: String): String? {
        val cleaned = text.replace("Carrier info", "").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").trim()
        if (cleaned.length < 3) return null
        if (UssdNavigationPolicy.isTransientPlaceholder(cleaned, false) || filler.containsMatchIn(cleaned)) return null
        return cleaned.take(300)
    }
}

package com.festpay.app.data

/** Reads static labels only. The lazy reader must never run for user-input nodes. */
internal object AccessibilityTextPolicy {
    fun readLabel(
        editable: Boolean,
        password: Boolean,
        textInput: Boolean,
        readText: () -> CharSequence?
    ): String? {
        if (editable || password || textInput) return null
        return readText()?.toString()
    }

    fun isSensitivePrompt(text: String, hasEditable: Boolean): Boolean {
        val normalized = text.lowercase().replace(Regex("[\\s\\u00a0\\u202f]+"), " ")
        // A VPA label must not override an authentication warning on the same screen.
        // A root menu can list "7. UPI PIN" without being an authentication prompt.
        val explicit = Regex(
            "\\b(?:enter|invalid|incorrect|wrong|confirm|provide|type|set|change|forgot)" +
                "(?:[\\s/-]+(?:your|the|old|new|upi|[46][ -]?digit|one[ -]time)){0,4}" +
                "[\\s:-]+(?:pin|password|otp|passcode|cvv)\\b" +
                "|\\b(?:pin|password|otp|passcode|cvv)\\s+(?:required|incorrect|invalid)\\b"
        ).containsMatchIn(normalized)
        return explicit || (hasEditable && Regex("\\b[46][ -]?digit\\b").containsMatchIn(normalized))
    }
}

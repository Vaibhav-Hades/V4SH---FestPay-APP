package com.festpay.app.data

object UssdNavigationPolicy {
    /** Static instruction only; a password flag, OTP or generic PIN is insufficient. */
    fun isSupportedUpiAuthenticationPrompt(text: String): Boolean {
        val value = normalized(text)
        // The carrier's own authentication screen: it names the UPI PIN, and is not an OTP/password/PIN-management/error screen.
        // Wording around it varies ("Enter 6 digit UPI PIN", "Do not share your UPI PIN. Enter UPI PIN"), so it is not pinned to one sentence.
        return Regex("\\bupi\\s*pin\\b").containsMatchIn(value) &&
            !Regex("\\b(?:otp|password|passcode|cvv|incorrect|invalid|wrong)\\b").containsMatchIn(value) &&
            !Regex("\\b(?:set|change|reset|forgot|generate)\\b[^.\\n]{0,25}\\bpin\\b").containsMatchIn(value)
    }
    private fun normalized(text: String) = text.lowercase().replace(Regex("\\s+"), " ")
    fun isRootMenu(text: String): Boolean {
        val value = normalized(text)
        // Banks and carriers word the NPCI menu slightly differently ("1:", "Check Bal", "Balance Enquiry").
        return Regex("\\b1\\s*[.):-]?\\s*send\\s*money\\b").containsMatchIn(value) &&
            Regex("\\b3\\s*[.):-]?\\s*(?:check\\s*(?:a/c\\s*|account\\s*)?bal(?:ance)?|bal(?:ance)?\\s*enquiry|account\\s*balance)\\b").containsMatchIn(value)
    }
    fun isAmountPrompt(text: String): Boolean = Regex("\\benter\\s+(?:(?:the|payment)\\s+)?(?:amount\\b|rs\\.?\\b)").containsMatchIn(normalized(text))
    fun isMatchingReview(text: String, recipient: String, amount: String): Boolean {
        val value = normalized(text)
        if (!PaymentInputValidator.isVpa(recipient) || PaymentInputValidator.amount(amount) == null || AccessibilityTextPolicy.isSensitivePrompt(value, true)) return false
        if (!Regex("\\b(?:confirm|review)\\b").containsMatchIn(value) || Regex("\\benter\\s+(?:remarks?|purpose|note|upi|mobile|amount)\\b").containsMatchIn(value)) return false
        if (CarrierResultClassifier.observe(value) == CarrierResultObservation.FAILURE_WORDING) return false
        // Match complete tokens, not an intended-address substring or one of several payees.
        val addresses = addresses(value)
        if (addresses.isEmpty() || addresses.any { !PaymentInputValidator.isVpa(it) || !it.equals(recipient, true) }) return false
        val values = amounts(value)
        if (values.size != 1) return false
        val observed = PaymentInputValidator.amount(values.single()) ?: return false
        return observed.toBigDecimal().compareTo(amount.toBigDecimal()) == 0
    }
    private fun addresses(value: String) = Regex("[^\\s:;,()\\[\\]<>]+@[^\\s:;,()\\[\\]<>]+")
        .findAll(value).map { it.value }.toList()
    private fun amounts(value: String) = Regex("(?:₹|\\brs\\.?|\\binr)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(?![a-z0-9_,]|\\.[0-9.])")
        .findAll(value).map { it.groupValues[1] }.toList()

    /** Payment details shown in a UPI PIN prompt's static instruction (never the input field). */
    enum class PromptDetails { MATCHING, AMOUNT_ONLY, ABSENT, MISMATCH }
    fun authenticationPromptDetails(text: String, recipient: String, amount: String): PromptDetails {
        val value = normalized(text)
        // A sentence-final period is punctuation, not part of the address.
        val addresses = addresses(value).map { it.trimEnd('.') }
        val values = amounts(value)
        if (addresses.any { !PaymentInputValidator.isVpa(it) || !it.equals(recipient, true) } || values.size > 1) return PromptDetails.MISMATCH
        val amountMatches = values.singleOrNull()?.let { observed ->
            val canonical = PaymentInputValidator.amount(observed.replace(",", "")) ?: return PromptDetails.MISMATCH
            canonical.toBigDecimal().compareTo(amount.toBigDecimal()) == 0
        }
        return when {
            amountMatches == false -> PromptDetails.MISMATCH
            amountMatches == true && addresses.isNotEmpty() -> PromptDetails.MATCHING
            amountMatches == true -> PromptDetails.AMOUNT_ONLY
            // A recipient alone, or nothing, does not show that the reviewed amount was accepted.
            else -> PromptDetails.ABSENT
        }
    }

    /** Explicit, unique skip instruction on an optional remark prompt. Never invents remark text. */
    fun remarkSkipDigit(text: String): String? {
        val value = normalized(text)
        if (!Regex("\\b(?:remarks?|note|message|description)\\b").containsMatchIn(value)) return null
        if (Regex("\\b(?:pin|otp|password|passcode|amount|upi\\s*id|confirm)\\b").containsMatchIn(value)) return null
        // Every "N to skip" mention counts, so two different skip digits are ambiguous.
        val instructions = Regex("\\b([0-9])\\s+to\\s+skip\\b").findAll(value).map { it.groupValues[1] }
        val options = Regex("\\b([0-9])\\s*[.)-]\\s*skip\\b").findAll(value).map { it.groupValues[1] }
        return (instructions + options).distinct().toList().singleOrNull()
    }
    /**
     * Short filler frames some carriers show between prompts ("Processing...", "Loading", "Connecting").
     * Matched only for non-editable frames that START with the filler word, never inside longer prompts.
     */
    fun isTransientPlaceholder(text: String, editable: Boolean): Boolean {
        if (editable) return false
        val value = normalized(text).trim()
        return value.length <= 60 && Regex("""^(?:processing|loading|connecting)\b""").containsMatchIn(value)
    }

    private val rejectionWording = listOf(
        """\b(?:invalid|incorrect|wrong)\s+(?:upi|vpa|amount|input|entry|id|selection|option|number|account)\b""",
        """\bnot\s+(?:a\s+)?(?:valid|registered|recogni[sz]ed|allowed|found|supported)\b""",
        """\b(?:transaction|payment)\s+(?:declined|failed|cancell?ed|denied|aborted)\b""",
        """\bunable\s+to\s+process\b""",
        """\binsufficient\s+(?:funds|balance)\b""",
        """\b(?:exceeds?|exceeded)\s+(?:the\s+)?(?:limit|amount)\b|\bover\s+limit\b|\blimit\s+exceeded\b""",
        """\bservice\s+(?:is\s+)?(?:unavailable|not\s+available|down)\b|\btry\s+again\s+later\b|\btemporarily\s+unavailable\b""",
        """\bsession\s+(?:timed\s+out|expired|terminated)\b""",
        """\b(?:could\s+not|couldn't|cannot|can't)\s+find\s+(?:your|the)\s+bank\b|\bno\s+bank\s+(?:linked|found)\b""",
        """\bcannot\s+(?:send|pay)\s+to\s+(?:self|yourself|the\s+same)\b|\bsame\s+(?:account|vpa)\b""",
        """\bplease\s+(?:enter|check)\s+(?:a\s+)?(?:valid|correct)\b""",
        """\bconnection\s+problem\b|\binvalid\s+mmi\b"""
    ).map { Regex(it) }

    /**
     * Carrier wording that rejects the request BEFORE any authentication prompt. Observation only:
     * it explains why assistance stopped; it never establishes a financial outcome.
     */
    fun isCarrierRejection(text: String): Boolean {
        val value = normalized(text)
        return rejectionWording.any { it.containsMatchIn(value) }
    }

    /** The carrier's own "N. Exit" option on its final menu; unique and exact, so "Save contact" is never chosen. */
    fun exitDigit(text: String): String? =
        Regex("""\b([0-9])\s*[.):-]?\s*exit\b""", RegexOption.IGNORE_CASE).findAll(normalized(text)).map { it.groupValues[1] }.distinct().toList().singleOrNull()

    /**
     * A plain information dialog the carrier shows before its menu (for example a notice with only an OK button).
     * Never a rejection, a result, a setup screen, a PIN screen or a transient "processing" frame: those are handled elsewhere.
     */
    fun isInformationalNotice(text: String, editable: Boolean): Boolean {
        if (editable) return false
        val value = normalized(text).trim()
        if (value.isBlank() || value.length > 400) return false
        if (isCarrierRejection(value) || isTransientPlaceholder(value, false) || isRootMenu(value)) return false
        if (CarrierSetupPrompt.detect(text) != null || AccessibilityTextPolicy.isSensitivePrompt(value, false)) return false
        if (CarrierResultClassifier.observe(value) != CarrierResultObservation.INCONCLUSIVE) return false
        if (Regex("""\b(?:upi\s*pin|otp|password)\b""").containsMatchIn(value)) return false
        return true
    }

    fun confirmationDigit(text: String): String? {
        val value = normalized(text)
        val instructions = Regex("\\b(?:press|enter|reply|select)\\s+([1-9])\\s+(?:to\\s+)?confirm\\b").findAll(value).map { it.groupValues[1] }
        val options = Regex("\\b([1-9])\\s*[.)-]\\s*(?:confirm|yes)\\b").findAll(value).map { it.groupValues[1] }
        return (instructions + options).distinct().toList().singleOrNull()
    }
}

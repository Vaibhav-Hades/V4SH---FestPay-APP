package com.festpay.app.data

enum class SetupPromptKind { BANK_SELECTION, CARD_DETAILS, PIN_SETUP }

data class SetupPrompt(val kind: SetupPromptKind, val title: String, val guidance: String)

/**
 * Screens the carrier shows when a SIM is not yet set up for *99# (choose the bank, give debit-card details, create a UPI PIN).
 * V4SH never types into these: the bank name is not needed from V4SH, and card details and PINs are the owner's alone.
 * Detection reads static carrier wording only. The normal "Enter UPI PIN" payment prompt and the main menu never match.
 */
object CarrierSetupPrompt {
    private fun norm(text: String) = text.lowercase().replace(Regex("\\s+"), " ")

    // "IFSC" alone is NOT enough: the normal Send Money menu lists an "IFSC & account number" option.
    private val bank = Regex("""first\s*(?:3|three)\s*letters?|\b(?:enter|type)\s+(?:your\s+|the\s+)?(?:bank\s+(?:name|short\s*name)|ifsc)""")
    // A numbered payment-method menu (Mobile no / UPI ID / saved beneficiary ...) is a normal menu, never a setup screen.
    private val paymentMethodMenu = Regex("""\b[1-9]\s*[.):-]\s*(?:mobile|upi|saved|beneficiary)""")
    private val card = Regex("""\bdebit\s*card\b|last\s*(?:6|six)\s*digits|\bexpiry\b|\bvalid\s*(?:thru|through|till|upto)\b""")
    private val pinSetup = Regex("""\b(?:set|create|generate|reset|change)\s+(?:your\s+|a\s+|the\s+|new\s+)?(?:upi\s*)?pin\b|\bnew\s+upi\s*pin\b|\bconfirm\s+(?:the\s+)?new\s+(?:upi\s*)?pin\b|\bpin\s+(?:is\s+)?not\s+set\b""")

    /**
     * The one value V4SH may type into a setup screen, or null when it must not (ambiguous wording, a PIN, an OTP, unknown format).
     * Card details are typed only when the screen asks for exactly one of them in a format stated on screen.
     */
    fun answer(text: String, bankName: String?, cardLast6: String?, cardExpiryMmYy: String?): String? {
        val value = norm(text)
        if (value.isBlank() || UssdNavigationPolicy.isRootMenu(value)) return null
        if (Regex("""\b(?:otp|one\s*time|password|cvv|pin)\b""").containsMatchIn(value)) return null
        val kind = detect(text)?.kind ?: return null
        return when (kind) {
            SetupPromptKind.BANK_SELECTION -> {
                if (!Regex("""first\s*(?:3|three)\s*letters?""").containsMatchIn(value) || !value.contains("bank")) return null
                bankName?.filter { it.isLetter() }?.take(3)?.takeIf { it.length == 3 }
            }
            SetupPromptKind.CARD_DETAILS -> {
                val asksLast6 = Regex("""last\s*(?:6|six)\s*digits""").containsMatchIn(value)
                val asksExpiry = Regex("""\bexpiry\b|\bvalid\s*(?:thru|through|till|upto)\b""").containsMatchIn(value)
                when {
                    asksLast6 && asksExpiry -> null
                    asksLast6 -> cardLast6
                    asksExpiry && Regex("""\bmmyy\b""").containsMatchIn(value) -> cardExpiryMmYy
                    else -> null
                }
            }
            SetupPromptKind.PIN_SETUP -> null
        }
    }

    fun detect(text: String): SetupPrompt? {
        val value = norm(text)
        if (value.isBlank() || UssdNavigationPolicy.isRootMenu(value) || paymentMethodMenu.containsMatchIn(value)) return null
        return when {
            card.containsMatchIn(value) -> SetupPrompt(SetupPromptKind.CARD_DETAILS, "Your bank is asking for card details",
                "This is the carrier's one-time UPI setup. V4SH does not enter card details or create a PIN. Finish it yourself on the carrier screen, or cancel and set up UPI in your bank's own app first. Then try again.")
            pinSetup.containsMatchIn(value) -> SetupPrompt(SetupPromptKind.PIN_SETUP, "The carrier is offering to set or change your UPI PIN",
                "V4SH stopped and typed nothing. Do not continue unless you want to change your PIN. Cancel the carrier screen and try again.")
            bank.containsMatchIn(value) -> SetupPrompt(SetupPromptKind.BANK_SELECTION, "The carrier is asking you to choose your bank",
                "This SIM is not linked to a bank for *99# yet. You do not need an IFSC code: on the carrier screen type the first 3 letters of your bank's name (for example SBI, HDF, ICI), pick your bank from the list, and follow the carrier's steps. V4SH stopped and typed nothing. After the carrier confirms, tap Try Again.")
            else -> null
        }
    }
}

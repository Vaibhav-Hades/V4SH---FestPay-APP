package com.festpay.app.data

/** Which dialog button submits a typed USSD reply. Exact labels only, never substrings. */
object UssdControlPolicy {
    /** Preference order: the stock "Send" first, then labels other dialer skins use for the same action. */
    private val order = listOf("send", "reply", "submit", "ok")

    /** The dialog's own OK button, by exact label. */
    fun isOk(label: CharSequence?): Boolean = label?.toString()?.trim()?.trimEnd('.', '!')?.lowercase() in setOf("ok", "okay")

    fun submitRank(label: CharSequence?): Int? {
        val value = label?.toString()?.trim()?.trimEnd('.', '!')?.lowercase() ?: return null
        return order.indexOf(value).takeIf { it >= 0 }
    }
}

package com.festpay.app.data

import java.net.URLEncoder

/**
 * Where V4SH support enquiries go. V4SH has no internet permission and no server, so nothing is sent by the app itself:
 * the message is prepared here and the owner of the phone taps Send in their own SMS or WhatsApp app.
 */
object SupportContact {
    const val NUMBER_E164 = "+919618099307"
    const val NUMBER_DISPLAY = "96180 99307"
    const val MAX_ISSUE_CHARS = 600

    fun message(issue: String, details: String?): String = buildString {
        append("V4SH support\n")
        append(issue.trim().take(MAX_ISSUE_CHARS))
        if (!details.isNullOrBlank()) { append("\n\n"); append(details.trim()) }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    fun smsUri(text: String) = "smsto:$NUMBER_E164?body=${enc(text)}"
    fun whatsAppUrl(text: String) = "https://wa.me/${NUMBER_E164.removePrefix("+")}?text=${enc(text)}"
}

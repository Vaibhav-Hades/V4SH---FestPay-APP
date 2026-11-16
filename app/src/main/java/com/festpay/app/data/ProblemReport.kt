package com.festpay.app.data

/**
 * A plain-text report the owner can send by hand when V4SH does not work on their phone, bank or SIM company.
 * It holds device and readiness facts and the TITLES of recent problems. Never a UPI ID, amount, name, PIN, card detail,
 * carrier or screen text, or any journal content.
 */
object ProblemReport {
    data class Facts(
        val appVersion: String, val androidSdk: Int, val manufacturer: String, val model: String,
        val operator: String?, val phonePermission: Boolean, val accessibilityConnected: Boolean,
        val autoSleep: Boolean, val chosenSim: String, val problemTitles: List<String>
    )

    fun build(f: Facts): String = buildString {
        appendLine("V4SH problem report")
        appendLine("App: ${f.appVersion}")
        appendLine("Phone: ${f.manufacturer} ${f.model}, Android SDK ${f.androidSdk}")
        appendLine("SIM company: ${f.operator ?: "unknown"}")
        appendLine("Calling SIM: ${f.chosenSim}")
        appendLine("Phone permission: ${if (f.phonePermission) "granted" else "not granted"}")
        appendLine("Accessibility: ${if (f.accessibilityConnected) "on" else "off"}")
        appendLine("Auto-sleep: ${if (f.autoSleep) "on" else "off"}")
        appendLine("Recent problems (titles only):")
        if (f.problemTitles.isEmpty()) appendLine("- none") else f.problemTitles.distinct().take(10).forEach { appendLine("- $it") }
        appendLine()
        appendLine("Please add: which bank, what you tapped, and what the carrier screen showed (without any PIN, OTP or card number).")
    }
}

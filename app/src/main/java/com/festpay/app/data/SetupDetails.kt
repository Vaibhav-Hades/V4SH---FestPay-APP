package com.festpay.app.data

import android.content.Context

/**
 * Details the owner may give V4SH ahead of a carrier's one-time *99# setup, so V4SH can type them for them.
 *  - Bank name (first letters): not sensitive; saved on the phone in V4SH's private settings.
 *  - Card last 6 digits and expiry: sensitive; held in memory ONLY, never saved, never logged, wiped after use,
 *    when the session ends, when the owner leaves the screen, and after [SENSITIVE_TTL_MS].
 *  - The OTP and the UPI PIN are never accepted or typed by V4SH.
 */
object SetupDetails {
    private const val PREFS = "v4sh_settings"
    private const val KEY_BANK = "setup_bank_query"
    const val SENSITIVE_TTL_MS = 10 * 60 * 1000L

    @Volatile private var cardLast6: String? = null
    @Volatile private var cardExpiry: String? = null
    @Volatile private var storedAt = 0L

    fun bankQuery(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BANK, null)?.takeIf { validBank(it) }

    fun setBankQuery(context: Context, value: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (value == null || !validBank(value)) remove(KEY_BANK) else putString(KEY_BANK, value.trim())
        }.apply()
    }

    fun validBank(value: String) = Regex("^[A-Za-z][A-Za-z .&-]{2,23}$").matches(value.trim())
    fun validLast6(value: String) = Regex("^[0-9]{6}$").matches(value)
    /** MMYY */
    fun validExpiry(value: String) = Regex("^(0[1-9]|1[0-2])[0-9]{2}$").matches(value)

    @Synchronized fun setCard(last6: String?, expiryMmYy: String?, now: Long = System.currentTimeMillis()) {
        cardLast6 = last6?.takeIf { validLast6(it) }
        cardExpiry = expiryMmYy?.takeIf { validExpiry(it) }
        storedAt = now
    }

    @Synchronized fun clearSensitive() { cardLast6 = null; cardExpiry = null; storedAt = 0L }

    @Synchronized fun hasCard(now: Long = System.currentTimeMillis()): Boolean { expire(now); return cardLast6 != null || cardExpiry != null }
    @Synchronized fun last6(now: Long = System.currentTimeMillis()): String? { expire(now); return cardLast6 }
    @Synchronized fun expiry(now: Long = System.currentTimeMillis()): String? { expire(now); return cardExpiry }

    private fun expire(now: Long) { if (storedAt != 0L && now - storedAt > SENSITIVE_TTL_MS) { cardLast6 = null; cardExpiry = null; storedAt = 0L } }
}

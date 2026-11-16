package com.festpay.app.data

import android.content.Context

/**
 * "Sleep mode": V4SH needs an Accessibility service only while it is driving a carrier session. Banking apps refuse to run
 * while a third-party Accessibility service is on, so the owner can put V4SH to sleep (the service switches itself off) and
 * choose whether it does so automatically after every session. Waking it up is an Android settings action only the owner can take.
 */
object SleepMode {
    private const val PREFS = "v4sh_settings"
    private const val KEY_AUTO = "auto_sleep_after_session"

    /** Off by default: V4SH stays on until the owner puts it to sleep, unless the owner turns this on. */
    fun autoSleep(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, false)

    fun setAutoSleep(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    private const val KEY_LAST_AUTO_OFF = "last_auto_sleep_at"
    const val NOTE_WINDOW_MS = 10 * 60 * 1000L

    fun noteAutoSlept(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_AUTO_OFF, now).apply()
    }

    fun dismissAutoSleepNote(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST_AUTO_OFF).apply()
    }

    /** True for a few minutes after V4SH switched itself off, so Home can say why Accessibility is off. */
    fun showAutoSleepNote(context: Context, serviceRunning: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (serviceRunning) return false
        val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_AUTO_OFF, 0L)
        return at > 0L && now - at in 0..NOTE_WINDOW_MS
    }

    enum class Refusal { NONE, SESSION_ACTIVE, NOT_RUNNING }

    /** Pure decision so the rule can be tested: never sleep in the middle of a carrier session. */
    fun canSleepNow(sessionActive: Boolean, serviceRunning: Boolean): Refusal = when {
        sessionActive -> Refusal.SESSION_ACTIVE
        !serviceRunning -> Refusal.NOT_RUNNING
        else -> Refusal.NONE
    }
}

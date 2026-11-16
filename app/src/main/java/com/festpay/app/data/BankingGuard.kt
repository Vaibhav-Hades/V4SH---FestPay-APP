package com.festpay.app.data

import android.content.Context

/**
 * Optional "banking-app guard". Many banking and UPI apps refuse to work while a third-party Accessibility service is on.
 * With the guard on, V4SH puts itself to sleep the moment one of those apps comes to the front, but never in the middle of
 * a carrier session of its own. Off by default. The list is common apps only and is not exhaustive.
 */
object BankingGuard {
    private const val PREFS = "v4sh_settings"
    private const val KEY = "banking_guard"

    val packages: Set<String> = setOf(
        "com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "net.one97.paytm", "in.org.npci.upiapp",
        "com.dreamplug.androidapp", "com.mobikwik_new", "in.amazon.mShop.android.shopping",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.snapwork.hdfc", "com.csam.icici.bank.imobile",
        "com.axis.mobile", "com.msf.kbank.mobile", "com.bankofbaroda.mconnect", "com.canarabank.mobility", "com.infrasoft.uboi"
    )

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun setEnabled(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply() }

    fun isBankingApp(packageName: CharSequence?): Boolean = packageName?.toString() in packages

    /** Pure rule: sleep only when the guard is on, a banking app is in front, and V4SH is not running its own session. */
    fun shouldSleep(guardOn: Boolean, foregroundPackage: CharSequence?, sessionActive: Boolean): Boolean =
        guardOn && !sessionActive && isBankingApp(foregroundPackage)
}

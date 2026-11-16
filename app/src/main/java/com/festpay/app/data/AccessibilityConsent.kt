package com.festpay.app.data

import android.content.Context

/** Versioned affirmative disclosure choice; contains no financial or screen data. */
object AccessibilityConsent {
    private const val STORE = "accessibility_disclosure"
    fun accepted(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean("v1", false)
    fun set(context: Context, accepted: Boolean): Boolean {
        val saved = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean("v1", accepted).commit()
        if (saved && !accepted) AccessibilityRuntime.revokeSession()
        return saved
    }
}

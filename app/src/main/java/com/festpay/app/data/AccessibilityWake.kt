package com.festpay.app.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.festpay.app.FestPayAccessibilityService

/**
 * Optional one-tap wake. Android lets only the phone's owner switch an Accessibility service on, through Settings. An owner who
 * wants a shortcut can grant V4SH the WRITE_SECURE_SETTINGS permission once from a computer; V4SH then switches ONLY its own entry
 * on when the owner taps Wake, and never anything else. Without that permission everything opens Android's Settings as before.
 */
object AccessibilityWake {
    const val ADB_GRANT = "adb shell pm grant com.festpay.app android.permission.WRITE_SECURE_SETTINGS"

    fun canWake(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun flat(context: Context): String = ComponentName(context, FestPayAccessibilityService::class.java).flattenToString()

    /** The enabled-services list with V4SH's entry added once. Every other entry is kept exactly as it was. */
    fun withService(existing: String?, flat: String): String {
        val parts = existing.orEmpty().split(':').filter { it.isNotBlank() }
        return if (parts.any { it.equals(flat, true) }) parts.joinToString(":") else (parts + flat).joinToString(":")
    }

    /** Switches V4SH's own Accessibility entry on. Returns false if Android refuses. */
    fun wake(context: Context): Boolean {
        if (!canWake(context)) return false
        return runCatching {
            val cr = context.contentResolver
            val now = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withService(now, flat(context)))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }
}

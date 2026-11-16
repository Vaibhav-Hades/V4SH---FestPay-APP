package com.festpay.app.data

import android.content.Context
import android.content.Intent
import android.provider.Settings

enum class IssueFix { NONE, ACCESSIBILITY_SETTINGS, PHONE_PERMISSION, HISTORY, STOP_STUCK_OPERATION }

data class AppIssue(val atMillis: Long, val title: String, val detail: String, val fix: IssueFix)

/**
 * Where the app's problems are collected so the owner can find them and the way to fix them in one place (Profile > Advanced).
 * Memory only, newest first, at most [MAX] entries. It holds generic messages, never a UPI ID, amount, PIN or carrier text.
 */
object IssueLog {
    private const val MAX = 30
    private val items = ArrayDeque<AppIssue>()
    @Volatile var version = 0; private set

    @Synchronized fun record(title: String, detail: String, fix: IssueFix = IssueFix.NONE, now: Long = System.currentTimeMillis()) {
        // A repeat of the newest problem only refreshes it.
        if (items.firstOrNull()?.let { it.title == title && it.detail == detail } == true) items.removeFirst()
        items.addFirst(AppIssue(now, title, detail, fix))
        while (items.size > MAX) items.removeLast()
        version++
    }

    @Synchronized fun recent(): List<AppIssue> = items.toList()
    @Synchronized fun clear() { items.clear(); version++ }

    /** Jumps straight to Android's Accessibility list. Returns false if the phone refuses. */
    fun openAccessibilitySettings(context: Context): Boolean =
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

package com.festpay.app

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.festpay.app.data.AccessibilityRuntime
import com.festpay.app.data.AccessibilityWake

/** A Quick Settings tile showing whether V4SH is awake, and the fastest way to change that. */
class V4SHTileService : TileService() {
    override fun onStartListening() { render() }

    private fun render() {
        val tile = qsTile ?: return
        val awake = AccessibilityRuntime.connection.snapshot.connected
        tile.state = if (awake) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "V4SH"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (awake) "Awake" else "Asleep"
        tile.updateTile()
    }

    override fun onClick() {
        val awake = AccessibilityRuntime.connection.snapshot.connected
        when {
            awake && !FestPayAccessibilityService.UssdSessionData.operationActive -> AccessibilityRuntime.sleepNow()
            !awake && AccessibilityWake.wake(this) -> {}
            !awake -> openSettings()
        }
        render()
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks Android to refresh the tile, e.g. when the service connects or switches off. */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, V4SHTileService::class.java)) }
        }
    }
}

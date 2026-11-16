package com.festpay.app.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.festpay.app.FestPayAccessibilityService
import java.lang.ref.WeakReference
import java.util.UUID

interface UssdSessionHost {
    fun connectionAlive(): Boolean
    fun armInitializedSession(command: OwnedUssdCommand, connectionId: String): Boolean
    /** Owner's request: switch the service off now. Default does nothing (test hosts). */
    fun sleepNow() {}
}

data class ConnectionSnapshot(
    val connectionId: String? = null,
    val instanceAvailable: Boolean = false,
    val connected: Boolean = false,
    val sessionId: String? = null,
    val initialized: Boolean = false,
    val acknowledged: Boolean = false,
    val armed: Boolean = false
)

/** Instance identity + connection generation. Reconnection never resumes an old lease. */
class AccessibilityConnection {
    private var owner = WeakReference<UssdSessionHost>(null)
    private var state by mutableStateOf(ConnectionSnapshot())
    private var command: OwnedUssdCommand? = null
    val snapshot: ConnectionSnapshot get() {
        val instance = owner.get()
        val alive = instance != null && runCatching { instance.connectionAlive() }.getOrDefault(false)
        // Once loss is observed, only a new lifecycle connection can authorize use.
        // A later successful getter must not resurrect a stale ACK/armed session.
        if (!alive && state.connected) {
            command = null
            state = ConnectionSnapshot(instanceAvailable = instance != null)
        }
        return state.copy(instanceAvailable = instance != null, connected = state.connected && alive,
            initialized = state.initialized && alive, acknowledged = state.acknowledged && alive, armed = state.armed && alive)
    }
    fun host(): UssdSessionHost? = owner.get()?.takeIf { snapshot.connected }
    fun isOwner(instance: UssdSessionHost): Boolean = owner.get() === instance && snapshot.connected
    fun connect(instance: UssdSessionHost): String {
        if (owner.get() === instance && snapshot.connected) return checkNotNull(state.connectionId)
        owner = WeakReference(instance)
        command = null
        state = ConnectionSnapshot(UUID.randomUUID().toString(), true, true)
        return checkNotNull(state.connectionId)
    }
    fun disconnect(instance: UssdSessionHost): Boolean {
        if (owner.get() !== instance) return false
        owner.clear(); command = null; state = ConnectionSnapshot()
        return true
    }
    fun claim(value: OwnedUssdCommand): String? {
        val current = snapshot
        if (!current.connected || current.sessionId != null) return null
        command = value
        state = current.copy(sessionId = value.sessionId, initialized = false, acknowledged = false, armed = false)
        return current.connectionId
    }
    fun matches(value: OwnedUssdCommand, connectionId: String): Boolean = snapshot.let {
        it.connected && it.connectionId == connectionId && it.sessionId == value.sessionId && command == value
    }
    fun preparedCommand(sessionId: String, connectionId: String): OwnedUssdCommand? = command?.takeIf {
        it.sessionId == sessionId && matches(it, connectionId)
    }
    fun initialize(instance: UssdSessionHost, sessionId: String, connectionId: String): Boolean {
        if (!isOwner(instance) || preparedCommand(sessionId, connectionId) == null || state.initialized) return false
        state = state.copy(initialized = true)
        return true
    }
    fun acknowledge(value: OwnedUssdCommand, connectionId: String): Boolean {
        if (!matches(value, connectionId) || !state.initialized) return false
        state = state.copy(acknowledged = true)
        return true
    }
    fun arm(instance: UssdSessionHost, value: OwnedUssdCommand, connectionId: String): Boolean {
        if (!isOwner(instance) || !matches(value, connectionId) || !state.initialized || !state.acknowledged || state.armed) return false
        state = state.copy(armed = true)
        return true
    }
    fun release(sessionId: String): Boolean {
        if (state.sessionId != sessionId) return false
        command = null
        state = state.copy(sessionId = null, initialized = false, acknowledged = false, armed = false)
        return true
    }
    fun clearSession() { state.sessionId?.let(::release) }
}

object AccessibilityRuntime {
    val connection = AccessibilityConnection()
    fun settingsEnabled(context: Context): Boolean = try {
        val expected = ComponentName(context, FestPayAccessibilityService::class.java)
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == expected }
    } catch (_: Exception) { false }
    fun diagnostics(context: Context, paymentBlocked: Boolean = true): ConnectionSnapshot {
        val state = connection.snapshot
        Log.i("V4SH_Readiness", "ENABLED=${settingsEnabled(context)} CONNECTED=${state.connected} INSTANCE=${state.instanceAvailable} INITIALIZED=${state.initialized} ACK=${state.acknowledged} LAUNCH_ALLOWED=${!paymentBlocked && state.connected && state.armed && AccessibilityConsent.accepted(context)}")
        return state
    }
    /** Asks the running service to switch itself off. The caller decides whether a session may be interrupted. */
    fun sleepNow() { connection.host()?.sleepNow() }

    fun revokeSession() {
        connection.clearSession()
        val session = FestPayAccessibilityService.UssdSessionData
        val active = session.operationActive
        val balance = session.isBalanceCheck
        session.operationActive = false
        session.reset(balance)
        if (active) {
            session.currentPresentationState = if (balance) "BALANCE_UNKNOWN" else "UNKNOWN"
            session.errorMessage = "Screen assistance stopped. No automatic retry was performed."
        }
    }
}

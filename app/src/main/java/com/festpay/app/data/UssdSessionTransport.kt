package com.festpay.app.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.festpay.app.FestPayAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

data class OwnedUssdCommand(
    val sessionId: String,
    val balance: Boolean,
    val attemptId: String? = null,
    val recipient: String = "",
    val amount: String = ""
) {
    init {
        require(UUID.fromString(sessionId).toString() == sessionId)
        if (balance) require(attemptId == null && recipient.isEmpty() && amount.isEmpty())
        else require(attemptId != null && UUID.fromString(attemptId).toString() == attemptId && PaymentInputValidator.isVpa(recipient) && PaymentInputValidator.amount(amount) == amount)
    }
    companion object {
        fun balance() = OwnedUssdCommand(UUID.randomUUID().toString(), true)
        fun payment(attempt: String, recipient: String, amount: String) = OwnedUssdCommand(UUID.randomUUID().toString(), false, attempt, recipient, amount)
    }
}

enum class PrepareFailure { NONE, CONSENT_MISSING, SERVICE_NOT_CONNECTED, OPERATION_ACTIVE, CLAIM_REJECTED, ACK_TIMEOUT, BROADCAST_FAILED }

/** Acknowledges initialization only. A deadline never proves authentication/submission. */
class UssdSessionTransport(
    private val action: String = FestPayAccessibilityService.ACTION_NEW_USSD_SESSION,
    private val acknowledgementDeadlineMs: Long = 3_000
) {
    /** Why the most recent [prepare] returned false (NONE after success). For error reporting only. */
    @Volatile var lastFailure: PrepareFailure = PrepareFailure.NONE
        private set

    suspend fun prepare(context: Context, command: OwnedUssdCommand): Boolean = withContext(Dispatchers.Main.immediate) {
        val session = FestPayAccessibilityService.UssdSessionData
        lastFailure = PrepareFailure.NONE
        fun refuse(reason: PrepareFailure): Boolean { lastFailure = reason; PaymentDiagnostics.stage("transport.prepare", "REFUSED", "reason=${reason.name}"); return false }
        if (!AccessibilityConsent.accepted(context)) return@withContext refuse(PrepareFailure.CONSENT_MISSING)
        if (!session.serviceConnected) return@withContext refuse(PrepareFailure.SERVICE_NOT_CONNECTED)
        if (session.operationActive) return@withContext refuse(PrepareFailure.OPERATION_ACTIVE)
        // A completed observation relinquishes its lease; never disturb an active one.
        AccessibilityRuntime.connection.clearSession()
        val epoch = AccessibilityRuntime.connection.claim(command) ?: return@withContext refuse(PrepareFailure.CLAIM_REJECTED)
        session.reset(command.balance)
        session.operationActive = true
        session.sessionId = command.sessionId
        var acknowledged = false
        try {
            acknowledged = withTimeoutOrNull(acknowledgementDeadlineMs) {
                suspendCancellableCoroutine { continuation ->
                    val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (continuation.isActive && resultCode == 0 && resultData?.getString(FestPayAccessibilityService.EXTRA_SESSION_ID) == command.sessionId && resultData.getString(FestPayAccessibilityService.EXTRA_CONNECTION_ID) == epoch && session.sessionId == command.sessionId && session.operationActive && AccessibilityConsent.accepted(context) && AccessibilityRuntime.connection.acknowledge(command, epoch)) {
                                continuation.resume(true)
                            }
                        }
                    }
                    val intent = Intent(action).apply {
                        setPackage(context.packageName)
                        putExtra(FestPayAccessibilityService.EXTRA_SESSION_ID, command.sessionId)
                        putExtra(FestPayAccessibilityService.EXTRA_CONNECTION_ID, epoch)
                        putExtra(FestPayAccessibilityService.EXTRA_SESSION_ACK, receiver)
                        putExtra(FestPayAccessibilityService.EXTRA_BALANCE_CHECK, command.balance)
                        putExtra(FestPayAccessibilityService.EXTRA_ATTEMPT_ID, command.attemptId)
                        putExtra(FestPayAccessibilityService.EXTRA_RECIPIENT, command.recipient)
                        putExtra(FestPayAccessibilityService.EXTRA_AMOUNT, command.amount)
                    }
                    try { context.sendBroadcast(intent) }
                    catch (e: Exception) {
                        lastFailure = PrepareFailure.BROADCAST_FAILED
                        PaymentDiagnostics.stage("transport.prepare", "BROADCAST_FAILED", "exception=${e.javaClass.simpleName}")
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            } == true
            AccessibilityRuntime.diagnostics(context)
            if (!acknowledged && lastFailure == PrepareFailure.NONE) lastFailure = PrepareFailure.ACK_TIMEOUT
            PaymentDiagnostics.stage("transport.prepare", if (acknowledged) "ACKNOWLEDGED" else "NOT_ACKNOWLEDGED", "reason=${lastFailure.name}")
            return@withContext acknowledged
        } finally { if (!acknowledged) release(command.sessionId) }
    }

    /** Only the current, acknowledged instance can arm. Caller must first persist payment intent. */
    fun arm(context: Context, command: OwnedUssdCommand): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!AccessibilityConsent.accepted(context)) return false
        val connection = AccessibilityRuntime.connection
        val epoch = connection.snapshot.connectionId ?: return false
        if (!connection.matches(command, epoch)) return false
        return connection.host()?.armInitializedSession(command, epoch) == true
    }

    fun release(sessionId: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { release(sessionId) }
            return
        }
        val session = FestPayAccessibilityService.UssdSessionData
        if (session.sessionId == sessionId) {
            AccessibilityRuntime.connection.release(sessionId)
            session.operationActive = false
            session.sessionId = ""
        }
    }
}

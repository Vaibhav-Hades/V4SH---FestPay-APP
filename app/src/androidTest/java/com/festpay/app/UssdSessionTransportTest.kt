package com.festpay.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class UssdSessionTransportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = object : UssdSessionHost {
        override fun connectionAlive() = true
        override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String) = AccessibilityRuntime.connection.arm(this, command, connectionId)
    }
    private fun acknowledge(intent: Intent, id: String = intent.getStringExtra(FestPayAccessibilityService.EXTRA_SESSION_ID)!!) {
        val epoch = intent.getStringExtra(FestPayAccessibilityService.EXTRA_CONNECTION_ID)!!
        AccessibilityRuntime.connection.initialize(host, intent.getStringExtra(FestPayAccessibilityService.EXTRA_SESSION_ID)!!, epoch)
        IntentCompat.getParcelableExtra(intent, FestPayAccessibilityService.EXTRA_SESSION_ACK, ResultReceiver::class.java)!!.send(0,
            Bundle().apply { putString(FestPayAccessibilityService.EXTRA_SESSION_ID, id); putString(FestPayAccessibilityService.EXTRA_CONNECTION_ID, epoch) })
    }
    @Test fun acknowledgedBalanceResetHasNoPaymentPayloadAndBusySessionsStayOwned() = runBlocking {
        val session = FestPayAccessibilityService.UssdSessionData
        val consent = AccessibilityConsent.accepted(context)
        val action = "com.festpay.app.TEST_SESSION_${UUID.randomUUID()}"
        val received = AtomicReference<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { received.set(intent!!); acknowledge(intent) }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            withContext(Dispatchers.Main) {
                AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host); session.operationActive = false
                session.recipientVpa = "old@upi"; session.amount = "9.00"; session.attemptId = UUID.randomUUID().toString(); session.parsedBalance = "stale"
                val transport = UssdSessionTransport(action)
                val balance = OwnedUssdCommand.balance()
                assertTrue(transport.prepare(context, balance))
                assertTrue(session.isBalanceCheck); assertNull(session.parsedBalance)
                assertEquals("", session.recipientVpa); assertEquals("", session.amount); assertEquals("", session.attemptId)
                assertTrue(received.get().getBooleanExtra(FestPayAccessibilityService.EXTRA_BALANCE_CHECK, false))
                assertNull(received.get().getStringExtra(FestPayAccessibilityService.EXTRA_ATTEMPT_ID))
                val payment = OwnedUssdCommand.payment(UUID.randomUUID().toString(), "new@upi", "2.00")
                assertFalse(transport.prepare(context, payment)); assertEquals(balance.sessionId, session.sessionId)
                transport.release(balance.sessionId)
                assertTrue(transport.prepare(context, payment)); assertFalse(session.isBalanceCheck)
                assertEquals(payment.attemptId, received.get().getStringExtra(FestPayAccessibilityService.EXTRA_ATTEMPT_ID))
                assertEquals("new@upi", received.get().getStringExtra(FestPayAccessibilityService.EXTRA_RECIPIENT))
                assertFalse(transport.prepare(context, OwnedUssdCommand.balance()))
                transport.release(payment.sessionId)
            }
        } finally {
            context.unregisterReceiver(receiver)
            withContext(Dispatchers.Main) { session.operationActive = false; session.reset(); AccessibilityRuntime.connection.disconnect(host); AccessibilityConsent.set(context, consent) }
        }
    }
    @Test fun lateOrWrongAcknowledgementCannotArmAnotherSession() = runBlocking {
        val session = FestPayAccessibilityService.UssdSessionData
        val consent = AccessibilityConsent.accepted(context)
        val action = "com.festpay.app.TEST_LATE_${UUID.randomUUID()}"
        val calls = AtomicInteger()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (calls.incrementAndGet()) {
                    1 -> Handler(Looper.getMainLooper()).postDelayed({ acknowledge(intent!!) }, 400)
                    2 -> acknowledge(intent!!)
                    else -> acknowledge(intent!!, UUID.randomUUID().toString())
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            withContext(Dispatchers.Main) {
                AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host); session.operationActive = false
                val expired = OwnedUssdCommand.balance()
                assertFalse(UssdSessionTransport(action, 100).prepare(context, expired))
                assertFalse(session.operationActive); assertEquals("", session.sessionId)
                val current = OwnedUssdCommand.balance()
                val transport = UssdSessionTransport(action, 1_000)
                assertTrue(transport.prepare(context, current))
                transport.release(expired.sessionId)
                assertEquals(current.sessionId, session.sessionId)
                assertTrue(session.operationActive)
                delay(500)
                assertEquals(current.sessionId, session.sessionId); assertTrue(session.operationActive)
                transport.release(current.sessionId)
                assertFalse(UssdSessionTransport(action, 100).prepare(context, OwnedUssdCommand.balance()))
                assertFalse(session.operationActive)
            }
        } finally {
            context.unregisterReceiver(receiver)
            withContext(Dispatchers.Main) { session.operationActive = false; session.reset(); AccessibilityRuntime.connection.disconnect(host); AccessibilityConsent.set(context, consent) }
        }
    }
    @Test fun newQrClearsPreviousRecipientAmountAndNote() {
        com.festpay.app.ui.screens.parseAndValidateUpiQr("upi://pay?pa=first@upi&pn=First&am=1.00&tn=first")
        assertEquals("first", com.festpay.app.ui.screens.SharedPaymentState.paymentNote)
        val state = com.festpay.app.ui.screens.SharedPaymentState
        assertEquals("first@upi", state.payeeVpa)
        assertNull(state.recipient.verifiedBeneficiaryName)
        val next = com.festpay.app.ui.screens.parseAndValidateUpiQr("upi://pay?pa=second@upi") as com.festpay.app.ui.screens.UpiParseResult.Valid
        assertEquals("second@upi", next.pa)
        assertEquals("second@upi", state.payeeVpa); assertNull(state.recipient.verifiedBeneficiaryName)
        assertEquals("", state.paymentAmount); assertEquals("", state.paymentNote); assertFalse(state.isMobilePayment)
        com.festpay.app.ui.screens.parseAndValidateUpiQr("invalid")
        assertEquals("", state.payeeVpa)
    }
}

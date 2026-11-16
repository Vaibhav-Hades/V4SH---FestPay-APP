package com.festpay.app

import android.content.*
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Synthetic stores only. No receiver arms and no test reaches a carrier launch. */
class PaymentReadinessLifecycleTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Host : UssdSessionHost {
        override fun connectionAlive() = true
        override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String): Boolean = error("Must never arm in a rejected preflight")
    }
    private fun isolated(name: String) = object : ContextWrapper(base) {
        override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        override fun startActivity(intent: Intent?) { error("Rejected preflight must not launch") }
    }
    @Test fun plannedAttemptIdIsDurableBeforeArmingAndCannotBeReplaced() {
        val name = "test_planned_${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        try {
            PaymentAttemptJournal(base, name).use { journal ->
                assertEquals(id, journal.create(PaymentRequest("synthetic@upi", "", "1.00", attemptId = id)))
                assertEquals(id, journal.unresolvedId())
                val saved = journal.records().single()
                assertEquals(id, saved.localTransactionId); assertEquals(PaymentState.INITIATED, saved.state)
                java.time.Instant.parse(saved.timestamp)
                assertNull(saved.bankReference); assertNull(saved.referenceProvenance)
                try { journal.create(PaymentRequest("another@upi", "", "2.00")); fail("Must retain reservation") }
                catch (_: IllegalStateException) { }
                assertEquals(listOf(saved), journal.records())
            }
            PaymentAttemptJournal(base, name).use { journal ->
                journal.recover()
                assertEquals(id, journal.unresolvedId())
                assertEquals(PaymentState.UNKNOWN, journal.records().single().state)
            }
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }
    @Test fun missingAcknowledgmentCreatesNoAttempt() = runBlocking { rejectedHandshake(false) }
    @Test fun changedReviewAfterAcknowledgmentCreatesNoAttempt() = runBlocking { rejectedHandshake(true) }

    private suspend fun rejectedHandshake(acknowledge: Boolean) {
        val name = "test_readiness_${UUID.randomUUID()}"
        val db = "$name.db"; val context = isolated(name); val host = Host()
        val action = "com.festpay.app.TEST_PREFLIGHT_${UUID.randomUUID()}"
        val store = ViewModelStore(); var current = true; var received = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                received = true
                if (!acknowledge) return
                val id = intent!!.getStringExtra(FestPayAccessibilityService.EXTRA_SESSION_ID)!!
                val epoch = intent.getStringExtra(FestPayAccessibilityService.EXTRA_CONNECTION_ID)!!
                assertTrue(AccessibilityRuntime.connection.initialize(host, id, epoch))
                current = false
                androidx.core.content.IntentCompat.getParcelableExtra(intent, FestPayAccessibilityService.EXTRA_SESSION_ACK, android.os.ResultReceiver::class.java)!!.send(0,
                    android.os.Bundle().apply { putString(FestPayAccessibilityService.EXTRA_SESSION_ID, id); putString(FestPayAccessibilityService.EXTRA_CONNECTION_ID, epoch) })
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            withContext(Dispatchers.Main) {
                AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host)
                FestPayAccessibilityService.UssdSessionData.operationActive = false
                val model = PaymentViewModel({ PaymentAttemptJournal(it, db) }, { TransactionRepository(it, name) }, { UssdSessionTransport(action, 100) })
                store.put("model", model)
                val done = CompletableDeferred<PaymentState>()
                model.startPayment("synthetic@upi", "", "1.00", context, { current }) { done.complete(it) }
                assertEquals(PaymentState.IDLE, withTimeout(5_000) { done.await() })
                assertTrue("The inert handshake must actually be exercised", received)
                assertNotNull(model.operationError)
                assertNull(model.selectedAttemptId)
                PaymentAttemptJournal(context, db).use {
                    assertTrue(it.records().isEmpty()); assertNull(it.unresolvedId())
                }
                assertFalse(AccessibilityRuntime.connection.snapshot.armed)
                assertFalse(FestPayAccessibilityService.UssdSessionData.operationActive)
            }
        } finally {
            context.unregisterReceiver(receiver)
            withContext(Dispatchers.Main) { store.clear(); AccessibilityRuntime.connection.disconnect(host); AccessibilityRuntime.revokeSession() }
            SecureTestStores.cleanupJournal(base, db); SecureTestStores.cleanupPreferences(base, name)
        }
    }

    @Test fun existingReservationRejectsBeforeAnyHandshakeAndKeepsExactRecord() = runBlocking {
        val name = "test_reserved_${UUID.randomUUID()}"; val db = "$name.db"; val context = isolated(name)
        val host = Host(); val models = ViewModelStore()
        try {
            PaymentAttemptJournal(context, db).use { journal ->
                val id = journal.create(PaymentRequest("synthetic@upi", "", "1.00"))
                journal.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
                val before = journal.records()
                withContext(Dispatchers.Main) {
                    AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host)
                    FestPayAccessibilityService.UssdSessionData.operationActive = false
                    val model = PaymentViewModel({ PaymentAttemptJournal(it, db) }, { TransactionRepository(it, name) }, { error("Reservation must reject before handshake") })
                    models.put("model", model)
                    var result: PaymentState? = null
                    model.startPayment("another@upi", "", "2.00", context, { true }) { result = it }
                    assertEquals(PaymentState.UNKNOWN, result); assertEquals(id, model.selectedAttemptId)
                    assertEquals(before, journal.records()); assertEquals(id, journal.unresolvedId())
                    assertNull(AccessibilityRuntime.connection.snapshot.sessionId)
                }
            }
        } finally {
            withContext(Dispatchers.Main) { models.clear(); AccessibilityRuntime.connection.disconnect(host); AccessibilityRuntime.revokeSession() }
            SecureTestStores.cleanupJournal(base, db); SecureTestStores.cleanupPreferences(base, name)
        }
    }

    @Test fun consentWithdrawalDisarmsInitializedSessionWithoutDisconnectingService() = runBlocking {
        val name = "test_withdraw_${UUID.randomUUID()}"; val context = isolated(name); val host = Host()
        try {
            withContext(Dispatchers.Main) {
                AccessibilityConsent.set(context, true)
                val epoch = AccessibilityRuntime.connection.connect(host); val command = OwnedUssdCommand.balance()
                AccessibilityRuntime.connection.claim(command)
                AccessibilityRuntime.connection.initialize(host, command.sessionId, epoch)
                AccessibilityRuntime.connection.acknowledge(command, epoch)
                AccessibilityRuntime.connection.arm(host, command, epoch)
                FestPayAccessibilityService.UssdSessionData.operationActive = true
                assertTrue(AccessibilityConsent.set(context, false))
                assertTrue(AccessibilityRuntime.connection.snapshot.connected)
                assertFalse(AccessibilityRuntime.connection.snapshot.armed)
                assertFalse(AccessibilityRuntime.connection.snapshot.acknowledged)
                assertFalse(FestPayAccessibilityService.UssdSessionData.operationActive)
                assertFalse(UssdSessionTransport().arm(context, command))
            }
        } finally {
            withContext(Dispatchers.Main) { AccessibilityRuntime.connection.disconnect(host); AccessibilityRuntime.revokeSession() }
            SecureTestStores.cleanupPreferences(base, name)
        }
    }
}

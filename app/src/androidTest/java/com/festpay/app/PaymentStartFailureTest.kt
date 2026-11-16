package com.festpay.app

import android.Manifest
import android.content.*
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Each guard between the Review button and the Android launch reports its OWN reason.
 * Synthetic journals, a fake Accessibility host and an intercepted dial only: no carrier, no real payment,
 * and the user's real journal is never opened.
 */
class PaymentStartFailureTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class Host(private val arms: Boolean = false) : UssdSessionHost {
        override fun connectionAlive() = true
        override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String): Boolean =
            arms && AccessibilityRuntime.connection.arm(this, command, connectionId)
    }

    private fun isolated(name: String, denyCallPhone: Boolean = false, launch: (Intent) -> Unit = { error("A refused start must never launch") }) =
        object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
            override fun startActivity(intent: Intent) = launch(intent)
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (denyCallPhone && permission == Manifest.permission.CALL_PHONE) android.content.pm.PackageManager.PERMISSION_DENIED
                else base.checkPermission(permission, pid, uid)
        }

    private fun model(name: String, transport: () -> UssdSessionTransport = { error("Transport must not be reached") }) =
        PaymentViewModel({ PaymentAttemptJournal(it, "$name.db") }, { TransactionRepository(it, name) }, transport)

    private fun cleanup(name: String, host: Host) {
        AccessibilityRuntime.connection.disconnect(host); AccessibilityRuntime.revokeSession()
        SecureTestStores.cleanupJournal(base, "$name.db"); SecureTestStores.cleanupPreferences(base, name)
    }

    private fun refused(name: String, setup: (Context, Host) -> Unit, denyCallPhone: Boolean = false, valid: Boolean = true): Pair<PaymentViewModel, PaymentState?> {
        val context = isolated(name, denyCallPhone); val host = Host()
        var out: PaymentState? = null
        val m = model(name)
        runBlocking(Dispatchers.Main) {
            setup(context, host)
            FestPayAccessibilityService.UssdSessionData.operationActive = false
            m.startPayment(if (valid) "synthetic@upi" else "9876543210", "", "1.00", context, { true }) { out = it }
        }
        return m to out
    }

    @Test fun accessibilityNotConnectedIsReportedAsExactlyThat() {
        val name = "test_fail_a11y_${UUID.randomUUID()}"; val host = Host()
        try {
            val (m, state) = refused(name, { c, h -> AccessibilityConsent.set(c, true); AccessibilityRuntime.connection.connect(h); AccessibilityRuntime.connection.disconnect(h) })
            assertEquals(PaymentState.IDLE, state)
            assertEquals(StartFailure.ACCESSIBILITY_UNAVAILABLE, m.startFailure)
            assertTrue(m.operationError!!, m.operationError!!.contains("Accessibility is not connected"))
            PaymentAttemptJournal(base, "$name.db").use { assertTrue(it.records().isEmpty()) }
        } finally { cleanup(name, host) }
    }

    @Test fun deniedPhonePermissionIsReportedAsExactlyThat() {
        val name = "test_fail_phone_${UUID.randomUUID()}"; val host = Host()
        try {
            val context = isolated(name, denyCallPhone = true); var out: PaymentState? = null
            val m = model(name)
            runBlocking(Dispatchers.Main) {
                AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host)
                FestPayAccessibilityService.UssdSessionData.operationActive = false
                m.startPayment("synthetic@upi", "", "1.00", context, { true }) { out = it }
            }
            assertEquals(PaymentState.IDLE, out)
            assertEquals(StartFailure.PHONE_PERMISSION, m.startFailure)
            assertTrue(m.operationError!!.contains("Phone permission"))
            PaymentAttemptJournal(base, "$name.db").use { assertTrue(it.records().isEmpty()) }
        } finally { cleanup(name, host) }
    }

    @Test fun withdrawnConsentIsReportedAsExactlyThat() {
        val name = "test_fail_consent_${UUID.randomUUID()}"; val host = Host()
        try {
            val (m, state) = refused(name, { c, h -> AccessibilityConsent.set(c, false); AccessibilityRuntime.connection.connect(h) })
            assertEquals(PaymentState.IDLE, state)
            assertEquals(StartFailure.CONSENT, m.startFailure)
            assertTrue(m.operationError!!.contains("disclosure"))
        } finally { cleanup(name, host) }
    }

    @Test fun invalidRequestIsReportedAsExactlyThatAndNeverReachesTheJournal() {
        val name = "test_fail_request_${UUID.randomUUID()}"; val host = Host()
        try {
            val (m, state) = refused(name, { c, h -> AccessibilityConsent.set(c, true); AccessibilityRuntime.connection.connect(h) }, valid = false)
            assertEquals(PaymentState.IDLE, state)
            assertEquals(StartFailure.INVALID_REQUEST, m.startFailure)
            assertTrue(m.operationError!!.contains("not valid"))
            PaymentAttemptJournal(base, "$name.db").use { assertTrue(it.records().isEmpty()) }
        } finally { cleanup(name, host) }
    }

    /** The condition the user hit on the device: an earlier UNKNOWN attempt holds the reservation. */
    @Test fun anExistingUnknownReservationBlocksBeforeAnyHandshakeEngineOrLaunchAndStaysByteForByteIntact() {
        val name = "test_fail_reserved_${UUID.randomUUID()}"; val host = Host(); val stores = ViewModelStore()
        try {
            val context = isolated(name)
            PaymentAttemptJournal(context, "$name.db").use { journal ->
                val id = journal.create(PaymentRequest("synthetic@upi", "", "1.00"))
                journal.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
                val before = journal.records()
                val m = model(name)
                stores.put("m", object : androidx.lifecycle.ViewModel() {})
                var out: PaymentState? = null
                runBlocking(Dispatchers.Main) {
                    AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host)
                    FestPayAccessibilityService.UssdSessionData.operationActive = false
                    // What the Review screen reads to enable or disable Pay.
                    m.inspectPaymentReadiness(context)
                    assertEquals(PreparationBlock.UNRESOLVED_ATTEMPT, m.preparationBlock)
                    assertTrue("Pay must be disabled", m.paymentPreparationBlocked)
                    // And what a tap would do if it were enabled.
                    m.startPayment("another@upi", "", "2.00", context, { true }) { out = it }
                }
                assertEquals(PaymentState.UNKNOWN, out)
                assertEquals(StartFailure.RESERVATION_PROTECTION, m.startFailure)
                assertTrue(m.operationError!!.contains("earlier payment attempt remains unresolved"))
                assertEquals(before, journal.records()); assertEquals(id, journal.unresolvedId())
                assertNull(AccessibilityRuntime.connection.snapshot.sessionId)
            }
        } finally { cleanup(name, host) }
    }

    @Test fun anUnacknowledgedAccessibilitySessionIsReportedAsExactlyThatAndCreatesNoAttempt() {
        val name = "test_fail_ack_${UUID.randomUUID()}"; val host = Host()
        try {
            val context = isolated(name); var out: PaymentState? = null; var transport: UssdSessionTransport? = null
            val m = model(name) { UssdSessionTransport("com.festpay.app.TEST_NO_RECEIVER_${UUID.randomUUID()}", 100).also { transport = it } }
            runBlocking(Dispatchers.Main) {
                AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host)
                FestPayAccessibilityService.UssdSessionData.operationActive = false
                val done = CompletableDeferred<PaymentState>()
                m.startPayment("synthetic@upi", "", "1.00", context, { true }) { done.complete(it) }
                out = withTimeout(5_000) { done.await() }
            }
            assertEquals(PaymentState.IDLE, out)
            assertEquals(PrepareFailure.ACK_TIMEOUT, transport!!.lastFailure)
            assertEquals(StartFailure.SESSION_NOT_ACKNOWLEDGED, m.startFailure)
            assertTrue(m.operationError!!.contains("did not acknowledge"))
            PaymentAttemptJournal(base, "$name.db").use { assertTrue(it.records().isEmpty()) }
        } finally { cleanup(name, host) }
    }

    /** Android refusing the dial: the engine IS reached, so the attempt is saved and stays UNKNOWN and reserved. */
    @Test fun androidRefusingTheDialIsReportedAsALaunchFailureAndKeepsTheAttemptReserved() {
        val name = "test_fail_launch_${UUID.randomUUID()}"; val host = Host(arms = true)
        val action = "com.festpay.app.TEST_LAUNCH_FAIL_${UUID.randomUUID()}"
        var launchAttempts = 0
        val context = isolated(name, launch = { intent ->
            launchAttempts++
            assertEquals(Intent.ACTION_CALL, intent.action); assertEquals("*99#", intent.data?.schemeSpecificPart)
            throw SecurityException("fixture: dial refused")
        })
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent) {
                val id = intent.getStringExtra(FestPayAccessibilityService.EXTRA_SESSION_ID)!!
                val epoch = intent.getStringExtra(FestPayAccessibilityService.EXTRA_CONNECTION_ID)!!
                assertTrue(AccessibilityRuntime.connection.initialize(host, id, epoch))
                IntentCompat.getParcelableExtra(intent, FestPayAccessibilityService.EXTRA_SESSION_ACK, ResultReceiver::class.java)!!.send(0,
                    Bundle().apply { putString(FestPayAccessibilityService.EXTRA_SESSION_ID, id); putString(FestPayAccessibilityService.EXTRA_CONNECTION_ID, epoch) })
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val m = model(name) { UssdSessionTransport(action) }
            var out: PaymentState? = null
            runBlocking(Dispatchers.Main) {
                AccessibilityConsent.set(context, true); AccessibilityRuntime.connection.connect(host)
                FestPayAccessibilityService.UssdSessionData.operationActive = false
                val done = CompletableDeferred<PaymentState>()
                m.startPayment("synthetic@upi", "", "1.00", context, { true }) { done.complete(it) }
                out = withTimeout(10_000) { done.await() }
            }
            assertEquals(PaymentState.UNKNOWN, out)
            assertEquals(1, launchAttempts)
            assertEquals(StartFailure.LAUNCH_FAILED, m.startFailure)
            assertEquals(LaunchDisposition.LAUNCH_REQUEST_FAILED, m.lastResult!!.launchDisposition)
            assertTrue(m.lastResult!!.message.contains("rejected the carrier launch"))
            PaymentAttemptJournal(base, "$name.db").use {
                assertEquals(PaymentState.UNKNOWN, it.records().single().state)
                assertEquals(m.selectedAttemptId, it.unresolvedId())
            }
        } finally {
            context.unregisterReceiver(receiver); cleanup(name, host)
        }
    }
}

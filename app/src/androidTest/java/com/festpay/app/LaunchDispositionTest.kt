package com.festpay.app

import android.content.*
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import org.json.JSONObject

class LaunchDispositionTest {
    @Test fun armRejectionNeverRequestsAndroidLaunch() = runBlocking { exercise(false, false, LaunchDisposition.PRE_LAUNCH_REJECTED) }
    @Test fun synchronousExceptionIsLaunchRequestFailure() = runBlocking { exercise(true, true, LaunchDisposition.LAUNCH_REQUEST_FAILED) }
    @Test fun returnedRequestDoesNotProveCarrierActivation() = runBlocking { exercise(true, false, LaunchDisposition.LAUNCH_REQUEST_RETURNED) }

    private suspend fun exercise(arm: Boolean, throws: Boolean, expected: LaunchDisposition) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_launch_${UUID.randomUUID()}"; val db = "$name.db"
        var calls = 0
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
            override fun startActivity(intent: Intent) {
                assertEquals(Intent.ACTION_CALL, intent.action); calls++
                if (throws) throw SecurityException("Synthetic rejection")
                // Never delegate: this fixture cannot dial.
            }
        }
        val host = object : UssdSessionHost {
            override fun connectionAlive() = true
            override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String) =
                arm && AccessibilityRuntime.connection.arm(this, command, connectionId)
        }
        try {
            val id = PaymentAttemptJournal(base, db).use { journal ->
                journal.create(PaymentRequest("synthetic@upi", "", "1.00")).also {
                    journal.transition(it, PaymentState.PROCESSING, "TEST_START")
                }
            }
            val command = OwnedUssdCommand.payment(id, "synthetic@upi", "1.00")
            withContext(Dispatchers.Main) {
                AccessibilityConsent.set(context, true)
                val c = AccessibilityRuntime.connection
                val epoch = c.connect(host)
                c.claim(command); c.initialize(host, command.sessionId, epoch); c.acknowledge(command, epoch)
                val engine = RealUssdPaymentEngine { PaymentAttemptJournal(base, db) }
                val result = engine.executePayment(PaymentRequest("synthetic@upi", "", "1.00", attemptId = id, preparedCommand = command), PaymentScenario.UNKNOWN, context)
                assertEquals(expected, result.launchDisposition)
                assertEquals(PaymentState.UNKNOWN, result.state)
                assertEquals(if (arm) 1 else 0, calls)
                assertNotEquals(LaunchDisposition.CARRIER_OBSERVED, result.launchDisposition)
            }
            PaymentAttemptJournal(base, db).use { journal ->
                assertEquals(id, journal.unresolvedId())
                assertEquals(PaymentState.PROCESSING, journal.records().single().state)
                val cipher = KeystoreRecordCipher("journal:$db")
                val reasons = mutableSetOf<String>()
                journal.readableDatabase.rawQuery("SELECT row_key,payload FROM events", null).use { cursor ->
                    while (cursor.moveToNext()) reasons += JSONObject(cipher.open(cursor.getBlob(1), "events:${cursor.getString(0)}").toString(Charsets.UTF_8)).getString("reason")
                }
                assertTrue(reasons.contains(if (arm) "SESSION_ARM_SUCCEEDED" else "SESSION_ARM_FAILED"))
                assertEquals(arm, reasons.contains("ANDROID_LAUNCH_REQUESTED"))
                assertEquals(arm && throws, reasons.contains("ANDROID_LAUNCH_THREW"))
                assertEquals(arm && !throws, reasons.contains("ANDROID_LAUNCH_RETURNED"))
                assertFalse(reasons.contains("CARRIER_OBSERVED"))
                if (arm) {
                    assertTrue(journal.recordLaunchMilestone(command, LaunchMilestone.CARRIER_OBSERVED))
                    assertFalse(journal.recordLaunchMilestone(command, LaunchMilestone.CARRIER_OBSERVED))
                    assertEquals(id, journal.unresolvedId())
                }
                try { journal.create(PaymentRequest("other@upi", "", "2.00")); fail("Duplicate") } catch (_: IllegalStateException) { }
            }
        } finally {
            withContext(Dispatchers.Main) { AccessibilityRuntime.connection.disconnect(host); AccessibilityRuntime.revokeSession() }
            SecureTestStores.cleanupJournal(base, db); SecureTestStores.cleanupPreferences(base, name)
        }
    }
}

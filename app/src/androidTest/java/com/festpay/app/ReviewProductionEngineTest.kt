package com.festpay.app

import android.content.*
import android.os.Bundle
import android.os.ResultReceiver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.screens.*
import com.festpay.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real Review, ViewModel, transport and engine; synthetic stores/host; dialer intent intercepted. */
class ReviewProductionEngineTest {
    @get:Rule val compose = createComposeRule()
    @Test fun payUsesFrozenCommandArmsOnceAndRequestsOnlyOneInterceptedDial() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_review_engine_${UUID.randomUUID()}"
        val db = "$name.db"
        val action = "com.festpay.app.TEST_REVIEW_${UUID.randomUUID()}"
        val journals = mutableListOf<PaymentAttemptJournal>()
        var launches = 0; var arms = 0
        var captured: OwnedUssdCommand? = null
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
            override fun startActivity(intent: Intent) {
                // Never delegate to Android: even an unexpected intent cannot launch anything.
                assertEquals(Intent.ACTION_CALL, intent.action)
                assertEquals("tel", intent.data?.scheme)
                assertEquals("*99#", intent.data?.schemeSpecificPart)
                launches++
            }
        }
        val host = object : UssdSessionHost {
            override fun connectionAlive() = true
            override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String): Boolean {
                PaymentAttemptJournal(base, db).use {
                    assertEquals(command.attemptId, it.unresolvedId())
                    assertEquals(PaymentState.PROCESSING, it.records().single().state)
                    assertEquals("frozen@upi", it.records().single().payeeVpa)
                    assertEquals("2.50", it.records().single().amount)
                }
                assertTrue(AccessibilityRuntime.connection.arm(this, command, connectionId))
                captured = command; arms++
                return true
            }
        }
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
        val model = PaymentViewModel({ PaymentAttemptJournal(base, db).also(journals::add) },
            { TransactionRepository(base, name) }, { UssdSessionTransport(action) })
        try {
            compose.runOnIdle {
                AccessibilityConsent.set(context, true)
                AccessibilityRuntime.connection.connect(host)
                FestPayAccessibilityService.UssdSessionData.operationActive = false
                SharedPaymentState.selectRecipient("frozen@upi", "2.50", "Local note", "Unverified QR Name")
            }
            compose.setContent {
                val resultOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
                CompositionLocalProvider(LocalContext provides context,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides checkNotNull(resultOwner)) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "review") {
                        composable("review") { ReviewPaymentScreen(nav, model) }
                        composable("payment_status_unclear") { androidx.compose.material3.Text("UNKNOWN fixture destination") }
                        composable("payment_journey") { CarrierPaymentJourneyScreen(nav, model) }
                    }
                }
            }
            compose.onNodeWithText("Pay ₹2.50").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(10_000) { model.lastResult != null }
            compose.runOnIdle {
                assertEquals(1, launches); assertEquals(1, arms)
                assertEquals("frozen@upi", captured!!.recipient); assertEquals("2.50", captured!!.amount)
                assertEquals(model.selectedAttemptId, captured!!.attemptId)
                assertEquals(PaymentState.UNKNOWN, model.paymentState)
                assertEquals(captured!!.attemptId, model.lastResult!!.localReference)
                model.startPayment("other@upi", "", "9.00", context, { true }) { }
                assertEquals(1, launches); assertEquals(1, arms)
                PaymentAttemptJournal(base, db).use {
                    assertEquals(1, it.records().size)
                    assertEquals(PaymentState.UNKNOWN, it.records().single().state)
                    assertEquals(captured!!.attemptId, it.unresolvedId())
                }
            }
        } finally {
            context.unregisterReceiver(receiver)
            compose.runOnIdle { AccessibilityRuntime.connection.disconnect(host); AccessibilityRuntime.revokeSession(); SharedPaymentState.clear() }
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(base, db); SecureTestStores.cleanupPreferences(base, name)
        }
    }
}


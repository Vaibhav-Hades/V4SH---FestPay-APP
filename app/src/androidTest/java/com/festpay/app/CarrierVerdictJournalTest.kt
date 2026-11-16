package com.festpay.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.screens.TransactionDetailsScreen
import com.festpay.app.ui.viewmodel.*
import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Synthetic journals only: the carrier's final message closes the attempt and shows clearly in history. */
class CarrierVerdictJournalTest {
    @get:Rule val compose = createComposeRule()
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun seed(db: String): String = PaymentAttemptJournal(base, db).use { j ->
        val id = j.create(PaymentRequest("synthetic@upi", "", "1.00"))
        j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH"); j.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN"); id
    }

    @Test fun aCarrierSuccessClosesTheAttemptStoresTheReferenceAndUnlocksTheNextPayment() {
        val name = "test_verdict_${UUID.randomUUID()}.db"
        try {
            val id = seed(name)
            PaymentAttemptJournal(base, name).use { j ->
                assertEquals(id, j.unresolvedId())
                assertTrue(j.resolveByCarrier(id, CarrierOutcome.SUCCESS, "214749357315"))
                assertFalse("idempotent", j.resolveByCarrier(id, CarrierOutcome.SUCCESS, "214749357315"))
                try { j.resolveByCarrier(id, CarrierOutcome.FAILED, null); fail("A conflicting verdict must be refused") } catch (_: IllegalStateException) { }
                assertNull(j.unresolvedId())
                val r = j.records().single()
                assertEquals("SUCCESS", r.carrierOutcome); assertEquals("214749357315", r.bankReference); assertEquals("CARRIER_MESSAGE", r.referenceProvenance)
                assertEquals(PaymentState.UNKNOWN, r.state)
                assertEquals(2, run { j.create(PaymentRequest("another@upi", "", "2.00")); j.records().size })
            }
            PaymentAttemptJournal(base, name).use { j -> j.recover(); assertEquals("SUCCESS", j.records().last().carrierOutcome) } // survives restart
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    @Test fun aCarrierFailureClosesTheAttemptWithoutAReference() {
        val name = "test_verdict_${UUID.randomUUID()}.db"
        try {
            val id = seed(name)
            PaymentAttemptJournal(base, name).use { j ->
                assertTrue(j.resolveByCarrier(id, CarrierOutcome.FAILED, null))
                val r = j.records().single(); assertEquals("FAILED", r.carrierOutcome); assertNull(r.bankReference); assertNull(j.unresolvedId())
            }
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    @Test fun aSuccessWithoutAReferenceAMalformedReferenceOrAnOpenNonUnknownAttemptIsRefused() {
        val name = "test_verdict_${UUID.randomUUID()}.db"
        try {
            val id = seed(name)
            PaymentAttemptJournal(base, name).use { j ->
                try { j.resolveByCarrier(id, CarrierOutcome.SUCCESS, null); fail() } catch (_: IllegalArgumentException) { }
                try { j.resolveByCarrier(id, CarrierOutcome.SUCCESS, "12; DROP"); fail() } catch (_: IllegalArgumentException) { }
                assertEquals(id, j.unresolvedId()); assertNull(j.records().single().carrierOutcome)
            }
            val other = "test_verdict_${UUID.randomUUID()}.db"
            try {
                PaymentAttemptJournal(base, other).use { j ->
                    val fresh = j.create(PaymentRequest("synthetic@upi", "", "1.00")) // INITIATED
                    try { j.resolveByCarrier(fresh, CarrierOutcome.FAILED, null); fail() } catch (_: IllegalStateException) { }
                    assertEquals(fresh, j.unresolvedId())
                }
            } finally { SecureTestStores.cleanupJournal(base, other) }
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    @Test fun anAttemptTheOwnerAlreadyResolvedIsNotOverwrittenByTheCarrier() {
        val name = "test_verdict_${UUID.randomUUID()}.db"
        try {
            val id = seed(name)
            PaymentAttemptJournal(base, name).use { j ->
                assertTrue(j.resolveByOwner(id, OwnerResolution.NOT_DEBITED))
                try { j.resolveByCarrier(id, CarrierOutcome.SUCCESS, "214749357315"); fail() } catch (_: IllegalStateException) { }
                assertNull(j.records().single().carrierOutcome)
            }
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    @Test fun detailsShowTheCarrierVerdictAndReferenceClearlyAndOfferNoManualResolveControl() {
        val name = "test_verdict_${UUID.randomUUID()}"; val db = "$name.db"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val id = seed(db)
            PaymentAttemptJournal(base, db).use { it.resolveByCarrier(id, CarrierOutcome.SUCCESS, "214749357315") }
            val model = PaymentViewModel({ PaymentAttemptJournal(it, db) }, { TransactionRepository(it, name) }, { UssdSessionTransport() })
            compose.runOnIdle { model.initRepository(context); model.selectedAttemptId = id }
            compose.setContent { CompositionLocalProvider(LocalContext provides context) { TransactionDetailsScreen(rememberNavController(), model) } }
            compose.onNodeWithText("Successful · confirmed by carrier").assertExists()
            compose.onNodeWithText("Transaction reference: 214749357315 (printed by the carrier)").assertExists()
            compose.onNodeWithText("Taken from the carrier's own final message. V4SH does not query your bank.").assertExists()
            compose.onNodeWithText("Resolve this attempt").assertDoesNotExist()
        } finally { SecureTestStores.cleanupJournal(base, db); SecureTestStores.cleanupPreferences(base, name) }
    }
}

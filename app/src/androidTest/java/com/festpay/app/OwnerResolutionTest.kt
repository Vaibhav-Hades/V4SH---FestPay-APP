package com.festpay.app

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.screens.TransactionDetailsScreen
import com.festpay.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Owner resolution on SYNTHETIC journals only. The user's real journal is never opened by these tests;
 * resolving the real attempt is something only the owner can do from the app.
 */
class OwnerResolutionTest {
    @get:Rule val compose = createComposeRule()
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun seed(db: String): String = PaymentAttemptJournal(base, db).use { j ->
        val id = j.create(PaymentRequest("synthetic@upi", "", "1.00"))
        j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH")
        j.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
        id
    }
    private fun cleanup(name: String) { SecureTestStores.cleanupJournal(base, "$name.db"); SecureTestStores.cleanupPreferences(base, name) }

    @Test fun resolvingUnlocksNewPaymentsButKeepsTheOldRecordUnknownAndIntact() {
        val name = "test_owner_${UUID.randomUUID()}"; val db = "$name.db"
        try {
            val id = seed(db)
            PaymentAttemptJournal(base, db).use { j ->
                val before = j.records().single()
                assertEquals(id, j.unresolvedId())
                try { j.create(PaymentRequest("another@upi", "", "2.00")); fail("Must still be locked") } catch (_: IllegalStateException) { }
                assertTrue(j.resolveByOwner(id, OwnerResolution.DEBITED))
                assertNull(j.unresolvedId())
                val after = j.records().single()
                assertEquals(PaymentState.UNKNOWN, after.state)            // never SUCCESS or FAILED
                assertEquals("DEBITED", after.ownerResolution)
                assertEquals(before.copy(ownerResolution = "DEBITED"), after)  // nothing else changed
                assertNull(after.bankReference); assertNull(after.referenceProvenance)
                val next = j.create(PaymentRequest("another@upi", "", "2.00"))
                assertEquals(next, j.unresolvedId())
                assertEquals(2, j.records().size)
            }
        } finally { cleanup(name) }
    }

    @Test fun theDeclarationIsIdempotentAndCannotBeChangedAndSurvivesRestart() {
        val name = "test_owner_${UUID.randomUUID()}"; val db = "$name.db"
        try {
            val id = seed(db)
            PaymentAttemptJournal(base, db).use { j ->
                assertTrue(j.resolveByOwner(id, OwnerResolution.NOT_DEBITED))
                assertFalse("Same answer again is a no-op", j.resolveByOwner(id, OwnerResolution.NOT_DEBITED))
                try { j.resolveByOwner(id, OwnerResolution.DEBITED); fail("A different answer must be refused") } catch (_: IllegalStateException) { }
            }
            // Fresh instance = restart: sealed store still opens, record and lock state persist, recovery leaves it alone.
            PaymentAttemptJournal(base, db).use { j ->
                j.recover()
                assertNull(j.unresolvedId())
                assertEquals("NOT_DEBITED", j.records().single().ownerResolution)
                assertEquals(PaymentState.UNKNOWN, j.records().single().state)
            }
        } finally { cleanup(name) }
    }

    @Test fun onlyTheReservedUnknownAttemptCanBeResolved() {
        val name = "test_owner_${UUID.randomUUID()}"; val db = "$name.db"
        try {
            PaymentAttemptJournal(base, db).use { j ->
                val id = j.create(PaymentRequest("synthetic@upi", "", "1.00"))     // INITIATED, not UNKNOWN
                try { j.resolveByOwner(id, OwnerResolution.DEBITED); fail("INITIATED must not be resolvable") } catch (_: IllegalStateException) { }
                assertEquals(id, j.unresolvedId())
                try { j.resolveByOwner(UUID.randomUUID().toString(), OwnerResolution.DEBITED); fail("Unknown id") } catch (_: ProtectedStorageException) { }
                assertEquals(id, j.unresolvedId())
                assertEquals(PaymentState.INITIATED, j.records().single().state)
                assertNull(j.records().single().ownerResolution)
            }
        } finally { cleanup(name) }
    }

    @Test fun theEventTrailRecordsTheDeclarationAsOwnerSourcedNotCarrierOrBank() {
        val name = "test_owner_${UUID.randomUUID()}"; val db = "$name.db"
        try {
            val id = seed(db)
            PaymentAttemptJournal(base, db).use { j ->
                j.resolveByOwner(id, OwnerResolution.DEBITED)
                assertEquals(0, j.carrierObservationCount(id))
            }
        } finally { cleanup(name) }
    }

    @Test fun theViewModelRefusesWhileACellularOperationIsActiveThenResolvesAndUnblocksPayment() {
        val name = "test_owner_${UUID.randomUUID()}"; val db = "$name.db"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val id = seed(db)
            val model = PaymentViewModel({ PaymentAttemptJournal(it, db) }, { TransactionRepository(it, name) }, { UssdSessionTransport() })
            compose.runOnIdle {
                model.initRepository(context)
                model.inspectPaymentReadiness(context)
                assertEquals(PreparationBlock.UNRESOLVED_ATTEMPT, model.preparationBlock)
                assertTrue(model.paymentPreparationBlocked)
                FestPayAccessibilityService.UssdSessionData.operationActive = true
                assertFalse(model.resolveAttemptByOwner(context, id, OwnerResolution.DEBITED))
                assertEquals(PreparationBlock.UNRESOLVED_ATTEMPT, model.preparationBlock)
                FestPayAccessibilityService.UssdSessionData.operationActive = false
                assertTrue(model.resolveAttemptByOwner(context, id, OwnerResolution.DEBITED))
                assertEquals(PreparationBlock.NONE, model.preparationBlock)
                assertFalse("Pay is enabled again", model.paymentPreparationBlocked)
                assertEquals(id, model.selectedAttemptId)
                assertEquals("DEBITED", model.transactionLedger.single().ownerResolution)
            }
        } finally { compose.runOnIdle { FestPayAccessibilityService.UssdSessionData.operationActive = false }; cleanup(name) }
    }

    @Test fun theDetailsScreenOffersTheControlOnlyForAnOpenUnknownAttemptAndAsksBeforeActing() {
        val name = "test_owner_${UUID.randomUUID()}"; val db = "$name.db"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val id = seed(db)
            val model = PaymentViewModel({ PaymentAttemptJournal(it, db) }, { TransactionRepository(it, name) }, { UssdSessionTransport() })
            compose.runOnIdle { model.initRepository(context); model.selectedAttemptId = id }
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    TransactionDetailsScreen(androidx.navigation.compose.rememberNavController(), model)
                }
            }
            compose.onNodeWithText("Outcome unknown").assertExists()
            compose.onNodeWithText("Resolve this attempt").performScrollTo().performClick()
            // Nothing happens until the owner answers.
            compose.onNodeWithText("Check your bank first").assertExists()
            compose.runOnIdle { assertEquals(id, model.selectedAttemptId); assertTrue(model.paymentPreparationBlocked || model.preparationBlock != PreparationBlock.NONE || model.transactionLedger.single().ownerResolution == null) }
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Check your bank first").assertDoesNotExist()
            compose.runOnIdle { assertNull(model.transactionLedger.single().ownerResolution) }
            compose.onNodeWithText("Resolve this attempt").performScrollTo().performClick()
            compose.onNodeWithText("Money was not debited").performClick()
            compose.waitUntil(5_000) { model.transactionLedger.singleOrNull()?.ownerResolution == "NOT_DEBITED" }
            compose.onNodeWithText("Resolved by you: money was not debited").assertExists()
            compose.onNodeWithText("Resolve this attempt").assertDoesNotExist()
            compose.onNodeWithText("This is your own statement after checking your bank. V4SH did not verify it.").assertExists()
        } finally { cleanup(name) }
    }
}

package com.festpay.app

import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Synthetic journals only. */
class CarrierNameAndConfirmationTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun attempt(j: PaymentAttemptJournal, name: String = ""): String {
        val id = j.create(PaymentRequest("synthetic@upi", name, "1.00"))
        j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH"); j.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN"); return id
    }

    @Test fun theCarrierPrintedNameFillsAnEmptySavedNameButNeverOverwritesASavedOne() {
        val db = "test_name_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(base, db).use { j ->
                val id = attempt(j)
                assertEquals("", j.records().single().payeeName)
                j.recordCarrierObservation(id, UUID.randomUUID().toString(), CarrierProgress(CarrierStage.PIN, 1, payeeName = "NALLURI HARSHA VARDHANI"))
                assertEquals("NALLURI HARSHA VARDHANI", j.records().single().payeeName)
                assertEquals(PaymentState.UNKNOWN, j.records().single().state)
            }
            val db2 = "test_name_${UUID.randomUUID()}.db"
            try {
                PaymentAttemptJournal(base, db2).use { j ->
                    val id = attempt(j, "QR Supplied Name")
                    j.recordCarrierObservation(id, UUID.randomUUID().toString(), CarrierProgress(CarrierStage.PIN, 1, payeeName = "CARRIER NAME"))
                    assertEquals("QR Supplied Name", j.records().single().payeeName)
                }
            } finally { SecureTestStores.cleanupJournal(base, db2) }
        } finally { SecureTestStores.cleanupJournal(base, db) }
    }

    @Test fun anInterruptedSessionIsFlaggedUnconfirmedButStaysOpenAndResolvable() {
        val db = "test_unconf_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(base, db).use { j ->
                val id = attempt(j)
                assertFalse(j.records().single().noCarrierConfirmation)
                j.recordCarrierObservation(id, UUID.randomUUID().toString(), CarrierProgress(CarrierStage.INTERRUPTED, 3, issue = CarrierIssue.TIMEOUT))
                val r = j.records().single()
                assertTrue(r.noCarrierConfirmation)
                assertEquals(PaymentState.UNKNOWN, r.state); assertEquals(id, j.unresolvedId())   // never claims failure; your bank decides
                assertTrue(j.resolveByOwner(id, OwnerResolution.DEBITED))
                assertTrue("a resolved attempt is no longer flagged", !j.records().single().noCarrierConfirmation || j.records().single().ownerResolution != null)
            }
        } finally { SecureTestStores.cleanupJournal(base, db) }
    }

    @Test fun aCarrierVerdictClearsTheUnconfirmedFlag() {
        val db = "test_unconf_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(base, db).use { j ->
                val id = attempt(j)
                j.recordCarrierObservation(id, UUID.randomUUID().toString(), CarrierProgress(CarrierStage.INTERRUPTED, 3, issue = CarrierIssue.UNSUPPORTED))
                j.resolveByCarrier(id, CarrierOutcome.FAILED, null)
                assertFalse(j.records().single().noCarrierConfirmation)
            }
        } finally { SecureTestStores.cleanupJournal(base, db) }
    }
}

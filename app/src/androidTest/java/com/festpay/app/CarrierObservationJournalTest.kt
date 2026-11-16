package com.festpay.app

import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CarrierObservationJournalTest {
    @Test fun operationalEventsDeduplicateWithoutSettlingOrReleasingReservation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_carrier_events_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(context, name).use { journal ->
                val id = journal.create(PaymentRequest("synthetic@upi", "", "1.00"))
                journal.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
                val original = journal.records().single()
                val session = UUID.randomUUID().toString()
                val canary = "SYNTHETIC_AUTH_CANARY_123456"
                val progress = CarrierProgress(CarrierStage.RESULT, 10,
                    review = CarrierReview("synthetic@upi", "1.00", canary),
                    resultWording = CarrierResultObservation.SUCCESS_WORDING)
                assertTrue(journal.recordCarrierObservation(id, session, progress))
                assertFalse(journal.recordCarrierObservation(id, session, progress))
                assertEquals(1, journal.carrierObservationCount(id))
                // Only the display-only carrier wording may differ; identity, state and every financial field are identical.
                assertEquals(original.copy(carrierResult = "SUCCESS_WORDING"), journal.records().single())
                assertEquals(id, journal.unresolvedId())
                // Inspect decrypted synthetic rows: encryption alone would not prove exclusion.
                val cipher = KeystoreRecordCipher("journal:$name")
                journal.readableDatabase.rawQuery("SELECT row_key,payload FROM events", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val plain = cipher.open(cursor.getBlob(1), "events:${cursor.getString(0)}").toString(Charsets.UTF_8)
                        assertFalse(plain.contains(canary))
                        assertFalse(plain.contains("synthetic@upi"))
                    }
                }
                try { journal.transition(id, PaymentState.SUCCESS, "TEST_WORDING"); fail("No authority") }
                catch (_: IllegalStateException) { }
            }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
    @Test fun observationsForWrongAttemptAreRejectedAndRecoveryNeverResubmits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_carrier_owner_${UUID.randomUUID()}.db"
        try {
            val id = PaymentAttemptJournal(context, name).use { journal ->
                val id = journal.create(PaymentRequest("synthetic@upi", "", "1.00"))
                journal.transition(id, PaymentState.PROCESSING, "TEST_START")
                try { journal.recordCarrierObservation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), CarrierProgress()); fail("Wrong attempt") }
                catch (_: IllegalStateException) { }
                assertEquals(0, journal.carrierObservationCount(id))
                id
            }
            PaymentAttemptJournal(context, name).use { journal ->
                journal.recover()
                assertEquals(id, journal.unresolvedId()); assertEquals(PaymentState.UNKNOWN, journal.records().single().state)
                assertEquals(0, journal.carrierObservationCount(id))
            }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
}

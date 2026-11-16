package com.festpay.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Synthetic journals only. */
class CarrierReferenceHistoryTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun theCarriersPrintedReferenceIsStoredAndShownAsCarrierReportedWithoutChangingTheFinancialState() {
        val name = "test_ref_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(base, name).use { j ->
                val id = j.create(PaymentRequest("synthetic@upi", "", "1.00"))
                j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH")
                assertNull(j.records().single().bankReference)
                val session = UUID.randomUUID().toString()
                j.recordCarrierObservation(id, session, CarrierProgress(CarrierStage.RESULT, 4,
                    resultWording = CarrierResultObservation.SUCCESS_WORDING, reference = "214749357315"))
                val r = j.records().single()
                assertEquals("214749357315", r.bankReference)
                assertEquals("CARRIER_MESSAGE", r.referenceProvenance)
                assertEquals("SUCCESS_WORDING", r.carrierResult)
                assertEquals(PaymentState.PROCESSING, r.state)
                assertEquals(id, j.unresolvedId())
            }
            PaymentAttemptJournal(base, name).use { assertEquals("214749357315", it.records().single().bankReference) } // survives restart
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    @Test fun aMalformedReferenceIsNeverStored() {
        val name = "test_ref_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(base, name).use { j ->
                val id = j.create(PaymentRequest("synthetic@upi", "", "1.00"))
                j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH")
                j.recordCarrierObservation(id, UUID.randomUUID().toString(), CarrierProgress(CarrierStage.RESULT, 1, reference = "12; DROP"))
                assertNull(j.records().single().bankReference)
            }
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }

    /** Measures what each repeated carrier frame used to cost when the service re-opened and re-read the journal. */
    @Test fun journalReadCostPerFrameIsMeasuredForTheRecord() {
        val name = "test_cost_${UUID.randomUUID()}.db"
        try {
            val id = PaymentAttemptJournal(base, name).use { j ->
                val id = j.create(PaymentRequest("synthetic@upi", "", "1.00")); j.transition(id, PaymentState.PROCESSING, "TEST_LAUNCH")
                val s = UUID.randomUUID().toString()
                for (i in 1..12) j.recordCarrierObservation(id, s, CarrierProgress(CarrierStage.values()[i % 8], i.toLong()))
                id
            }
            val samples = (1..20).map {
                val t = System.nanoTime()
                PaymentAttemptJournal(base, name).use { j -> j.unresolvedId(); j.records() }
                (System.nanoTime() - t) / 1_000_000.0
            }
            Log.i("V4SH_BENCH", "open+unresolvedId+records ms: first=${samples.first()} median=${samples.sorted()[10]} max=${samples.max()} (id=${id.take(8)})")
            val c = KeystoreRecordCipher("journal:$name")
            val exists = (1..20).map { val t = System.nanoTime(); c.exists(); (System.nanoTime() - t) / 1_000_000.0 }
            Log.i("V4SH_BENCH", "keystore exists() ms: median=${exists.sorted()[10]} max=${exists.max()}")
            assertTrue(samples.isNotEmpty())
        } finally { SecureTestStores.cleanupJournal(base, name) }
    }
}

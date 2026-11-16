package com.festpay.app

import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.ui.viewmodel.PaymentRequest
import com.festpay.app.ui.viewmodel.PaymentState
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PaymentJournalTest {
    @Test fun initiatedRecoveryDuplicateEventsAndIdentityCollisionFailClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_state_${UUID.randomUUID()}.db"
        val fixedId = UUID.randomUUID().toString()
        try {
            PaymentAttemptJournal(context, name, { fixedId }).use { journal ->
                val request = PaymentRequest("test@upi", "Test", "1.00")
                val id = journal.create(request)
                journal.transition(id, PaymentState.PROCESSING, "TEST_ARM")
                val eventCount = journal.readableDatabase.rawQuery("SELECT COUNT(*) FROM events", null).use { it.moveToFirst(); it.getInt(0) }
                journal.transition(id, PaymentState.PROCESSING, "TEST_DUPLICATE")
                assertEquals(eventCount, journal.readableDatabase.rawQuery("SELECT COUNT(*) FROM events", null).use { it.moveToFirst(); it.getInt(0) })
                for (forbidden in listOf(PaymentState.SUCCESS, PaymentState.FAILED, PaymentState.CANCELLED)) {
                    try { journal.transition(id, forbidden, "TEST_FORBIDDEN"); fail("Forbidden transition") } catch (_: IllegalStateException) { }
                    assertEquals(id, journal.unresolvedId())
                }
            }
            PaymentAttemptJournal(context, name, { fixedId }).use { restored ->
                restored.recover()
                assertEquals(PaymentState.UNKNOWN, restored.records().single().state)
                assertEquals(fixedId, restored.unresolvedId())
            }
            val collisionName = "test_collision_${UUID.randomUUID()}.db"
            try {
                PaymentAttemptJournal(context, collisionName, { fixedId }).use { store ->
                    val request = PaymentRequest("test@upi", "Test", "1.00")
                    store.create(request)
                    store.transition(fixedId, PaymentState.CANCELLED, "TEST_CANCEL")
                    try { store.create(request); fail("Identity cannot be reused") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
                    assertEquals(1, store.records().size)
                    assertNull(store.unresolvedId())
                }
            } finally { SecureTestStores.cleanupJournal(context, collisionName) }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
    @Test fun interruptedInitiatedAttemptRecoversAsUnknown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_initiated_${UUID.randomUUID()}.db"
        try {
            val id = PaymentAttemptJournal(context, name).use { it.create(PaymentRequest("test@upi", "Test", "1.00")) }
            PaymentAttemptJournal(context, name).use {
                it.recover()
                assertEquals(PaymentState.UNKNOWN, it.records().single().state)
                assertEquals(id, it.unresolvedId())
            }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
    @Test fun storedTerminalLabelWithoutProviderEvidenceCannotBecomeConfirmedHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (label in listOf("SUCCESS", "FAILED")) {
            val name = "test_terminal_${UUID.randomUUID()}.db"
            try {
                val id = SecureTestStores.legacyJournal(context, name, label)
                PaymentAttemptJournal(context, name).use { journal ->
                    assertEquals(PaymentState.UNKNOWN, journal.records().single().state)
                    assertEquals(id, journal.unresolvedId())
                }
            } finally { SecureTestStores.cleanupJournal(context, name) }
        }
    }
    @Test fun durableReservationSurvivesReopenAndRecovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_journal_${UUID.randomUUID()}.db"
        try {
            val first = PaymentAttemptJournal(context, name)
            val id = first.create(PaymentRequest("test@upi", "Test", "1.00"))
            assertEquals(PaymentState.INITIATED, first.records().single().state)
            first.transition(id, PaymentState.PROCESSING, "TEST_ARM")
            first.close()
            val recovered = PaymentAttemptJournal(context, name)
            recovered.recover()
            assertEquals(id, recovered.unresolvedId())
            assertEquals(PaymentState.UNKNOWN, recovered.records().single().state)
            try { recovered.create(PaymentRequest("other@upi", "Other", "2.00")); fail("Duplicate execution must be blocked") }
            catch (_: IllegalStateException) { }
            recovered.close()
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }

    @Test fun cancellationBeforeExecutionReleasesReservationWithoutReusingIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_journal_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(context, name).use { journal ->
                val request = PaymentRequest("test@upi", "Test", "1.00")
                val old = journal.create(request)
                journal.transition(old, PaymentState.CANCELLED, "TEST_CANCEL")
                assertNull(journal.unresolvedId())
                assertNotEquals(old, journal.create(request))
            }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
}

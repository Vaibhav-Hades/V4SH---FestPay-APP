package com.festpay.app

import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentRequest
import com.festpay.app.ui.viewmodel.PaymentState
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class JournalBoundaryTest {
    private class InterruptedWrite : RuntimeException()
    @Test fun everyWriteBoundaryRestoresUnknownOrBlocksWithoutReexecution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PaymentRequest("synthetic@upi", "Synthetic boundary", "1.00")
        for (boundary in JournalBoundary.entries) {
            val name = "test_boundary_${UUID.randomUUID()}.db"
            try {
                val id = PaymentAttemptJournal(context, name).use { it.create(request) }
                PaymentAttemptJournal(context, name, faultInjector = { if (it == boundary) throw InterruptedWrite() }).use {
                    try { it.transition(id, PaymentState.PROCESSING, "TEST_ARM"); fail("Fault did not fire") }
                    catch (_: InterruptedWrite) { }
                }
                PaymentAttemptJournal(context, name).use { restored ->
                    if (boundary in listOf(JournalBoundary.AFTER_SEAL_BEFORE_COMMIT, JournalBoundary.AFTER_COMMIT_BEFORE_FINAL_SEAL)) {
                        try { restored.records(); fail("Interrupted seal must block") } catch (_: ProtectedStorageException) { }
                        try { restored.create(request); fail("No reexecution after interrupted seal") } catch (_: ProtectedStorageException) { }
                    } else {
                        restored.recover()
                        assertEquals(PaymentState.UNKNOWN, restored.records().single().state)
                        assertEquals(id, restored.unresolvedId())
                        try { restored.create(request); fail("No reexecution after recovery") } catch (_: IllegalStateException) { }
                    }
                    assertEquals(1, restored.readableDatabase.rawQuery("SELECT COUNT(*) FROM attempts", null).use { it.moveToFirst(); it.getInt(0) })
                    assertEquals(1, restored.readableDatabase.rawQuery("SELECT COUNT(*) FROM reservation", null).use { it.moveToFirst(); it.getInt(0) })
                }
            } finally { SecureTestStores.cleanupJournal(context, name) }
        }
    }
    @Test fun staleVersionIsRejectedWithoutStateChange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_version_${UUID.randomUUID()}.db"
        try {
            PaymentAttemptJournal(context, name).use {
                val id = it.create(PaymentRequest("synthetic@upi", "Synthetic", "1.00"))
                try { it.transition(id, PaymentState.PROCESSING, "TEST_STALE", expectedVersion = 9); fail("Stale version accepted") }
                catch (_: IllegalStateException) { }
                assertEquals(PaymentState.INITIATED, it.records().single().state)
                assertEquals(id, it.unresolvedId())
            }
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }
}

package com.festpay.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.TransactionRepository
import com.festpay.app.ui.viewmodel.PaymentRequest
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.PaymentViewModel
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PaymentHistoryTest {
    @Test fun historyRefreshReadsSavedStateAndRetainsRecordsOnReadFailure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suffix = UUID.randomUUID().toString()
        val databaseName = "test_history_$suffix.db"
        val preferencesName = "test_history_$suffix"
        val journals = mutableListOf<PaymentAttemptJournal>()
        val modelStore = ViewModelStore()
        try {
            val id = UUID.randomUUID().toString()
            PaymentAttemptJournal(context, databaseName, { id }).use { writer ->
                val model = PaymentViewModel(
                    journalFactory = { PaymentAttemptJournal(it, databaseName).also { journals.add(it) } },
                    repositoryFactory = { TransactionRepository(it, preferencesName) }
                )
                instrumentation.runOnMainSync {
                    modelStore.put("history", model)
                    model.refreshTransactionHistory(context)
                    assertTrue(model.transactionLedger.isEmpty())
                    writer.create(PaymentRequest("synthetic@upi", "Synthetic", "1.00"))
                    model.refreshTransactionHistory(context)
                    assertNull(model.historyError)
                    assertEquals(id, model.transactionLedger.single().localTransactionId)
                    assertEquals(PaymentState.INITIATED, model.transactionLedger.single().state)
                    Instant.parse(model.transactionLedger.single().timestamp)
                }
                writer.transition(id, PaymentState.PROCESSING, "TEST_ARM")
                writer.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
                instrumentation.runOnMainSync {
                    model.refreshTransactionHistory(context)
                    assertEquals(PaymentState.UNKNOWN, model.transactionLedger.single().state)
                    model.inspectPaymentReadiness(context)
                    assertTrue(model.paymentPreparationBlocked)
                    assertEquals(id, model.selectedAttemptId)
                }
                // A corrupt synthetic payload must produce an error, not erase the
                // previously displayed history or release the reservation.
                writer.writableDatabase.execSQL("UPDATE attempts SET payload=?", arrayOf(byteArrayOf(1, 2, 3)))
                instrumentation.runOnMainSync {
                    model.refreshTransactionHistory(context)
                    assertNotNull(model.historyError)
                    assertEquals(id, model.transactionLedger.single().localTransactionId)
                    assertEquals(PaymentState.UNKNOWN, model.transactionLedger.single().state)
                }
                try { writer.unresolvedId(); fail("Tampered journal must be unavailable") }
                catch (_: com.festpay.app.data.ProtectedStorageException) { }
                try { writer.create(PaymentRequest("other@upi", "Other", "2.00")); fail("Corruption must block another attempt") }
                catch (_: com.festpay.app.data.ProtectedStorageException) { }
                assertEquals(1, writer.readableDatabase.rawQuery("SELECT COUNT(*) FROM reservation", null).use { it.moveToFirst(); it.getInt(0) })
                assertEquals(1, writer.readableDatabase.rawQuery("SELECT COUNT(*) FROM attempts", null).use { it.moveToFirst(); it.getInt(0) })
            }
        } finally {
            instrumentation.runOnMainSync { modelStore.clear() }
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(context, databaseName)
            SecureTestStores.cleanupPreferences(context, preferencesName)
        }
    }
}

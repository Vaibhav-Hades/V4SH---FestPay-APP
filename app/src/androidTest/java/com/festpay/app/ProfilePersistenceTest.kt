package com.festpay.app

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.ReceiveQr
import com.festpay.app.data.TransactionRepository
import com.festpay.app.data.UpiQrParser
import com.festpay.app.ui.viewmodel.PaymentViewModel
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ProfilePersistenceTest {
    @Test fun validProfileSurvivesReloadAndInvalidSaveDoesNotReplaceIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suffix = UUID.randomUUID().toString()
        val databaseName = "test_profile_$suffix.db"
        val preferencesName = "test_profile_$suffix"
        val models = ViewModelStore()
        val journals = mutableListOf<PaymentAttemptJournal>()
        fun newModel() = PaymentViewModel(
            journalFactory = { PaymentAttemptJournal(it, databaseName).also { journals.add(it) } },
            repositoryFactory = { TransactionRepository(it, preferencesName) }
        )
        try {
            instrumentation.runOnMainSync {
                val first = newModel()
                models.put("first", first)
                assertFalse(first.saveUserProfile(context, "invalid", "Name"))
                assertNotNull(first.profileSaveError)
                assertFalse(context.getDatabasePath(databaseName).exists())
                assertTrue(first.saveUserProfile(context, "  synthetic@upi  ", "  Synthetic Name  "))
                assertEquals("synthetic@upi", first.userVpa)
                assertEquals("Synthetic Name", first.userName)
                assertNull(first.profileSaveError)
                assertFalse(first.saveUserProfile(context, "other@", "Other"))
                assertEquals("synthetic@upi", first.userVpa)

                val reloaded = newModel()
                models.put("reloaded", reloaded)
                reloaded.initRepository(context)
                assertEquals("synthetic@upi", reloaded.userVpa)
                assertEquals("Synthetic Name", reloaded.userName)
                val parsed = UpiQrParser.parse(checkNotNull(ReceiveQr.payload(reloaded.userVpa, reloaded.userName)))
                assertEquals("synthetic@upi", parsed!!.vpa)
                assertEquals("Synthetic Name", parsed.name)
                assertTrue(reloaded.transactionLedger.isEmpty())
                assertNull(reloaded.selectedAttemptId)
            }
            journals.forEach { assertNull(it.unresolvedId()) }
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(context, databaseName)
            SecureTestStores.cleanupPreferences(context, preferencesName)
        }
    }
}

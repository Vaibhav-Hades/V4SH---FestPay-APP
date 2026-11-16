package com.festpay.app

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.ui.viewmodel.PaymentRequest
import com.festpay.app.ui.viewmodel.PaymentState
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivityRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun recreationAndColdActivityRestartKeepUnknownAndReservation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val database = "test_recreate_$suffix.db"
        val preferences = "test_recreate_$suffix"
        try {
            val id = PaymentAttemptJournal(context, database).use {
                it.create(PaymentRequest("synthetic@upi", "Synthetic", "1.00")).also { id -> it.transition(id, PaymentState.PROCESSING, "TEST_ARM") }
            }
            val intent = Intent(context, RecoveryStateActivity::class.java).putExtra("database", database).putExtra("preferences", preferences)
            ActivityScenario.launch<RecoveryStateActivity>(intent).use { scenario ->
                compose.onNodeWithText("Saved outcome: UNKNOWN").assertIsDisplayed()
                compose.onNodeWithText("Reservation active: true").assertIsDisplayed()
                scenario.recreate()
                compose.onNodeWithText("Saved outcome: UNKNOWN").assertIsDisplayed()
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(10_000) {
                    var landscape = false
                    scenario.onActivity { landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                    landscape
                }
                compose.onNodeWithText("Saved outcome: UNKNOWN").assertIsDisplayed()
                scenario.onActivity {
                    assertEquals(id, it.model.selectedAttemptId); assertEquals(1, it.model.transactionLedger.size)
                    assertFalse(it.model.markCancelled())
                    assertEquals(PaymentState.UNKNOWN, it.model.paymentState)
                }
            }
            ActivityScenario.launch<RecoveryStateActivity>(intent).use {
                compose.onNodeWithText("Saved outcome: UNKNOWN").assertIsDisplayed()
                compose.onNodeWithText("Reservation active: true").assertIsDisplayed()
            }
            PaymentAttemptJournal(context, database).use {
                assertEquals(id, it.unresolvedId())
                assertEquals(1, it.records().size)
            }
        } finally {
            SecureTestStores.cleanupJournal(context, database)
            SecureTestStores.cleanupPreferences(context, preferences)
        }
    }
}

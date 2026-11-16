package com.festpay.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.TransactionRepository
import com.festpay.app.ui.screens.PaymentSuccessfulScreen
import com.festpay.app.ui.screens.UpiDetailsScreen
import com.festpay.app.ui.theme.V4SHTheme
import com.festpay.app.ui.viewmodel.PaymentViewModel
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FinancialUiGuardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun successRouteWithoutProviderEvidenceDisplaysUnclearInstead() {
        val model = PaymentViewModel()
        compose.setContent {
            V4SHTheme { PaymentSuccessfulScreen(rememberNavController(), model) }
        }
        compose.onNodeWithText("Payment status unclear").assertIsDisplayed()
        compose.onNodeWithText("Payment Successful").assertDoesNotExist()
        assertTrue(model.transactionLedger.isEmpty())
    }

    @Test fun invalidProfileNeverDisplaysSaved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val databaseName = "test_profile_ui_$suffix.db"
        val preferencesName = "test_profile_ui_$suffix"
        val journals = mutableListOf<PaymentAttemptJournal>()
        val model = PaymentViewModel(
            journalFactory = { PaymentAttemptJournal(it, databaseName).also { journals.add(it) } },
            repositoryFactory = { TransactionRepository(it, preferencesName) }
        )
        try {
            compose.setContent { V4SHTheme { UpiDetailsScreen(rememberNavController(), model) } }
            compose.onNodeWithText("Display Name").performTextInput("Synthetic")
            compose.onNodeWithText("UPI ID / VPA").performTextInput("invalid@")
            compose.onNodeWithText("Save UPI Details").performScrollTo().performClick()
            compose.onNodeWithText("UPI Details Saved").assertDoesNotExist()
            compose.onNodeWithText("Enter a valid UPI ID and a display name of 1–100 characters without control characters.").assertExists()
            compose.runOnIdle {
                assertNotNull(model.profileSaveError)
                assertEquals("", model.userVpa)
                assertTrue(model.transactionLedger.isEmpty())
            }
        } finally {
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(context, databaseName)
            SecureTestStores.cleanupPreferences(context, preferencesName)
        }
    }
}

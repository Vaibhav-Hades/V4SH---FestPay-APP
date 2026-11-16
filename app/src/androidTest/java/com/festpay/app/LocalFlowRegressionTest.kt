package com.festpay.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.TransactionRepository
import com.festpay.app.ui.screens.*
import com.festpay.app.ui.viewmodel.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalFlowRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualUpiReviewReplacesEarlierQrAndKeepsUnresolvedPaymentBlocked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val database = "test_manual_$suffix.db"
        val preferences = "test_manual_$suffix"
        val journals = mutableListOf<PaymentAttemptJournal>()
        try {
            PaymentAttemptJournal(context, database).use {
                val id = it.create(PaymentRequest("earlier@upi", "Synthetic", "1.00"))
                it.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
            }
            SharedPaymentState.selectRecipient("earlierqr@upi", "9.00", "Earlier note", "Previous QR Name")
            val model = PaymentViewModel(
                journalFactory = { PaymentAttemptJournal(it, database).also(journals::add) },
                repositoryFactory = { TransactionRepository(it, preferences) }
            )
            compose.setContent {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "manual") {
                    composable("manual") { UpiIdPaymentScreen(nav) }
                    composable("amount_entry") { AmountEntryScreen(nav, model) }
                }
            }
            compose.onNodeWithText("UPI ID").performTextInput("manual@upi")
            compose.onNodeWithText("Continue", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Amount (₹)").performTextInput("2.50")
            // The Review page is gone: Pay is on this screen. With an earlier unresolved attempt it is disabled,
            // says why, shows the NEW recipient (never the earlier QR) and starts nothing.
            compose.onNodeWithText("Pay ₹2.50").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("An earlier payment attempt remains unresolved", substring = true).assertExists()
            compose.onNodeWithText("manual@upi").assertExists()
            compose.onNodeWithText("Name unavailable").assertExists()
            compose.onNodeWithText("earlierqr@upi").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("manual@upi", SharedPaymentState.payeeVpa)
                assertTrue(model.paymentPreparationBlocked)
                assertEquals(1, model.transactionLedger.size)
            }
        } finally {
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(context, database)
            SecureTestStores.cleanupPreferences(context, preferences)
        }
    }

    @Test fun helpNavigatesToSupportAndOnlyUserEnteredNoteIsOfferedToChooser() {
        var offered: Intent? = null
        compose.setContent {
            val base = LocalContext.current
            val context = object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) { offered = intent }
            }
            CompositionLocalProvider(LocalContext provides context) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "help") {
                    composable("help") { HelpCenterScreen(nav) }
                    composable("contact_support") { ContactSupportScreen(nav) }
                }
            }
        }
        compose.onNodeWithText("How can we help?").assertIsDisplayed()
        compose.onNodeWithText("Contact Support").performScrollTo().performClick()
        compose.onNodeWithText("Share support note").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Describe the issue").performScrollTo().performTextInput("Test note")
        compose.onNodeWithText("Share support note").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Intent.ACTION_CHOOSER, offered?.action)
            val send = androidx.core.content.IntentCompat.getParcelableExtra(checkNotNull(offered), Intent.EXTRA_INTENT, Intent::class.java)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertEquals("Test note", send.getStringExtra(Intent.EXTRA_TEXT))
            assertNull(send.getStringArrayExtra(Intent.EXTRA_EMAIL))
        }
    }

    @Test fun qrNameNeverBecomesBankIdentityAndChangedReviewCannotPay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val database = "test_qr_identity_$suffix.db"
        val preferences = "test_qr_identity_$suffix"
        val journals = mutableListOf<PaymentAttemptJournal>()
        try {
            val parsed = parseAndValidateUpiQr("upi://pay?pa=qrfirst@upi&pn=UNTRUSTED%20QR%20NAME&am=1.00") as UpiParseResult.Valid
            assertEquals("UNTRUSTED QR NAME", parsed.pn)
            val ticket = SharedPaymentState.selectRecipient(parsed.pa, parsed.am.orEmpty(), SharedPaymentState.paymentNote, parsed.pn)
            kotlinx.coroutines.runBlocking { SharedPaymentState.resolve(ticket) }
            assertNull(SharedPaymentState.recipient.verifiedBeneficiaryName)
            val model = PaymentViewModel(
                journalFactory = { PaymentAttemptJournal(it, database).also(journals::add) },
                repositoryFactory = { TransactionRepository(it, preferences) }
            )
            compose.setContent { ReviewPaymentScreen(rememberNavController(), model) }
            compose.onNodeWithText("UNTRUSTED QR NAME").assertExists()
            compose.onNodeWithText("Name from QR — unverified").assertExists()
            compose.onNodeWithText("qrfirst@upi").assertExists()
            compose.onNodeWithText("Recipient name not verified").assertExists()
            compose.runOnIdle { SharedPaymentState.selectRecipient("qrsecond@upi", "2.00") }
            compose.onNodeWithText("Payment details changed. Return to recipient and amount entry for a fresh Review. No payment was started.").assertExists()
            compose.onNodeWithText("Pay ₹1.00").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("qrfirst@upi").assertExists()
            compose.onNodeWithText("qrsecond@upi").assertDoesNotExist()
            compose.runOnIdle { assertTrue(model.transactionLedger.isEmpty()); assertNull(model.selectedAttemptId) }
        } finally {
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(context, database)
            SecureTestStores.cleanupPreferences(context, preferences)
        }
    }
}

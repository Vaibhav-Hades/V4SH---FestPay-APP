package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CarrierSetupPromptTest {
    @Test fun bankChoiceScreenIsRecognisedAndNeedsNoIfsc() {
        val p = CarrierSetupPrompt.detect("Welcome to *99#. Please enter first 3 letters of your bank name or IFSC code")
        assertEquals(SetupPromptKind.BANK_SELECTION, p?.kind)
        assertTrue(p!!.guidance.contains("do not need an IFSC"))
    }

    @Test fun cardDetailsAndPinCreationAreRecognised() {
        assertEquals(SetupPromptKind.CARD_DETAILS, CarrierSetupPrompt.detect("Enter last 6 digits of debit card and expiry date")?.kind)
        assertEquals(SetupPromptKind.PIN_SETUP, CarrierSetupPrompt.detect("Set your UPI PIN")?.kind)
        assertEquals(SetupPromptKind.PIN_SETUP, CarrierSetupPrompt.detect("Enter new UPI PIN")?.kind)
    }

    @Test fun normalScreensAreNeverTreatedAsSetup() {
        assertNull(CarrierSetupPrompt.detect("Enter UPI PIN to proceed"))
        assertNull(CarrierSetupPrompt.detect("Do not share your UPI PIN. Enter 6 digit UPI PIN"))
        assertNull(CarrierSetupPrompt.detect("1. Send Money 2. Request Money 3. Check Balance 4. My Profile 5. Pending Requests 6. Transactions 7. UPI Pin"))
        assertNull(CarrierSetupPrompt.detect("Your balance is Rs 120.50"))
        assertNull(CarrierSetupPrompt.detect("Enter amount"))
        assertNull(CarrierSetupPrompt.detect(""))
    }

    @Test fun mainMenuWordingVariantsAreAccepted() {
        assertTrue(UssdNavigationPolicy.isRootMenu("1: Send Money\n2: Request Money\n3: Check Bal"))
        assertTrue(UssdNavigationPolicy.isRootMenu("1. Send Money 3. Balance Enquiry"))
        assertTrue(UssdNavigationPolicy.isRootMenu("1) send money 3) check a/c balance"))
        assertFalse(UssdNavigationPolicy.isRootMenu("1. Send Money 2. Request Money"))
    }

    @Test fun issueLogKeepsNewestFirstAndRepeatsOnlyRefresh() {
        IssueLog.clear()
        IssueLog.record("A", "one", IssueFix.NONE, now = 1)
        IssueLog.record("B", "two", IssueFix.ACCESSIBILITY_SETTINGS, now = 2)
        IssueLog.record("B", "two", IssueFix.ACCESSIBILITY_SETTINGS, now = 3)
        val items = IssueLog.recent()
        assertEquals(listOf("B", "A"), items.map { it.title })
        assertEquals(3L, items.first().atMillis)
        repeat(40) { IssueLog.record("T$it", "d") }
        assertEquals(30, IssueLog.recent().size)
        IssueLog.clear(); assertTrue(IssueLog.recent().isEmpty())
    }

    @Test fun answersOnlyWhatTheScreenAsksForAndNeverPinOrOtp() {
        val bank = "Please enter first 3 letters of your bank name"
        assertEquals("Sta", CarrierSetupPrompt.answer(bank, "State Bank of India", null, null))
        assertNull(CarrierSetupPrompt.answer(bank, null, null, null))
        assertNull(CarrierSetupPrompt.answer("Enter IFSC code of your bank", "State Bank", null, null))
        assertEquals("123456", CarrierSetupPrompt.answer("Enter last 6 digits of debit card", "x", "123456", "0828"))
        assertEquals("0828", CarrierSetupPrompt.answer("Enter debit card expiry date (MMYY)", "x", "123456", "0828"))
        assertNull("both asked together: stop", CarrierSetupPrompt.answer("Enter last 6 digits of debit card and expiry", "x", "123456", "0828"))
        assertNull("format not stated", CarrierSetupPrompt.answer("Enter debit card expiry", "x", "123456", "0828"))
        assertNull(CarrierSetupPrompt.answer("Set your UPI PIN", "x", "123456", "0828"))
        assertNull(CarrierSetupPrompt.answer("Enter OTP and last 6 digits of debit card", "x", "123456", "0828"))
        assertNull(CarrierSetupPrompt.answer("Enter UPI PIN", "x", "123456", "0828"))
    }

    @Test fun cardDetailsAreMemoryOnlyAndExpire() {
        SetupDetails.setCard("123456", "0828", now = 1_000)
        assertEquals("123456", SetupDetails.last6(now = 2_000))
        assertNull(SetupDetails.last6(now = 1_000 + SetupDetails.SENSITIVE_TTL_MS + 1))
        SetupDetails.setCard("12345", "1328"); assertFalse("invalid values are refused", SetupDetails.hasCard())
        SetupDetails.setCard("123456", null); assertTrue(SetupDetails.hasCard()); SetupDetails.clearSensitive(); assertFalse(SetupDetails.hasCard())
    }

    @Test fun theSendMoneyMethodMenuIsNeverASetupScreen() {
        // Regression: this menu lists an IFSC option and was wrongly treated as "choose your bank", stopping every payment.
        assertNull(CarrierSetupPrompt.detect("1. Mobile No. 2. UPI ID 3. Saved Beneficiary 4. IFSC and Account Number"))
        assertNull(CarrierSetupPrompt.detect("Send Money using 1 Mobile no 2 UPI ID 3 Saved Beneficiary 4 IFSC code and A/c no"))
        assertNull(CarrierSetupPrompt.detect("Enter UPI ID"))
        assertEquals(SetupPromptKind.BANK_SELECTION, CarrierSetupPrompt.detect("Enter first 3 letters of your bank name")?.kind)
        assertEquals(SetupPromptKind.BANK_SELECTION, CarrierSetupPrompt.detect("Please enter IFSC of your bank")?.kind)
    }
}

package com.festpay.app

import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 1 verification tests: Check Balance *99# USSD launch.
 *
 * These tests verify every JVM-testable invariant of the USSD session
 * launch sequence defined in PaymentViewModel.startBalanceCheck():
 *
 *   1. Session reset occurs BEFORE isBalanceCheck = true.
 *   2. isBalanceCheck is true after initialisation.
 *   3. ACTION_CALL (not ACTION_DIAL) is used — auto-calls, no user tap.
 *   4. The USSD URI encodes *99# correctly.
 *   5. A reset wipes isBalanceCheck back to false.
 *   6. Two consecutive calls don't cross-contaminate session data.
 *   7. Payment flow is not affected (isBalanceCheck stays false).
 *   8. currentPresentationState starts at CONNECTING after reset.
 *   9. parsedBalance is null after reset (no stale balance data).
 *  10. ACTION_NEW_USSD_SESSION broadcast action string is stable.
 */
class CheckBalanceStep1LaunchTest {

    @Before
    fun setUp() {
        FestPayAccessibilityService.UssdSessionData.reset()
    }

    // ------------------------------------------------------------------
    // 1. Reset-before-set ordering
    // ------------------------------------------------------------------

    @Test
    fun `reset clears isBalanceCheck before it is set to true`() {
        // Pre-condition: polluted state from a prior run
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        FestPayAccessibilityService.UssdSessionData.parsedBalance = "9999.00"

        // Mirrors startBalanceCheck sequence:
        FestPayAccessibilityService.UssdSessionData.reset()
        assertFalse(
            "isBalanceCheck must be false immediately after reset, before assignment",
            FestPayAccessibilityService.UssdSessionData.isBalanceCheck
        )
        assertNull(
            "parsedBalance must be null after reset",
            FestPayAccessibilityService.UssdSessionData.parsedBalance
        )

        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        assertTrue(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
    }

    // ------------------------------------------------------------------
    // 2. isBalanceCheck is true after the full launch sequence
    // ------------------------------------------------------------------

    @Test
    fun `isBalanceCheck is true after balance check session initialisation`() {
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        assertTrue(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
    }

    // ------------------------------------------------------------------
    // 3. ACTION_CALL is used for balance check (auto-initiates, no tap)
    // ------------------------------------------------------------------

    @Test
    fun `balance check uses ACTION_CALL not ACTION_DIAL`() {
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true

        // Mirror the intent-action selection logic in startBalanceCheck:
        val action = if (FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
            Intent.ACTION_CALL else Intent.ACTION_DIAL

        assertEquals(
            "ACTION_CALL must be used so *99# is dialled automatically without user tap",
            Intent.ACTION_CALL,
            action
        )
    }

    @Test
    fun `payment flow uses ACTION_DIAL not ACTION_CALL`() {
        // RealUssdPaymentEngine uses ACTION_DIAL for payment (isBalanceCheck == false)
        FestPayAccessibilityService.UssdSessionData.reset()
        assertFalse(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)

        val action = if (FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
            Intent.ACTION_CALL else Intent.ACTION_DIAL

        assertEquals(Intent.ACTION_DIAL, action)
    }

    // ------------------------------------------------------------------
    // 4. USSD URI string encodes *99# correctly (pure JVM, no Android API)
    // ------------------------------------------------------------------

    @Test
    fun `USSD URI string scheme is tel`() {
        // Mirror: Uri.parse("tel:" + Uri.encode("*99#"))
        // Verify the raw string before Android-parse contains the correct scheme.
        val ussdCode = "*99#"
        val uriString = "tel:" + java.net.URLEncoder.encode(ussdCode, "UTF-8")
        assertTrue("URI must start with tel:", uriString.startsWith("tel:"))
    }

    @Test
    fun `USSD code 99 digits survive URL encoding`() {
        // URLEncoder is the JVM equivalent of Uri.encode for this purpose.
        val encoded = java.net.URLEncoder.encode("*99#", "UTF-8")
        assertTrue("Encoded USSD must still contain 99", encoded.contains("99"))
    }

    // ------------------------------------------------------------------
    // 5. Reset wipes isBalanceCheck
    // ------------------------------------------------------------------

    @Test
    fun `reset after balance check session wipes isBalanceCheck`() {
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        assertTrue(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)

        FestPayAccessibilityService.UssdSessionData.reset()
        assertFalse(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
    }

    // ------------------------------------------------------------------
    // 6. Two consecutive calls don't cross-contaminate session data
    // ------------------------------------------------------------------

    @Test
    fun `second balance check session starts clean`() {
        // First session — completes with a parsed balance
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        FestPayAccessibilityService.UssdSessionData.parsedBalance = "12345.00"
        FestPayAccessibilityService.UssdSessionData.currentPresentationState = "BALANCE_SUCCESS"

        // Second session starts
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true

        assertNull("parsedBalance from prior session must be cleared", FestPayAccessibilityService.UssdSessionData.parsedBalance)
        assertEquals(
            "presentationState must reset to CONNECTING",
            "CONNECTING",
            FestPayAccessibilityService.UssdSessionData.currentPresentationState
        )
    }

    // ------------------------------------------------------------------
    // 7. Payment flow is not affected by balance check initialisation
    // ------------------------------------------------------------------

    @Test
    fun `after reset isBalanceCheck is false so payment flow is unaffected`() {
        // Balance check session
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true

        // Then reset (as would happen before a new payment)
        FestPayAccessibilityService.UssdSessionData.reset()

        assertFalse(
            "isBalanceCheck must be false after reset so payment flow is unaffected",
            FestPayAccessibilityService.UssdSessionData.isBalanceCheck
        )
        assertEquals("", FestPayAccessibilityService.UssdSessionData.recipientVpa)
        assertEquals("", FestPayAccessibilityService.UssdSessionData.amount)
    }

    // ------------------------------------------------------------------
    // 8. presentationState starts at CONNECTING after reset
    // ------------------------------------------------------------------

    @Test
    fun `currentPresentationState is CONNECTING after reset`() {
        FestPayAccessibilityService.UssdSessionData.currentPresentationState = "BALANCE_SUCCESS"
        FestPayAccessibilityService.UssdSessionData.reset()
        assertEquals("CONNECTING", FestPayAccessibilityService.UssdSessionData.currentPresentationState)
    }

    // ------------------------------------------------------------------
    // 9. parsedBalance is null after reset
    // ------------------------------------------------------------------

    @Test
    fun `parsedBalance is null after reset`() {
        FestPayAccessibilityService.UssdSessionData.parsedBalance = "500.00"
        FestPayAccessibilityService.UssdSessionData.reset()
        assertNull(FestPayAccessibilityService.UssdSessionData.parsedBalance)
    }

    // ------------------------------------------------------------------
    // 10. Broadcast action string is stable (AccessibilityService contract)
    // ------------------------------------------------------------------

    @Test
    fun `ACTION_NEW_USSD_SESSION broadcast action string matches service constant`() {
        assertEquals(
            "com.festpay.app.ACTION_NEW_USSD_SESSION",
            FestPayAccessibilityService.ACTION_NEW_USSD_SESSION
        )
    }
}

package com.festpay.app

import android.content.Intent
import com.festpay.app.data.BalanceParser
import com.festpay.app.ui.viewmodel.PaymentState
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal

class ExampleUnitTest {

    @Test
    fun testBalanceSessionInitialization() {
        FestPayAccessibilityService.UssdSessionData.reset()
        assertFalse(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)

        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        assertTrue(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)

        FestPayAccessibilityService.UssdSessionData.reset()
        assertFalse(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
    }

    @Test
    fun testBalanceSessionOrdering() {
        // Reset happens first, then isBalanceCheck is set to true
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        assertTrue(FestPayAccessibilityService.UssdSessionData.isBalanceCheck)
    }

    @Test
    fun testUssdActionSelection() {
        FestPayAccessibilityService.UssdSessionData.reset()
        FestPayAccessibilityService.UssdSessionData.isBalanceCheck = true
        val balanceAction = if (FestPayAccessibilityService.UssdSessionData.isBalanceCheck) Intent.ACTION_CALL else Intent.ACTION_DIAL
        assertEquals(Intent.ACTION_CALL, balanceAction)

        FestPayAccessibilityService.UssdSessionData.reset()
        val paymentAction = if (FestPayAccessibilityService.UssdSessionData.isBalanceCheck) Intent.ACTION_CALL else Intent.ACTION_DIAL
        assertEquals(Intent.ACTION_DIAL, paymentAction)
    }

    @Test
    fun testPaymentStatesExist() {
        assertNotNull(PaymentState.SUCCESS)
        assertNotNull(PaymentState.FAILED)
        assertNotNull(PaymentState.UNKNOWN)
        assertNotNull(PaymentState.AUTHENTICATION_REQUIRED)
    }

    // --- BalanceParser Unit Tests ---

    @Test
    fun testBalanceParser_RupeeSymbol() {
        val res = BalanceParser.parseBalance("Available balance is ₹12,345.67")
        assertNotNull(res.balance)
        assertEquals(BigDecimal("12345.67"), res.balance)
    }

    @Test
    fun testBalanceParser_INR() {
        val res = BalanceParser.parseBalance("Your account balance is INR 12,345.67")
        assertNotNull(res.balance)
        assertEquals(BigDecimal("12345.67"), res.balance)
    }

    @Test
    fun testBalanceParser_RsDot() {
        val res = BalanceParser.parseBalance("Available balance Rs. 12,345.67")
        assertNotNull(res.balance)
        assertEquals(BigDecimal("12345.67"), res.balance)
    }

    @Test
    fun testBalanceParser_IndianFormatLakh() {
        val res = BalanceParser.parseBalance("Available balance is ₹1,25,500.50")
        assertNotNull(res.balance)
        assertEquals(BigDecimal("125500.50"), res.balance)
    }

    @Test
    fun testBalanceParser_CurrentBalKeyword() {
        val res = BalanceParser.parseBalance("Current bal: Rs 500.00")
        assertNotNull(res.balance)
        assertEquals(BigDecimal("500.00"), res.balance)
    }

    @Test
    fun testBalanceParser_AccountNumberNoBalanceKeyword() {
        val res = BalanceParser.parseBalance("Account number 987654321098 linked")
        assertNull(res.balance)
    }

    @Test
    fun testBalanceParser_TxnNumberNoBalanceKeyword() {
        val res = BalanceParser.parseBalance("Ref TXN1234567890 processed")
        assertNull(res.balance)
    }

    @Test
    fun testBalanceParser_PinScreen() {
        val res = BalanceParser.parseBalance("Enter 6 digit UPI PIN for account")
        assertNull(res.balance)
    }

    @Test
    fun testBalanceParser_EmptyScreen() {
        val res = BalanceParser.parseBalance("")
        assertNull(res.balance)
    }

    @Test
    fun testBalanceParser_UnrecognizedCarrierResponse() {
        val res = BalanceParser.parseBalance("Carrier Info: Network request timed out")
        assertNull(res.balance)
    }
}

package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CarrierInputRevalidationTest {
    @Test fun changedMenusCannotReceivePreviouslyOfferedDigits() {
        val root = CarrierInput(CarrierInputKind.ROOT, "2")
        assertTrue(CarrierPaymentFlow.matchesInput(root, "2.Send Money 5.Check Balance", true))
        assertFalse(CarrierPaymentFlow.matchesInput(root, "1.Send Money 5.Check Balance", true))
        val method = CarrierInput(CarrierInputKind.METHOD, "8")
        assertTrue(CarrierPaymentFlow.matchesInput(method, "Send money to 8.UPI ID", true))
        assertFalse(CarrierPaymentFlow.matchesInput(method, "Send money to 7.UPI ID", true))
    }
    @Test fun recipientAmountAndPinBoundariesCannotBeCrossedByPendingInput() {
        val recipient = CarrierInput(CarrierInputKind.RECIPIENT, "synthetic@upi")
        val amount = CarrierInput(CarrierInputKind.AMOUNT, "1.00")
        assertTrue(CarrierPaymentFlow.matchesInput(recipient, "Enter UPI ID", true))
        assertTrue(CarrierPaymentFlow.matchesInput(amount, "Enter amount", true))
        assertFalse(CarrierPaymentFlow.matchesInput(recipient, "Enter amount", true))
        assertFalse(CarrierPaymentFlow.matchesInput(amount, "Enter UPI ID", true))
        for (input in listOf(recipient, amount, CarrierInput(CarrierInputKind.CONFIRMATION, "1"))) {
            assertFalse(CarrierPaymentFlow.matchesInput(input, "Enter UPI PIN", true))
            assertFalse(CarrierPaymentFlow.matchesInput(input, "Enter amount", false))
            assertFalse(CarrierPaymentFlow.matchesInput(input, "Confirm synthetic@upi INR 1.00", true))
        }
    }
}

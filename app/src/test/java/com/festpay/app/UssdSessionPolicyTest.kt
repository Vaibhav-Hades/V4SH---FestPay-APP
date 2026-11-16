package com.festpay.app

import com.festpay.app.data.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class UssdSessionPolicyTest {
    @Test fun sourceMustBeOwnedCurrentSystemPhoneWindow() {
        assertTrue(UssdSourcePolicy.allows("com.android.phone", "com.android.phone", true, true, 21, 20))
        assertFalse(UssdSourcePolicy.allows("com.example.bank", "com.example.bank", true, true, 21, 20))
        assertFalse(UssdSourcePolicy.allows("com.android.phone", "com.android.phone", false, true, 21, 20))
        assertFalse(UssdSourcePolicy.allows("com.android.phone", "com.android.systemui", true, true, 21, 20))
        assertFalse(UssdSourcePolicy.allows("com.android.phone", "com.android.phone", true, false, 21, 20))
        assertFalse(UssdSourcePolicy.allows("com.android.phone", "com.android.phone", true, true, 19, 20))
        assertFalse(UssdSourcePolicy.allows("com.android.phone", "com.android.phone", true, true, 0, 20))
    }
    @Test fun balanceAndPaymentCommandsCannotShareFinancialFields() {
        val balance = OwnedUssdCommand.balance()
        assertTrue(balance.balance); assertNull(balance.attemptId); assertEquals("", balance.recipient); assertEquals("", balance.amount)
        val attempt = UUID.randomUUID().toString()
        val payment = OwnedUssdCommand.payment(attempt, "synthetic@upi", "1.00")
        assertFalse(payment.balance); assertEquals(attempt, payment.attemptId); assertNotEquals(attempt, payment.sessionId)
        try { payment.copy(balance = true); fail("Payment metadata must not enter balance mode") } catch (_: IllegalArgumentException) { }
        try { payment.copy(amount = "0"); fail("Invalid execution amount") } catch (_: IllegalArgumentException) { }
    }
}

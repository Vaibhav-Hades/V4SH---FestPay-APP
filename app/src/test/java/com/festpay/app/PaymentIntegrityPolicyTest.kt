package com.festpay.app

import com.festpay.app.data.PaymentIntegrityPolicy
import com.festpay.app.ui.viewmodel.PaymentState
import org.junit.Assert.*
import org.junit.Test

class PaymentIntegrityPolicyTest {
    @org.junit.Test fun `stored terminal labels without evidence restore as unknown`() {
        org.junit.Assert.assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.restoredState(PaymentState.SUCCESS))
        org.junit.Assert.assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.restoredState(PaymentState.FAILED))
        org.junit.Assert.assertEquals(PaymentState.INITIATED, PaymentIntegrityPolicy.restoredState(PaymentState.INITIATED))
    }
    @Test fun `no screen wording can grant success or definitive failure`() {
        assertFalse(PaymentIntegrityPolicy.confirmationAvailable)
        for (word in listOf("success", "successful", "unsuccessful", "failed", "failure", "declined", "rejected", "unknown")) {
            assertEquals(word, PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome(word))
        }
    }
    @Test fun `every unsupported transition is rejected`() {
        val allowed = setOf(PaymentState.INITIATED to PaymentState.PROCESSING, PaymentState.INITIATED to PaymentState.CANCELLED,
            PaymentState.INITIATED to PaymentState.UNKNOWN, PaymentState.PROCESSING to PaymentState.UNKNOWN)
        for (from in PaymentState.entries) for (to in PaymentState.entries) {
            assertEquals("$from -> $to", (from to to) in allowed, PaymentIntegrityPolicy.allows(from, to))
        }
    }
}

package com.festpay.app

import com.festpay.app.data.BalanceObservationPolicy
import org.junit.Assert.*
import org.junit.Test

class BalanceObservationPolicyTest {
    @Test fun `PIN window remains pending even when header disappears`() {
        assertFalse(BalanceObservationPolicy.canReadResult(true, true))
    }

    @Test fun `unrelated windows cannot provide balance`() {
        assertFalse(BalanceObservationPolicy.canReadResult(false, false))
    }

    @Test fun `static result from the associated source can be parsed`() {
        assertTrue(BalanceObservationPolicy.canReadResult(true, false))
        assertNull(com.festpay.app.data.AccessibilityTextPolicy.readLabel(true, false, true) {
            throw AssertionError("Retained input field must not be read")
        })
    }
}

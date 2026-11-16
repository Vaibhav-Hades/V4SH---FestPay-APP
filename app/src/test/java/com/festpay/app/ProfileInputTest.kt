package com.festpay.app

import com.festpay.app.data.PaymentInputValidator
import org.junit.Assert.*
import org.junit.Test

class ProfileInputTest {
    @Test fun `valid profile removes surrounding spaces without changing account ID`() {
        assertEquals(PaymentInputValidator.Profile("person@upi", "Person Name"),
            PaymentInputValidator.profile("  person@upi  ", "  Person Name  "))
    }

    @Test fun `invalid or ambiguous profile input is rejected`() {
        for (target in listOf("", "person@", "@upi", "person@upi;*99#", "person@upi\n", "x".repeat(256) + "@upi")) {
            assertNull(PaymentInputValidator.profile(target, "Name"))
        }
        for (name in listOf("", "   ", "\nName", "Name\tValue", "x".repeat(101))) {
            assertNull(PaymentInputValidator.profile("person@upi", name))
        }
    }
}

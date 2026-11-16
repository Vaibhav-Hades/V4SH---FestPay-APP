package com.festpay.app

import com.festpay.app.data.AccessibilityTextPolicy
import org.junit.Assert.*
import org.junit.Test

class AccessibilityTextPolicyTest {
    @Test fun `input values are never requested`() {
        for (flags in listOf(Triple(true, false, false), Triple(false, true, false), Triple(false, false, true))) {
            val result = AccessibilityTextPolicy.readLabel(flags.first, flags.second, flags.third) {
                throw AssertionError("Input value reader must not run")
            }
            assertNull(result)
        }
    }

    @Test fun `static menu label remains available`() {
        assertEquals("3. Check Balance", AccessibilityTextPolicy.readLabel(false, false, false) { "3. Check Balance" })
        assertNull(AccessibilityTextPolicy.readLabel(false, false, false) { null })
    }

    @Test fun `authentication labels halt even with VPA context or no editable field`() {
        for (prompt in listOf("Enter UPI PIN for upi id", "Invalid PIN", "Enter OTP", "Password required", "Change UPI PIN")) {
            assertTrue(prompt, AccessibilityTextPolicy.isSensitivePrompt(prompt, true))
            assertTrue(prompt, AccessibilityTextPolicy.isSensitivePrompt(prompt, false))
        }
        assertTrue(AccessibilityTextPolicy.isSensitivePrompt("Enter 6-digit code", true))
        assertTrue(AccessibilityTextPolicy.isSensitivePrompt("Enter 4 digit code", true))
    }

    @Test fun `ordinary navigation labels do not trigger secret handling`() {
        for (label in listOf("Select option 3. Check Balance", "Enter UPI ID", "Enter amount", "Send Money", "Select option 1. Send Money 3. Check Balance 7. UPI PIN")) {
            assertFalse(label, AccessibilityTextPolicy.isSensitivePrompt(label, true))
        }
    }
}

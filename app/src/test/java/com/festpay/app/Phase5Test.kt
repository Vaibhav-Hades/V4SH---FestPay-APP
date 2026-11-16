package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase5Test {
    @Test fun wakeAddsOnlyV4shAndKeepsEveryOtherService() {
        val me = "com.festpay.app/com.festpay.app.FestPayAccessibilityService"
        assertEquals(me, AccessibilityWake.withService(null, me))
        assertEquals(me, AccessibilityWake.withService("", me))
        assertEquals("a/b:c/d:$me", AccessibilityWake.withService("a/b:c/d", me))
        assertEquals("a/b:$me", AccessibilityWake.withService("a/b:$me", me))
    }

    @Test fun supportMessageGoesToTheOwnersNumberAndIsBounded() {
        assertEquals("+919618099307", SupportContact.NUMBER_E164)
        val text = SupportContact.message("  It failed  ", "Phone: X")
        assertEquals("V4SH support\nIt failed\n\nPhone: X", text)
        assertEquals(SupportContact.MAX_ISSUE_CHARS + "V4SH support\n".length, SupportContact.message("x".repeat(5000), null).length)
        assertTrue(SupportContact.smsUri("hi there").startsWith("smsto:+919618099307?body=hi%20there"))
        assertTrue(SupportContact.whatsAppUrl("hi").startsWith("https://wa.me/919618099307?text=hi"))
    }
}

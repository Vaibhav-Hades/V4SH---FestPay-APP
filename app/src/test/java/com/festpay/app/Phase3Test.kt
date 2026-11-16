package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase3Test {
    @Test fun pasteAcceptsAUpiIdALinkOrOneIdInsideText() {
        assertEquals(PastedPayee.Vpa("asha@ybl"), ClipboardPayee.parse("  asha@ybl "))
        assertTrue(ClipboardPayee.parse("upi://pay?pa=asha@ybl&pn=Asha") is PastedPayee.Link)
        assertEquals(PastedPayee.Vpa("asha@ybl"), ClipboardPayee.parse("Please pay to asha@ybl."))
        assertEquals(PastedPayee.Nothing, ClipboardPayee.parse("pay a@ybl or b@sbi"))
        assertEquals(PastedPayee.Nothing, ClipboardPayee.parse("9876543210"))
        assertEquals(PastedPayee.Nothing, ClipboardPayee.parse(""))
        assertEquals(PastedPayee.Nothing, ClipboardPayee.parse(null))
        assertEquals(PastedPayee.Nothing, ClipboardPayee.parse("x".repeat(3000)))
    }

    @Test fun balanceQuickIsOneShot() {
        BalanceQuick.consume(); assertFalse(BalanceQuick.consume())
        BalanceQuick.request(); assertTrue(BalanceQuick.consume()); assertFalse(BalanceQuick.consume())
    }

    @Test fun plainErrorsPointToTheFix() {
        assertEquals(IssueFix.ACCESSIBILITY_SETTINGS, ErrorHelp.friendly("V4SH Accessibility is not running. Enable it again")?.fix)
        assertEquals(IssueFix.PHONE_PERMISSION, ErrorHelp.friendly("Payment not started. Phone permission is not granted.")?.fix)
        assertEquals(IssueFix.STOP_STUCK_OPERATION, ErrorHelp.friendly("Another cellular operation is active. Finish it")?.fix)
        assertTrue(ErrorHelp.friendly("The carrier replied: \"Connection problem or invalid MMI code.\"")!!.sentence.contains("did not accept"))
        assertNull(ErrorHelp.friendly("Something unrelated"))
        assertNull(ErrorHelp.friendly(null))
    }
}

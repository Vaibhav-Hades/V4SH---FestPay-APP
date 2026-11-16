package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CarrierMessageTest {
    @Test fun theCarriersWordsAreShownWithoutTheDialogTitleOnOneLine() {
        assertEquals("MMI code not valid.", CarrierMessage.plain("Carrier info\nMMI code not valid."))
        assertEquals("Connection problem or invalid MMI code.", CarrierMessage.plain("Carrier info\n  Connection problem or invalid MMI code.  \n"))
        assertEquals("Line one Line two", CarrierMessage.plain("Line one\n\nLine two"))
    }

    @Test fun fillerBlankAndOverlongFramesAreHandledSafely() {
        assertNull(CarrierMessage.plain("")); assertNull(CarrierMessage.plain("Carrier info")); assertNull(CarrierMessage.plain("Carrier info\nUSSD code running..."))
        assertNull(CarrierMessage.plain("Please wait")); assertNull(CarrierMessage.plain("Processing..."))
        assertEquals(300, CarrierMessage.plain("x".repeat(1000))!!.length)
    }

    @Test fun theRejectionWordingSeenOnRealCarriersEndsTheBalanceRequestWithAReason() {
        for (text in listOf("mmi code not valid.", "connection problem or invalid mmi code.", "service is not available. try again later", "could not find your bank")) {
            assertTrue(text, UssdNavigationPolicy.isCarrierRejection(text))
        }
        assertFalse(UssdNavigationPolicy.isCarrierRejection("select option: state bank of india 1. send money 3. check balance"))
        assertFalse(UssdNavigationPolicy.isCarrierRejection("your balance is rs. 1,234.56"))
    }
}

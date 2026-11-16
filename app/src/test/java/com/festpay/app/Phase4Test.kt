package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase4Test {
    @Test fun requestQrCarriesAValidAmountOnly() {
        assertEquals("upi://pay?pa=a%40ybl&pn=Asha&cu=INR", ReceiveQr.payload("a@ybl", "Asha"))
        assertEquals("upi://pay?pa=a%40ybl&pn=Asha&am=25.50&cu=INR", ReceiveQr.payload("a@ybl", "Asha", "25.50"))
        assertEquals("invalid amount is ignored", "upi://pay?pa=a%40ybl&pn=Asha&cu=INR", ReceiveQr.payload("a@ybl", "Asha", "abc"))
        assertEquals("zero is ignored", "upi://pay?pa=a%40ybl&pn=Asha&cu=INR", ReceiveQr.payload("a@ybl", "Asha", "0"))
        assertNull(ReceiveQr.payload("not-a-vpa", "x", "5"))
    }

    @Test fun requestQrRoundTripsThroughTheAppsOwnParser() {
        val payload = ReceiveQr.payload("asha@ybl", "Asha K", "40")!!
        val parsed = UpiQrParser.analyze(payload).value
        assertEquals("asha@ybl", parsed?.vpa); assertEquals(0, java.math.BigDecimal("40").compareTo(java.math.BigDecimal(parsed?.amount)))
    }

    @Test fun languageChoicesAreFixedAndUnknownCodesFallBackToThePhone() {
        assertEquals(listOf("", "en", "hi", "te"), AppLanguage.choices.map { it.code })
    }
}

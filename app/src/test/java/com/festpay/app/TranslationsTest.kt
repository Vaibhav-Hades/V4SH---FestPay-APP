package com.festpay.app

import com.festpay.app.data.Translations
import org.junit.Assert.*
import org.junit.Test

class TranslationsTest {
    @Test fun knownTextIsTranslatedAndUnknownTextIsLeftAlone() {
        assertEquals("भुगतान सफल", Translations.translate("Payment successful", "hi"))
        assertEquals("చెల్లింపు విజయవంతం", Translations.translate("Payment successful", "te"))
        assertEquals("Payment successful", Translations.translate("Payment successful", "en"))
        assertEquals("Pay Asha ₹1", Translations.translate("Pay Asha ₹1", "hi"))
        assertEquals("Anything", Translations.translate("Anything", "fr"))
    }

    @Test fun theTableIsSubstantial() { assertTrue(Translations.size > 200) }

    @Test fun neverTranslatesTheUpiPinWarningIntoSomethingThatDropsThePin() {
        listOf("hi", "te").forEach { lang ->
            val t = Translations.translate("Type your UPI PIN on the carrier screen. V4SH never sees or enters it.", lang)
            assertTrue(t.contains("UPI PIN")); assertTrue(t.contains("V4SH"))
        }
    }
}

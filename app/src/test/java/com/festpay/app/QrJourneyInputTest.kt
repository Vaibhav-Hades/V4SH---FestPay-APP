package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class QrJourneyInputTest {
    @Test fun qrCurrencyAmountAndNoteAreCanonicalAndNameIsNotIdentity() {
        val qr = UpiQrParser.parse("upi://pay?pa=sample%40upi&pn=Unverified&am=12.5&cu=inr&tn=Local%20note")!!
        assertEquals("sample@upi", qr.vpa); assertEquals("12.50", qr.amount)
        assertEquals("INR", qr.currency); assertEquals("Local note", qr.note)
        val prep = PaymentPreparation(); prep.select(qr.vpa, qr.amount!!, qr.note!!)
        assertNull(prep.review()!!.recipient.verifiedBeneficiaryName)
        assertEquals(VerificationStatus.UNAVAILABLE, prep.review()!!.recipient.verificationStatus)
    }
    @Test fun malformedEncodingAndInvisibleDirectionalPayloadsAreRejected() {
        for (field in listOf("pn=%FF", "tn=%E2%80%AEfake", "tn=%00", "cu=USD")) assertNull(UpiQrParser.parse("upi://pay?pa=sample@upi&$field"))
        assertNotNull(UpiQrParser.parse("upi://pay?pa=sample@upi&tn=%F0%9F%8D%B5"))
    }
    @Test fun aNewQrCannotKeepPriorAmountNoteOrVerifiedIdentity() {
        val prep = PaymentPreparation(); prep.select("first@upi", "1", "First")
        val old = prep.review()!!
        prep.select("second@upi")
        assertFalse(prep.isCurrent(old)); assertNull(prep.review()); assertEquals("", prep.amount); assertEquals("", prep.note)
        assertNull(prep.recipient.verifiedBeneficiaryName)
    }
}

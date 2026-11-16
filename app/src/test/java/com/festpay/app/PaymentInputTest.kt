package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PaymentInputTest {
    @Test fun `blank QR amount requires entry but explicit zero is rejected`() {
        for (amount in listOf("")) {
            val result = UpiQrParser.analyze("upi://pay?pa=test@upi&am=$amount&cu=INR")
            assertEquals(QrIssue.NONE, result.issue)
            assertNull(result.value!!.amount)
        }
        for (amount in listOf("0", "0.00", "-1")) assertEquals(QrIssue.INVALID_AMOUNT, UpiQrParser.analyze("upi://pay?pa=test@upi&am=$amount").issue)
        assertEquals(QrIssue.DUPLICATE_FIELD, UpiQrParser.analyze("upi://pay?pa=a@upi&PA=b@upi").issue)
        assertEquals(QrIssue.INVALID_AMOUNT, UpiQrParser.analyze("upi://pay?pa=a@upi&am=-1").issue)
    }
    @Test fun `QR parses locally with exact monetary values`() {
        val parsed = UpiQrParser.parse("upi://pay?pa=person%40upi&pn=Test%20Person&am=12.5&cu=INR&tn=Lunch")!!
        assertEquals("person@upi", parsed.vpa); assertEquals("12.50", parsed.amount); assertEquals("Lunch", parsed.note)
    }
    @Test fun `malformed or ambiguous QR cannot supply a request`() {
        for (raw in listOf("https://example.com?pa=a@upi", "upi://bad?pa=a@upi", "upi://pay?pa=a@upi&pa=b@upi", "upi://pay?pa=a@upi&am=-1", "upi://pay?pa=a@upi&am=1.001", "upi://pay?pa=a@upi&cu=USD", "upi://pay?pa=a@upi&pn=%0a", "upi://pay?pa=wrong", "upi://pay?pa=a@upi#fragment")) assertNull(raw, UpiQrParser.parse(raw))
    }
    @Test fun `amount validation rejects nonfinite notation and precision loss`() {
        for (value in listOf("0", "-1", "NaN", "Infinity", "1e3", "1.001", "1,000", " 12")) assertNull(value, PaymentInputValidator.amount(value))
        assertEquals("0.01", PaymentInputValidator.amount("0.01"))
    }
    @Test fun `mobile target cannot contain USSD instructions`() {
        assertEquals("9876543210", PaymentInputValidator.mobile("+91 98765 43210"))
        assertNull(PaymentInputValidator.mobile("9876543210#")); assertFalse(PaymentInputValidator.isVpa("a@upi;*99#"))
    }
}

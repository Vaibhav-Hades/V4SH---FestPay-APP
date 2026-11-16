package com.festpay.app

import com.festpay.app.ui.screens.UpiParseResult
import com.festpay.app.ui.screens.parseAndValidateUpiQr
import org.junit.Assert.*
import org.junit.Test

/** Each rejected QR must reach its own screen, and a rejection must clear any earlier draft. */
class QrRejectionRoutingTest {
    @Test fun rejectedCodesRouteToTheMatchingScreenAndClearTheDraft() {
        assertTrue(parseAndValidateUpiQr("upi://pay?pa=synthetic@upi&am=1.00&cu=INR") is UpiParseResult.Valid)
        assertTrue(parseAndValidateUpiQr("https://example.com/hello") is UpiParseResult.UnsupportedFormat)
        assertTrue(com.festpay.app.ui.screens.SharedPaymentState.payeeVpa.isBlank())
        assertTrue(parseAndValidateUpiQr("upi://pay?pa=synthetic@upi&am=1.00&cu=USD") is UpiParseResult.UnsupportedFormat)
        assertTrue(parseAndValidateUpiQr("upi://pay?am=1.00&cu=INR") is UpiParseResult.MissingDetails)
        assertTrue(parseAndValidateUpiQr("upi://pay?pa=not-a-vpa&am=1.00&cu=INR") is UpiParseResult.InvalidFormat)
        assertTrue(parseAndValidateUpiQr("upi://pay?pa=synthetic@upi&am=1.234&cu=INR") is UpiParseResult.InvalidFormat)
        assertTrue(com.festpay.app.ui.screens.SharedPaymentState.payeeVpa.isBlank())
    }
}

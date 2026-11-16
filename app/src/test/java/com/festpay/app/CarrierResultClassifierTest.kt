package com.festpay.app

import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentState
import org.junit.Assert.*
import org.junit.Test

class CarrierResultClassifierTest {
    @Test fun failureWordsCannotMatchSuccess() {
        for (text in listOf("failure", "failed", "unsuccessful", "declined", "rejected", "SUCCESS but FAILED", "not successful")) {
            for (variant in listOf(text, text.uppercase(), text.lowercase())) {
                assertEquals(variant, CarrierResultObservation.FAILURE_WORDING, CarrierResultClassifier.observe(variant))
                assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome(variant))
            }
        }
    }
    @Test fun untrustedWordingAlwaysRemainsFinanciallyUnknown() {
        for (text in listOf("success", "successful", "SUCCESS", "SuCcEsSfUl", "FAILED", "timeout", "unknown", "ambiguous", "ambiguous carrier response", "presuccess", "")) {
            assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome(text))
        }
        assertEquals(CarrierResultObservation.INCONCLUSIVE, CarrierResultClassifier.observe("presuccess"))
        assertEquals(CarrierResultObservation.SUCCESS_WORDING, CarrierResultClassifier.observe("successful"))
    }
}

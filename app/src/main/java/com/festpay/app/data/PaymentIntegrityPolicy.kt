package com.festpay.app.data

import com.festpay.app.ui.viewmodel.PaymentState

object PaymentIntegrityPolicy {
    // No authorized transaction-specific verification adapter exists in this product.
    val confirmationAvailable: Boolean get() = false
    fun carrierOutcome(text: String = ""): PaymentState {
        CarrierResultClassifier.observe(text)
        return PaymentState.UNKNOWN
    }
    // This journal has no authorized provider evidence/provenance fields.
    // A stored terminal label alone cannot prove a financial outcome.
    fun restoredState(state: PaymentState): PaymentState = when (state) {
        PaymentState.SUCCESS, PaymentState.FAILED -> PaymentState.UNKNOWN
        else -> state
    }

    fun allows(from: PaymentState, to: PaymentState): Boolean {
        if (to == PaymentState.SUCCESS || to == PaymentState.FAILED) return false
        return when (from) {
            PaymentState.INITIATED -> to == PaymentState.PROCESSING || to == PaymentState.CANCELLED || to == PaymentState.UNKNOWN
            PaymentState.PROCESSING -> to == PaymentState.UNKNOWN
            else -> false
        }
    }
}

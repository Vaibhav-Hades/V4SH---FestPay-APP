package com.festpay.app

import android.content.Context
import com.festpay.app.ui.viewmodel.*

/** Test-source-only simulation; excluded from both debug and release application artifacts. */
class LocalMockPaymentEngine : PaymentEngine {
    override suspend fun executePayment(request: PaymentRequest, scenario: PaymentScenario, context: Context?): PaymentResult =
        PaymentResult(when (scenario) {
            PaymentScenario.SUCCESS -> PaymentState.SUCCESS
            PaymentScenario.FAILED -> PaymentState.FAILED
            PaymentScenario.UNKNOWN -> PaymentState.UNKNOWN
        }, "SIMULATION-ONLY", "Simulated test result; no payment occurred")
}

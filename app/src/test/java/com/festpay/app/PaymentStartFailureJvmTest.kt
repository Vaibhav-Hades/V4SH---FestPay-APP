package com.festpay.app

import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.PaymentViewModel
import com.festpay.app.ui.viewmodel.StartFailure
import org.junit.Assert.*
import org.junit.Test

/** The earliest guards need no Android context: each reports its own reason and never calls the next stage. */
class PaymentStartFailureJvmTest {
    private fun refuse(vpa: String, amount: String): Triple<PaymentViewModel, PaymentState?, Boolean> {
        val model = PaymentViewModel(); var result: PaymentState? = null
        model.startPayment(vpa, "Name", amount, null) { result = it }
        return Triple(model, result, model.operationError != null)
    }

    @Test fun anInvalidUpiIdIsReportedAsAnInvalidRequest() {
        for (bad in listOf("9876543210", "", "no-at-sign", "a@", "@bank")) {
            val (m, state, hasMessage) = refuse(bad, "1.00")
            assertEquals(bad, PaymentState.IDLE, state); assertEquals(bad, StartFailure.INVALID_REQUEST, m.startFailure); assertTrue(hasMessage)
            assertTrue(m.operationError!!.contains("not valid"))
        }
    }

    @Test fun anInvalidAmountIsReportedAsAnInvalidRequest() {
        for (bad in listOf("0", "0.00", "-1", "1.234", "abc", "", "10000000", "1e2")) {
            val (m, state, _) = refuse("synthetic@upi", bad)
            assertEquals(bad, PaymentState.IDLE, state); assertEquals(bad, StartFailure.INVALID_REQUEST, m.startFailure)
        }
    }

    @Test fun aValidRequestWithoutAScreenContextIsReportedAsThatNotAsAnInvalidRequest() {
        val (m, state, _) = refuse("synthetic@upi", "1.00")
        assertEquals(PaymentState.IDLE, state)
        assertEquals(StartFailure.CONTEXT_UNAVAILABLE, m.startFailure)
        assertFalse(m.operationError!!.contains("not valid"))
    }

    @Test fun aRefusedStartNeverCreatesAnAttemptOrSelectsOne() {
        val (m, _, _) = refuse("synthetic@upi", "1.00")
        assertNull(m.selectedAttemptId)
        assertTrue(m.transactionLedger.isEmpty())
        assertNull(m.lastResult)
    }

    @Test fun everyFailureKindHasADistinctNameForDiagnostics() {
        val names = StartFailure.values().map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (required in listOf("INVALID_REQUEST", "RESERVATION_PROTECTION", "ACCESSIBILITY_UNAVAILABLE", "PHONE_PERMISSION",
                "ENGINE_REJECTED", "LAUNCH_FAILED", "SESSION_NOT_ACKNOWLEDGED")) assertTrue(required, required in names)
    }
}

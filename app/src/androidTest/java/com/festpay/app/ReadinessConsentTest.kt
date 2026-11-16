package com.festpay.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.AccessibilityConsent
import com.festpay.app.data.CellularReadinessReader
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.PaymentViewModel
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ReadinessConsentTest {
    @Test fun busyPaymentCannotBeOverwrittenByBalanceConsentError() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_busy_consent_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        }
        val session = FestPayAccessibilityService.UssdSessionData
        try {
            session.reset(false)
            session.operationActive = true
            session.sessionId = UUID.randomUUID().toString()
            session.attemptId = UUID.randomUUID().toString()
            session.currentPresentationState = "AWAITING_AUTH"
            val owner = session.sessionId
            var result: PaymentState? = null
            PaymentViewModel().startBalanceCheck(context) { result = it }
            assertEquals(PaymentState.IDLE, result)
            assertEquals("AWAITING_AUTH", session.currentPresentationState)
            assertEquals(owner, session.sessionId)
            assertTrue(session.operationActive)
            assertFalse(session.isBalanceCheck)
            assertNull(session.errorMessage)
        } finally {
            session.operationActive = false; session.reset()
            base.deleteSharedPreferences(name)
        }
    }
    @Test fun deniedOrWithdrawnConsentPreventsLaunchBeforeAnyJournalAccess() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_consent_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            assertFalse(AccessibilityConsent.accepted(context))
            assertTrue(AccessibilityConsent.set(context, true))
            assertTrue(AccessibilityConsent.accepted(context))
            assertTrue(AccessibilityConsent.set(context, false))
            val model = PaymentViewModel(journalFactory = { error("Must not access journal without consent") })
            var result: PaymentState? = null
            model.startPayment("synthetic@upi", "Synthetic", "1.00", context) { result = it }
            assertEquals(PaymentState.IDLE, result)
            assertNull(model.selectedAttemptId)
            assertTrue(model.transactionLedger.isEmpty())
            assertNotNull(model.operationError)
        } finally { base.deleteSharedPreferences(name) }
    }

    @Test fun publicReadinessObservationNeedsNoNewRuntimePermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = CellularReadinessReader.read(context)
        assertNotNull(snapshot.sim)
        assertTrue(snapshot.operator == null || snapshot.operator.length <= 80)
        // READ_PHONE_STATE was removed from this list on 2026-11-09: the owner asked for a SIM chooser, which lists SIMs with it
        // (requested only when the chooser is opened). Readiness itself still needs none of these permissions.
        for (permission in listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.READ_SMS")) {
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(permission))
        }
    }
}

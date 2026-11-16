package com.festpay.app

import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentState
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UiPresentationTest {
    @Test fun greetingFollowsTheHourAndNeverShowsAnEmptyOrInventedName() {
        assertEquals("Good morning, Asha.", UiPresentation.greeting(9, "Asha Rao"))
        assertEquals("Good afternoon, Asha.", UiPresentation.greeting(15, " Asha "))
        assertEquals("Good evening.", UiPresentation.greeting(20, ""))
        assertEquals("Good evening.", UiPresentation.greeting(23, "   "))
        assertEquals("Hello.", UiPresentation.greeting(2, null))
        assertEquals("Good morning.", UiPresentation.greeting(5, null))
    }

    @Test fun verificationWordingNeverLeaksEnumNamesOrImpliesVerification() {
        for (status in VerificationStatus.values()) {
            val label = UiPresentation.verificationLabel(status)
            assertFalse(label, label.contains('_') || label == label.uppercase())
            if (status != VerificationStatus.VERIFIED) assertFalse(label, label.lowercase().contains("verified") && !label.lowercase().contains("not verified"))
        }
        assertEquals("Recipient name not verified", UiPresentation.verificationLabel(VerificationStatus.UNAVAILABLE))
    }

    @Test fun blankSavedNameFallsBackToTheUpiIdWithoutInventingAName() {
        assertEquals("a@upi", UiPresentation.payeeTitle("", "a@upi"))
        assertEquals("a@upi", UiPresentation.payeeTitle("  ", "a@upi"))
        assertEquals("Saved Name", UiPresentation.payeeTitle("Saved Name", "a@upi"))
    }

    @Test fun ownerResolutionWordingIsAStatementByTheOwnerNeverAConfirmation() {
        assertEquals("Resolved by you: money was debited", UiPresentation.ownerResolutionLabel("DEBITED"))
        assertEquals("Resolved by you: money was not debited", UiPresentation.ownerResolutionLabel("NOT_DEBITED"))
        assertNull(UiPresentation.ownerResolutionLabel(null))
        assertNull(UiPresentation.ownerResolutionLabel("SUCCESS"))
        for (r in listOf("DEBITED", "NOT_DEBITED")) {
            val label = UiPresentation.ownerResolutionLabel(r)!!.lowercase()
            assertTrue(label, label.startsWith("resolved by you") && !label.contains("confirmed") && !label.contains("settled"))
        }
    }

    @Test fun progressWordingIsOperationalAndCarrierResultsAreAlwaysMarkedUnverified() {
        assertEquals(0, UiPresentation.carrierRank(CarrierStage.WAITING))
        assertTrue(UiPresentation.carrierRank(CarrierStage.PIN) > UiPresentation.carrierRank(CarrierStage.AMOUNT_SENT))
        assertTrue(UiPresentation.carrierRank(CarrierStage.RESULT) > UiPresentation.carrierRank(CarrierStage.PIN))
        assertEquals("Carrier said: success (unverified)", UiPresentation.carrierResultBadge("SUCCESS_WORDING"))
        assertEquals("Carrier said: failed (unverified)", UiPresentation.carrierResultBadge("FAILURE_WORDING"))
        assertNull(UiPresentation.carrierResultBadge("INCONCLUSIVE")); assertNull(UiPresentation.carrierResultBadge(null))
        for (w in CarrierResultObservation.values()) assertTrue(UiPresentation.carrierResultText(w).contains("bank", ignoreCase = true) || w == CarrierResultObservation.INCONCLUSIVE)
        for (issue in CarrierIssue.values()) assertTrue(UiPresentation.interruptionText(issue).isNotBlank())
        for ((_, label) in UiPresentation.PROGRESS_STEPS) assertFalse(label.lowercase().contains("success") || label.lowercase().contains("paid"))
    }

    @Test fun savedStatesNeverReadAsSettledWithoutConfirmation() {
        for (state in listOf(PaymentState.INITIATED, PaymentState.PROCESSING, PaymentState.UNKNOWN)) {
            assertEquals("Outcome unknown", UiPresentation.outcomeLabel(state))
        }
        assertEquals("No bank confirmation available", UiPresentation.evidenceLabel("NO_AUTHORIZED_CONFIRMATION"))
    }

    @Test fun shortTimestampIsZoneIndependentTextAndNeverUsesTheClock() {
        assertEquals("2 Nov 2026, 9:00 PM", UiPresentation.shortTimestamp("2026-11-02T15:30:00Z", ZoneId.of("Asia/Kolkata")))
        assertEquals("Timestamp unavailable for this older record", UiPresentation.shortTimestamp("invalid"))
    }

    @Test fun amountProblemsExplainEveryRejectedInputAndAcceptValidOnes() {
        assertNull(UiPresentation.amountProblem(""))
        assertNull(UiPresentation.amountProblem("1"))
        assertNull(UiPresentation.amountProblem("100.50"))
        assertEquals("Use at most 2 decimal places.", UiPresentation.amountProblem("1.234"))
        assertEquals("That amount is too large.", UiPresentation.amountProblem("10000000"))
        assertEquals("Enter an amount above ₹0.", UiPresentation.amountProblem("0"))
        assertEquals("Enter an amount above ₹0.", UiPresentation.amountProblem("0.00"))
        assertEquals("Add the paise digits, or remove the decimal point.", UiPresentation.amountProblem("5."))
        assertNotNull(UiPresentation.amountProblem("."))
    }

    private fun eval(storage: Boolean = true, history: Boolean = false, unresolved: Boolean = false, consent: Boolean = true,
                     phone: Boolean = true, a11y: Boolean = true, cellular: String? = null) =
        HomeReadinessEvaluator.evaluate(storage, history, unresolved, consent, phone, a11y, cellular)

    @Test fun readinessReportsTheMostImportantBlockerFirstAndReadyOnlyWhenEverythingHolds() {
        assertEquals(HomeReadinessState.READY, eval().state)
        assertTrue(eval().detail.contains("only when a payment starts"))
        assertEquals(HomeReadinessState.BLOCKED, eval(unresolved = true, a11y = false).state)
        assertEquals(HomeReadinessState.BLOCKED, eval(storage = false, unresolved = true).state)
        assertEquals(HomeReadinessState.BLOCKED, eval(history = true).state)
        assertEquals(HomeReadinessState.SETUP_NEEDED, eval(consent = false).state)
        assertEquals(HomeReadinessState.SETUP_NEEDED, eval(phone = false).state)
        assertEquals("Turn on V4SH in Android Accessibility settings.", eval(a11y = false).detail)
        assertEquals("Turn airplane mode off.", eval(cellular = "Turn airplane mode off.").detail)
    }
}

class QrRejectionWordingTest {
    private fun issue(raw: String) = UpiQrParser.analyze(raw).issue
    @Test fun everyRejectedTestCodeGetsADistinctPlainReasonAndNoEnumName() {
        val cases = mapOf(
            "https://example.com/hello" to QrIssue.UNSUPPORTED_SCHEME,
            "upi://pay?pa=synthetic@upi&am=1.00&cu=USD" to QrIssue.UNSUPPORTED_CURRENCY,
            "upi://pay?pa=not-a-vpa&am=1.00&cu=INR" to QrIssue.INVALID_RECIPIENT,
            "upi://pay?pa=synthetic@upi&am=1.234&cu=INR" to QrIssue.INVALID_AMOUNT,
            "upi://pay?am=1.00&cu=INR" to QrIssue.MISSING_RECIPIENT)
        for ((raw, expected) in cases) {
            assertEquals(raw, expected, issue(raw))
            val label = UiPresentation.qrIssueLabel(expected)
            assertTrue(raw, label.isNotBlank() && !label.contains('_') && label != label.uppercase())
        }
        assertEquals("", UiPresentation.qrIssueLabel(QrIssue.NONE))
    }
    @Test fun validTestCodesAreAcceptedWithOrWithoutAnAmount() {
        assertEquals(QrIssue.NONE, issue("upi://pay?pa=synthetic@upi&pn=Test%20Shop&am=1.00&cu=INR&tn=lunch"))
        assertNull(UpiQrParser.analyze("upi://pay?pa=synthetic@upi&pn=Test%20Shop&cu=INR").value!!.amount)
    }
}

package com.festpay.app

import com.festpay.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QrRecipientNameTest {
    @Test fun qrNameSurvivesSelectionResolutionAndFrozenReview() = runBlocking {
        val qr = UpiQrParser.parse("upi://pay?pa=sample@upi&pn=Sample%20Shop&am=1")!!
        val draft = PaymentPreparation()
        draft.select(qr.vpa, qr.amount!!, qrSuppliedName = qr.name)
        val continuation = draft.select(qr.vpa, qr.amount, qrSuppliedName = qr.name)
        draft.resolve(continuation)
        val frozen = draft.review()!!
        assertEquals("Sample Shop", frozen.recipient.displayName)
        assertEquals("QR_PN_UNVERIFIED", frozen.recipient.qrNameProvenance)
        assertEquals("Name from QR — unverified", frozen.recipient.nameSourceLabel)
        assertNull(frozen.recipient.verifiedBeneficiaryName)
        assertEquals(VerificationStatus.UNAVAILABLE, frozen.recipient.verificationStatus)
        draft.select("other@upi", "2")
        assertEquals("Name unavailable", draft.recipient.displayName)
        assertNull(draft.recipient.qrNameProvenance)
        assertFalse(draft.isCurrent(frozen))
        assertEquals("Sample Shop", frozen.recipient.displayName)
    }
    @Test fun manualEntryEvenForSameAddressClearsQrNameAndMissingNamesStayUnavailable() {
        val draft = PaymentPreparation()
        draft.select("sample@upi", qrSuppliedName = "Shop")
        draft.select("sample@upi")
        assertNull(draft.recipient.qrSuppliedName)
        assertEquals("Name unavailable", draft.recipient.displayName)
        draft.select("other@upi", qrSuppliedName = "   ")
        assertEquals("Name unavailable", draft.recipient.displayName)
        assertNull(draft.recipient.nameSourceLabel)
    }
    @Test fun verifiedNameHasPriorityAndOnlyFreshSameRecipientVerificationIsRetained() = runBlocking {
        var now = 1_800_000_000_000L
        val provider = object : AuthorizedBeneficiaryProvider {
            override val sourceId = "TEST_ONLY"
            override suspend fun lookup(query: BeneficiaryQuery) = BeneficiaryReply(query.requestId, query.upiId,
                VerificationStatus.VERIFIED, "Synthetic Bank Name", sourceId, now)
        }
        val draft = PaymentPreparation(BeneficiaryNameResolver(provider, { now }))
        draft.resolve(draft.select("sample@upi", "1", qrSuppliedName = "QR Shop"))
        val frozen = draft.review()!!
        assertEquals("Synthetic Bank Name", frozen.recipient.displayName)
        assertNull(frozen.recipient.nameSourceLabel)
        assertEquals("QR Shop", frozen.recipient.qrSuppliedName)
        assertEquals("TEST_ONLY", frozen.recipient.verificationSource)
        draft.select("sample@upi")
        assertEquals("Synthetic Bank Name", draft.recipient.displayName)
        assertNull(draft.recipient.qrSuppliedName)
        now += 300_001
        draft.select("sample@upi")
        assertNull(draft.recipient.verifiedBeneficiaryName)
        draft.select("other@upi")
        assertNull(draft.recipient.verifiedBeneficiaryName)
    }
}

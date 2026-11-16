package com.festpay.app

import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic provider exists only in test sources. No network or production authority. */
class BeneficiaryVerificationTest {
    private val now = 1_800_000_000_000L
    private fun provider(block: suspend (BeneficiaryQuery) -> BeneficiaryReply) = object : AuthorizedBeneficiaryProvider {
        override val sourceId = "TEST_ONLY_AUTHORIZED_BOUNDARY"
        override suspend fun lookup(query: BeneficiaryQuery) = block(query)
    }
    private fun evidence(q: BeneficiaryQuery) = BeneficiaryReply(q.requestId, q.upiId, VerificationStatus.VERIFIED,
        "SYNTHETIC BANK NAME", "TEST_ONLY_AUTHORIZED_BOUNDARY", now)

    @Test fun syntaxNormalizationPreservesAddressCaseAndRejectsMalformedInputs() {
        assertEquals("Example.Name@UPI", PaymentInputValidator.normalizedVpa(" Example.Name@UPI "))
        assertEquals("a@bank", PaymentInputValidator.normalizedVpa("a@bank"))
        for (bad in listOf("", " ", "name", "name@", "@upi", "a b@upi", "a@b@upi", "a@upi\n", "a@upi;*99#", "a".repeat(256)+"@upi")) {
            assertNull(bad, PaymentInputValidator.normalizedVpa(bad))
            assertEquals(VerificationStatus.INVALID_UPI_ID, BeneficiaryNameResolver().initial(bad).verificationStatus)
        }
    }
    @Test fun noConfiguredProviderCannotProduceBankIdentity() = runBlocking {
        val r = BeneficiaryNameResolver().resolve(BeneficiaryQuery("local-request", "valid@upi"))
        assertEquals(VerificationStatus.UNAVAILABLE, r.verificationStatus)
        assertNull(r.verifiedBeneficiaryName); assertNull(r.verificationSource); assertNull(r.verificationTimestamp)
        assertEquals("Name unavailable", r.displayName)
    }
    @Test fun verifiedResultRequiresCorrelatedFreshProvenanceAndNeverAuthorizesPayment() = runBlocking {
        val resolver = BeneficiaryNameResolver(provider(::evidence), { now })
        assertEquals(VerificationStatus.PENDING, resolver.initial("valid@upi").verificationStatus)
        val r = resolver.resolve(BeneficiaryQuery("request", "valid@upi"))
        assertEquals(VerificationStatus.VERIFIED, r.verificationStatus)
        assertEquals("SYNTHETIC BANK NAME", r.verifiedBeneficiaryName)
        assertEquals("TEST_ONLY_AUTHORIZED_BOUNDARY", r.verificationSource)
        assertEquals(PaymentState.UNKNOWN, PaymentIntegrityPolicy.carrierOutcome("successful"))
        assertFalse(PaymentIntegrityPolicy.confirmationAvailable)
    }
    @Test fun missingMalformedDifferentOrStaleEvidenceCannotSupplyAName() = runBlocking {
        val query = BeneficiaryQuery("request", "valid@upi")
        val valid = evidence(query)
        for (bad in listOf(valid.copy(requestId="other"), valid.copy(upiId="other@upi"), valid.copy(bankRecordName=null),
            valid.copy(bankRecordName=""), valid.copy(bankRecordName="bad\nname"), valid.copy(source=null),
            valid.copy(source="QR_PN"), valid.copy(verifiedAt=null), valid.copy(verifiedAt=now-300_001), valid.copy(verifiedAt=now+1))) {
            val r = BeneficiaryNameResolver(provider { bad }, { now }).resolve(query)
            assertEquals(VerificationStatus.ERROR, r.verificationStatus); assertNull(r.verifiedBeneficiaryName)
        }
    }
    @Test fun unavailableInvalidErrorsAndTimeoutRemainDistinctFromVerification() = runBlocking {
        val q = BeneficiaryQuery("request", "valid@upi")
        for (status in listOf(VerificationStatus.UNAVAILABLE, VerificationStatus.INVALID_UPI_ID, VerificationStatus.ERROR)) {
            val r = BeneficiaryNameResolver(provider { BeneficiaryReply(it.requestId,it.upiId,status) }, { now }).resolve(q)
            assertEquals(status, r.verificationStatus); assertNull(r.verifiedBeneficiaryName)
        }
        val failure = BeneficiaryNameResolver(provider { error("Synthetic provider unavailable") }, { now }).resolve(q)
        assertEquals(VerificationProblem.PROVIDER_ERROR, failure.problem)
        val timeout = BeneficiaryNameResolver(provider { delay(200); evidence(it) }, { now }, timeoutMs=10).resolve(q)
        assertEquals(VerificationProblem.TIMEOUT, timeout.problem); assertNull(timeout.verifiedBeneficiaryName)
    }
    @Test fun oldResponseCannotReplaceNewRecipientEvenWhenAddressIsReused() = runBlocking {
        val firstReply = CompletableDeferred<BeneficiaryReply>()
        var first: BeneficiaryQuery? = null
        val draft = PaymentPreparation(BeneficiaryNameResolver(provider { first=it; firstReply.await() }, { now }))
        val old = draft.select("old@upi", "1.00")
        val task = launch { draft.resolve(old) }
        yield()
        draft.select("new@upi", "2.00")
        draft.select("old@upi", "3.00")
        firstReply.complete(evidence(checkNotNull(first)))
        task.join()
        assertEquals("old@upi", draft.recipient.upiId)
        assertEquals(VerificationStatus.PENDING, draft.recipient.verificationStatus)
        assertNull(draft.recipient.verifiedBeneficiaryName)
        assertEquals("3.00", draft.amount)
    }
    @Test fun reviewFreezesExactDetailsAndAnyEditRequiresFreshReview() {
        val draft = PaymentPreparation()
        draft.select("one@upi", "1", "note")
        val reviewed = draft.review()!!
        assertEquals("1.00", reviewed.amount); assertTrue(draft.isCurrent(reviewed))
        draft.editAmount("2")
        assertFalse(draft.isCurrent(reviewed)); assertEquals("1.00", reviewed.amount)
        val second = draft.review()!!
        draft.select("two@upi", "3")
        assertFalse(draft.isCurrent(second)); assertEquals("one@upi", reviewed.recipient.upiId)
        assertNull(draft.recipient.verifiedBeneficiaryName); assertEquals("", draft.note)
        draft.clear(); assertNull(draft.review())
    }
    @Test fun expiredVerifiedNameCannotBeReusedForReview() = runBlocking {
        var clock = now
        val draft = PaymentPreparation(BeneficiaryNameResolver(provider(::evidence), { clock }))
        val ticket = draft.select("valid@upi", "1.00")
        draft.resolve(ticket)
        val review = draft.review()!!
        clock += 300_001
        assertNull(draft.review()); assertFalse(draft.isCurrent(review))
    }
}

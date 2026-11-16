package com.festpay.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class VerificationStatus { VERIFIED, UNAVAILABLE, INVALID_UPI_ID, PENDING, ERROR }
enum class VerificationProblem { NONE, NO_AUTHORIZED_SOURCE, INVALID_ADDRESS, PROVIDER_ERROR, TIMEOUT, INVALID_EVIDENCE }

@ConsistentCopyVisibility
data class PaymentRecipient internal constructor(
    val upiId: String,
    val verifiedBeneficiaryName: String? = null,
    val verificationStatus: VerificationStatus,
    val verificationSource: String? = null,
    val verificationTimestamp: Long? = null,
    val problem: VerificationProblem = VerificationProblem.NONE,
    /** Requester-controlled QR pn, never bank identity. */
    val qrSuppliedName: String? = null
) {
    init {
        require(qrSuppliedName == null || (qrSuppliedName.isNotBlank() && qrSuppliedName == qrSuppliedName.trim() &&
            qrSuppliedName.length <= 100 && qrSuppliedName.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }))
        if (verificationStatus == VerificationStatus.VERIFIED) {
            require(PaymentInputValidator.isVpa(upiId) && !verifiedBeneficiaryName.isNullOrBlank() &&
                !verificationSource.isNullOrBlank() && verificationTimestamp != null)
        } else require(verifiedBeneficiaryName == null && verificationSource == null && verificationTimestamp == null)
    }
    val qrNameProvenance: String? get() = qrSuppliedName?.let { "QR_PN_UNVERIFIED" }
    val displayName: String get() = verifiedBeneficiaryName ?: qrSuppliedName ?: "Name unavailable"
    val nameSourceLabel: String? get() = if (verifiedBeneficiaryName == null && qrSuppliedName != null) "Name from QR — unverified" else null
}

data class BeneficiaryQuery(val requestId: String, val upiId: String)
data class BeneficiaryReply(
    val requestId: String,
    val upiId: String,
    val status: VerificationStatus,
    val bankRecordName: String? = null,
    val source: String? = null,
    val verifiedAt: Long? = null
)

/** Only a genuinely authorized adapter may implement this trust boundary.
 * It must authenticate its provider and bank-name provenance before returning a reply.
 * No production implementation or credentials are configured in V4SH. */
interface AuthorizedBeneficiaryProvider {
    val sourceId: String
    suspend fun lookup(query: BeneficiaryQuery): BeneficiaryReply
}

class BeneficiaryNameResolver(
    private val provider: AuthorizedBeneficiaryProvider? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 5_000,
    private val maximumAgeMs: Long = 300_000
) {
    fun isFresh(recipient: PaymentRecipient): Boolean = recipient.verificationStatus != VerificationStatus.VERIFIED ||
        (recipient.verificationTimestamp != null && recipient.verificationTimestamp > 0 && recipient.verificationTimestamp <= clock() && clock() - recipient.verificationTimestamp <= maximumAgeMs)
    fun initial(input: String): PaymentRecipient {
        val vpa = PaymentInputValidator.normalizedVpa(input)
            ?: return PaymentRecipient(input.trim().take(255), verificationStatus = VerificationStatus.INVALID_UPI_ID, problem = VerificationProblem.INVALID_ADDRESS)
        return PaymentRecipient(vpa, verificationStatus = if (provider == null) VerificationStatus.UNAVAILABLE else VerificationStatus.PENDING,
            problem = if (provider == null) VerificationProblem.NO_AUTHORIZED_SOURCE else VerificationProblem.NONE)
    }

    suspend fun resolve(query: BeneficiaryQuery): PaymentRecipient {
        val initial = initial(query.upiId)
        if (initial.verificationStatus != VerificationStatus.PENDING) return initial
        fun error(problem: VerificationProblem) = PaymentRecipient(initial.upiId, verificationStatus = VerificationStatus.ERROR, problem = problem)
        val reply = try {
            withTimeoutOrNull(timeoutMs) { provider!!.lookup(query) } ?: return error(VerificationProblem.TIMEOUT)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { return error(VerificationProblem.PROVIDER_ERROR) }
        if (reply.requestId != query.requestId || reply.upiId != initial.upiId) return error(VerificationProblem.INVALID_EVIDENCE)
        if (reply.status != VerificationStatus.VERIFIED) {
            return when (reply.status) {
                VerificationStatus.UNAVAILABLE -> PaymentRecipient(initial.upiId, verificationStatus = VerificationStatus.UNAVAILABLE, problem = VerificationProblem.NO_AUTHORIZED_SOURCE)
                VerificationStatus.INVALID_UPI_ID -> PaymentRecipient(initial.upiId, verificationStatus = VerificationStatus.INVALID_UPI_ID, problem = VerificationProblem.INVALID_ADDRESS)
                else -> error(VerificationProblem.PROVIDER_ERROR)
            }
        }
        val name = reply.bankRecordName
        val timestamp = reply.verifiedAt
        val now = clock()
        if (name.isNullOrBlank() || name != name.trim() || name.length > 100 ||
            name.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() } ||
            reply.source.isNullOrBlank() || reply.source != provider!!.sourceId ||
            timestamp == null || timestamp <= 0 || timestamp > now || now - timestamp > maximumAgeMs) return error(VerificationProblem.INVALID_EVIDENCE)
        return PaymentRecipient(initial.upiId, name, VerificationStatus.VERIFIED, reply.source, timestamp)
    }
}

@ConsistentCopyVisibility
data class ReviewedPayment internal constructor(val revision: Long, val recipient: PaymentRecipient, val amount: String, val note: String)

/** One volatile recipient/draft for QR and manual entry. Never financial authority or persistence. */
class PaymentPreparation(private val resolver: BeneficiaryNameResolver = BeneficiaryNameResolver()) {
    var recipient by mutableStateOf(resolver.initial(""))
        private set
    var amount by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var revision by mutableStateOf(0L)
        private set
    var selectionId by mutableStateOf("")
        private set
    private var query: BeneficiaryQuery? = null

    fun select(input: String, amount: String = "", note: String = "", qrSuppliedName: String? = null): BeneficiaryQuery {
        revision++
        val initial = resolver.initial(input)
        val sameVerified = recipient.takeIf { it.upiId == initial.upiId && it.verificationStatus == VerificationStatus.VERIFIED && resolver.isFresh(it) }
        recipient = (sameVerified ?: initial).copy(qrSuppliedName = qrSuppliedName?.trim()?.takeIf { it.isNotEmpty() })
        this.amount = amount
        this.note = note
        return BeneficiaryQuery(UUID.randomUUID().toString(), recipient.upiId).also { query = it; selectionId = it.requestId }
    }
    fun clear() { select("") }
    fun editAmount(value: String) { if (value != amount) { revision++; amount = value } }
    fun editNote(value: String) { if (value != note) { revision++; note = value } }
    suspend fun resolve(ticket: BeneficiaryQuery) {
        if (ticket != query) return
        val result = resolver.resolve(ticket)
        if (ticket == query && result.upiId == recipient.upiId) {
            val updated = result.copy(qrSuppliedName = recipient.qrSuppliedName)
            if (updated != recipient) revision++
            recipient = updated
        }
    }
    fun review(): ReviewedPayment? {
        val money = PaymentInputValidator.amount(amount) ?: return null
        if (!PaymentInputValidator.isVpa(recipient.upiId) || !resolver.isFresh(recipient) || recipient.verificationStatus in listOf(VerificationStatus.INVALID_UPI_ID, VerificationStatus.PENDING) ||
            note.length > 140 || note.any { it.isISOControl() }) return null
        return ReviewedPayment(revision, recipient, money, note)
    }
    fun isCurrent(review: ReviewedPayment): Boolean = review.revision == revision && review == this.review()
}

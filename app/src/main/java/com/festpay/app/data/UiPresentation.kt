package com.festpay.app.data

import com.festpay.app.ui.viewmodel.PaymentState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure, user-facing wording. Nothing here establishes a financial outcome or verifies a recipient. */
object UiPresentation {
    fun greeting(hour: Int, displayName: String?): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..23 -> "Good evening"
            else -> "Hello"
        }
        val first = displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
        return if (first == null) "$part." else "$part, $first."
    }

    fun verificationLabel(status: VerificationStatus): String = when (status) {
        VerificationStatus.VERIFIED -> "Recipient name verified"
        VerificationStatus.UNAVAILABLE -> "Recipient name not verified"
        VerificationStatus.INVALID_UPI_ID -> "Invalid UPI ID"
        VerificationStatus.PENDING -> "Checking recipient name"
        VerificationStatus.ERROR -> "Recipient name could not be checked"
    }

    /** Name saved with an attempt can never be called verified: no authorized source exists. */
    fun payeeTitle(name: String, vpa: String): String = name.trim().ifEmpty { vpa }

    fun outcomeLabel(state: PaymentState): String = when (PaymentIntegrityPolicy.restoredState(state)) {
        PaymentState.SUCCESS -> "Confirmed"
        PaymentState.FAILED -> "Failed"
        PaymentState.CANCELLED -> "Cancelled"
        else -> "Outcome unknown"
    }

    /** Wording for an attempt the owner closed from their own bank record. Never presented as bank-confirmed. */
    fun ownerResolutionLabel(resolution: String?): String? = when (resolution) {
        "DEBITED" -> "Resolved by you: money was debited"
        "NOT_DEBITED" -> "Resolved by you: money was not debited"
        else -> null
    }

    fun evidenceLabel(evidence: String): String = when (evidence) {
        "NO_AUTHORIZED_CONFIRMATION" -> "No bank confirmation available"
        else -> "Not available"
    }

    fun shortTimestamp(value: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
        Instant.parse(value).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
    } catch (_: Exception) {
        "Timestamp unavailable for this older record"
    }

    fun qrIssueLabel(issue: QrIssue): String = when (issue) {
        QrIssue.NONE -> ""
        QrIssue.UNSUPPORTED_SCHEME, QrIssue.UNSUPPORTED_ACTION -> "This is not a UPI payment code."
        QrIssue.MISSING_RECIPIENT -> "No recipient UPI ID was found in this code."
        QrIssue.INVALID_RECIPIENT -> "The recipient UPI ID in this code is not valid."
        QrIssue.INVALID_AMOUNT -> "The amount in this code is not valid (above zero, up to 2 decimals)."
        QrIssue.UNSUPPORTED_CURRENCY -> "Only INR payments are supported."
        QrIssue.MALFORMED_URI, QrIssue.DUPLICATE_FIELD, QrIssue.INVALID_FIELD -> "This code is damaged or not a standard UPI request."
    }

    /** Operational progress only (never a payment outcome). */
    fun carrierRank(stage: CarrierStage): Int = when (stage) {
        CarrierStage.WAITING -> 0
        CarrierStage.METHOD -> 1
        CarrierStage.RECIPIENT -> 2
        CarrierStage.AMOUNT_PENDING -> 3
        CarrierStage.AMOUNT, CarrierStage.AMOUNT_SENT -> 4
        CarrierStage.REMARK_SENT, CarrierStage.REVIEW, CarrierStage.CONFIRMATION_SENT -> 5
        CarrierStage.PIN -> 6
        CarrierStage.RESULT -> 7
        CarrierStage.INTERRUPTED -> 0
    }

    /** (rank at which the step is the current one, label). */
    val PROGRESS_STEPS: List<Pair<Int, String>> = listOf(
        0 to "Opening the carrier menu",
        1 to "Choosing Send Money",
        2 to "Choosing the UPI ID option",
        3 to "Entering the UPI ID",
        4 to "Entering the amount",
        6 to "You enter your UPI PIN on the carrier screen",
        7 to "Carrier reply"
    )

    fun interruptionText(issue: CarrierIssue): String = when (issue) {
        CarrierIssue.CARRIER_REJECTED -> "The carrier rejected the request. Check the details and your bank before trying again."
        CarrierIssue.MISMATCH -> "The carrier showed a different UPI ID or amount, so V4SH stopped. Check your bank before trying again."
        CarrierIssue.UNSUPPORTED -> "The carrier showed a screen V4SH does not recognise, so it stopped. Finish or cancel on the carrier screen."
        CarrierIssue.TIMEOUT -> "No carrier reply was seen in time. Check your bank before paying again."
        CarrierIssue.DISCONNECTED -> "Accessibility disconnected, so V4SH stopped. Turn it on again in Android settings."
        CarrierIssue.USER_STOPPED -> "You stopped V4SH's assistance."
        CarrierIssue.INPUT_UNCERTAIN, CarrierIssue.UNEXPECTED_AUTHENTICATION, CarrierIssue.NONE ->
            "V4SH stopped because it could not be sure what the carrier was asking. Check the carrier screen and your bank."
    }

    fun carrierResultText(wording: CarrierResultObservation?): String = when (wording) {
        CarrierResultObservation.SUCCESS_WORDING -> "The carrier's final message sounded like success. Your bank is the authority; check it to be sure."
        CarrierResultObservation.FAILURE_WORDING -> "The carrier's final message sounded like a failure. Your bank is the authority; check it to be sure."
        else -> "The carrier's final message was not clear. Check your bank for the result."
    }

    /** First letter of a name, upper-case, for the profile circle. */
    fun initial(name: String?): String = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    /** Status for an attempt the carrier's final message closed (owner policy). */
    fun carrierOutcomeLabel(outcome: String?): String? = when (outcome) {
        "SUCCESS" -> "Successful · confirmed by carrier"
        "FAILED" -> "Failed · reported by carrier"
        else -> null
    }

    /** Short history wording for what the carrier itself said last. Always marked unverified. */
    fun carrierResultBadge(wording: String?): String? = when (wording) {
        "SUCCESS_WORDING" -> "Carrier said: success (unverified)"
        "FAILURE_WORDING" -> "Carrier said: failed (unverified)"
        else -> null
    }

    fun amountProblem(input: String): String? {
        if (input.isEmpty() || PaymentInputValidator.amount(input) != null) return null
        val digits = input.substringBefore('.')
        val fraction = input.substringAfter('.', "")
        return when {
            input.endsWith(".") && fraction.isEmpty() && digits.isNotEmpty() -> "Add the paise digits, or remove the decimal point."
            fraction.length > 2 -> "Use at most 2 decimal places."
            digits.length > 7 -> "That amount is too large."
            input.toBigDecimalOrNull()?.signum() == 0 -> "Enter an amount above ₹0."
            else -> "Enter a valid amount such as 100 or 100.50."
        }
    }
}

enum class HomeReadinessState { READY, SETUP_NEEDED, BLOCKED }
data class HomeReadiness(val state: HomeReadinessState, val headline: String, val detail: String)

object HomeReadinessEvaluator {
    fun evaluate(
        storageOk: Boolean, earlierHistory: Boolean, unresolvedAttempt: Boolean,
        consent: Boolean, phonePermission: Boolean, accessibilityConnected: Boolean, cellularBlock: String?
    ): HomeReadiness = when {
        !storageOk -> HomeReadiness(HomeReadinessState.BLOCKED, "Payments paused",
            "Protected payment storage is unavailable. Nothing was reset.")
        unresolvedAttempt -> HomeReadiness(HomeReadinessState.BLOCKED, "Payments paused",
            "An earlier payment is unresolved. Check your bank history; V4SH will not start another.")
        earlierHistory -> HomeReadiness(HomeReadinessState.BLOCKED, "Payments paused",
            "Earlier unverified history needs review before another payment.")
        !consent -> HomeReadiness(HomeReadinessState.SETUP_NEEDED, "Setup needed", "Allow cellular screen assistance to pay or check balance.")
        !phonePermission -> HomeReadiness(HomeReadinessState.SETUP_NEEDED, "Setup needed", "Phone permission is needed to start *99#.")
        !accessibilityConnected -> HomeReadiness(HomeReadinessState.SETUP_NEEDED, "Setup needed", "Turn on V4SH in Android Accessibility settings.")
        cellularBlock != null -> HomeReadiness(HomeReadinessState.SETUP_NEEDED, "Cellular not ready", cellularBlock)
        else -> HomeReadiness(HomeReadinessState.READY, "Ready for *99# payments",
            "Carrier support is confirmed only when a payment starts.")
    }
}

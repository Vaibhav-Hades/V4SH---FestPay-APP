package com.festpay.app.data

/** Operational observation only. This reducer has no Android, journal or payment-launch access. */
enum class CarrierStage { WAITING, METHOD, RECIPIENT, AMOUNT_PENDING, AMOUNT, AMOUNT_SENT, REMARK_SENT, REVIEW, CONFIRMATION_SENT, PIN, RESULT, INTERRUPTED }
enum class CarrierIssue { NONE, UNSUPPORTED, MISMATCH, INPUT_UNCERTAIN, DISCONNECTED, USER_STOPPED, UNEXPECTED_AUTHENTICATION, TIMEOUT, CARRIER_REJECTED }
enum class CarrierInputKind { ROOT, METHOD, RECIPIENT, AMOUNT, REMARK_SKIP, CONFIRMATION, EXIT }
data class CarrierInput(val kind: CarrierInputKind, val value: String)
data class CarrierReview(val recipient: String, val amount: String, val displayedName: String?)
data class CarrierProgress(
    val stage: CarrierStage = CarrierStage.WAITING,
    val revision: Long = 0,
    val review: CarrierReview? = null,
    val amountAccepted: Boolean = false,
    val authorized: Boolean = false,
    val issue: CarrierIssue = CarrierIssue.NONE,
    val resultWording: CarrierResultObservation? = null,
    val waitingForResult: Boolean = false,
    /** The carrier's own printed reference, when its final message showed one for this exact amount. Unverified. */
    val reference: String? = null,
    /** The carrier's PIN prompt printed a UPI ID or amount that differs from (or is ambiguous against) the request. V4SH warns and keeps watching. */
    val detailsMismatch: Boolean = false,
    /** The carrier's own final menu was closed with its Exit option (nothing else is ever chosen there). */
    val exited: Boolean = false,
    /** The payee name the carrier printed. Carrier-reported, never bank-verified. */
    val payeeName: String? = null
)

class CarrierPaymentFlow(val recipient: String, val amount: String) {
    init { require(PaymentInputValidator.isVpa(recipient) && PaymentInputValidator.amount(amount) == amount) }
    var progress = CarrierProgress()
        private set
    private var lastEvent = 0L
    private var offered: CarrierInput? = null
    private var reviewKey: CarrierReview? = null

    private fun update(next: CarrierProgress) {
        if (next.copy(revision = progress.revision) != progress) progress = next.copy(revision = progress.revision + 1)
    }
    fun interrupt(issue: CarrierIssue = CarrierIssue.DISCONNECTED) {
        // A result the carrier already gave is never overwritten by a later "interruption" (for example the dialog closing).
        if (progress.stage == CarrierStage.RESULT) { offered = null; return }
        offered = null; reviewKey = null
        update(progress.copy(stage = CarrierStage.INTERRUPTED, authorized = false, review = null, issue = issue))
    }
    /** [labels] are the prompt's static instruction labels only; input values are never supplied. */
    fun observeAuthentication(eventTime: Long, supportedUpiPrompt: Boolean = false, labels: String = "") {
        if (eventTime <= lastEvent || eventTime <= 0 || progress.stage in setOf(CarrierStage.INTERRUPTED, CarrierStage.RESULT)) return
        lastEvent = eventTime
        authenticate(supportedUpiPrompt, labels)
    }
    private fun authenticate(supportedUpiPrompt: Boolean, labels: String) {
        offered = null
        if (!supportedUpiPrompt) { interrupt(CarrierIssue.UNEXPECTED_AUTHENTICATION); return }
        when (progress.stage) {
            // Repeated authentication frames keep whatever was observed when the prompt first appeared.
            CarrierStage.PIN -> update(progress.copy(authorized = false, waitingForResult = false))
            CarrierStage.REVIEW -> if (progress.review == null || !progress.amountAccepted) interrupt(CarrierIssue.UNEXPECTED_AUTHENTICATION)
                else update(progress.copy(stage = CarrierStage.PIN, authorized = false, review = null, waitingForResult = false))
            // Standard *99# flow: no separate review screen; the PIN prompt itself states the payment.
            // Its static instruction is compared with the immutable request. The PIN field is never read.
            CarrierStage.AMOUNT_SENT, CarrierStage.REMARK_SENT -> {
                val pin = progress.copy(stage = CarrierStage.PIN, authorized = false, review = null, amountAccepted = false, waitingForResult = false)
                when (UssdNavigationPolicy.authenticationPromptDetails(labels, recipient, amount)) {
                    // V4SH cannot stop the carrier's PIN screen, and stopping would only forfeit reading the result.
                    // Warn loudly and keep observing; a success still needs the reviewed amount in the final message.
                    UssdNavigationPolicy.PromptDetails.MISMATCH -> update(pin.copy(detailsMismatch = true))
                    UssdNavigationPolicy.PromptDetails.MATCHING -> update(pin.copy(review = CarrierReview(recipient, amount, null), amountAccepted = true))
                    UssdNavigationPolicy.PromptDetails.AMOUNT_ONLY -> update(pin.copy(amountAccepted = true))
                    // Nothing displayed: the user is told the details were not verified by V4SH.
                    UssdNavigationPolicy.PromptDetails.ABSENT -> update(pin)
                }
            }
            else -> interrupt(CarrierIssue.UNEXPECTED_AUTHENTICATION)
        }
    }

    /** Called only for fresh, owned, approved system-dialog static labels, never input values. */
    fun observe(rawLabels: String, editable: Boolean, eventTime: Long): CarrierInput? {
        val labels = plain(rawLabels)
        if (eventTime <= lastEvent || eventTime <= 0 || progress.stage == CarrierStage.INTERRUPTED) return null
        lastEvent = eventTime
        if (progress.stage == CarrierStage.RESULT) {
            // The result is decided. The carrier may show "1. Save 2. Exit" in a LATER frame: still close it with Exit, never Save.
            if (progress.exited || !editable || offered != null) return null
            val exit = UssdNavigationPolicy.exitDigit(labels) ?: return null
            offered = CarrierInput(CarrierInputKind.EXIT, exit)
            return offered
        }
        if (AccessibilityTextPolicy.isSensitivePrompt(labels, editable)) {
            authenticate(UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt(labels), labels); return null
        }
        if (labels.isBlank()) return null
        if (progress.stage == CarrierStage.PIN) {
            // The caller has already excluded password/sensitive nodes. Wording is NOT finality.
            val wording = CarrierResultClassifier.observe(labels)
            // A final message may be followed by an input box ("Select option to 1. Save contact 2. Exit").
            if (wording != CarrierResultObservation.INCONCLUSIVE) {
                update(progress.copy(stage = CarrierStage.RESULT, resultWording = wording, reference = CarrierReference.parse(labels, amount),
                    payeeName = CarrierPayee.parse(labels) ?: progress.payeeName))
                // The final message is followed by "Select option to 1. Save contact 2. Exit": close it with Exit, never Save.
                val exit = if (editable) UssdNavigationPolicy.exitDigit(labels) else null
                offered = exit?.let { CarrierInput(CarrierInputKind.EXIT, it) }
                return offered
            }
            update(progress.copy(waitingForResult = true, payeeName = progress.payeeName ?: CarrierPayee.parse(labels)))
            return null
        }
        if (Regex("\\b(?:please\\s+wait|ussd\\s+code\\s+running)\\b", RegexOption.IGNORE_CASE).containsMatchIn(labels) ||
            UssdNavigationPolicy.isTransientPlaceholder(labels, editable)) return null
        // Before authentication: an explicit carrier rejection stops assistance with an accurate reason,
        // even when the same frame re-shows a known prompt (otherwise the flow would wait silently).
        if (UssdNavigationPolicy.isCarrierRejection(labels)) { interrupt(CarrierIssue.CARRIER_REJECTED); return null }
        // An OS input result is still pending. Preserve it without offering another write.
        if (offered != null) return null
        val root = rootOption(labels)
        val method = methodOption(labels)
        val recipientPrompt = Regex("\\benter\\s+(?:(?:the|recipient|beneficiary)\\s+)?(?:upi\\s*(?:id|address)|vpa)\\b", RegexOption.IGNORE_CASE).containsMatchIn(labels)
        val amountPrompt = UssdNavigationPolicy.isAmountPrompt(labels)
        val review = parseReview(labels)
        val stage = progress.stage
        val remarkSkip = if (editable && stage in setOf(CarrierStage.AMOUNT_SENT, CarrierStage.REMARK_SENT)) UssdNavigationPolicy.remarkSkipDigit(labels) else null
        val action = when (stage) {
            CarrierStage.WAITING -> if (editable && root != null) CarrierInput(CarrierInputKind.ROOT, root) else null
            CarrierStage.METHOD -> if (editable && method != null) CarrierInput(CarrierInputKind.METHOD, method) else null
            CarrierStage.RECIPIENT -> if (editable && recipientPrompt) CarrierInput(CarrierInputKind.RECIPIENT, recipient) else null
            CarrierStage.AMOUNT_PENDING -> {
                if (editable && amountPrompt) {
                    // Pay already authorized this immutable amount. Do not ask for it again.
                    update(progress.copy(stage = CarrierStage.AMOUNT))
                    CarrierInput(CarrierInputKind.AMOUNT, amount)
                } else {
                    if (isReview(labels)) interrupt(CarrierIssue.UNSUPPORTED)
                    null
                }
            }
            CarrierStage.AMOUNT -> null // Submission in flight; never re-offer a field write.
            CarrierStage.AMOUNT_SENT, CarrierStage.REMARK_SENT -> when {
                review != null -> {
                    reviewKey = review
                    update(progress.copy(stage = CarrierStage.REVIEW, review = review, amountAccepted = true, authorized = false))
                    null
                }
                isReview(labels) -> { interrupt(CarrierIssue.MISMATCH); null }
                // Optional remark: only the carrier's own explicit skip instruction; no remark text is invented.
                // A repeated remark prompt after a skip never replays it.
                stage == CarrierStage.AMOUNT_SENT && remarkSkip != null -> CarrierInput(CarrierInputKind.REMARK_SKIP, remarkSkip)
                else -> null
            }
            CarrierStage.REVIEW -> {
                if (review == null) {
                    // An unexpected/mismatching review never consumes an earlier confirmation.
                    if (isReview(labels)) interrupt(CarrierIssue.MISMATCH)
                    else if (root == null && method == null && !recipientPrompt && !amountPrompt) interrupt(CarrierIssue.UNSUPPORTED)
                    null
                } else if (review != reviewKey) {
                    reviewKey = review
                    update(progress.copy(review = review, authorized = false))
                    null
                } else null // Exact review is a manual native handoff, never an input offer.
            }
            else -> null
        }
        if (progress.stage == CarrierStage.RECIPIENT && editable && amountPrompt) {
            // A prompt alone is not proof a VPA was accepted. Only reach this after sending VPA.
            interrupt(CarrierIssue.UNSUPPORTED)
        }
        // Duplicate prior screens do not replay inputs; unsupported prompts stop assistance.
        val known = root != null || method != null || recipientPrompt || amountPrompt || review != null || remarkSkip != null
        // Before anything has been typed, an unrecognised non-editable frame is a transient carrier message
        // (the real menu follows). Nothing was sent, so waiting is safe; later stages still stop on unknown frames.
        val waitingForFirstMenu = progress.stage == CarrierStage.WAITING && !editable
        if (action == null && !known && !waitingForFirstMenu && progress.stage !in setOf(CarrierStage.PIN, CarrierStage.RESULT, CarrierStage.INTERRUPTED)) interrupt(CarrierIssue.UNSUPPORTED)
        offered = action
        return action
    }

    fun inputResult(input: CarrierInput, submitted: Boolean) {
        if (input != offered || progress.stage == CarrierStage.INTERRUPTED) return
        offered = null
        if (input.kind == CarrierInputKind.EXIT) { if (submitted) update(progress.copy(exited = true)); return }
        if (!submitted) { interrupt(CarrierIssue.INPUT_UNCERTAIN); return }
        update(progress.copy(stage = when (input.kind) {
            CarrierInputKind.ROOT -> CarrierStage.METHOD
            CarrierInputKind.METHOD -> CarrierStage.RECIPIENT
            CarrierInputKind.RECIPIENT -> CarrierStage.AMOUNT_PENDING
            CarrierInputKind.AMOUNT -> CarrierStage.AMOUNT_SENT
            CarrierInputKind.REMARK_SKIP -> CarrierStage.REMARK_SENT
            CarrierInputKind.CONFIRMATION -> CarrierStage.CONFIRMATION_SENT
            CarrierInputKind.EXIT -> progress.stage
        }, authorized = false))
    }

    private fun isReview(labels: String) = Regex("\\b(?:confirm|review)\\b", RegexOption.IGNORE_CASE).containsMatchIn(labels)
    private fun parseReview(labels: String): CarrierReview? {
        if (!UssdNavigationPolicy.isMatchingReview(labels, recipient, amount)) return null
        val names = Regex("(?im)^\\s*(?:beneficiary|payee)\\s+name\\s*:\\s*([\\p{L} .'-]{1,100})\\s*$")
            .findAll(labels).map { it.groupValues[1].trim() }.toList()
        val name = names.singleOrNull()?.takeIf { it.isNotBlank() && !Regex("\\b(?:pin|otp|password|passcode)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        return CarrierReview(recipient, amount, name)
    }
    companion object {
        /** Revalidate the specific offered action against fresh static labels before each OS action. */
        fun matchesInput(input: CarrierInput, rawLabels: String, editable: Boolean): Boolean {
            val labels = plain(rawLabels)
            if (!editable || AccessibilityTextPolicy.isSensitivePrompt(labels, editable)) return false
            return when (input.kind) {
                CarrierInputKind.ROOT -> rootOption(labels) == input.value
                CarrierInputKind.METHOD -> methodOption(labels) == input.value
                CarrierInputKind.RECIPIENT -> Regex("\\benter\\s+(?:(?:the|recipient|beneficiary)\\s+)?(?:upi\\s*(?:id|address)|vpa)\\b", RegexOption.IGNORE_CASE).containsMatchIn(labels)
                CarrierInputKind.AMOUNT -> UssdNavigationPolicy.isAmountPrompt(labels)
                CarrierInputKind.REMARK_SKIP -> UssdNavigationPolicy.remarkSkipDigit(labels) == input.value
                CarrierInputKind.CONFIRMATION -> false
                CarrierInputKind.EXIT -> UssdNavigationPolicy.exitDigit(labels) == input.value
            }
        }
        /** Carrier dialogs may use non-breaking spaces; treat them as ordinary spaces. */
        fun plain(text: String): String = text.replace('\u00a0', ' ').replace('\u202f', ' ').replace('\u2007', ' ')
        private fun option(rawText: String, label: String): String? = plain(rawText).let { text -> Regex("\\b([1-9])\\s*[.)-]?\\s*$label\\b", RegexOption.IGNORE_CASE)
            .findAll(text).map { it.groupValues[1] }.distinct().toList().singleOrNull() }
        fun rootOption(text: String): String? {
            val send = option(text, "send\\s+money") ?: return null
            val balance = option(text, "check\\s+balance") ?: return null
            return send.takeIf { it != balance }
        }
        fun methodOption(text: String): String? {
            val upi = option(text, "upi\\s*(?:id|address)") ?: return null
            val header = Regex("send\\s+money\\s+(?:to|using|via)", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val alternative = option(text, "mobile(?:\\s*(?:no|number))?") ?: option(text, "(?:account|ifsc)")
            return upi.takeIf { header || (alternative != null && alternative != upi) }
        }
    }
}

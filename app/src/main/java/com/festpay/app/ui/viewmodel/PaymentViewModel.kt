// File: PaymentViewModel.kt
package com.festpay.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.TransactionRepository
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.PaymentInputValidator
import com.festpay.app.data.PaymentIntegrityPolicy
import com.festpay.app.data.OwnedUssdCommand
import com.festpay.app.data.UssdSessionTransport
import com.festpay.app.data.PaymentDiagnostics
import com.festpay.app.data.PrepareFailure
import kotlinx.coroutines.launch
import com.festpay.app.data.LaunchDisposition
import com.festpay.app.data.LaunchMilestone

data class PaymentRequest(
    val payeeVpa: String,
    val payeeName: String,
    val amount: String,
    val currency: String = "INR",
    val mobile: Boolean = false,
    val attemptId: String = "",
    val preparedCommand: OwnedUssdCommand? = null
)

enum class PaymentState {
    IDLE, INITIATED, PROCESSING, SUCCESS, FAILED, UNKNOWN, CANCELLED, AUTHENTICATION_REQUIRED
}

data class PaymentResult(
    val state: PaymentState,
    val localReference: String,
    val message: String,
    val launchDisposition: com.festpay.app.data.LaunchDisposition? = null
)

interface PaymentEngine {
    suspend fun executePayment(request: PaymentRequest, scenario: PaymentScenario, context: Context? = null): PaymentResult
}

enum class PaymentScenario { SUCCESS, FAILED, UNKNOWN }

enum class PreparationBlock { NONE, STORAGE, EARLIER_HISTORY, UNRESOLVED_ATTEMPT }

/** The first operation that stopped a Pay tap before any carrier launch. NONE until a tap is refused. */
enum class StartFailure {
    NONE, CONSENT, BUSY, OPERATION_ACTIVE, INVALID_REQUEST, CONTEXT_UNAVAILABLE, ACCESSIBILITY_UNAVAILABLE,
    PHONE_PERMISSION, CELLULAR_UNAVAILABLE, STORAGE_UNAVAILABLE, EARLIER_HISTORY, RESERVATION_PROTECTION,
    REVIEW_CHANGED, SESSION_NOT_ACKNOWLEDGED, ENGINE_REJECTED, LAUNCH_FAILED, PREPARATION_INTERRUPTED
}

class RealUssdPaymentEngine(
    private val journalFactory: (Context) -> PaymentAttemptJournal = { PaymentAttemptJournal(it) }
) : PaymentEngine {
    companion object {
        private const val TAG = "RealUssdPaymentEngine"
    }

    override suspend fun executePayment(request: PaymentRequest, scenario: PaymentScenario, context: Context?): PaymentResult {
        fun result(disposition: LaunchDisposition, message: String) =
            PaymentResult(PaymentState.UNKNOWN, request.attemptId, message, disposition)
        val rejected = "Unable to prepare carrier session. No Android launch requested. The saved attempt remains protected."
        PaymentDiagnostics.stage("engine.entry", "OK", "attempt=${PaymentDiagnostics.shortId(request.attemptId)}")
        val command = request.preparedCommand
        if (context == null || command == null) {
            PaymentDiagnostics.stage("engine.entry", "REJECTED", "reason=${if (context == null) "NO_CONTEXT" else "NO_PREPARED_COMMAND"}")
            return result(LaunchDisposition.PRE_LAUNCH_REJECTED, "Payment engine unavailable: the prepared carrier session is missing. No Android launch requested. The saved attempt remains protected.")
        }
        val transport = UssdSessionTransport()
        val matches = runCatching {
            journalFactory(context).use { journal ->
                journal.unresolvedId() == request.attemptId && journal.records().singleOrNull {
                    it.localTransactionId == request.attemptId && it.payeeVpa == request.payeeVpa &&
                        it.amount == request.amount && it.currency == request.currency && it.state == PaymentState.PROCESSING
                } != null
            }
        }.getOrDefault(false)
        if (!matches || command.balance || command.attemptId != request.attemptId || command.recipient != request.payeeVpa || command.amount != request.amount) {
            PaymentDiagnostics.stage("engine.verifyAttempt", "MISMATCH")
            return result(LaunchDisposition.PRE_LAUNCH_REJECTED, "Payment engine refused: the saved attempt does not match the prepared request. No Android launch requested. The saved attempt remains protected.")
        }
        PaymentDiagnostics.stage("engine.verifyAttempt", "OK")
        fun record(milestone: LaunchMilestone) = journalFactory(context).use { it.recordLaunchMilestone(command, milestone) }
        val armed = runCatching { transport.arm(context, command) }.getOrDefault(false)
        PaymentDiagnostics.stage("engine.arm", if (armed) "OK" else "REFUSED")
        if (!armed) {
            transport.release(command.sessionId)
            runCatching { record(LaunchMilestone.SESSION_ARM_FAILED) }
            return result(LaunchDisposition.PRE_LAUNCH_REJECTED, "Payment engine could not arm the Accessibility session. No Android launch requested. The saved attempt remains protected.")
        }
        try {
            record(LaunchMilestone.SESSION_ARM_SUCCEEDED)
            record(LaunchMilestone.ANDROID_LAUNCH_REQUESTED)
        } catch (_: Exception) {
            transport.release(command.sessionId)
            return result(LaunchDisposition.PRE_LAUNCH_REJECTED, "Unable to persist launch preparation. No Android launch requested. Do not retry.")
        }
        val callIntent = com.festpay.app.data.SimSelection.dialIntent(context)
        try {
            PaymentDiagnostics.stage("android.startActivity", "INVOKE", "action=ACTION_CALL")
            context.startActivity(callIntent)
            PaymentDiagnostics.stage("android.startActivity", "RETURNED")
        } catch (e: Exception) {
            PaymentDiagnostics.stage("android.startActivity", "THREW", "exception=${e.javaClass.simpleName}")
            transport.release(command.sessionId)
            val saved = runCatching { record(LaunchMilestone.ANDROID_LAUNCH_THREW) }.isSuccess
            return result(LaunchDisposition.LAUNCH_REQUEST_FAILED,
                "Android rejected the carrier launch synchronously. Payment outcome unverified. Do not retry." +
                    if (saved) "" else " Launch evidence could not be saved.")
        }
        val saved = runCatching { record(LaunchMilestone.ANDROID_LAUNCH_RETURNED) }.isSuccess
        return result(LaunchDisposition.LAUNCH_REQUEST_RETURNED,
            "Carrier launch requested; waiting for Airtel. Android returned normally; carrier activation and payment outcome remain unverified." +
                if (saved) "" else " Launch evidence could not be saved.")
    }
}

data class TransactionRecord(
    val localTransactionId: String,
    val timestamp: String,
    val payeeName: String,
    val payeeVpa: String,
    val amount: String,
    val currency: String = "INR",
    val state: PaymentState,
    val operation: String = "PAYMENT",
    val evidenceStatus: String = "NO_AUTHORIZED_CONFIRMATION",
    val bankReference: String? = null,
    val referenceProvenance: String? = null,
    /** DEBITED / NOT_DEBITED as declared by the owner after checking their own bank; never verified by V4SH. */
    val ownerResolution: String? = null,
    /** SUCCESS_WORDING / FAILURE_WORDING: what the carrier's last message sounded like. Never verified. */
    val carrierResult: String? = null,
    /** SUCCESS / FAILED: the attempt was closed by the carrier's own final message (owner policy), not by a bank. */
    val carrierOutcome: String? = null,
    /** The carrier stopped or never confirmed: shown as unsuccessful, but the attempt stays open (your bank is the authority). */
    val noCarrierConfirmation: Boolean = false
)

class PaymentViewModel(
    private val journalFactory: (Context) -> PaymentAttemptJournal = { PaymentAttemptJournal(it) },
    private val repositoryFactory: (Context) -> TransactionRepository = { TransactionRepository(it) },
    private val sessionTransportFactory: () -> UssdSessionTransport = { UssdSessionTransport() }
) : ViewModel() {
    var paymentState by mutableStateOf(PaymentState.IDLE)
        private set

    var userVpa by mutableStateOf("")
    var userName by mutableStateOf("")

    var lastResult by mutableStateOf<PaymentResult?>(null)
        private set

    val transactionLedger = mutableStateListOf<TransactionRecord>()

    private val realEngine: PaymentEngine = RealUssdPaymentEngine(journalFactory)

    private var transactionRepo: TransactionRepository? = null
    private var journal: PaymentAttemptJournal? = null
    var selectedAttemptId by mutableStateOf<String?>(null)
    var operationError by mutableStateOf<String?>(null)
        private set
    var paymentPreparationBlocked by mutableStateOf(true)
        private set
    var startFailure by mutableStateOf(StartFailure.NONE)
        private set
    /** Why payment preparation is blocked, for status display only. Conservative until inspected. */
    var preparationBlock by mutableStateOf(PreparationBlock.STORAGE)
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    var profileSaveError by mutableStateOf<String?>(null)
        private set
    var profileAvailable by mutableStateOf(false)
        private set
    private var legacyRecords = emptyList<TransactionRecord>()
    private var initializationFailed = true
    private var preparingPayment = false

    fun initRepository(ctx: Context) {
        initializationFailed = true
        try {
            val repo = transactionRepo ?: repositoryFactory(ctx)
            val savedVpa = repo.loadUserVpa()
            val savedName = repo.loadUserName()
            val savedLegacy = repo.loadTransactions().map { it.copy(state = PaymentState.UNKNOWN) }
            transactionRepo = repo
            userVpa = savedVpa
            userName = savedName
            legacyRecords = savedLegacy
            profileAvailable = true
        } catch (_: Exception) {
            profileAvailable = false
            profileSaveError = "Protected profile storage is unavailable. Existing data was not reset."
            operationError = "Protected storage could not be read. Payment execution is blocked."
            historyError = "Protected history is unavailable. Existing data and payment protection were not reset."
            paymentPreparationBlocked = true
            return
        }
        if (journal == null) {
            try {
                journal = journalFactory(ctx)
                journal!!.recover()
                selectedAttemptId = journal!!.unresolvedId()
                if (selectedAttemptId != null) paymentState = PaymentState.UNKNOWN
                refreshHistory()
            } catch (_: Exception) {
                operationError = "Payment storage is unavailable. No payment can be started."
                historyError = "Payment history could not be loaded. Existing payment protection remains active."
                journal?.close()
                journal = null
            }
        }
        initializationFailed = journal == null
    }

    fun saveUserProfile(ctx: Context, newVpa: String, newName: String): Boolean {
        profileSaveError = null
        val profile = PaymentInputValidator.profile(newVpa, newName)
        if (profile == null) {
            profileSaveError = "Enter a valid UPI ID and a display name of 1–100 characters without control characters."
            return false
        }
        return try {
            initRepository(ctx)
            if (transactionRepo?.saveUserProfile(profile.vpa, profile.name) != true) {
                profileSaveError = "UPI details could not be confirmed as saved. Check storage before using Receive."
                false
            } else {
                userVpa = profile.vpa
                userName = profile.name
                true
            }
        } catch (_: Exception) {
            profileSaveError = "UPI details could not be confirmed as saved. Check storage before using Receive."
            false
        }
    }

    /** Inspect readiness without creating an attempt or requesting a carrier launch. */
    fun inspectPaymentReadiness(ctx: Context) {
        initRepository(ctx)
        if (initializationFailed) {
            paymentPreparationBlocked = true
            preparationBlock = PreparationBlock.STORAGE
            return
        }
        val store = journal
        var block = PreparationBlock.NONE
        operationError = when {
            store == null -> { block = PreparationBlock.STORAGE; "Payment storage is unavailable. No carrier launch was requested." }
            legacyRecords.isNotEmpty() -> { block = PreparationBlock.EARLIER_HISTORY; "Earlier unverified local history requires review before another payment. No carrier launch was requested." }
            else -> try {
                val unresolved = store.unresolvedId()
                if (unresolved != null) {
                    selectedAttemptId = unresolved
                    block = PreparationBlock.UNRESOLVED_ATTEMPT
                    "An earlier payment attempt remains unresolved. Pay is blocked; no new carrier launch was requested. Check Transactions and your official bank history."
                } else null
            } catch (_: Exception) {
                block = PreparationBlock.STORAGE
                "Payment storage could not be checked. No carrier launch was requested."
            }
        }
        preparationBlock = block
        paymentPreparationBlocked = operationError != null
        com.festpay.app.data.PaymentDiagnostics.stage("viewmodel.readiness", "BLOCK=${block.name}", "payBlocked=$paymentPreparationBlocked attempt=${com.festpay.app.data.PaymentDiagnostics.shortId(selectedAttemptId)}")
    }

    /** Clears the last refusal message after the owner has dealt with it (for example by stopping a stuck operation). */
    fun clearStartRefusal() { operationError = null; startFailure = StartFailure.NONE }

    private fun refuseStart(kind: StartFailure, message: String, state: PaymentState, onComplete: (PaymentState) -> Unit) {
        startFailure = kind
        operationError = message
        PaymentDiagnostics.stage("viewmodel.startPayment", "REFUSED", "reason=${kind.name}")
        com.festpay.app.data.IssueLog.record("Payment not started", message, when (kind) {
            StartFailure.ACCESSIBILITY_UNAVAILABLE, StartFailure.SESSION_NOT_ACKNOWLEDGED -> com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS
            StartFailure.PHONE_PERMISSION -> com.festpay.app.data.IssueFix.PHONE_PERMISSION
            StartFailure.OPERATION_ACTIVE -> com.festpay.app.data.IssueFix.STOP_STUCK_OPERATION
            StartFailure.EARLIER_HISTORY -> com.festpay.app.data.IssueFix.HISTORY
            else -> com.festpay.app.data.IssueFix.NONE
        })
        onComplete(state)
    }

    private fun prepareFailureMessage(reason: PrepareFailure): Pair<StartFailure, String> = when (reason) {
        PrepareFailure.CONSENT_MISSING -> StartFailure.CONSENT to "Payment not started. Reopen V4SH and review the screen assistance disclosure."
        PrepareFailure.SERVICE_NOT_CONNECTED -> StartFailure.ACCESSIBILITY_UNAVAILABLE to
            "Payment not started. V4SH Accessibility is not connected. Turn V4SH on in Android Accessibility settings, then return to Review."
        PrepareFailure.OPERATION_ACTIVE -> StartFailure.OPERATION_ACTIVE to "Another cellular operation is active. Finish it before starting payment."
        PrepareFailure.CLAIM_REJECTED -> StartFailure.SESSION_NOT_ACKNOWLEDGED to
            "Payment not started. Accessibility already holds another session. Close any carrier dialog, then return to Review."
        PrepareFailure.ACK_TIMEOUT, PrepareFailure.BROADCAST_FAILED, PrepareFailure.NONE -> StartFailure.SESSION_NOT_ACKNOWLEDGED to
            "Payment not started. Accessibility did not acknowledge the current connection. Toggle V4SH off and on in Accessibility settings, then return to Review."
    }

    fun startPayment(vpa: String, name: String, amount: String, ctx: Context?, reviewCurrent: () -> Boolean = { false }, onComplete: (PaymentState) -> Unit) {
        operationError = null
        startFailure = StartFailure.NONE
        PaymentDiagnostics.stage("viewmodel.startPayment", "ENTER")
        if (ctx != null && !com.festpay.app.data.AccessibilityConsent.accepted(ctx)) {
            refuseStart(StartFailure.CONSENT, "Payment not started. Reopen V4SH and review the screen assistance disclosure.", PaymentState.IDLE, onComplete); return
        }
        if (paymentState == PaymentState.PROCESSING || preparingPayment) {
            refuseStart(StartFailure.BUSY, "A payment is already being prepared. Wait for it to finish.", PaymentState.IDLE, onComplete); return
        }
        if (FestPayAccessibilityService.UssdSessionData.operationActive) {
            refuseStart(StartFailure.OPERATION_ACTIVE, "Another cellular operation is active. Finish it before starting payment.", PaymentState.IDLE, onComplete); return
        }
        val mobile = false // Mobile recipient lookup/navigation is not qualified for use.
        val target = vpa.trim().takeIf { PaymentInputValidator.isVpa(it) }
        val validAmount = PaymentInputValidator.amount(amount)
        if (target == null || validAmount == null) {
            refuseStart(StartFailure.INVALID_REQUEST, "Payment not started. The recipient UPI ID or amount in this review is not valid. Return to amount entry.", PaymentState.IDLE, onComplete); return
        }
        if (ctx == null) {
            refuseStart(StartFailure.CONTEXT_UNAVAILABLE, "Payment not started. The app screen context is unavailable. Reopen V4SH and try again.", PaymentState.IDLE, onComplete); return
        }
        PaymentDiagnostics.stage("viewmodel.validate", "OK")
        if (!FestPayAccessibilityService.UssdSessionData.serviceConnected) {
            com.festpay.app.data.IssueLog.openAccessibilitySettings(ctx)
            refuseStart(StartFailure.ACCESSIBILITY_UNAVAILABLE,
                "Payment not started. V4SH Accessibility is not connected. Turn V4SH on in Android Accessibility settings, then return to Review.", PaymentState.IDLE, onComplete); return
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            refuseStart(StartFailure.PHONE_PERMISSION, "Payment not started. Phone permission is not granted. Allow it, then tap Pay again.", PaymentState.IDLE, onComplete); return
        }
        com.festpay.app.data.CellularReadinessReader.read(ctx).launchBlock?.let {
            refuseStart(StartFailure.CELLULAR_UNAVAILABLE, "Payment not started. $it", PaymentState.IDLE, onComplete); return
        }
        initRepository(ctx)
        if (initializationFailed) {
            refuseStart(StartFailure.STORAGE_UNAVAILABLE, operationError ?: "Payment storage is unavailable. No carrier launch was requested.", PaymentState.IDLE, onComplete); return
        }
        if (legacyRecords.isNotEmpty()) {
            refuseStart(StartFailure.EARLIER_HISTORY, "Earlier unverified local history requires review before another payment. No payment started.", PaymentState.UNKNOWN, onComplete); return
        }
        val store = journal
        if (store == null) {
            refuseStart(StartFailure.STORAGE_UNAVAILABLE, "Payment storage is unavailable. No carrier launch was requested.", PaymentState.IDLE, onComplete); return
        }
        val unresolved = try { store.unresolvedId() } catch (e: Exception) {
            PaymentDiagnostics.stage("journal.unresolvedId", "THREW", "exception=${e.javaClass.simpleName}")
            refuseStart(StartFailure.STORAGE_UNAVAILABLE, "Payment storage could not be checked. No carrier launch was requested.", PaymentState.IDLE, onComplete); return
        }
        PaymentDiagnostics.stage("journal.unresolvedId", if (unresolved == null) "NONE" else "PRESENT", "attempt=${PaymentDiagnostics.shortId(unresolved)}")
        if (unresolved != null) {
            selectedAttemptId = unresolved
            refuseStart(StartFailure.RESERVATION_PROTECTION,
                "An earlier payment attempt remains unresolved. This tap did not launch the carrier or start a new payment. Check the earlier attempt in Transactions and your official bank history.",
                PaymentState.UNKNOWN, onComplete); return
        }
        if (!reviewCurrent()) {
            refuseStart(StartFailure.REVIEW_CHANGED, "Payment not started. Review the current recipient and amount again.", PaymentState.IDLE, onComplete); return
        }
        preparingPayment = true
        viewModelScope.launch {
            val transport = sessionTransportFactory()
            val plannedId = java.util.UUID.randomUUID().toString()
            val command = OwnedUssdCommand.payment(plannedId, target, validAmount)
            var durable = false
            try {
                // Inert initialization only: no carrier launch, UI automation or financial record.
                if (!transport.prepare(ctx, command)) {
                    val (kind, message) = prepareFailureMessage(transport.lastFailure)
                    startFailure = kind; operationError = message
                    PaymentDiagnostics.stage("viewmodel.prepare", "REFUSED", "reason=${transport.lastFailure.name}")
                    onComplete(PaymentState.IDLE); return@launch
                }
                val recheck = when {
                    !reviewCurrent() -> StartFailure.REVIEW_CHANGED to "Payment not started. The reviewed recipient or amount changed. Return to Review. No carrier launch requested."
                    !com.festpay.app.data.AccessibilityConsent.accepted(ctx) -> StartFailure.CONSENT to "Payment not started. Screen assistance consent is no longer active. No carrier launch requested."
                    !com.festpay.app.data.AccessibilityRuntime.connection.snapshot.acknowledged -> StartFailure.SESSION_NOT_ACKNOWLEDGED to "Payment not started. The Accessibility acknowledgment was lost. No carrier launch requested."
                    com.festpay.app.data.CellularReadinessReader.read(ctx).launchBlock != null -> StartFailure.CELLULAR_UNAVAILABLE to "Payment not started. Cellular readiness changed. No carrier launch requested."
                    androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED -> StartFailure.PHONE_PERMISSION to "Payment not started. Phone permission was withdrawn. No carrier launch requested."
                    store.unresolvedId() != null -> StartFailure.RESERVATION_PROTECTION to "Payment not started. An earlier attempt became unresolved. No carrier launch requested."
                    else -> null
                }
                if (recheck != null) {
                    startFailure = recheck.first; operationError = recheck.second
                    PaymentDiagnostics.stage("viewmodel.recheck", "REFUSED", "reason=${recheck.first.name}")
                    onComplete(PaymentState.IDLE); return@launch
                }
                PaymentDiagnostics.stage("viewmodel.recheck", "OK")
                val request = PaymentRequest(target, name, validAmount, mobile = mobile, attemptId = plannedId, preparedCommand = command)
                val attempt = store.create(request) // Durable INITIATED + reservation BEFORE arming.
                durable = true
                PaymentDiagnostics.stage("journal.create", "OK", "attempt=${PaymentDiagnostics.shortId(attempt)}")
                selectedAttemptId = attempt
                check(attempt == plannedId && store.unresolvedId() == attempt)
                store.transition(attempt, PaymentState.PROCESSING, "EXTERNAL_LAUNCH_PREPARING")
                paymentState = PaymentState.PROCESSING
                refreshHistory()
                PaymentDiagnostics.stage("engine.executePayment", "INVOKE", "attempt=${PaymentDiagnostics.shortId(attempt)}")
                val result = realEngine.executePayment(request, PaymentScenario.UNKNOWN, ctx)
                PaymentDiagnostics.stage("engine.executePayment", "RETURNED", "disposition=${result.launchDisposition?.name ?: "none"}")
                when (result.launchDisposition) {
                    LaunchDisposition.PRE_LAUNCH_REJECTED -> startFailure = StartFailure.ENGINE_REJECTED
                    LaunchDisposition.LAUNCH_REQUEST_FAILED -> startFailure = StartFailure.LAUNCH_FAILED
                    else -> Unit
                }
                lastResult = result
                paymentState = PaymentState.UNKNOWN
                store.transition(attempt, PaymentState.UNKNOWN, when (result.launchDisposition) {
                    LaunchDisposition.PRE_LAUNCH_REJECTED -> "PRE_LAUNCH_REJECTED"
                    LaunchDisposition.LAUNCH_REQUEST_FAILED -> "LAUNCH_REQUEST_FAILED"
                    LaunchDisposition.LAUNCH_REQUEST_RETURNED -> "LAUNCH_REQUEST_RETURNED"
                    LaunchDisposition.CARRIER_OBSERVED -> "CARRIER_OBSERVED"
                    null -> "LAUNCH_DISPOSITION_UNAVAILABLE"
                })
                refreshHistory()
                onComplete(PaymentState.UNKNOWN)
            } catch (e: Exception) {
                startFailure = StartFailure.PREPARATION_INTERRUPTED
                PaymentDiagnostics.stage("viewmodel.startPayment", "THREW", "exception=${e.javaClass.simpleName} durable=$durable")
                operationError = if (durable) "Payment preparation interrupted. The saved attempt remains reserved; do not retry." else "Payment preparation unavailable. No carrier launch requested. Check Transactions before trying again."
                if (durable) {
                    paymentState = PaymentState.UNKNOWN
                    runCatching { store.transition(plannedId, PaymentState.UNKNOWN, "PREPARATION_INTERRUPTED"); refreshHistory() }
                }
                onComplete(if (durable) PaymentState.UNKNOWN else PaymentState.IDLE)
            } finally {
                preparingPayment = false
                // Retain an armed observation until it ends; release only inert preparation.
                if (!com.festpay.app.data.AccessibilityRuntime.connection.snapshot.armed) transport.release(command.sessionId)
            }
        }
    }

    private fun carrierCommand(): OwnedUssdCommand? {
        val session = FestPayAccessibilityService.UssdSessionData
        val state = com.festpay.app.data.AccessibilityRuntime.connection.snapshot
        val command = com.festpay.app.data.AccessibilityRuntime.connection.preparedCommand(session.sessionId, state.connectionId.orEmpty()) ?: return null
        if (!state.armed || !session.operationActive || command.balance || command.attemptId != selectedAttemptId) return null
        return try {
            val store = journal ?: return null
            if (store.unresolvedId() != command.attemptId || store.records().none {
                it.localTransactionId == command.attemptId && it.payeeVpa == command.recipient && it.amount == command.amount
            }) null else command
        } catch (_: Exception) { null }
    }

    /**
     * The owner resolves an UNKNOWN attempt from their own bank record. Only the lock is released; the attempt
     * stays UNKNOWN. Refused while a cellular operation is active. Returns true if the record is now resolved.
     */
    fun resolveAttemptByOwner(ctx: Context, id: String, resolution: com.festpay.app.data.OwnerResolution): Boolean {
        PaymentDiagnostics.stage("viewmodel.ownerResolve", "ENTER", "attempt=${PaymentDiagnostics.shortId(id)}")
        if (FestPayAccessibilityService.UssdSessionData.operationActive) {
            operationError = "A cellular operation is active. Finish it before resolving an earlier attempt."
            return false
        }
        val store = journal ?: run { operationError = "Payment storage is unavailable. Nothing was changed."; return false }
        return try {
            store.resolveByOwner(id, resolution)
            refreshHistory()
            // Keep the resolved record selected so its details stay on screen.
            selectedAttemptId = store.unresolvedId() ?: id
            if (store.unresolvedId() == null && paymentState == PaymentState.UNKNOWN) paymentState = PaymentState.IDLE
            inspectPaymentReadiness(ctx)
            PaymentDiagnostics.stage("viewmodel.ownerResolve", "DONE", "resolution=${resolution.name}")
            true
        } catch (e: Exception) {
            PaymentDiagnostics.stage("viewmodel.ownerResolve", "REFUSED", "exception=${e.javaClass.simpleName}")
            operationError = "The attempt could not be resolved. Nothing was changed."
            false
        }
    }

    fun stopWaiting() {
        val owned = carrierCommand()
        if (paymentState == PaymentState.PROCESSING || paymentState == PaymentState.AUTHENTICATION_REQUIRED || owned != null) {
            val progress = FestPayAccessibilityService.UssdSessionData.paymentProgress
            if (owned != null) {
                try {
                    if (progress != null) journal?.recordCarrierObservation(owned.attemptId!!, owned.sessionId,
                        progress.copy(stage = com.festpay.app.data.CarrierStage.INTERRUPTED, revision = progress.revision + 1,
                            issue = com.festpay.app.data.CarrierIssue.USER_STOPPED, authorized = false, review = null))
                } catch (_: Exception) { operationError = "Observation could not be saved. The attempt remains reserved; do not retry." }
                UssdSessionTransport().release(owned.sessionId)
            }
            paymentState = PaymentState.UNKNOWN
            lastResult = PaymentResult(PaymentState.UNKNOWN, selectedAttemptId.orEmpty(), "Payment status unclear")
            selectedAttemptId?.let { id ->
                try { journal?.transition(id, PaymentState.UNKNOWN, "OBSERVATION_STOPPED"); refreshHistory() }
                catch (_: Exception) { operationError = "Could not update local history. Do not retry the payment." }
            }
        }
    }

    fun markCancelled(): Boolean {
        // Cancelled only before payment initiation
        if (paymentState == PaymentState.IDLE) {
            paymentState = PaymentState.CANCELLED
            return true
        }
        return false
    }

    fun checkStatus(onStatusChecked: (PaymentState) -> Unit) {
        operationError = "Bank verification is unavailable in V4SH. Check your bank's official transaction history before paying again."
        onStatusChecked(paymentState)
    }

    fun refreshTransactionHistory(ctx: Context) {
        try {
            initRepository(ctx)
            if (initializationFailed) throw com.festpay.app.data.ProtectedStorageException()
            refreshHistory()
        } catch (_: Exception) {
            historyError = "History could not be refreshed. Previously loaded records are retained; payment protection remains active."
        }
    }

    private fun refreshHistory() {
        // Finish reading before changing the visible list. A read error must not
        // erase previously loaded history or look like an empty journal.
        val records = checkNotNull(journal).records() + legacyRecords
        transactionLedger.clear()
        transactionLedger.addAll(records)
        historyError = null
    }

    override fun onCleared() {
        journal?.close()
        super.onCleared()
    }

    fun startBalanceCheck(ctx: Context?, onComplete: (PaymentState) -> Unit) {
        val session = FestPayAccessibilityService.UssdSessionData
        // A rejected balance request must not overwrite another operation's presentation.
        if (paymentState == PaymentState.PROCESSING || session.operationActive) {
            onComplete(PaymentState.IDLE)
            return
        }
        if (ctx != null && !com.festpay.app.data.AccessibilityConsent.accepted(ctx)) {
            session.errorMessage = "Reopen V4SH and review the screen assistance disclosure before using cellular screen assistance."
            session.currentPresentationState = "BALANCE_UNKNOWN"
            onComplete(PaymentState.UNKNOWN); return
        }
        if (!session.serviceConnected || ctx == null) {
            session.parsedBalance = null
            session.errorMessage = "V4SH Accessibility is not running. Enable it again in Android Accessibility Settings, then retry."
            com.festpay.app.data.IssueLog.record("Balance not checked", "V4SH Accessibility is off.", com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS)
            // Go straight to the Android switch instead of through another V4SH screen.
            if (ctx != null) com.festpay.app.data.IssueLog.openAccessibilitySettings(ctx)
            session.currentPresentationState = "BALANCE_UNKNOWN"
            onComplete(PaymentState.UNKNOWN)
            return
        }
        com.festpay.app.data.CellularReadinessReader.read(ctx).launchBlock?.let {
            session.parsedBalance = null
            session.errorMessage = it
            session.currentPresentationState = "BALANCE_UNKNOWN"
            onComplete(PaymentState.UNKNOWN); return
        }
        viewModelScope.launch {
            val command = OwnedUssdCommand.balance()
            val transport = UssdSessionTransport()
            if (!transport.prepare(ctx, command) || !transport.arm(ctx, command)) {
                transport.release(command.sessionId)
                session.errorMessage = "Accessibility could not prepare the balance session. No carrier launch was requested. Check Accessibility and close any old carrier dialog."
                session.currentPresentationState = "BALANCE_UNKNOWN"
                onComplete(PaymentState.UNKNOWN)
                return@launch
            }
            try {
                ctx.startActivity(com.festpay.app.data.SimSelection.dialIntent(ctx))
                onComplete(PaymentState.PROCESSING)
            } catch (_: Exception) {
                transport.release(command.sessionId)
                Log.e("PaymentViewModel", "BALANCE_LAUNCH_FAILED=true")
                session.errorMessage = "Could not open cellular USSD. Check Phone permission, cellular signal and your bank-registered SIM. Mobile data and Wi-Fi are not required."
                session.currentPresentationState = "BALANCE_UNKNOWN"
                onComplete(PaymentState.UNKNOWN)
            }
        }
    }
}

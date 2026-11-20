package com.festpay.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.festpay.app.data.BalanceParser
import com.festpay.app.data.AccessibilityTextPolicy
import com.festpay.app.data.BalanceObservationPolicy
import com.festpay.app.data.CarrierResultClassifier
import com.festpay.app.data.UssdSourcePolicy
import com.festpay.app.data.UssdNavigationPolicy
import com.festpay.app.data.AccessibilityRuntime
import com.festpay.app.data.OwnedUssdCommand
import com.festpay.app.data.UssdSessionHost
import com.festpay.app.data.CarrierPaymentFlow
import com.festpay.app.data.CarrierProgress
import com.festpay.app.data.CarrierStage
import com.festpay.app.data.CarrierIssue
import com.festpay.app.data.CarrierInputKind
import com.festpay.app.data.CarrierDialogLifecycle
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.ui.viewmodel.PaymentState
import androidx.core.content.IntentCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class FestPayAccessibilityService : AccessibilityService(), UssdSessionHost {

    companion object {
        private const val TAG = "FestPayAccessibility"
        const val ACTION_NEW_USSD_SESSION = "com.festpay.app.ACTION_NEW_USSD_SESSION"
        const val EXTRA_BALANCE_CHECK = "com.festpay.app.EXTRA_BALANCE_CHECK"
        const val EXTRA_RECIPIENT = "com.festpay.app.EXTRA_RECIPIENT"
        const val EXTRA_AMOUNT = "com.festpay.app.EXTRA_AMOUNT"
        const val EXTRA_MOBILE = "com.festpay.app.EXTRA_MOBILE"
        const val EXTRA_ATTEMPT_ID = "com.festpay.app.EXTRA_ATTEMPT_ID"
        const val EXTRA_SESSION_ID = "com.festpay.app.EXTRA_SESSION_ID"
        const val EXTRA_SESSION_ACK = "com.festpay.app.EXTRA_SESSION_ACK"
        const val EXTRA_CONNECTION_ID = "com.festpay.app.EXTRA_CONNECTION_ID"
        internal const val NAVIGATION_TIMEOUT_MS = 120_000L
        internal const val AUTO_OFF_DELAY_MS = 9_000L
        /** Pause before V4SH answers a carrier menu. */
        internal const val INPUT_SETTLE_MS = 700L
        /** Whether V4SH presses OK on a plain carrier notice shown before the menu (for example "Welcome to *99#"). */
        internal const val AUTO_PRESS_OK = true
        internal const val PIN_RESULT_TIMEOUT_MS = 300_000L
    }

    object UssdSessionData {
        var recipientVpa: String = ""
        var amount: String = ""
        var mobilePayment = false
        var attemptId: String = ""
        private var activeState by mutableStateOf(false)
        var operationActive: Boolean
            get() = activeState
            set(value) { val was = activeState; activeState = value; if (was && !value) { lastSessionEndedElapsed = runCatching { android.os.SystemClock.elapsedRealtime() }.getOrNull(); onSessionEnded?.invoke() } }
        /** For the app lock: when the last cellular session ended (elapsed realtime), or null. */
        var lastSessionEndedElapsed: Long? = null
        /** Set by the service; fired when a cellular session ends so it can switch itself off. */
        var onSessionEnded: (() -> Unit)? = null
        var sessionId = ""
        var isBalanceCheck: Boolean = false
        var currentPresentationState by mutableStateOf("CONNECTING")
        var parsedBalance by mutableStateOf<String?>(null)
        val serviceConnected get() = AccessibilityRuntime.connection.snapshot.connected
        var errorMessage by mutableStateOf<String?>(null)
        /** The last non-sensitive text the carrier showed, for displaying its own words when something fails. */
        var lastCarrierMessage by mutableStateOf<String?>(null)
        var paymentProgress by mutableStateOf<CarrierProgress?>(null)
        var carrierDialogEnded by mutableStateOf(false)
        var launchDisposition by mutableStateOf<com.festpay.app.data.LaunchDisposition?>(null)

        fun reset(balanceCheck: Boolean = false) {
            sessionId = ""
            recipientVpa = ""
            amount = ""
            mobilePayment = false
            attemptId = ""
            isBalanceCheck = balanceCheck
            currentPresentationState = "CONNECTING"
            parsedBalance = null
            errorMessage = null
            lastCarrierMessage = null
            paymentProgress = null
            carrierDialogEnded = false
            launchDisposition = null
        }
    }

    /**
     * Explicit state machine for USSD interaction.
     */
    private enum class USSDState {
        IDLE,
        USSD_DETECTED,
        INPUT_ENTERED,
        SUBMITTING,
        WAITING_RESPONSE,
        CONFIRMATION_REVIEW,
        CONFIRMATION_SUBMITTED,
        AUTHENTICATION_REQUIRED,
        WAITING_RESULT,
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    // ---------------------------------------------------------------------
    // State variables – they are **reset** each time we start a fresh USSD session.
    // ---------------------------------------------------------------------
    private var ussdState: USSDState = USSDState.IDLE
    private var paymentFlow: CarrierPaymentFlow? = null
    private var carrierDialog = CarrierDialogLifecycle()
    private val dialogReturn = android.os.Handler(android.os.Looper.getMainLooper())
    private var inputFilled = false
    private var resultProcessed = false
    private var sendClicked = false
    private var lastInputTime = 0L
    private var balanceSourcePackage: String? = null
    private var sessionArmed = false
    private var receiverRegistered = false
    private var sessionRequestId = ""
    private var sessionStartedAt = 0L
    private var connectionId = ""
    private val operationTimeout = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoOff = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Banking apps refuse to run while a third-party Accessibility service is on. V4SH needs it only during a carrier session,
     * so it switches itself off shortly after the session ends (turn it on again before the next payment).
     */
    private fun scheduleAutoOff(delayMs: Long) {
        autoOff.removeCallbacksAndMessages(null)
        if (!com.festpay.app.data.SleepMode.autoSleep(applicationContext)) return
        autoOff.postDelayed({
            if (!UssdSessionData.operationActive && AccessibilityRuntime.connection.isOwner(this)) {
                com.festpay.app.data.PaymentDiagnostics.stage("svc.autoOff", "DISABLE_SELF")
                com.festpay.app.data.SleepMode.noteAutoSlept(applicationContext)
                try { disableSelf() } catch (_: Exception) { }
            }
        }, delayMs)
    }

    // ---------------------------------------------------------------------
    // BroadcastReceiver that listens for an explicit signal from MainActivity
    // when the user taps the "TEST *99#" button.
    // ---------------------------------------------------------------------
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NEW_USSD_SESSION) {
                if (!com.festpay.app.data.AccessibilityConsent.accepted(this@FestPayAccessibilityService)) return
                val requestId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
                val epoch = intent.getStringExtra(EXTRA_CONNECTION_ID) ?: return
                val acknowledgement = IntentCompat.getParcelableExtra(intent, EXTRA_SESSION_ACK, android.os.ResultReceiver::class.java) ?: return
                if (!AccessibilityRuntime.connection.isOwner(this@FestPayAccessibilityService) || epoch != connectionId) return
                val command = AccessibilityRuntime.connection.preparedCommand(requestId, epoch) ?: return
                if (!UssdSessionData.operationActive || UssdSessionData.sessionId != requestId) return
                fun acknowledge() = acknowledgement.send(0, android.os.Bundle().apply {
                    putString(EXTRA_SESSION_ID, requestId); putString(EXTRA_CONNECTION_ID, epoch)
                })
                if (AccessibilityRuntime.connection.snapshot.initialized) { acknowledge(); return }
                if (!AccessibilityRuntime.connection.initialize(this@FestPayAccessibilityService, requestId, epoch)) return
                sessionRequestId = requestId
                autoOff.removeCallbacksAndMessages(null)
                Log.d(TAG, "Received new USSD session broadcast – resetting session")
                resetSession(command.balance)
                UssdSessionData.sessionId = requestId
                UssdSessionData.recipientVpa = command.recipient
                UssdSessionData.amount = command.amount
                UssdSessionData.mobilePayment = false
                UssdSessionData.attemptId = command.attemptId.orEmpty()
                paymentFlow = if (command.balance) null else CarrierPaymentFlow(command.recipient, command.amount)
                UssdSessionData.paymentProgress = paymentFlow?.progress
                // Initialization/ACK cannot interact with an old dialog or create an effect.
                sessionArmed = false
                UssdSessionData.operationActive = true
                operationTimeout.removeCallbacksAndMessages(null)
                acknowledge()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (AccessibilityRuntime.connection.isOwner(this) && receiverRegistered) {
            Log.d(TAG, "DUPLICATE_CONNECTION_IGNORED=true")
            return
        }
        connectionId = AccessibilityRuntime.connection.connect(this)
        V4SHTileService.refresh(applicationContext)
        UssdSessionData.onSessionEnded = { com.festpay.app.data.SetupDetails.clearSensitive(); scheduleAutoOff(AUTO_OFF_DELAY_MS) }
        // Register for the explicit new‑session broadcast.
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, sessionReceiver, IntentFilter(ACTION_NEW_USSD_SESSION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        resetSession(UssdSessionData.isBalanceCheck)
        // Reconnecting or updating the APK must not automate an existing carrier dialog.
        sessionArmed = false
        UssdSessionData.operationActive = false
        AccessibilityRuntime.diagnostics(this)
        Log.d(TAG, "===== ACCESSIBILITY SERVICE CONNECTED =====")
        Log.d(TAG, "USSD_STATE = $ussdState")
    }

    private fun resetSession(balanceCheck: Boolean = false) {
        dialogReturn.removeCallbacksAndMessages(null)
        balanceCancel.removeCallbacksAndMessages(null)
        balanceDialogSeen = false
        balanceMenuSeen = false
        setupTyped.clear()
        resultGrace.removeCallbacksAndMessages(null)
        infoOkClicks = 0
        carrierDialog = CarrierDialogLifecycle()
        paymentFlow = null
        ussdState = USSDState.IDLE
        inputFilled = false
        resultProcessed = false
        sendClicked = false
        lastInputTime = 0L
        balanceSourcePackage = null
        UssdSessionData.reset(balanceCheck)
    }

    override fun onDestroy() {
        detachConnection()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detachConnection()
        return super.onUnbind(intent)
    }

    private fun detachConnection() {
        UssdSessionData.onSessionEnded = null
        autoOff.removeCallbacksAndMessages(null)
        dialogReturn.removeCallbacksAndMessages(null)
        operationTimeout.removeCallbacksAndMessages(null)
        sessionArmed = false
        if (AccessibilityRuntime.connection.disconnect(this)) {
            val active = UssdSessionData.operationActive
            val balance = UssdSessionData.isBalanceCheck
            UssdSessionData.operationActive = false
            resetSession()
            if (active) {
                UssdSessionData.currentPresentationState = if (balance) "BALANCE_UNKNOWN" else "UNKNOWN"
                UssdSessionData.errorMessage = "Accessibility disconnected. No automatic retry was performed. Re-enable V4SH in Android Accessibility settings."
            }
        }
        // Clean up the broadcast receiver.
        if (receiverRegistered) {
            try {
                unregisterReceiver(sessionReceiver)
            } catch (_: IllegalArgumentException) {
                Log.d(TAG, "SESSION_RECEIVER_ALREADY_RELEASED=true")
            } finally {
                receiverRegistered = false
            }
        }
        V4SHTileService.refresh(applicationContext)
        Log.d(TAG, "===== ACCESSIBILITY SERVICE DESTROYED =====")
        AccessibilityRuntime.diagnostics(this)
    }

    override fun sleepNow() { com.festpay.app.data.SleepMode.dismissAutoSleepNote(applicationContext); UssdSessionData.onSessionEnded = null; autoOff.removeCallbacksAndMessages(null); try { disableSelf() } catch (_: Exception) { } }

    override fun connectionAlive(): Boolean = try { serviceInfo != null } catch (_: Exception) { false }

    override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String): Boolean {
        if (!com.festpay.app.data.AccessibilityConsent.accepted(this) || !AccessibilityRuntime.connection.arm(this, command, connectionId)) return false
        sessionArmed = true
        sessionStartedAt = android.os.SystemClock.uptimeMillis()
        scheduleOperationTimeout(command.sessionId, NAVIGATION_TIMEOUT_MS)
        return true
    }

    /**
     * One pending timeout per session. A timer is never evidence of acceptance or completion; it only
     * ends observation. Reaching the PIN prompt re-arms it with a longer window so a user who is
     * still entering their PIN is not reported as a failed submission.
     */
    private fun scheduleOperationTimeout(sessionId: String, delayMs: Long) {
        operationTimeout.removeCallbacksAndMessages(null)
        operationTimeout.postDelayed({
            if (!AccessibilityRuntime.connection.isOwner(this) || sessionRequestId != sessionId || !UssdSessionData.operationActive) return@postDelayed
            if (paymentFlow?.progress?.stage == CarrierStage.PIN) {
                paymentFlow?.interrupt(CarrierIssue.TIMEOUT)
                publishPaymentProgress()
                resultProcessed = true
                ussdState = USSDState.UNKNOWN
                UssdSessionData.operationActive = false
                UssdSessionData.currentPresentationState = "UNKNOWN"
                UssdSessionData.errorMessage = "No carrier result was observed in time. The payment outcome is unknown. Check your bank history and do not pay again."
            } else {
                markSubmissionUnclear()
                UssdSessionData.errorMessage = withCarrierWords("The cellular session did not provide a usable result in time. No automatic retry was performed.")
            }
        }, delayMs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!com.festpay.app.data.AccessibilityConsent.accepted(this)) return
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            com.festpay.app.data.BankingGuard.shouldSleep(com.festpay.app.data.BankingGuard.enabled(this), event.packageName, UssdSessionData.operationActive)) {
            com.festpay.app.data.PaymentDiagnostics.stage("svc.bankingGuard", "SLEEP")
            com.festpay.app.data.SleepMode.noteAutoSlept(applicationContext)
            android.widget.Toast.makeText(applicationContext, "V4SH went to sleep so your banking app can run. Reopen the banking app.", android.widget.Toast.LENGTH_LONG).show()
            UssdSessionData.onSessionEnded = null
            try { disableSelf() } catch (_: Exception) { }
            return
        }
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED && UssdSessionData.isBalanceCheck) {
            scheduleBalanceCancelCheck()
            return
        }
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED && ownsPaymentFlow(requireActive = false)) {
            if (android.os.Build.VERSION.SDK_INT >= 28 && event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0) {
                carrierDialog.removedWindow(event.windowId, event.eventTime)
            }
            scheduleReturnAfterDialogRemoval()
            // Window-list events do not supply payment labels or authorize inputs.
            return
        }
        if (event == null) return
        val dropReason = when {
            !AccessibilityRuntime.connection.isOwner(this) -> "NOT_OWNER"
            !AccessibilityRuntime.connection.snapshot.armed -> "NOT_ARMED"
            !sessionArmed -> "SESSION_NOT_ARMED"
            !UssdSessionData.operationActive -> "NOT_ACTIVE"
            UssdSessionData.sessionId != sessionRequestId -> "SESSION_MISMATCH"
            else -> null
        }
        if (dropReason != null) { logDrop(dropReason); return }
        val rootNode = rootInActiveWindow ?: return
        val rootPackage = rootNode.packageName?.toString()
        val systemApp = isSystemPackage(rootPackage)
        if (!UssdSourcePolicy.allows(rootPackage, event.packageName?.toString(), systemApp, true, event.eventTime, sessionStartedAt)) {
            logDrop("SOURCE_REJECTED pkg=${rootPackage ?: "null"} system=$systemApp")
            return
        }
        if (event.windowId >= 0 && rootNode.windowId >= 0 && event.windowId != rootNode.windowId) return
        if (UssdSessionData.isBalanceCheck) balanceDialogSeen = true
        if (UssdSessionData.isBalanceCheck && hasEditableEditText(rootNode)) balanceMenuSeen = true

        // One-time carrier setup screens (choose bank, card details, create PIN): never typed into by V4SH.
        val setupText = collectAllTexts(rootNode, preserveCase = true)
        // A bank-choice screen can only be the FIRST thing the carrier shows. Once V4SH has answered a menu it is never one.
        val earlyInSession = if (UssdSessionData.isBalanceCheck) !inputFilled else paymentFlow?.progress?.stage == CarrierStage.WAITING
        com.festpay.app.data.CarrierSetupPrompt.detect(setupText)
            ?.takeIf { it.kind != com.festpay.app.data.SetupPromptKind.BANK_SELECTION || earlyInSession }
            ?.let { prompt ->
            if (!typeSetupAnswer(rootNode, setupText, prompt)) stopForSetupPrompt(prompt)
            return
        }

        // Inspect flags only, before reading any labels. Never read password values.
        if (hasPasswordField(rootNode)) {
            if (!UssdSessionData.isBalanceCheck) {
                if (!ownsPaymentFlow()) return
                // Only static siblings are inspected: collectAllTexts skips entire input subtrees.
                val instruction = collectAllTexts(rootNode, preserveCase = true)
                paymentFlow?.observeAuthentication(event.eventTime,
                    UssdNavigationPolicy.isSupportedUpiAuthenticationPrompt(instruction), instruction)
                publishPaymentProgress()
                if (paymentFlow?.progress?.stage == CarrierStage.PIN) carrierDialog.observeOwnedWindow(rootNode.windowId, event.eventTime)
                return
            }
            ussdState = USSDState.AUTHENTICATION_REQUIRED
            UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
            Log.d(TAG, "USSD_SCREEN = PIN_OR_SENSITIVE")
            return
        }

        // Compute screen text and editable EditText once
        val screenText = collectAllTexts(rootNode, preserveCase = !UssdSessionData.isBalanceCheck)
        val hasEditable = hasEditableEditText(rootNode)
        if (!UssdSessionData.isBalanceCheck) {
            handlePaymentObservation(rootNode, screenText, hasEditable, event.eventTime)
            if (paymentFlow?.progress?.stage in setOf(CarrierStage.METHOD, CarrierStage.RECIPIENT,
                    CarrierStage.AMOUNT_PENDING, CarrierStage.AMOUNT, CarrierStage.AMOUNT_SENT,
                    CarrierStage.REMARK_SENT, CarrierStage.REVIEW, CarrierStage.PIN, CarrierStage.RESULT)) {
                carrierDialog.observeOwnedWindow(rootNode.windowId, event.eventTime)
            }
            return
        }

        // Balance: remember the carrier's own words, and if it plainly rejects the request, say so now instead of waiting.
        val carrierWords = plainCarrierMessage(collectAllTexts(rootNode, preserveCase = true))
        if (carrierWords != null && !isPinOrSensitiveScreen(screenText, hasEditable)) UssdSessionData.lastCarrierMessage = carrierWords
        val echoed = carrierWords != null && !hasEditable && UssdNavigationPolicy.isEchoedInput(collectAllTexts(rootNode, preserveCase = true))
        if (carrierWords != null && carrierWords != lastBalanceLogged) {
            lastBalanceLogged = carrierWords
            com.festpay.app.data.PaymentDiagnostics.stage("svc.balanceText", "TEXT", "text=" + carrierWords.take(120))
        }
        if (carrierWords != null && !hasEditable && ussdState != USSDState.SUCCESS && (echoed || UssdNavigationPolicy.isCarrierRejection(screenText))) {
            resultProcessed = true
            ussdState = USSDState.UNKNOWN
            UssdSessionData.errorMessage = if (echoed)
                "The carrier answered with only the number V4SH sent, not the next menu, so its *99# session dropped. No balance was read. Try again. If it repeats, dial *99# yourself to check that your bank's service answers on this SIM."
                else "The carrier replied: \"$carrierWords\""
            if (echoed) closeEchoDialog(rootNode)
            UssdSessionData.currentPresentationState = "BALANCE_UNKNOWN"
            UssdSessionData.operationActive = false
            return
        }
        if (!inputFilled && (ussdState == USSDState.IDLE || ussdState == USSDState.USSD_DETECTED) &&
            dismissInfoNotice(rootNode, collectAllTexts(rootNode, preserveCase = true), hasEditable)) return
        Log.d(TAG, "--- Accessibility Event ---")
        Log.d(TAG, "Current USSD_STATE = $ussdState")
        Log.d(TAG, "isUssdWindow = ${isUssdWindow(screenText)}")
        Log.d(TAG, "isPinOrSensitiveScreen = ${isPinOrSensitiveScreen(screenText, hasEditable)}")

        // ---------- 0. Once terminal state reached, stop processing further events for this session ----------
        if (ussdState == USSDState.SUCCESS || ussdState == USSDState.FAILED || ussdState == USSDState.UNKNOWN) {
            UssdSessionData.operationActive = false
            return
        }

        // ---------- 0.5. Post-authentication result detection ----------
        if (ussdState == USSDState.AUTHENTICATION_REQUIRED || ussdState == USSDState.WAITING_RESULT) {
            if (UssdSessionData.isBalanceCheck) {
                val sameSource = balanceSourcePackage != null &&
                    rootNode.packageName?.toString() == balanceSourcePackage &&
                    event.packageName?.toString() == balanceSourcePackage
                val sensitive = isPinOrSensitiveScreen(screenText, hasEditable)
                Log.d(TAG, "BALANCE_GUARD sourceMatch=$sameSource sensitive=$sensitive editable=$hasEditable")
                if (!BalanceObservationPolicy.canReadResult(
                        sameSource, sensitive
                    )) return

                val balanceRes = BalanceParser.parseBalance(screenText)
                // Partial, unrelated, and intermediate windows must not terminate observation.
                if (balanceRes.balance == null) {
                    Log.d(TAG, "BALANCE_RESULT_PENDING=true")
                    return
                }
                if (!resultProcessed) {
                    resultProcessed = true
                    UssdSessionData.parsedBalance = balanceRes.balance.toPlainString()
                    UssdSessionData.currentPresentationState = "BALANCE_SUCCESS"
                    ussdState = USSDState.SUCCESS
                    Log.d(TAG, "BALANCE_RESULT_PARSED=true")
                    UssdSessionData.operationActive = false
                }
                return
            }
            if (!isPinOrSensitiveScreen(screenText, hasEditable) && !isInitialMenu(screenText, hasEditable)) {
                if (UssdSessionData.isBalanceCheck) {
                    val balanceRes = BalanceParser.parseBalance(screenText)
                    if (!resultProcessed) {
                        resultProcessed = true
                        if (balanceRes.balance != null) {
                            UssdSessionData.parsedBalance = balanceRes.balance.toPlainString()
                            UssdSessionData.currentPresentationState = "BALANCE_SUCCESS"
                            ussdState = USSDState.SUCCESS
                            Log.d(TAG, "BALANCE_RESULT_PARSED=true")
                        } else {
                            UssdSessionData.currentPresentationState = "BALANCE_UNKNOWN"
                            ussdState = USSDState.UNKNOWN
                            Log.d(TAG, "BALANCE UNKNOWN - COULD NOT PARSE")
                        }
                    }
                } else {
                    val terminalState = classifyPostAuthResult(screenText)
                    if (!resultProcessed) {
                        resultProcessed = true
                        ussdState = terminalState
                        UssdSessionData.currentPresentationState = terminalState.name
                        UssdSessionData.operationActive = false
                        Log.d(TAG, "POST-AUTH TERMINAL RESULT CLASSIFIED = $terminalState")
                    }
                }
                return
            }
        }

        // ---------- 1. Is this any USSD window? ----------
        if (!isUssdWindow(screenText)) {
            Log.d(TAG, "Not a USSD window - ignoring event (current state: $ussdState)")
            if ((ussdState == USSDState.AUTHENTICATION_REQUIRED || ussdState == USSDState.WAITING_RESULT) && !resultProcessed) {
                resultProcessed = true
                if (UssdSessionData.isBalanceCheck && UssdSessionData.parsedBalance != null) {
                    UssdSessionData.currentPresentationState = "BALANCE_SUCCESS"
                    ussdState = USSDState.SUCCESS
                } else if (UssdSessionData.isBalanceCheck) {
                    UssdSessionData.currentPresentationState = "BALANCE_UNKNOWN"
                    ussdState = USSDState.UNKNOWN
                } else {
                    ussdState = USSDState.UNKNOWN
                    UssdSessionData.currentPresentationState = "UNKNOWN"
                }
                Log.d(TAG, "USSD window disappeared post-auth - classified as $ussdState")
                UssdSessionData.operationActive = false
            }
            return
        }

        // ---------- 2. If we are already waiting for a response, ignore everything ----------
        if (ussdState == USSDState.WAITING_RESPONSE) {
            val responseClass = when {
                isPinOrSensitiveScreen(screenText, hasEditable) -> "PIN_OR_SENSITIVE"
                else -> CarrierResultClassifier.observe(screenText).name
            }
            Log.d(TAG, "USSD_RESPONSE_SCREEN = $responseClass")
            return
        }

        // ---------- 3. Screen Classification & Safety ----------
        val screenType = when {
            isPinOrSensitiveScreen(screenText, hasEditable) -> "PIN_OR_SENSITIVE"
            !UssdSessionData.isBalanceCheck && isConfirmationReviewScreen(screenText, hasEditable) -> "CONFIRMATION_REVIEW"
            !UssdSessionData.isBalanceCheck && isAmountInputScreen(screenText, hasEditable) -> "AMOUNT_INPUT"
            !UssdSessionData.isBalanceCheck && hasEditable &&
                (screenText.contains("enter upi id", ignoreCase = true) ||
                 (UssdSessionData.mobilePayment && Regex("enter.*mobile", RegexOption.IGNORE_CASE).containsMatchIn(screenText))) -> "UPI_ID_INPUT"
            !UssdSessionData.isBalanceCheck && isSendMoneyMethodMenu(screenText, hasEditable) -> "SEND_MONEY_METHOD_MENU"
            isInitialMenu(screenText, hasEditable) -> "INITIAL_MENU"
            else -> "NON_ACTIONABLE"
        }
        Log.d(TAG, "USSD_SCREEN = $screenType")
        // Detect error response for invalid UPI ID
        if (screenText.contains("invalid upi id", ignoreCase = true)) {
            Log.d(TAG, "USSD_SCREEN = INVALID_UPI_ID")
        }

        // ---------- 4. PIN / sensitive screen – abort automation and enter AUTHENTICATION_REQUIRED ----------
        if (screenType == "PIN_OR_SENSITIVE") {
            Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping automation at AUTHENTICATION_REQUIRED")
            ussdState = USSDState.AUTHENTICATION_REQUIRED
            UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
            Log.d(TAG, "USSD_STATE = $ussdState")
            return // **Never** perform any further action on sensitive fields
        }

        // ---------- 5. Non-actionable screen ----------
        if (screenType == "NON_ACTIONABLE") {
            return // stay in current state, do nothing
        }

        // ---------- 6. Handle the menu (state machine) ----------
        handleUssdWindow(rootNode, screenText, hasEditable, screenType)
    }

    /** Very loose detection that this window belongs to a USSD dialog. */
    private fun isUssdWindow(screenText: String): Boolean {
        // USSD dialogs usually contain either "carrier info" or "select option".
        return screenText.contains("carrier info") || screenText.contains("select option")
    }

    /**
     * Determines whether the current screen is the **initial menu** (*99# main menu).
     */
    private fun isInitialMenu(screenText: String, hasEditable: Boolean): Boolean {
        if (!hasEditable || isPinOrSensitiveScreen(screenText, hasEditable)) return false
        if (!UssdSessionData.isBalanceCheck && isSendMoneyMethodMenu(screenText, hasEditable)) return false

        return UssdNavigationPolicy.isRootMenu(screenText)
    }

    /**
     * Determines whether the current screen is the **Send Money Method Menu**.
     */
    private fun isSendMoneyMethodMenu(screenText: String, hasEditable: Boolean): Boolean {
        if (UssdSessionData.isBalanceCheck) return false
        if (!hasEditable || isPinOrSensitiveScreen(screenText, hasEditable)) return false

        val lower = screenText.lowercase()
        val containsHeader = lower.contains("send money to")
        val containsOptions = lower.contains("upi id") && (lower.contains("mobile no") || lower.contains("ifsc"))

        return containsHeader || containsOptions
    }

    /**
     * Determines whether the current screen is the **Amount Input Screen**.
     */
    private fun isAmountInputScreen(screenText: String, hasEditable: Boolean): Boolean {
        if (UssdSessionData.isBalanceCheck) return false
        if (!hasEditable || isPinOrSensitiveScreen(screenText, hasEditable)) return false
        return UssdNavigationPolicy.isAmountPrompt(screenText)
    }

    /**
     * Determines whether the current screen is the **Confirmation / Review Screen**.
     */
    private fun isConfirmationReviewScreen(screenText: String, hasEditable: Boolean): Boolean {
        if (UssdSessionData.isBalanceCheck) return false
        if (isPinOrSensitiveScreen(screenText, hasEditable)) return false
        if (isInitialMenu(screenText, hasEditable)) return false
        if (isSendMoneyMethodMenu(screenText, hasEditable)) return false
        if (isAmountInputScreen(screenText, hasEditable)) return false
        if (screenText.contains("enter upi id", ignoreCase = true)) return false

        return UssdNavigationPolicy.isMatchingReview(screenText, UssdSessionData.recipientVpa, UssdSessionData.amount)
    }

    /**
     * Dedicated post-authentication result classifier.
     */
    private fun classifyPostAuthResult(screenText: String): USSDState {
        // UI strings are observations, never authorized transaction-specific evidence.
        return USSDState.UNKNOWN
    }

    /**
     * Returns true **only** for real PIN‑entry or PIN‑error screens.
     */
    private fun isPinOrSensitiveScreen(screenText: String, hasEditable: Boolean): Boolean =
        AccessibilityTextPolicy.isSensitivePrompt(screenText, hasEditable)

    private fun hasPasswordField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasPasswordField(child)) return true
        }
        return false
    }

    /** Recursively searches for an editable EditText anywhere in the tree. */
    private fun hasEditableEditText(node: AccessibilityNodeInfo): Boolean {
        if (node.className == "android.widget.EditText" && node.isEditable) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasEditableEditText(child)) return true
        }
        return false
    }

    /** Collect static labels only; do not read values or descendants of input nodes. */
    internal fun collectAllTexts(node: AccessibilityNodeInfo, accumulator: StringBuilder = StringBuilder(), preserveCase: Boolean = false): String {
        if (node.isPassword || node.isEditable || node.className == "android.widget.EditText") return accumulator.toString()
        AccessibilityTextPolicy.readLabel(node.isEditable, node.isPassword, false) { node.text }
            ?.let { accumulator.append(if (preserveCase) it else it.lowercase()).append(if (preserveCase) '\n' else ' ') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, accumulator, preserveCase)
        }
        return accumulator.toString()
    }

    /** Core automation logic based on the explicit state machine. */
    private fun handleUssdWindow(
        root: AccessibilityNodeInfo,
        screenText: String,
        hasEditable: Boolean,
        screenType: String
    ) {
        // Transition from IDLE to USSD_DETECTED on the first event of a fresh session.
        if (ussdState == USSDState.IDLE) {
            ussdState = USSDState.USSD_DETECTED
            UssdSessionData.currentPresentationState = if (UssdSessionData.isBalanceCheck) "CHECKING_BALANCE" else "SEND_MONEY"
            Log.d(TAG, "USSD_STATE = $ussdState")
        }

        // ---------- STAGE 1A: Enter input "1" (Send Money) or "3" (Check Balance) on INITIAL_MENU ----------
        if (screenType == "INITIAL_MENU" && !inputFilled && ussdState == USSDState.USSD_DETECTED) {
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            Log.d(TAG, "V4SH_BALANCE_DEBUG: ROOT_DETECTED")
            Log.d(TAG, "V4SH_BALANCE_DEBUG: IS_BALANCE_CHECK=${UssdSessionData.isBalanceCheck}")
            Log.d(TAG, "V4SH_BALANCE_DEBUG: IS_INITIAL_MENU=${isInitialMenu(screenText, hasEditable)}")

            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.AUTHENTICATION_REQUIRED
                UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
                return
            }

            val editNode = findEditableEditText(root)
            if (editNode != null) {
                val inputDigit = if (UssdSessionData.isBalanceCheck) "3" else "1"
                if (UssdSessionData.isBalanceCheck) balanceSourcePackage = root.packageName?.toString()
                Log.d(TAG, "V4SH_BALANCE_DEBUG: SELECTED_OPTION=$inputDigit")
                Log.d(TAG, "V4SH_BALANCE_DEBUG: ABOUT_TO_SEND_OPTION=$inputDigit")
                Log.d(TAG, "V4SH_BALANCE_DEBUG: INPUT_METHOD=AccessibilityNodeInfo.ACTION_SET_TEXT")
                Log.d(TAG, "AUTOMATION: entering option $inputDigit (${if (UssdSessionData.isBalanceCheck) "Check Balance" else "Send Money"})")
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        inputDigit
                    )
                }
                var success = false
                var inputException = false
                try {
                    success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } catch (e: Exception) {
                    inputException = true
                }
                Log.d(TAG, "V4SH_BALANCE_DEBUG: INPUT_RESULT=$success")
                Log.d(TAG, "V4SH_BALANCE_DEBUG: INPUT_EXCEPTION=$inputException")

                if (success) {
                    // Physically submit the USSD option by clicking the dialog's Send button.
                    // ACTION_SET_TEXT fills the field; ACTION_CLICK on Send is required for
                    // the carrier to receive the input — same mechanism used in Stage 1E.
                    inputFilled = true
                    val sendBtn = findSendButton(root)
                    val sendClicked = sendBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                    Log.d(TAG, "V4SH_BALANCE_DEBUG: SEND_BUTTON_FOUND=${sendBtn != null}")
                    Log.d(TAG, "V4SH_BALANCE_DEBUG: SEND_BUTTON_CLICKED=$sendClicked")
                    if (!sendClicked) { markSubmissionUnclear(); return }
                    inputFilled = true
                    lastInputTime = System.currentTimeMillis()
                    ussdState = USSDState.INPUT_ENTERED
                    UssdSessionData.currentPresentationState = if (UssdSessionData.isBalanceCheck) "CHECKING_BALANCE" else "SEND_MONEY"
                    return
                }
            } else {
                Log.d(TAG, "V4SH_BALANCE_DEBUG: EDIT_NODE_FOUND=false")
            }
        }

    }

    private val systemPackageCache = HashMap<String, Boolean>()
    /** Package-manager lookups are slow and the answer is stable for a running process. */
    private fun isSystemPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        return systemPackageCache.getOrPut(pkg) {
            try {
                val flags = packageManager.getApplicationInfo(pkg, 0).flags
                flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            } catch (_: Exception) { false }
        }
    }

    private var lastDrop = ""
    /** Diagnostics only: one line per change of reason, no screen text. Stripped from release builds. */
    private fun logDrop(reason: String) {
        if (reason != lastDrop) { lastDrop = reason; com.festpay.app.data.PaymentDiagnostics.stage("svc.event", "DROPPED", "reason=$reason") }
    }

    private fun ownsPaymentFlow(requireActive: Boolean = true): Boolean {
        if (!com.festpay.app.data.AccessibilityConsent.accepted(this) || !AccessibilityRuntime.connection.isOwner(this)) return false
        val state = AccessibilityRuntime.connection.snapshot
        val command = AccessibilityRuntime.connection.preparedCommand(sessionRequestId, state.connectionId.orEmpty()) ?: return false
        val flow = paymentFlow ?: return false
        return state.armed && sessionArmed && (!requireActive || UssdSessionData.operationActive) && !command.balance &&
            UssdSessionData.sessionId == command.sessionId && UssdSessionData.attemptId == command.attemptId &&
            flow.recipient == command.recipient && flow.amount == command.amount
    }

    private val resultGrace = android.os.Handler(android.os.Looper.getMainLooper())
    private var infoOkClicks = 0

    /**
     * Presses OK on a plain carrier notice shown BEFORE the menu (no input box, not a rejection, result, setup or PIN screen).
     * At most once per session (a second press could dismiss the real menu that follows). Returns true when OK was pressed.
     */
    private fun dismissInfoNotice(root: AccessibilityNodeInfo, labels: String, editable: Boolean): Boolean {
        if (!AUTO_PRESS_OK) return false
        if (infoOkClicks >= 1 || !UssdNavigationPolicy.isInformationalNotice(labels, editable) || hasPasswordField(root)) return false
        fun findOk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isPassword || n.isEditable || n.className == "android.widget.EditText") return null
            if (n.isClickable && com.festpay.app.data.UssdControlPolicy.isOk(n.text)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findOk(c)?.let { return it } }
            return null
        }
        val button = findOk(root) ?: return false
        val clicked = try { button.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
        com.festpay.app.data.PaymentDiagnostics.stage("svc.infoOk", if (clicked) "CLICKED" else "FAILED")
        if (clicked) infoOkClicks++
        return clicked
    }

    /** Closes the leftover "Carrier info <digit> OK" box after V4SH has reported that the carrier session dropped. */
    private fun closeEchoDialog(root: AccessibilityNodeInfo) {
        if (hasPasswordField(root)) return
        fun findOk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isPassword || n.isEditable || n.className == "android.widget.EditText") return null
            if (n.isClickable && com.festpay.app.data.UssdControlPolicy.isOk(n.text)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findOk(c)?.let { return it } }
            return null
        }
        runCatching { findOk(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }

    private val setupTyped = mutableMapOf<com.festpay.app.data.SetupPromptKind, Long>()

    /**
     * Types the owner's pre-entered bank name or card detail into the carrier's one-time setup screen. At most once per kind per
     * session (a repeated prompt means the carrier rejected it, so nothing more is tried), never into a password-flagged field,
     * never a PIN or OTP. Returns true when it handled the frame (typed, or already typed and waiting).
     */
    private fun typeSetupAnswer(root: AccessibilityNodeInfo, text: String, prompt: com.festpay.app.data.SetupPrompt): Boolean {
        if (!UssdSessionData.operationActive || !sessionArmed || resultProcessed) return false
        // Still showing what was just answered: wait. Shown again later: the carrier rejected it, so stop.
        setupTyped[prompt.kind]?.let { at -> return android.os.SystemClock.uptimeMillis() - at < 4_000L }
        if (hasPasswordField(root)) return false
        val answer = com.festpay.app.data.CarrierSetupPrompt.answer(text,
            com.festpay.app.data.SetupDetails.bankQuery(this), com.festpay.app.data.SetupDetails.last6(), com.festpay.app.data.SetupDetails.expiry()) ?: return false
        val node = findEditableEditText(root) ?: return false
        val set = try {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, answer)
            })
        } catch (_: Exception) { false }
        val clicked = set && (try { findSendButton(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true } catch (_: Exception) { false })
        com.festpay.app.data.PaymentDiagnostics.stage("svc.setupAnswer", if (clicked) "SENT" else "FAILED", "kind=${prompt.kind.name}")
        if (!clicked) return false
        setupTyped[prompt.kind] = android.os.SystemClock.uptimeMillis()
        if (prompt.kind == com.festpay.app.data.SetupPromptKind.CARD_DETAILS) com.festpay.app.data.SetupDetails.clearSensitive()
        return true
    }

    private fun stopForSetupPrompt(prompt: com.festpay.app.data.SetupPrompt) {
        if (resultProcessed || !UssdSessionData.operationActive) return
        com.festpay.app.data.PaymentDiagnostics.stage("svc.setupPrompt", prompt.kind.name)
        if (!UssdSessionData.isBalanceCheck && ownsPaymentFlow()) {
            paymentFlow?.interrupt(CarrierIssue.UNSUPPORTED)
            publishPaymentProgress()
        }
        resultProcessed = true
        ussdState = USSDState.UNKNOWN
        UssdSessionData.errorMessage = prompt.title + ". " + prompt.guidance
        UssdSessionData.currentPresentationState = if (UssdSessionData.isBalanceCheck) "BALANCE_UNKNOWN" else "UNKNOWN"
        com.festpay.app.data.IssueLog.record(prompt.title, prompt.guidance)
        operationTimeout.removeCallbacksAndMessages(null)
        balanceCancel.removeCallbacksAndMessages(null)
        sessionArmed = false
        UssdSessionData.operationActive = false
    }

    private var balanceDialogSeen = false
    private var balanceMenuSeen = false
    private var lastBalanceLogged: String? = null
    private val balanceCancel = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Balance only. If the owner closes the carrier dialog (Cancel) before a balance arrives, nothing else would ever end the
     * session, so Check Balance would wait and every later operation would be refused as "another operation is active".
     * Before the PIN a closed dialog is plain; after the PIN the carrier also closes its dialog while it works, so a longer wait is allowed.
     */
    private fun scheduleBalanceCancelCheck() {
        if (!UssdSessionData.isBalanceCheck || !UssdSessionData.operationActive || !sessionArmed || !balanceDialogSeen || resultProcessed) return
        // Once V4SH has sent an option, the carrier dialog normally CLOSES while it waits for the network, and the reply can take
        // 30 seconds or more. That is waiting, not a cancel: only a dialog closed before anything was sent is a cancel.
        // Before the menu has ever appeared, a closing dialog is the carrier's "Welcome to *99#" notice: the menu opens after it.
        val afterPin = !balanceMenuSeen || inputFilled || ussdState == USSDState.WAITING_RESPONSE ||
            ussdState == USSDState.AUTHENTICATION_REQUIRED || ussdState == USSDState.WAITING_RESULT
        val id = sessionRequestId
        balanceCancel.removeCallbacksAndMessages(null)
        balanceCancel.postDelayed({
            if (id != sessionRequestId || !UssdSessionData.operationActive || resultProcessed || !AccessibilityRuntime.connection.isOwner(this)) return@postDelayed
            val phoneVisible = try { windows.any { it.root?.packageName?.toString() in UssdSourcePolicy.approvedPackages } } catch (_: Exception) { true }
            if (phoneVisible) return@postDelayed
            com.festpay.app.data.PaymentDiagnostics.stage("svc.balance", "CARRIER_CLOSED", "afterPin=$afterPin")
            resultProcessed = true
            ussdState = USSDState.UNKNOWN
            UssdSessionData.errorMessage = withCarrierWords("The carrier screen was closed before a balance arrived. Nothing was changed. Tap Try Again to check again.")
            UssdSessionData.currentPresentationState = "BALANCE_UNKNOWN"
            operationTimeout.removeCallbacksAndMessages(null)
            sessionArmed = false
            UssdSessionData.operationActive = false
            AccessibilityRuntime.connection.release(id)
            try {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                })
            } catch (_: Exception) { }
        }, if (afterPin) 60_000L else 1_200L)
    }

    private fun scheduleReturnAfterDialogRemoval() {
        val lease = AccessibilityRuntime.connection.preparedCommand(sessionRequestId, connectionId) ?: return
        // After the PIN the carrier closes its dialog for several seconds while it processes, then shows the reply.
        // That gap is not the end of the session: keep observing until a result, the timeout, or the user stops.
        if (paymentFlow?.progress?.stage == CarrierStage.PIN) return
        val lifecycle = carrierDialog
        fun closed(consume: Boolean): Boolean {
            if (carrierDialog !== lifecycle || !ownsPaymentFlow(requireActive = false) ||
                !AccessibilityRuntime.connection.matches(lease, connectionId)) return false
            val reserved = runCatching {
                PaymentAttemptJournal(this).use { journal ->
                    journal.unresolvedId() == lease.attemptId && journal.records().any {
                        it.localTransactionId == lease.attemptId && it.payeeVpa == lease.recipient &&
                            it.amount == lease.amount && it.state in setOf(PaymentState.PROCESSING, PaymentState.UNKNOWN)
                    }
                }
            }.getOrDefault(false)
            if (!reserved) return false
            val visible = windows
            // Empty/incomplete lists, another phone window, or a missing root are inconclusive.
            // Read package/window metadata only, never the contents of other applications.
            val packages = visible.map { it.root?.packageName?.toString() }
            val activePackage = rootInActiveWindow?.packageName?.toString()
            // Removal from the interactive list may also mean obscured, not destroyed.
            // Require V4SH itself to be usable; never foreground it over Phone/Recents.
            val complete = visible.isNotEmpty() && packages.all { it != null } && activePackage == packageName
            val phone = packages.any { it in UssdSourcePolicy.approvedPackages } || activePackage in UssdSourcePolicy.approvedPackages
            val ids = visible.map { it.id }.toSet()
            return if (consume) lifecycle.consumeReturn(true, complete, phone, ids)
                else lifecycle.canReturn(true, complete, phone, ids)
        }
        if (!closed(false)) return
        dialogReturn.removeCallbacksAndMessages(null)
        // Debounce transient window replacement; the delay itself proves nothing.
        dialogReturn.postDelayed({
            if (!closed(true)) return@postDelayed
            if (UssdSessionData.operationActive) {
                paymentFlow?.interrupt(CarrierIssue.INPUT_UNCERTAIN)
                publishPaymentProgress()
            }
            UssdSessionData.carrierDialogEnded = true
            UssdSessionData.operationActive = false
            sessionArmed = false
            operationTimeout.removeCallbacksAndMessages(null)
            AccessibilityRuntime.connection.release(lease.sessionId)
            // No financial transition or reservation release. Reuse the existing app task.
            try {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                })
            } catch (_: Exception) {
                UssdSessionData.errorMessage = "Carrier dialog ended. Open V4SH to view the unverified outcome. Do not retry."
            }
        }, 400L)
    }

    private fun publishPaymentProgress(): Boolean {
        if (!ownsPaymentFlow()) return false
        val flow = paymentFlow ?: return false
        val next = flow.progress
        // Frames repeat about seven times a second. Nothing changed, so nothing is read or written.
        if (next == UssdSessionData.paymentProgress) return true
        try {
            PaymentAttemptJournal(this).use { journal ->
                check(journal.unresolvedId() == UssdSessionData.attemptId)
                check(journal.records().any { it.localTransactionId == UssdSessionData.attemptId && it.payeeVpa == flow.recipient && it.amount == flow.amount && it.state in setOf(PaymentState.PROCESSING, PaymentState.UNKNOWN) })
                if (next != UssdSessionData.paymentProgress) journal.recordCarrierObservation(UssdSessionData.attemptId, sessionRequestId, next)
            }
        } catch (_: Exception) {
            UssdSessionData.operationActive = false
            UssdSessionData.errorMessage = "Payment observation storage is unavailable. No automatic retry was performed."
            UssdSessionData.currentPresentationState = "UNKNOWN"
            return false
        }
        // Owner policy: the carrier's own final message decides. Failure here never blocks showing the result.
        com.festpay.app.data.CarrierOutcomePolicy.decide(next)?.let { outcome ->
            try {
                PaymentAttemptJournal(this).use { it.resolveByCarrier(UssdSessionData.attemptId, outcome, next.reference) }
                com.festpay.app.data.PaymentDiagnostics.stage("svc.carrierResolution", outcome.name, "attempt=${com.festpay.app.data.PaymentDiagnostics.shortId(UssdSessionData.attemptId)}")
            } catch (e: Exception) {
                com.festpay.app.data.PaymentDiagnostics.stage("svc.carrierResolution", "FAILED", "exception=${e.javaClass.simpleName}")
            }
        }
        val enteredPin = next.stage == CarrierStage.PIN && UssdSessionData.paymentProgress?.stage != CarrierStage.PIN
        UssdSessionData.paymentProgress = next
        UssdSessionData.currentPresentationState = "CARRIER_${next.stage.name}"
        if (enteredPin) scheduleOperationTimeout(sessionRequestId, PIN_RESULT_TIMEOUT_MS)
        if (next.stage == CarrierStage.INTERRUPTED || (next.stage == CarrierStage.RESULT && next.exited)) {
            resultGrace.removeCallbacksAndMessages(null)
            UssdSessionData.operationActive = false
        } else if (next.stage == CarrierStage.RESULT) {
            // The carrier may still show "1. Save 2. Exit". Keep watching briefly so V4SH can choose Exit, then close the session.
            resultGrace.removeCallbacksAndMessages(null)
            val id = sessionRequestId
            resultGrace.postDelayed({
                if (id == sessionRequestId && paymentFlow?.progress?.stage == CarrierStage.RESULT) UssdSessionData.operationActive = false
            }, 8_000L)
        }
        return true
    }

    private fun handlePaymentObservation(root: AccessibilityNodeInfo, labels: String, editable: Boolean, eventTime: Long) {
        val flow = paymentFlow ?: return
        if (!ownsPaymentFlow()) { logDrop("NOT_OWNING_FLOW"); return }
        if (!AccessibilityTextPolicy.isSensitivePrompt(labels, editable)) plainCarrierMessage(labels)?.let { UssdSessionData.lastCarrierMessage = it }
        lastDrop = ""
        val before = flow.progress.stage
        val input = flow.observe(labels, editable, eventTime)
        if (input == null && before == CarrierStage.WAITING && flow.progress.stage == CarrierStage.WAITING && dismissInfoNotice(root, labels, editable)) return
        // Debug builds only (PaymentDiagnostics is stripped from release): the carrier's own words when assistance stops.
        if (before != CarrierStage.INTERRUPTED && flow.progress.stage == CarrierStage.INTERRUPTED) {
            com.festpay.app.data.PaymentDiagnostics.stage("svc.interruptText", "TEXT", "text=" + labels.replace('\n', '|').take(120))
            if (UssdNavigationPolicy.isEchoedInput(labels)) {
                val msg = "The carrier answered with only the number V4SH sent, not the next menu, so its *99# session dropped. Nothing was paid. Try again. If it repeats, dial *99# yourself to check that your bank's service answers on this SIM."
                UssdSessionData.errorMessage = msg
                com.festpay.app.data.IssueLog.record("Carrier session dropped", msg)
                closeEchoDialog(root)
            }
        }
        com.festpay.app.data.PaymentDiagnostics.stage("svc.observe", "FRAME",
            "stage=${before.name}->${flow.progress.stage.name} issue=${flow.progress.issue.name} editable=$editable offered=${input?.kind?.name ?: "none"} chars=${labels.length}")
        if (UssdSessionData.launchDisposition != com.festpay.app.data.LaunchDisposition.CARRIER_OBSERVED &&
            (input != null || flow.progress.stage !in setOf(CarrierStage.WAITING, CarrierStage.INTERRUPTED))) {
            val command = AccessibilityRuntime.connection.preparedCommand(sessionRequestId, connectionId) ?: return
            try {
                PaymentAttemptJournal(this).use { it.recordLaunchMilestone(command, com.festpay.app.data.LaunchMilestone.CARRIER_OBSERVED) }
                UssdSessionData.launchDisposition = com.festpay.app.data.LaunchDisposition.CARRIER_OBSERVED
            } catch (_: Exception) {
                flow.interrupt(CarrierIssue.INPUT_UNCERTAIN)
                publishPaymentProgress()
                return
            }
        }
        if (!publishPaymentProgress() || input == null) return
        // ACTION_SET_TEXT and Send are requests only. A later exact review establishes observation.
        fun freshPrompt(): Boolean {
            fun no(reason: String): Boolean { com.festpay.app.data.PaymentDiagnostics.stage("svc.freshPrompt", "FAIL", "reason=$reason kind=${input.kind.name}"); return false }
            if (!ownsPaymentFlow()) return no("NOT_OWNING")
            if (!root.refresh()) return no("REFRESH_FAILED")
            if (hasPasswordField(root)) return no("PASSWORD_FIELD")
            val active = rootInActiveWindow ?: return no("NO_ACTIVE_WINDOW")
            if (active.windowId != root.windowId || active.packageName != root.packageName) return no("ACTIVE_WINDOW_DIFFERS active=${active.packageName}")
            if (!CarrierPaymentFlow.matchesInput(input, collectAllTexts(root), hasEditableEditText(root))) return no("PROMPT_NO_LONGER_MATCHES")
            return true
        }
        // Give the carrier's dialog a moment to settle before answering, like a person would. Answering within milliseconds of a
        // menu appearing can race the carrier session. The prompt is re-checked (refresh + match) right after.
        runCatching { Thread.sleep(INPUT_SETTLE_MS) }
        val sent = try {
            if (!freshPrompt()) false
            else if (input.kind == CarrierInputKind.CONFIRMATION) false // Native user confirmation only.
            else {
                val node = findEditableEditText(root)
                val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, input.value) }
                val set = node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
                com.festpay.app.data.PaymentDiagnostics.stage("svc.setText", if (set) "OK" else "FAILED", "kind=${input.kind.name} fieldFound=${node != null}")
                // Refresh flags/static labels before clicking, without ever reading an input value.
                if (!set || !freshPrompt()) false
                else {
                    val button = findSendButton(root)
                    val clicked = button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    com.festpay.app.data.PaymentDiagnostics.stage("svc.send", if (clicked) "CLICKED" else "FAILED", "buttonFound=${button != null}")
                    clicked
                }
            }
        } catch (e: Exception) { com.festpay.app.data.PaymentDiagnostics.stage("svc.input", "THREW", "exception=${e.javaClass.simpleName}"); false }
        flow.inputResult(input, sent)
        publishPaymentProgress()
    }

    private fun plainCarrierMessage(text: String): String? = com.festpay.app.data.CarrierMessage.plain(text)

    private fun withCarrierWords(message: String): String =
        UssdSessionData.lastCarrierMessage?.let { "$message The carrier last said: \"$it\"." } ?: message

    private fun markSubmissionUnclear() {
        paymentFlow?.interrupt(CarrierIssue.INPUT_UNCERTAIN)
        if (paymentFlow != null) publishPaymentProgress()
        resultProcessed = true
        ussdState = USSDState.UNKNOWN
        UssdSessionData.operationActive = false
        UssdSessionData.errorMessage = withCarrierWords("Could not submit the carrier menu option. Check the carrier screen before starting another operation.")
        UssdSessionData.currentPresentationState = if (UssdSessionData.isBalanceCheck) "BALANCE_UNKNOWN" else "UNKNOWN"
    }

    private fun sendEnteredInput(root: AccessibilityNodeInfo): Boolean {
        val sent = try { findSendButton(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
        catch (_: Exception) { false }
        Log.d(TAG, "NAVIGATION_SEND_CLICKED=$sent")
        if (!sent) markSubmissionUnclear()
        return sent
    }

    /** Find the first editable EditText (used for the menu selection). */
    private fun findEditableEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isPassword) return null
        if (node.className == "android.widget.EditText" && node.isEditable) {
            // Sensitive-screen checks use static labels, never this field's value.
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableEditText(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Find the clickable control that submits the typed reply. Exact labels only (Send, Reply, Submit, OK),
     * preferring the stock "Send"; input and password nodes are never candidates.
     */
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestRank = Int.MAX_VALUE
        fun visit(n: AccessibilityNodeInfo) {
            if (n.isPassword || n.isEditable || n.className == "android.widget.EditText") return
            val rank = if (n.isClickable) com.festpay.app.data.UssdControlPolicy.submitRank(n.text) else null
            if (rank != null && rank < bestRank) { best = n; bestRank = rank }
            for (i in 0 until n.childCount) n.getChild(i)?.let(::visit)
        }
        visit(node)
        return best
    }

    override fun onInterrupt() {
        sessionArmed = false
        if (AccessibilityRuntime.connection.isOwner(this)) AccessibilityRuntime.revokeSession()
        Log.d(TAG, "Service interrupted")
    }
}

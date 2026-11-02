package com.festpay.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.festpay.app.data.BalanceParser

@SuppressLint("AccessibilityPolicy")
class FestPayAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FestPayAccessibility"
        const val ACTION_NEW_USSD_SESSION = "com.festpay.app.ACTION_NEW_USSD_SESSION"
    }

    object UssdSessionData {
        var recipientVpa: String = ""
        var amount: String = ""
        var isBalanceCheck: Boolean = false
        var currentPresentationState: String = "CONNECTING"
        var parsedBalance: String? = null

        fun reset() {
            recipientVpa = ""
            amount = ""
            isBalanceCheck = false
            currentPresentationState = "CONNECTING"
            parsedBalance = null
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
    private var inputFilled = false
    private var methodInputFilled = false
    private var vpaFilled = false
    private var amountFilled = false
    private var confirmationDetected = false
    private var confirmationSubmitted = false
    private var resultProcessed = false
    private var sendClicked = false
    private var lastInputTime = 0L

    // ---------------------------------------------------------------------
    // BroadcastReceiver that listens for an explicit signal from MainActivity
    // when the user taps the "TEST *99#" button.
    // ---------------------------------------------------------------------
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NEW_USSD_SESSION) {
                Log.d(TAG, "Received new USSD session broadcast – resetting session")
                resetSession()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Register for the explicit new‑session broadcast.
        ContextCompat.registerReceiver(this, sessionReceiver, IntentFilter(ACTION_NEW_USSD_SESSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        resetSession()
        Log.d(TAG, "===== ACCESSIBILITY SERVICE CONNECTED =====")
        Log.d(TAG, "USSD_STATE = $ussdState")
    }

    private fun resetSession() {
        ussdState = USSDState.IDLE
        inputFilled = false
        methodInputFilled = false
        vpaFilled = false
        amountFilled = false
        confirmationDetected = false
        confirmationSubmitted = false
        resultProcessed = false
        sendClicked = false
        lastInputTime = 0L
        UssdSessionData.reset()
    }

    override fun onDestroy() {
        // Clean up the broadcast receiver.
        unregisterReceiver(sessionReceiver)
        Log.d(TAG, "===== ACCESSIBILITY SERVICE DESTROYED =====")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val rootNode = rootInActiveWindow ?: return

        // Compute screen text and editable EditText once
        val screenText = collectAllTexts(rootNode)
        val hasEditable = hasEditableEditText(rootNode)

        Log.d(TAG, "--- Accessibility Event ---")
        Log.d(TAG, "Current USSD_STATE = $ussdState")
        Log.d(TAG, "Full screen text: '$screenText'")
        Log.d(TAG, "isUssdWindow = ${isUssdWindow(screenText)}")
        Log.d(TAG, "isPinOrSensitiveScreen = ${isPinOrSensitiveScreen(screenText, hasEditable)}")

        // ---------- 0. Once terminal state reached, stop processing further events for this session ----------
        if (ussdState == USSDState.SUCCESS || ussdState == USSDState.FAILED || ussdState == USSDState.UNKNOWN) {
            return
        }

        // ---------- 0.5. Post-authentication result detection ----------
        if (ussdState == USSDState.AUTHENTICATION_REQUIRED || ussdState == USSDState.WAITING_RESULT) {
            if (!isPinOrSensitiveScreen(screenText, hasEditable) && !isInitialMenu(screenText, hasEditable)) {
                if (UssdSessionData.isBalanceCheck) {
                    val balanceRes = BalanceParser.parseBalance(screenText)
                    if (!resultProcessed) {
                        resultProcessed = true
                        if (balanceRes.balance != null) {
                            UssdSessionData.parsedBalance = balanceRes.balance.toPlainString()
                            UssdSessionData.currentPresentationState = "BALANCE_SUCCESS"
                            ussdState = USSDState.SUCCESS
                            Log.d(TAG, "BALANCE PARSED SUCCESSFULLY = ${balanceRes.balance}")
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
            }
            return
        }

        // ---------- 2. If we are already waiting for a response, ignore everything ----------
        if (ussdState == USSDState.WAITING_RESPONSE) {
            val responseClass = when {
                isPinOrSensitiveScreen(screenText, hasEditable) -> "PIN_OR_SENSITIVE"
                screenText.contains("success", ignoreCase = true) -> "SUCCESS"
                screenText.contains("failed", ignoreCase = true) ||
                        screenText.contains("invalid", ignoreCase = true) -> "FAILED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "USSD_RESPONSE_SCREEN = $responseClass")
            return
        }

        // ---------- 3. Screen Classification & Safety ----------
        val screenType = when {
            isPinOrSensitiveScreen(screenText, hasEditable) -> "PIN_OR_SENSITIVE"
            !UssdSessionData.isBalanceCheck && isConfirmationReviewScreen(screenText, hasEditable) -> "CONFIRMATION_REVIEW"
            !UssdSessionData.isBalanceCheck && isAmountInputScreen(screenText, hasEditable) -> "AMOUNT_INPUT"
            !UssdSessionData.isBalanceCheck && hasEditable && screenText.contains("enter upi id", ignoreCase = true) -> "UPI_ID_INPUT"
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

        val initialKeywords = listOf(
            "select option",
            "check balance",
            "my profile",
            "pending requests"
        )
        val lower = screenText.lowercase()
        return initialKeywords.any { lower.contains(it) }
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
        val lower = screenText.lowercase()
        if (lower.contains("upi id")) return false
        return lower.contains("enter amount") || lower.contains("enter rs") || (lower.contains("amount") && !lower.contains("upi id"))
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

        val lower = screenText.lowercase()
        val hasReviewKeywords = lower.contains("confirm") || lower.contains("send") || lower.contains("pay") || lower.contains("review") || lower.contains("details")
        val hasContext = lower.contains("rs.") || lower.contains("₹") || lower.contains("to") || lower.contains("@")
        return hasReviewKeywords && hasContext
    }

    /**
     * Dedicated post-authentication result classifier.
     */
    private fun classifyPostAuthResult(screenText: String): USSDState {
        val lower = screenText
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        val successKeywords = listOf("successful", "success", "transaction successful", "transaction completed")
        val failureKeywords = listOf("failed", "failure", "declined", "unsuccessful", "transaction failed")

        val hasSuccess = successKeywords.any { lower.contains(it) }
        val hasFailure = failureKeywords.any { lower.contains(it) }

        return when {
            hasSuccess -> USSDState.SUCCESS
            hasFailure -> USSDState.FAILED
            else -> USSDState.UNKNOWN
        }
    }

    /**
     * Returns true **only** for real PIN‑entry or PIN‑error screens.
     */
    private fun isPinOrSensitiveScreen(screenText: String, hasEditable: Boolean): Boolean {
        // Must have an editable field; otherwise not a PIN entry screen.
        if (!hasEditable) return false

        val normalizedScreenText = screenText
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        // Exclude UPI ID related screens to prevent false PIN classification
        if (normalizedScreenText.contains("upi id")) return false

        val pinPromptKeywords = listOf(
            "enter upi pin",
            "enter your upi pin",
            "invalid upi pin",
            "6 digit",
            "6-digit",
            "enter pin"
        )

        val pinManagementKeywords = listOf(
            "set/forgot upi pin",
            "change upi pin"
        )
        val containsPrompt = pinPromptKeywords.any { normalizedScreenText.contains(it) }
        val containsManagement = pinManagementKeywords.any { normalizedScreenText.contains(it) }
        return containsManagement || (hasEditable && containsPrompt)
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

    /** Recursively collects all text from the node tree (lower‑cased). */
    private fun collectAllTexts(node: AccessibilityNodeInfo, accumulator: StringBuilder = StringBuilder()): String {
        node.text?.let { accumulator.append(it.toString().lowercase()).append(' ') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, accumulator)
        }
        return accumulator.toString()
    }

    /** Find a confirmation button or control in the tree. */
    private fun findConfirmationButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val confirmKeywords = listOf("send", "confirm", "pay", "proceed", "yes")
        if (node.isClickable && confirmKeywords.any { text.contains(it) }) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findConfirmationButton(child)
            if (result != null) return result
        }
        return null
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
            Log.d(TAG, "V4SH_BALANCE_DEBUG: SCREEN_TEXT=$screenText")
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
                var inputException: String? = null
                try {
                    success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } catch (e: Exception) {
                    inputException = e.message
                }
                Log.d(TAG, "V4SH_BALANCE_DEBUG: INPUT_RESULT=$success")
                Log.d(TAG, "V4SH_BALANCE_DEBUG: INPUT_EXCEPTION=${inputException ?: "none"}")

                if (success) {
                    // Physically submit the USSD option by clicking the dialog's Send button.
                    // ACTION_SET_TEXT fills the field; ACTION_CLICK on Send is required for
                    // the carrier to receive the input — same mechanism used in Stage 1E.
                    val sendBtn = findSendButton(root)
                    val sendClicked = sendBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                    Log.d(TAG, "V4SH_BALANCE_DEBUG: SEND_BUTTON_FOUND=${sendBtn != null}")
                    Log.d(TAG, "V4SH_BALANCE_DEBUG: SEND_BUTTON_CLICKED=$sendClicked")
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

        // ---------- STAGE 1B: Enter input "3" (UPI ID) on SEND_MONEY_METHOD_MENU ----------
        if (!UssdSessionData.isBalanceCheck && screenType == "SEND_MONEY_METHOD_MENU" && !methodInputFilled) {
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.AUTHENTICATION_REQUIRED
                UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
                return
            }

            val editNode = findEditableEditText(root)
            if (editNode != null) {
                Log.d(TAG, "AUTOMATION: entering option 3 (UPI ID)")
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "3"
                    )
                }
                val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (success) {
                    methodInputFilled = true
                    lastInputTime = System.currentTimeMillis()
                    ussdState = USSDState.INPUT_ENTERED
                    UssdSessionData.currentPresentationState = "RECIPIENT_ENTRY"
                    return
                }
            }
        }

        // ---------- STAGE 1C: Enter recipient VPA on UPI_ID_INPUT ----------
        if (!UssdSessionData.isBalanceCheck && screenType == "UPI_ID_INPUT" && !vpaFilled) {
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.AUTHENTICATION_REQUIRED
                UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
                return
            }

            val targetVpa = UssdSessionData.recipientVpa.trim()
            if (targetVpa.isNotBlank() && targetVpa.contains("@")) {
                val editNode = findEditableEditText(root)
                if (editNode != null) {
                    Log.d(TAG, "AUTOMATION: entering recipient VPA ($targetVpa)")
                    val args = android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            targetVpa
                        )
                    }
                    val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    if (success) {
                        vpaFilled = true
                        lastInputTime = System.currentTimeMillis()
                        ussdState = USSDState.INPUT_ENTERED
                        UssdSessionData.currentPresentationState = "AMOUNT_ENTRY"
                        return
                    }
                }
            }
        }

        // ---------- STAGE 1D: Enter payment amount on AMOUNT_INPUT ----------
        if (!UssdSessionData.isBalanceCheck && screenType == "AMOUNT_INPUT" && !amountFilled) {
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.AUTHENTICATION_REQUIRED
                UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
                return
            }

            val targetAmount = UssdSessionData.amount.trim()
            if (targetAmount.isNotBlank()) {
                val editNode = findEditableEditText(root)
                if (editNode != null) {
                    Log.d(TAG, "AUTOMATION: entering payment amount ($targetAmount)")
                    val args = android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            targetAmount
                        )
                    }
                    val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    if (success) {
                        amountFilled = true
                        lastInputTime = System.currentTimeMillis()
                        ussdState = USSDState.INPUT_ENTERED
                        UssdSessionData.currentPresentationState = "REVIEW"
                        return
                    }
                }
            }
        }

        // ---------- STAGE 1E: Controlled confirmation automation on CONFIRMATION_REVIEW ----------
        if (!UssdSessionData.isBalanceCheck && screenType == "CONFIRMATION_REVIEW" && !confirmationSubmitted) {
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.AUTHENTICATION_REQUIRED
                UssdSessionData.currentPresentationState = "AUTHENTICATION_REQUIRED"
                return
            }

            val confirmButton = findConfirmationButton(root)
            if (confirmButton != null) {
                Log.d(TAG, "AUTOMATION: activating confirmation control")
                val success = confirmButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "CONFIRMATION CLICK RESULT = $success")
                if (success) {
                    confirmationSubmitted = true
                    ussdState = USSDState.WAITING_RESULT
                    UssdSessionData.currentPresentationState = "CONFIRMING"
                    Log.d(TAG, "AUTOMATION: confirmation submitted successfully - entering WAITING_RESULT")
                    return
                }
            } else {
                val editNode = findEditableEditText(root)
                if (editNode != null && (currentText.contains("confirm") || currentText.contains("press 1"))) {
                    Log.d(TAG, "AUTOMATION: entering '1' to confirm review")
                    val args = android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            "1"
                        )
                    }
                    val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    if (success) {
                        confirmationSubmitted = true
                        ussdState = USSDState.WAITING_RESULT
                        UssdSessionData.currentPresentationState = "CONFIRMING"
                        Log.d(TAG, "AUTOMATION: confirmation '1' entered successfully - entering WAITING_RESULT")
                        return
                    }
                }
            }
        }
    }

    /** Find the first editable EditText (used for the menu selection). */
    private fun findEditableEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.EditText" && node.isEditable) {
            // Guard: avoid any edit text that mentions PIN in its own label/content.
            if (node.text?.toString()?.contains("pin", ignoreCase = true) == true) return null
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableEditText(child)
            if (result != null) return result
        }
        return null
    }

    /** Find a clickable node whose visible text is "Send". */
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.text?.toString()?.equals("Send", ignoreCase = true) == true && node.isClickable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSendButton(child)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
}

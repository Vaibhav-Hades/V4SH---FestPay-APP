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

@SuppressLint("AccessibilityPolicy")
class FestPayAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FestPayAccessibility"
        const val ACTION_NEW_USSD_SESSION = "com.festpay.app.ACTION_NEW_USSD_SESSION"
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
        COMPLETED,
        FAILED,
        UNKNOWN
    }

    // ---------------------------------------------------------------------
    // State variables – they are **reset** each time we start a fresh USSD session.
    // ---------------------------------------------------------------------
    private var ussdState: USSDState = USSDState.IDLE
    private var inputFilled = false
    private var methodInputFilled = false
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
        sendClicked = false
        lastInputTime = 0L
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

        // ---------- 1. Is this any USSD window? ----------
        if (!isUssdWindow(screenText)) {
            Log.d(TAG, "Not a USSD window - ignoring event (current state: $ussdState)")
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
            hasEditable && screenText.contains("enter upi id", ignoreCase = true) -> "UPI_ID_INPUT"
            isSendMoneyMethodMenu(screenText, hasEditable) -> "SEND_MONEY_METHOD_MENU"
            isInitialMenu(screenText, hasEditable) -> "INITIAL_MENU"
            else -> "NON_ACTIONABLE"
        }
        Log.d(TAG, "USSD_SCREEN = $screenType")
        // Detect error response for invalid UPI ID
        if (screenText.contains("invalid upi id", ignoreCase = true)) {
            Log.d(TAG, "USSD_SCREEN = INVALID_UPI_ID")
        }

        // ---------- 4. PIN / sensitive screen – abort completely ----------
        if (screenType == "PIN_OR_SENSITIVE") {
            Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
            ussdState = USSDState.FAILED
            Log.d(TAG, "USSD_STATE = $ussdState")
            return // **Never** perform any further action
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
     * Must contain known initial menu keywords and NOT be the Send Money Method Menu or PIN screen.
     */
    private fun isInitialMenu(screenText: String, hasEditable: Boolean): Boolean {
        if (!hasEditable || isPinOrSensitiveScreen(screenText, hasEditable)) return false
        if (isSendMoneyMethodMenu(screenText, hasEditable)) return false

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
     * Structure: "Send money to: 1. Mobile No. 3. UPI ID 4. Saved Beneficiary 5. IFSC, A/C No."
     */
    private fun isSendMoneyMethodMenu(screenText: String, hasEditable: Boolean): Boolean {
        if (!hasEditable || isPinOrSensitiveScreen(screenText, hasEditable)) return false

        val lower = screenText.lowercase()
        val containsHeader = lower.contains("send money to")
        val containsOptions = lower.contains("upi id") && (lower.contains("mobile no") || lower.contains("ifsc"))

        return containsHeader || containsOptions
    }

    /**
     * Returns true **only** for real PIN‑entry or PIN‑error screens.
     * We look for explicit prompt/error wording **and** an editable EditText.
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

        // Keywords that indicate a real PIN‑entry screen (require an editable field).
        val pinPromptKeywords = listOf(
            "enter upi pin",
            "enter your upi pin",
            "invalid upi pin",
            "6 digit",
            "6-digit",
            "enter pin"
        )

        // Keywords that represent PIN‑management menu screens – treat as sensitive regardless of editability
        val pinManagementKeywords = listOf(
            "set/forgot upi pin",
            "change upi pin"
        )
        val containsPrompt = pinPromptKeywords.any { normalizedScreenText.contains(it) }
        val containsManagement = pinManagementKeywords.any { normalizedScreenText.contains(it) }
        // Diagnostic logging
        Log.d(TAG, "PIN DEBUG screenText=[$screenText]")
        Log.d(TAG, "PIN DEBUG normalizedScreenText=[$normalizedScreenText]")
        Log.d(TAG, "PIN DEBUG hasEditable=$hasEditable")
        Log.d(TAG, "PIN DEBUG containsPrompt=$containsPrompt")
        Log.d(TAG, "PIN DEBUG containsManagement=$containsManagement")
        Log.d(TAG, "PIN DEBUG isPinOrSensitiveScreen=${containsManagement || containsPrompt}")
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
            Log.d(TAG, "USSD_STATE = $ussdState")
        }

        // ---------- STAGE 1A: Enter input "1" (Send Money) on INITIAL_MENU ----------
        if (screenType == "INITIAL_MENU" && !inputFilled && ussdState == USSDState.USSD_DETECTED) {
            // Guard: CRITICAL SAFETY check before ACTION_SET_TEXT
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.FAILED
                Log.d(TAG, "USSD_STATE = $ussdState")
                return
            }

            val editNode = findEditableEditText(root)
            if (editNode != null) {
                Log.d(TAG, "AUTOMATION: entering option 1 (Send Money)")
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "1"
                    )
                }
                val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Log.d(TAG, "SET TEXT RESULT = $success")
                if (success) {
                    inputFilled = true
                    lastInputTime = System.currentTimeMillis()
                    ussdState = USSDState.INPUT_ENTERED
                    Log.d(TAG, "AUTOMATION: option 1 entered successfully")
                    Log.d(TAG, "USSD_STATE = $ussdState")
                    // Do NOT click Send - stop processing that event
                    return
                }
            }
        }

        // ---------- STAGE 1B: Enter input "3" (UPI ID) on SEND_MONEY_METHOD_MENU ----------
        if (screenType == "SEND_MONEY_METHOD_MENU" && !methodInputFilled) {
            // Guard: CRITICAL SAFETY check before ACTION_SET_TEXT
            val currentText = collectAllTexts(root)
            val currentHasEditable = hasEditableEditText(root)
            if (isPinOrSensitiveScreen(currentText, currentHasEditable)) {
                Log.d(TAG, "AUTOMATION: PIN/sensitive screen detected - stopping")
                ussdState = USSDState.FAILED
                Log.d(TAG, "USSD_STATE = $ussdState")
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
                Log.d(TAG, "SET TEXT RESULT = $success")
                if (success) {
                    methodInputFilled = true
                    lastInputTime = System.currentTimeMillis()
                    ussdState = USSDState.INPUT_ENTERED
                    Log.d(TAG, "AUTOMATION: UPI ID option entered successfully")
                    Log.d(TAG, "USSD_STATE = $ussdState")
                    // Do NOT click Send - stop processing that event
                    return
                }
            }
        }

        // ---------- STAGE 2: Send-button automation is DISABLED for this experiment ----------
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
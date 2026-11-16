package com.festpay.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.festpay.app.data.AccessibilityConsent
import com.festpay.app.data.AccessibilityRuntime
import com.festpay.app.data.AccessibilityTextPolicy
import com.festpay.app.data.CarrierPaymentFlow
import com.festpay.app.data.UssdSourcePolicy
import com.festpay.app.data.UssdNavigationPolicy
import org.json.JSONObject
import java.io.File

/** Debug-only physical observation. No dialer, input action, session claim or journal access. */
class UssdWindowProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var expiresAt = 0L
    private var previous = ""
    private var samples = 0
    private var retainedField: AccessibilityNodeInfo? = null
    private var originalWindow: Int? = null
    private lateinit var status: TextView
    private val output get() = File(filesDir, "ussd_window_probe.jsonl")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        column.addView(TextView(this).apply {
            text = "Read-only USSD window diagnostic\n\nNo payment, balance, recipient, amount, PIN or OTP. This probe never dials, types or presses carrier Send.\n\nClose any old carrier dialog first. Start observation, manually dial *99# ONCE in Phone, stay on its initial menu, switch to V4SH and back using Recents. Do not choose any menu option. Cancel the carrier dialog when finished."
            textSize = 18f
        })
        status = TextView(this).apply { text = "Not started. Existing payment protection is unchanged." }
        column.addView(status)
        column.addView(Button(this).apply { text = "Start read-only observation (3 minutes)"; setOnClickListener { startProbe() } })
        column.addView(Button(this).apply { text = "Stop observation"; setOnClickListener { stopProbe("USER_STOPPED") } })
        column.addView(Button(this).apply {
            text = "Open V4SH screen"
            setOnClickListener { startActivity(Intent(this@UssdWindowProbeActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        })
        column.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun idle(): Boolean = !FestPayAccessibilityService.UssdSessionData.operationActive &&
        AccessibilityRuntime.connection.snapshot.let { it.sessionId == null && !it.armed && !it.initialized && !it.acknowledged }

    private fun startProbe() {
        if (running) return
        if (!AccessibilityConsent.accepted(this) || !AccessibilityRuntime.connection.snapshot.connected || !idle()) {
            status.text = "Blocked: consent/binding unavailable or another operation is active. No session was changed."
            return
        }
        output.writeText("") // Only this debug probe's non-sensitive metadata file.
        previous = ""; samples = 0; retainedField = null; originalWindow = null
        running = true; expiresAt = SystemClock.uptimeMillis() + 180_000
        append(JSONObject().put("event", "USER_STARTED_READ_ONLY_PROBE"))
        status.text = "Observing metadata only. Manually open *99# once; do not select an operation."
        handler.post(tick)
    }

    private fun append(value: JSONObject) {
        value.put("uptime_ms", SystemClock.uptimeMillis()).put("pid", Process.myPid())
        output.appendText(value.toString() + "\n")
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (SystemClock.uptimeMillis() >= expiresAt) { stopProbe("OBSERVATION_DEADLINE"); return }
            if (!AccessibilityConsent.accepted(this@UssdWindowProbeActivity) || !idle()) { stopProbe("CONSENT_OR_OPERATION_CHANGED"); return }
            val connection = AccessibilityRuntime.connection.snapshot
            val service = AccessibilityRuntime.connection.host() as? FestPayAccessibilityService
            val value = JSONObject().put("connected", connection.connected).put("instance", service != null)
                .put("session_armed", connection.armed).put("operation_active", FestPayAccessibilityService.UssdSessionData.operationActive)
                .put("connection_generation", connection.connectionId ?: JSONObject.NULL)
            try {
                value.put("service_flags", service?.serviceInfo?.flags ?: 0)
                    .put("interactive_window_count", service?.windows?.size ?: 0)
                val root = service?.rootInActiveWindow
                value.put("root_available", root != null)
                if (root != null) {
                    val pkg = root.packageName?.toString().orEmpty()
                    val approved = pkg in UssdSourcePolicy.approvedPackages && runCatching {
                        packageManager.getApplicationInfo(pkg, 0).flags and
                            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    }.getOrDefault(false)
                    value.put("package", if (approved || pkg == packageName) pkg else "OTHER")
                        .put("window_id", root.windowId).put("root_class", root.className?.toString())
                        .put("carrier_system_package", approved)
                    if (approved) inspectInitialMenu(root, value)
                    value.put("original_menu_window", originalWindow ?: JSONObject.NULL)
                        .put("same_window_as_initial", originalWindow != null && originalWindow == root.windowId)
                }
                val old = retainedField
                if (old != null) {
                    val fresh = old.refresh()
                    value.put("retained_field_refresh", fresh)
                    if (fresh) {
                        value.put("retained_field_window", old.windowId).put("retained_field_password", old.isPassword)
                        if (old.isPassword) { retainedField = null; value.put("sensitive_flags", true) }
                    }
                }
            } catch (_: Exception) { value.put("metadata_query_failed", true) }
            val encoded = value.toString()
            if (encoded != previous && samples < 150) {
                append(value); previous = encoded; samples++
                status.text = "Metadata snapshots: $samples. No carrier input or Send action has been performed."
            }
            if (value.optBoolean("sensitive_flags")) { stopProbe("AUTHENTICATION_BOUNDARY_NOT_TESTED"); return }
            handler.postDelayed(this, 200)
        }
    }

    private fun inspectInitialMenu(root: AccessibilityNodeInfo, result: JSONObject) {
        if (passwordFlag(root)) { result.put("sensitive_flags", true); return }
        val field = editable(root)
        val labels = staticLabels(root)
        if (AccessibilityTextPolicy.isSensitivePrompt(labels, field != null)) { result.put("sensitive_flags", true); return }
        val initial = CarrierPaymentFlow.rootOption(labels) != null
        result.put("initial_banking_menu", initial)
        if (UssdNavigationPolicy.isAmountPrompt(labels) || Regex("\\benter\\s+(?:(?:the|recipient|beneficiary)\\s+)?(?:upi|vpa|mobile)", RegexOption.IGNORE_CASE).containsMatchIn(labels)) {
            result.put("sensitive_flags", true).put("past_initial_menu", true); return
        }
        val send = sendButton(root)
        val dialogControls = field != null && send != null
        result.put("input_send_dialog_controls", dialogControls)
        if (!initial && !dialogControls) return // Do not assume one bank's menu wording.
        if (originalWindow == null) originalWindow = root.windowId
        result.put("editable_field", field != null)
        if (field != null) {
            result.put("field_class", field.className?.toString()).put("field_editable", field.isEditable)
                .put("field_password", field.isPassword).put("field_enabled", field.isEnabled)
                .put("field_window_id", field.windowId).put("field_resource_id", field.viewIdResourceName ?: JSONObject.NULL)
                .put("field_action_ids", org.json.JSONArray(field.actionList.map { it.id }))
                .put("set_text_advertised", field.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT })
            if (retainedField == null) retainedField = field
        }
        result.put("send_control", send != null).put("send_click_advertised", send?.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } == true)
        result.put("input_value_read", false).put("carrier_action_performed", false)
    }

    private fun passwordFlag(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        for (i in 0 until node.childCount) if (node.getChild(i)?.let(::passwordFlag) == true) return true
        return false
    }
    private fun editable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isPassword) return null
        if (node.isEditable && node.className == "android.widget.EditText") return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { editable(it)?.let { field -> return field } }
        return null
    }
    private fun staticLabels(node: AccessibilityNodeInfo): String {
        if (node.isPassword || node.isEditable || node.className == "android.widget.EditText") return ""
        val text = StringBuilder(AccessibilityTextPolicy.readLabel(false, false, false) { node.text }.orEmpty())
        for (i in 0 until node.childCount) node.getChild(i)?.let { text.append(' ').append(staticLabels(it)) }
        return text.toString() // Used only in memory to recognize the initial menu, never serialized.
    }
    private fun sendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isPassword || node.isEditable || node.className == "android.widget.EditText") return null
        if (node.isClickable && node.text?.toString()?.equals("Send", true) == true) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { sendButton(it)?.let { button -> return button } }
        return null
    }
    private fun stopProbe(reason: String) {
        if (running) append(JSONObject().put("event", reason))
        running = false; handler.removeCallbacks(tick); retainedField = null
        status.text = "Observation stopped: $reason. No carrier actions were performed."
    }
    override fun onDestroy() {
        stopProbe("PROBE_ACTIVITY_DESTROYED")
        super.onDestroy()
    }
}

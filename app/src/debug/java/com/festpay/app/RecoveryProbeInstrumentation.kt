package com.festpay.app

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Process
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentRequest
import com.festpay.app.ui.viewmodel.PaymentState
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Host-controlled synthetic fixtures only. No payment engine, dialer or accessibility calls. */
class RecoveryProbeInstrumentation : Instrumentation() {
    private lateinit var arguments: Bundle
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); this.arguments = arguments ?: Bundle(); start() }
    override fun onStart() {
        try {
            val name = requireNotNull(arguments.getString("store"))
            require(Regex("test_recovery_[a-zA-Z0-9_-]+\\.db").matches(name))
            val id = requireNotNull(arguments.getString("identity"))
            require(UUID.fromString(id).toString() == id)
            val mode = arguments.getString("mode")
            val context = targetContext
            val request = PaymentRequest("synthetic@upi", "Synthetic recovery", "1.00")
            when (mode) {
                "seed", "seed_unknown" -> PaymentAttemptJournal(context, name, { id }).use {
                    it.create(request); it.transition(id, PaymentState.PROCESSING, "TEST_ARM")
                    if (mode == "seed_unknown") it.transition(id, PaymentState.UNKNOWN, "TEST_UNCERTAIN")
                }
                "crash" -> {
                    PaymentAttemptJournal(context, name, { id }).use { it.create(request) }
                    val boundary = JournalBoundary.valueOf(requireNotNull(arguments.getString("boundary")))
                    PaymentAttemptJournal(context, name, { id }, { point ->
                        if (point == boundary) {
                            sendStatus(0, Bundle().apply { putString("stream", "RECOVERY_PROBE=CRASH_BOUNDARY_REACHED:$point\n") })
                            Process.killProcess(Process.myPid())
                            throw IllegalStateException("Process termination requested")
                        }
                    }).use { it.transition(id, PaymentState.PROCESSING, "TEST_ARM") }
                    error("Crash fixture did not terminate")
                }
                "verify" -> {
                    PaymentAttemptJournal(context, name).use { store ->
                        if (arguments.getString("expected") == "UNAVAILABLE") {
                            blocked { store.records() }; blocked { store.create(request) }
                            check(store.readableDatabase.rawQuery("SELECT COUNT(*) FROM reservation", null).use { it.moveToFirst(); it.getInt(0) } == 1)
                        } else {
                            store.recover()
                            check(store.unresolvedId() == id)
                            check(store.records().single().state == PaymentState.UNKNOWN)
                            check(store.records().single().localTransactionId == id)
                            blocked { store.create(request) }
                        }
                    }
                    cleanup(name)
                }
                "cleanup" -> cleanup(name)
                else -> error("Unsupported fixture mode")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "RECOVERY_PROBE=PASS:$mode\n") })
        } catch (failure: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "RECOVERY_PROBE=FAIL:${failure.javaClass.simpleName}\n") })
        }
    }
    private fun blocked(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalStateException) { failed = true }
        check(failed)
    }
    private fun cleanup(name: String) {
        require(name.startsWith("test_recovery_"))
        targetContext.deleteDatabase(name)
        val namespace = "journal:$name"
        val file = StorageSeal.path(targetContext, namespace)
        for (suffix in listOf("", ".bak", ".new")) File(file.path + suffix).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KeystoreRecordCipher(namespace).alias) }
    }
}

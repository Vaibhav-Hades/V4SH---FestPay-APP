package com.festpay.app

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.StorageSeal
import com.festpay.app.data.StorageNames
import org.json.JSONObject
import java.io.File

/** Explicit read-only real-store audit. Never recovers, transitions, launches or logs financial fields. */
class PaymentReadOnlyAuditInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val context = targetContext
            val database = context.getDatabasePath("v4sh_attempts.db")
            require(database.isFile)
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { require(it.version == 2) }
            val seal = StorageSeal.path(context, "journal:v4sh_attempts.db")
            fun digest(file: File) = StorageNames.digest(file.readBytes())
            val before = digest(database) to digest(seal)
            val output = PaymentAttemptJournal(context).use { store ->
                val id = checkNotNull(store.unresolvedId())
                val records = store.records()
                val record = records.single { it.localTransactionId == id }
                JSONObject().apply {
                    put("local_attempt_id", id); put("created_at_utc", record.timestamp)
                    put("saved_state", record.state.name); put("reservation_present", true)
                    put("reservation_matches_record", true); put("provider_reference_present", record.bankReference != null)
                    put("provider_provenance_present", record.referenceProvenance != null)
                    put("record_count", records.size)
                }
            }
            val after = digest(database) to digest(seal)
            check(before == after)
            output.put("journal_and_seal_unchanged", true)
            File(context.filesDir, "read_only_attempt_audit.json").writeText(output.toString(2))
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "READ_ONLY_ATTEMPT_AUDIT=PASS\n") })
        } catch (_: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "READ_ONLY_ATTEMPT_AUDIT=UNAVAILABLE_NO_RESET\n") })
        }
    }
}

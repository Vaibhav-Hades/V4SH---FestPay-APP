package com.festpay.app

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import com.festpay.app.data.KeystoreRecordCipher
import com.festpay.app.data.StorageNames
import com.festpay.app.data.StorageSeal
import org.json.JSONObject
import java.time.Instant

/** Temporary debug-only inspector. No helper, migration, key initialization, recovery or file output. */
class JournalEventInspectorInstrumentation : Instrumentation() {
    /** Defaults to the most recently created attempt; pass -e attempt <id> to inspect another. Read-only. */
    private var attempt = ""
    private var requested: String? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); requested = arguments?.getString("attempt"); start() }
    override fun onStart() {
        val lines = mutableListOf<String>()
        var ok = false
        try {
            val context = targetContext
            val file = context.getDatabasePath("v4sh_attempts.db")
            val anchor = StorageSeal.path(context, "journal:v4sh_attempts.db")
            check(file.isFile && anchor.isFile)
            fun fingerprints() = listOf(file, anchor).map { StorageNames.digest(it.readBytes()) }
            val before = fingerprints()
            try {
                val cipher = KeystoreRecordCipher("journal:v4sh_attempts.db")
                check(cipher.exists()) // Never initialize a key.
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    check(db.isReadOnly && db.version == 2)
                    val header = db.rawQuery("SELECT payload FROM store_meta WHERE slot=1", null).use {
                        check(it.moveToFirst()); cipher.open(it.getBlob(0), "header")
                    }
                    check(header.contentEquals(cipher.open(anchor.readBytes(), "anchor")))
                    lines += "DATABASE_READ_ONLY=true; EXISTING_KEY=true; HEADER_SEAL_MATCH=true"
                    if (requested != null) attempt = requested!! else {
                        var newest = -1L
                        db.rawQuery("SELECT row_key,payload FROM attempts", null).use { c ->
                            while (c.moveToNext()) {
                                val v = JSONObject(cipher.open(c.getBlob(1), "attempts:${c.getString(0)}").toString(Charsets.UTF_8))
                                if (v.getLong("created") > newest) { newest = v.getLong("created"); attempt = v.getString("id") }
                            }
                        }
                    }
                    db.rawQuery("SELECT payload FROM attempts WHERE row_key=?", arrayOf(StorageNames.digest(attempt))).use {
                        check(it.moveToFirst())
                        val value = JSONObject(cipher.open(it.getBlob(0), "attempts:${StorageNames.digest(attempt)}").toString(Charsets.UTF_8))
                        check(value.getString("id") == attempt)
                        lines += "ATTEMPT " + JSONObject().apply {
                            for (key in listOf("id", "created", "state", "version")) put(key, value.opt(key) ?: "UNAVAILABLE")
                        }
                    }
                    val events = mutableListOf<JSONObject>()
                    var failed = 0
                    var unattributable = 0
                    db.rawQuery("SELECT row_key,payload FROM events", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            check(events.size < 10000)
                            val key = cursor.getString(0)
                            val value = try {
                                JSONObject(cipher.open(cursor.getBlob(1), "events:$key").toString(Charsets.UTF_8))
                            } catch (_: Exception) { failed++; continue }
                            if (!value.has("attempt") || value.isNull("attempt")) { unattributable++; continue }
                            if (value.optString("attempt") != attempt) continue
                            val output = JSONObject()
                            for (field in listOf("event_id", "attempt", "occurred", "from_state", "to_state", "reason", "source", "policy", "attempt_version", "carrier_issue", "amount_observed_matching", "result_wording", "carrier_reference")) {
                                output.put(field, if (value.has(field)) value.get(field) else "UNAVAILABLE")
                            }
                            output.put("row_id_matches_event", StorageNames.digest(value.optString("event_id")) == key)
                            val time = value.optLong("occurred", -1)
                            output.put("timestamp_utc", if (time > 0) runCatching { Instant.ofEpochMilli(time).toString() }.getOrDefault("MALFORMED") else "MALFORMED_OR_UNAVAILABLE")
                            output.put("field_validation", listOf("event_id", "attempt", "to_state", "reason", "source").filter { value.has(it) && value.opt(it) !is String }.joinToString().ifEmpty { "No string type errors detected" })
                            events += output
                        }
                    }
                    events.sortedBy { it.optLong("occurred", Long.MAX_VALUE) }.forEach { lines += "EVENT $it" }
                    lines += "MATCHED_EVENTS=${events.size}; UNDECRYPTABLE_ROWS=$failed; UNATTRIBUTABLE_ROWS=$unattributable"
                    lines += "Absent rows do not prove an operation did not occur; undecryptable/unattributable rows cannot be assigned to this attempt."
                }
                ok = true
            } finally {
                check(before == fingerprints()) { "Fingerprint mismatch" }
                lines += "JOURNAL_AND_SEAL_UNCHANGED=true"
            }
        } catch (_: Exception) {
            lines += "INSPECTION_INCOMPLETE: open, schema, decryption, consistency or fingerprint check failed; no recovery attempted."
            ok = false
        }
        for (line in lines) sendStatus(0, Bundle().apply { putString("stream", "$line\n") })
        finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", "READ_ONLY_EVENT_INSPECTION=${if (ok) "PASS" else "INCOMPLETE"}\n") })
    }
}

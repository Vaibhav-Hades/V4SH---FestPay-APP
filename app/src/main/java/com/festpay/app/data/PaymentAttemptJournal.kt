package com.festpay.app.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.festpay.app.ui.viewmodel.PaymentRequest
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.TransactionRecord
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** What the owner declares after checking their own bank. Never an authoritative payment outcome. */
enum class OwnerResolution { DEBITED, NOT_DEBITED }

enum class JournalBoundary { BEFORE_MUTATION, AFTER_MUTATION, AFTER_SEAL_BEFORE_COMMIT, AFTER_COMMIT_BEFORE_FINAL_SEAL, AFTER_FINAL_SEAL }

/** Same transactional SQLite journal, with authenticated encrypted record payloads. */
class PaymentAttemptJournal(
    context: Context,
    databaseName: String = "v4sh_attempts.db",
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val faultInjector: (JournalBoundary) -> Unit = {}
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, 2, DatabaseErrorHandler { throw ProtectedStorageException() }) {
    private val namespace = "journal:$databaseName"
    private val cipher = KeystoreRecordCipher(namespace)
    private val seal = StorageSeal(context.applicationContext, cipher)
    private val lock = StorageNames.lock(context.getDatabasePath(databaseName).absolutePath)
    private var finishInitialization = false

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
    }

    private fun tables(db: SQLiteDatabase) {
        for (table in listOf("attempts", "events", "reservation")) {
            db.execSQL("CREATE TABLE $table(row_key TEXT PRIMARY KEY, payload BLOB NOT NULL)")
        }
        db.execSQL("CREATE TABLE store_meta(slot INTEGER PRIMARY KEY CHECK(slot=1), payload BLOB NOT NULL)")
    }

    override fun onCreate(db: SQLiteDatabase) {
        if (seal.exists() || cipher.exists()) throw ProtectedStorageException()
        cipher.initialize()
        seal.write(StorageSeal.DIRTY)
        tables(db)
        setHeader(db, header(db, 0))
        finishInitialization = true
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != 1 || newVersion != 2 || seal.exists() || cipher.exists()) throw ProtectedStorageException()
        val attempts = db.rawQuery("SELECT id,created,name,recipient,amount,currency,state FROM attempts", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val state = PaymentIntegrityPolicy.restoredState(PaymentState.valueOf(cursor.getString(6)))
                    val amount = PaymentInputValidator.amount(cursor.getString(4)) ?: throw ProtectedStorageException()
                    add(attempt(cursor.getString(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3), amount, cursor.getString(5), state))
                }
            }
        }
        val reservation = db.rawQuery("SELECT attempt FROM reservation", null).use {
            if (!it.moveToFirst()) null else it.getString(0).also { _ -> if (it.moveToNext()) throw ProtectedStorageException() }
        }
        val events = db.rawQuery("SELECT event_id,attempt,occurred,from_state,to_state,reason FROM events", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(JSONObject().apply {
                    put("event_id", cursor.getString(0)); put("attempt", cursor.getString(1)); put("occurred", cursor.getLong(2))
                    put("from_state", cursor.getString(3) ?: JSONObject.NULL); put("to_state", cursor.getString(4)); put("reason", cursor.getString(5))
                    put("source", "LOCAL_COORDINATOR"); put("policy", 1)
                })
            }
        }
        attempts.forEach { validate(it) }
        val unresolved = attempts.filter { PaymentState.valueOf(it.getString("state")) != PaymentState.CANCELLED }
        if (unresolved.size > 1 || (reservation != null && unresolved.none { it.getString("id") == reservation })) throw ProtectedStorageException()
        cipher.initialize()
        seal.write(StorageSeal.DIRTY)
        for (table in listOf("attempts", "events", "reservation")) db.execSQL("DROP TABLE $table")
        tables(db)
        attempts.forEach { put(db, "attempts", StorageNames.digest(it.getString("id")), it) }
        events.forEach {
            validId(it.getString("event_id")); validId(it.getString("attempt")); validTime(it.getLong("occurred"))
            if (attempts.none { record -> record.getString("id") == it.getString("attempt") }) throw ProtectedStorageException()
            put(db, "events", StorageNames.digest(it.getString("event_id")), it)
        }
        (reservation ?: unresolved.firstOrNull()?.getString("id"))?.let { put(db, "reservation", "active", JSONObject().put("attempt", it)) }
        setHeader(db, header(db, 1))
        finishInitialization = true
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (finishInitialization) {
            // Upgrade transaction has committed. Remove legacy plaintext free pages.
            db.execSQL("VACUUM")
            seal.write(readHeader(db))
            finishInitialization = false
        }
    }

    private fun checked(): SQLiteDatabase {
        val db = writableDatabase
        val stored = readHeader(db)
        if (!seal.exists() || seal.read() != stored) throw ProtectedStorageException()
        val json = JSONObject(stored)
        if (json.getInt("schema") != 2 || json.getLong("revision") < 0 || json.getString("root") != root(db)) throw ProtectedStorageException()
        return db
    }

    private fun root(db: SQLiteDatabase): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (table in listOf("attempts", "events", "reservation")) {
            digest.update(table.toByteArray())
            db.rawQuery("SELECT row_key,payload FROM $table ORDER BY row_key", null).use {
                while (it.moveToNext()) {
                    val key = it.getString(0).toByteArray(Charsets.UTF_8)
                    val payload = it.getBlob(1)
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(key.size).array()); digest.update(key)
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(payload.size).array()); digest.update(payload)
                }
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    private fun header(db: SQLiteDatabase, revision: Long) = JSONObject().put("schema", 2).put("revision", revision).put("root", root(db)).toString()
    private fun setHeader(db: SQLiteDatabase, value: String) {
        db.insertWithOnConflict("store_meta", null, ContentValues().apply { put("slot", 1); put("payload", cipher.seal(value.toByteArray(), "header")) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun readHeader(db: SQLiteDatabase): String = db.rawQuery("SELECT payload FROM store_meta WHERE slot=1", null).use {
        if (!it.moveToFirst()) throw ProtectedStorageException()
        cipher.open(it.getBlob(0), "header").toString(Charsets.UTF_8)
    }

    private fun put(db: SQLiteDatabase, table: String, key: String, value: JSONObject, replace: Boolean = false) {
        val values = ContentValues().apply { put("row_key", key); put("payload", cipher.seal(value.toString().toByteArray(Charsets.UTF_8), "$table:$key")) }
        if (replace) {
            if (db.update(table, values, "row_key=?", arrayOf(key)) != 1) throw ProtectedStorageException()
        } else db.insertOrThrow(table, null, values)
    }
    private fun get(db: SQLiteDatabase, table: String, key: String): JSONObject? = db.rawQuery("SELECT payload FROM $table WHERE row_key=?", arrayOf(key)).use {
        if (!it.moveToFirst()) null else JSONObject(cipher.open(it.getBlob(0), "$table:$key").toString(Charsets.UTF_8))
    }
    private fun all(db: SQLiteDatabase): List<JSONObject> = db.rawQuery("SELECT row_key,payload FROM attempts", null).use {
        buildList {
            while (it.moveToNext()) {
                val value = JSONObject(cipher.open(it.getBlob(1), "attempts:${it.getString(0)}").toString(Charsets.UTF_8))
                validate(value)
                if (StorageNames.digest(value.getString("id")) != it.getString(0)) throw ProtectedStorageException()
                add(value)
            }
        }
    }

    private fun <T> change(block: (SQLiteDatabase) -> T): T = synchronized(lock) {
        val db = checked()
        faultInjector(JournalBoundary.BEFORE_MUTATION)
        val oldHeader = JSONObject(readHeader(db))
        var committed = false
        db.beginTransaction()
        try {
            val result = block(db)
            faultInjector(JournalBoundary.AFTER_MUTATION)
            val next = header(db, oldHeader.getLong("revision") + 1)
            if (JSONObject(next).getString("root") != oldHeader.getString("root")) {
                seal.write(StorageSeal.DIRTY)
                setHeader(db, next)
                faultInjector(JournalBoundary.AFTER_SEAL_BEFORE_COMMIT)
            }
            db.setTransactionSuccessful()
            db.endTransaction()
            committed = true
            faultInjector(JournalBoundary.AFTER_COMMIT_BEFORE_FINAL_SEAL)
            seal.write(readHeader(db))
            faultInjector(JournalBoundary.AFTER_FINAL_SEAL)
            result
        } finally { if (!committed && db.inTransaction()) db.endTransaction() }
    }

    fun create(request: PaymentRequest): String = change { db ->
        require(PaymentInputValidator.isVpa(request.payeeVpa) && PaymentInputValidator.amount(request.amount) == request.amount && request.currency == "INR" && !request.mobile) { "Invalid payment request" }
        val existing = all(db)
        check(get(db, "reservation", "active") == null && existing.none { isOpen(it) }) { "Unresolved payment" }
        val id = request.attemptId.ifEmpty { idFactory() }; validId(id)
        val value = attempt(id, System.currentTimeMillis(), request.payeeName, request.payeeVpa, request.amount, request.currency, PaymentState.INITIATED)
        validate(value)
        put(db, "attempts", StorageNames.digest(id), value)
        put(db, "reservation", "active", JSONObject().put("attempt", id))
        event(db, id, null, PaymentState.INITIATED, "USER_AUTHORIZED", 0)
        id
    }

    fun transition(id: String, to: PaymentState, reason: String, expectedVersion: Long? = null) = change { db ->
        require(Regex("[A-Z_]{1,80}").matches(reason)) { "Invalid event reason" }
        validId(id)
        val key = StorageNames.digest(id)
        val value = get(db, "attempts", key) ?: throw ProtectedStorageException()
        validate(value)
        val from = PaymentState.valueOf(value.getString("state"))
        check(expectedVersion == null || expectedVersion == value.getLong("version")) { "Stale transition" }
        if (from != to) {
            check(PaymentIntegrityPolicy.allows(from, to)) { "Transition blocked" }
            val version = value.getLong("version") + 1
            value.put("state", to.name).put("version", version)
            put(db, "attempts", key, value, true)
            event(db, id, from, to, reason, version)
            if (to == PaymentState.CANCELLED) db.delete("reservation", "row_key=?", arrayOf("active"))
        }
    }

    fun recover() {
        val interrupted = synchronized(lock) { all(checked()).filter { it.getString("state") in listOf("INITIATED", "PROCESSING") }.map { it.getString("id") } }
        interrupted.forEach { transition(it, PaymentState.UNKNOWN, "PROCESS_RESTART") }
    }

    /** Append non-authoritative operational codes only; never change financial state/reservation. */
    fun recordCarrierObservation(id: String, sessionId: String, progress: CarrierProgress): Boolean = change { db ->
        validId(id); validId(sessionId); require(progress.revision >= 0)
        check(get(db, "reservation", "active")?.getString("attempt") == id) { "Not the reserved attempt" }
        val value = get(db, "attempts", StorageNames.digest(id)) ?: throw ProtectedStorageException()
        validate(value)
        val state = PaymentState.valueOf(value.getString("state"))
        check(state in setOf(PaymentState.PROCESSING, PaymentState.UNKNOWN))
        val eventId = UUID.nameUUIDFromBytes("$id:$sessionId:${progress.revision}".toByteArray(Charsets.UTF_8)).toString()
        val reason = "CARRIER_${progress.stage.name}"
        val existing = get(db, "events", StorageNames.digest(eventId))
        if (existing != null) {
            check(existing.getString("attempt") == id && existing.getString("reason") == reason && existing.getString("carrier_issue") == progress.issue.name)
            false
        } else {
            event(db, id, state, state, reason, value.getLong("version"), eventId, progress)
            true
        }
    }

    /** Append operational evidence only. No financial transition, version change or reservation release. */
    fun recordLaunchMilestone(command: OwnedUssdCommand, milestone: LaunchMilestone): Boolean = change { db ->
        val id = checkNotNull(command.attemptId)
        validId(id); validId(command.sessionId); check(!command.balance)
        check(get(db, "reservation", "active")?.getString("attempt") == id)
        val value = checkNotNull(get(db, "attempts", StorageNames.digest(id)))
        validate(value)
        check(value.getString("recipient") == command.recipient && value.getString("amount") == command.amount)
        val state = PaymentState.valueOf(value.getString("state"))
        check(state in setOf(PaymentState.PROCESSING, PaymentState.UNKNOWN))
        val eventId = UUID.nameUUIDFromBytes("launch:$id:${command.sessionId}:${milestone.name}".toByteArray(Charsets.UTF_8)).toString()
        if (get(db, "events", StorageNames.digest(eventId)) != null) false else {
            event(db, id, state, state, milestone.name, value.getLong("version"), eventId)
            true
        }
    }

    fun carrierObservationCount(id: String): Int = synchronized(lock) {
        validId(id)
        checked().rawQuery("SELECT row_key,payload FROM events", null).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                val entry = JSONObject(cipher.open(cursor.getBlob(1), "events:${cursor.getString(0)}").toString(Charsets.UTF_8))
                if (entry.getString("attempt") == id && entry.optString("source") == "CARRIER_OBSERVATION") count++
            }
            count
        }
    }

    fun unresolvedId(): String? = synchronized(lock) {
        val db = checked()
        val attempts = all(db)
        val id = get(db, "reservation", "active")?.getString("attempt")
        val unresolved = attempts.filter { isOpen(it) }
        if (unresolved.size > 1 || (id == null && unresolved.isNotEmpty()) || (id != null && unresolved.none { it.getString("id") == id })) throw ProtectedStorageException()
        id
    }

    /**
     * The owner states, after checking their own bank, what happened to an UNKNOWN attempt. This closes the
     * attempt for locking purposes only: its state stays UNKNOWN (never SUCCESS/FAILED), no reference is invented,
     * the declaration is stored beside it, and a dedicated event is appended. Atomic with the reservation release.
     * Idempotent for the same answer; a different second answer is refused.
     */
    fun resolveByOwner(id: String, resolution: OwnerResolution): Boolean = change { db ->
        validId(id)
        val key = StorageNames.digest(id)
        val value = get(db, "attempts", key) ?: throw ProtectedStorageException()
        validate(value)
        val already = value.optString("owner_resolution", "")
        if (already.isNotEmpty()) {
            check(already == resolution.name) { "Already resolved differently" }
            return@change false
        }
        check(PaymentState.valueOf(value.getString("state")) == PaymentState.UNKNOWN) { "Only an UNKNOWN attempt can be resolved by its owner" }
        check(get(db, "reservation", "active")?.getString("attempt") == id) { "Not the reserved attempt" }
        val version = value.getLong("version") + 1
        value.put("owner_resolution", resolution.name).put("owner_resolved_at", System.currentTimeMillis()).put("version", version)
        put(db, "attempts", key, value, true)
        event(db, id, PaymentState.UNKNOWN, PaymentState.UNKNOWN, "OWNER_DECLARED_${resolution.name}", version, source = "OWNER_DECLARATION")
        db.delete("reservation", "row_key=?", arrayOf("active"))
        true
    }

    /**
     * Closes an UNKNOWN attempt from the carrier's own final message (owner policy). The financial state stays UNKNOWN in the
     * state machine; the carrier's verdict and printed reference are stored beside it, with provenance, and the reservation
     * is released atomically. Idempotent for the same verdict; a conflicting second verdict is refused.
     */
    fun resolveByCarrier(id: String, outcome: CarrierOutcome, reference: String?): Boolean = change { db ->
        validId(id)
        require(reference == null || Regex("[0-9]{6,20}").matches(reference))
        require(outcome != CarrierOutcome.SUCCESS || reference != null) { "A success needs the carrier's reference" }
        val key = StorageNames.digest(id)
        val value = get(db, "attempts", key) ?: throw ProtectedStorageException()
        validate(value)
        val already = value.optString("carrier_resolution", "")
        if (already.isNotEmpty()) {
            check(already == outcome.name) { "Already closed with a different carrier verdict" }
            return@change false
        }
        check(value.optString("owner_resolution", "").isEmpty()) { "Already resolved by its owner" }
        check(PaymentState.valueOf(value.getString("state")) == PaymentState.UNKNOWN) { "Only an UNKNOWN attempt can be closed" }
        check(get(db, "reservation", "active")?.getString("attempt") == id) { "Not the reserved attempt" }
        val version = value.getLong("version") + 1
        value.put("carrier_resolution", outcome.name).put("carrier_resolved_at", System.currentTimeMillis()).put("version", version)
        if (reference != null) value.put("carrier_resolution_ref", reference)
        put(db, "attempts", key, value, true)
        event(db, id, PaymentState.UNKNOWN, PaymentState.UNKNOWN, "CARRIER_MESSAGE_${outcome.name}", version, source = "CARRIER_RESOLUTION")
        db.delete("reservation", "row_key=?", arrayOf("active"))
        true
    }

    /** An attempt still holds the reservation unless it was cancelled, closed by its owner, or closed by the carrier's verdict. */
    private fun isOpen(value: JSONObject): Boolean =
        value.getString("state") != PaymentState.CANCELLED.name && value.optString("owner_resolution", "").isEmpty() &&
            value.optString("carrier_resolution", "").isEmpty()

    /** Latest carrier-reported wording per attempt (observation only), from the encrypted event trail. */
    /** One pass over the encrypted event trail: latest carrier wording and latest carrier-printed reference per attempt. */
    private class Observed(val wordings: Map<String, String>, val references: Map<String, String>, val names: Map<String, String>, val interrupted: Set<String>)

    private fun carrierObservations(db: SQLiteDatabase): Observed {
        val wordings = HashMap<String, Pair<Long, String>>()
        val references = HashMap<String, Pair<Long, String>>()
        val names = HashMap<String, Pair<Long, String>>()
        val lastReason = HashMap<String, Pair<Long, String>>()
        db.rawQuery("SELECT row_key,payload FROM events", null).use { cursor ->
            while (cursor.moveToNext()) {
                val entry = JSONObject(cipher.open(cursor.getBlob(1), "events:${cursor.getString(0)}").toString(Charsets.UTF_8))
                if (entry.optString("source") != "CARRIER_OBSERVATION") continue
                val at = entry.optLong("occurred", 0); val id = entry.getString("attempt")
                val wording = entry.optString("result_wording", "")
                if ((wording == "SUCCESS_WORDING" || wording == "FAILURE_WORDING") && (wordings[id]?.first ?: -1L) <= at) wordings[id] = at to wording
                val ref = entry.optString("carrier_reference", "")
                if (Regex("[0-9]{6,20}").matches(ref) && (references[id]?.first ?: -1L) <= at) references[id] = at to ref
                val nm = entry.optString("carrier_payee_name", "")
                if (nm.isNotEmpty() && (names[id]?.first ?: -1L) <= at) names[id] = at to nm
                if ((lastReason[id]?.first ?: -1L) <= at) lastReason[id] = at to entry.optString("reason", "")
            }
        }
        return Observed(wordings.mapValues { it.value.second }, references.mapValues { it.value.second }, names.mapValues { it.value.second },
            lastReason.filter { it.value.second == "CARRIER_INTERRUPTED" }.keys)
    }

    fun records(): List<TransactionRecord> = synchronized(lock) {
        val db = checked()
        val observed = carrierObservations(db); val results = observed.wordings; val references = observed.references
        all(db).sortedByDescending { it.getLong("created") }.map {
            TransactionRecord(it.getString("id"), Instant.ofEpochMilli(it.getLong("created")).toString(), it.getString("name").ifEmpty { observed.names[it.getString("id")] ?: "" },
                it.getString("recipient"), it.getString("amount"), it.getString("currency"), PaymentIntegrityPolicy.restoredState(PaymentState.valueOf(it.getString("state"))),
                it.getString("operation"), it.getString("evidence"), (it.optString("carrier_resolution_ref", "").ifEmpty { null } ?: references[it.getString("id")]), (it.optString("carrier_resolution_ref", "").ifEmpty { null } ?: references[it.getString("id")])?.let { "CARRIER_MESSAGE" },
                it.optString("owner_resolution", "").ifEmpty { null }, results[it.getString("id")], it.optString("carrier_resolution", "").ifEmpty { null },
                it.getString("id") in observed.interrupted && it.optString("carrier_resolution", "").isEmpty() && it.optString("owner_resolution", "").isEmpty())
        }
    }

    private fun attempt(id: String, created: Long, name: String, recipient: String, amount: String, currency: String, state: PaymentState) = JSONObject().apply {
        put("id", id); put("created", created); put("name", name); put("recipient", recipient); put("amount", amount); put("currency", currency)
        put("state", state.name); put("operation", "PAYMENT"); put("evidence", "NO_AUTHORIZED_CONFIRMATION"); put("version", 0)
        put("provider_reference", JSONObject.NULL); put("reference_provenance", JSONObject.NULL)
    }
    private fun validate(value: JSONObject) {
        validId(value.getString("id")); validTime(value.getLong("created"))
        if (!PaymentInputValidator.isVpa(value.getString("recipient")) || PaymentInputValidator.amount(value.getString("amount")) != value.getString("amount") || value.getString("currency") != "INR") throw ProtectedStorageException()
        if (value.getString("name").length > 100 || value.getString("name").any { it.isISOControl() } || value.getString("state") !in listOf("INITIATED", "PROCESSING", "UNKNOWN", "CANCELLED")) throw ProtectedStorageException()
        val resolution = value.optString("owner_resolution", "")
        if (resolution.isNotEmpty() && (resolution !in OwnerResolution.values().map { it.name } || value.getString("state") != PaymentState.UNKNOWN.name || !value.has("owner_resolved_at"))) throw ProtectedStorageException()
        if (resolution.isNotEmpty()) validTime(value.getLong("owner_resolved_at"))
        val carrier = value.optString("carrier_resolution", "")
        if (carrier.isNotEmpty() && (carrier !in CarrierOutcome.values().map { it.name } || value.getString("state") != PaymentState.UNKNOWN.name ||
                !value.has("carrier_resolved_at") || (value.has("carrier_resolution_ref") && !Regex("[0-9]{6,20}").matches(value.getString("carrier_resolution_ref"))) ||
                (carrier == "SUCCESS" && !value.has("carrier_resolution_ref")))) throw ProtectedStorageException()
        if (carrier.isNotEmpty()) validTime(value.getLong("carrier_resolved_at"))
        if (value.getString("operation") != "PAYMENT" || value.getString("evidence") != "NO_AUTHORIZED_CONFIRMATION" || value.getLong("version") < 0 || !value.isNull("provider_reference") || !value.isNull("reference_provenance")) throw ProtectedStorageException()
    }
    private fun validId(id: String) { if (UUID.fromString(id).toString() != id) throw ProtectedStorageException() }
    private fun validTime(time: Long) { if (time !in 1..253402300799999L) throw ProtectedStorageException() }
    private fun event(db: SQLiteDatabase, id: String, from: PaymentState?, to: PaymentState, reason: String, version: Long,
                      eventId: String = UUID.randomUUID().toString(), observation: CarrierProgress? = null, source: String? = null) {
        put(db, "events", StorageNames.digest(eventId), JSONObject().apply {
            put("event_id", eventId); put("attempt", id); put("occurred", System.currentTimeMillis()); put("from_state", from?.name ?: JSONObject.NULL)
            put("to_state", to.name); put("reason", reason); put("source", source ?: if (observation == null) "LOCAL_COORDINATOR" else "CARRIER_OBSERVATION"); put("policy", 1); put("attempt_version", version)
            if (observation != null) {
                put("carrier_issue", observation.issue.name); put("amount_observed_matching", observation.amountAccepted)
                put("result_wording", observation.resultWording?.name ?: JSONObject.NULL)
                if (observation.detailsMismatch) put("details_mismatch", true)
                observation.payeeName?.let { n -> if (n.length in 2..60 && n.none { it.isISOControl() }) put("carrier_payee_name", n) }
                observation.reference?.let { ref -> if (Regex("[0-9]{6,20}").matches(ref)) put("carrier_reference", ref) }
            }
        })
    }
}

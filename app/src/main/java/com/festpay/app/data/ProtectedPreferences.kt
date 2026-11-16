package com.festpay.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Encrypts profile/legacy-history values without encrypting unrelated UI preferences. */
class ProtectedPreferences(context: Context, name: String) {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val namespace = "preferences:$name"
    private val cipher = KeystoreRecordCipher(namespace)
    private val seal = StorageSeal(context.applicationContext, cipher)
    private val lock = StorageNames.lock(namespace)

    private fun read(): JSONObject {
        val encoded = prefs.getString("protected_v1", null)
        if (encoded == null) {
            if (seal.exists() || cipher.exists()) throw ProtectedStorageException()
            val legacy = JSONObject().apply {
                put("user_vpa", prefs.getString("user_vpa", "").orEmpty())
                put("user_name", prefs.getString("user_name", "").orEmpty())
                put("transactions_list", JSONArray(prefs.getString("transactions_list", "[]").orEmpty()))
            }
            validate(legacy)
            cipher.initialize()
            persist(legacy)
            return legacy
        }
        if (!seal.exists()) throw ProtectedStorageException()
        val bytes = try { Base64.decode(encoded, Base64.NO_WRAP) } catch (_: Exception) { throw ProtectedStorageException() }
        if (seal.read() != StorageNames.digest(bytes)) throw ProtectedStorageException()
        return JSONObject(cipher.open(bytes, "profile").toString(Charsets.UTF_8)).also { validate(it) }
    }

    private fun validate(value: JSONObject) {
        val vpa = value.getString("user_vpa")
        val name = value.getString("user_name")
        if ((vpa.isNotEmpty() && !PaymentInputValidator.isVpa(vpa)) || name.length > 100 || name.any { it.isISOControl() }) throw ProtectedStorageException()
        val records = value.getJSONArray("transactions_list")
        if (records.length() > 10000) throw ProtectedStorageException()
        val ids = mutableSetOf<String>()
        for (index in 0 until records.length()) {
            val record = records.getJSONObject(index)
            if (record.optString("id").isBlank() || !PaymentInputValidator.isVpa(record.optString("payeeVpa")) || PaymentInputValidator.amount(record.optString("amount")) == null) throw ProtectedStorageException()
            if (!ids.add(record.getString("id")) || record.optString("currency", "INR") != "INR") throw ProtectedStorageException()
            // Legacy relative/invalid timestamps have no recoverable instant.
            val timestamp = record.optString("timestamp")
            if (timestamp.isNotEmpty()) {
                try { java.time.Instant.parse(timestamp) } catch (_: Exception) { record.put("timestamp", JSONObject.NULL) }
            }
            record.put("state", "UNKNOWN")
        }
    }

    private fun persist(value: JSONObject) {
        validate(value)
        val bytes = cipher.seal(value.toString().toByteArray(Charsets.UTF_8), "profile")
        seal.write(StorageSeal.DIRTY)
        if (!prefs.edit().putString("protected_v1", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .remove("user_vpa").remove("user_name").remove("transactions_list").commit()) throw ProtectedStorageException()
        seal.write(StorageNames.digest(bytes))
    }

    fun snapshot(): JSONObject = synchronized(lock) { read() }
    fun update(block: (JSONObject) -> Unit): Boolean = synchronized(lock) {
        try { val value = read(); block(value); persist(value); true }
        catch (_: Exception) { false }
    }
}

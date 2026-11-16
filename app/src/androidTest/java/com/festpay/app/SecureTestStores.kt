package com.festpay.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.festpay.app.data.KeystoreRecordCipher
import com.festpay.app.data.StorageSeal
import java.io.File
import java.security.KeyStore
import java.util.UUID

object SecureTestStores {
    fun legacyJournal(context: Context, name: String, state: String = "PROCESSING", created: Long = System.currentTimeMillis()): String {
        require(name.startsWith("test_"))
        val id = UUID.randomUUID().toString()
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY, created INTEGER NOT NULL, name TEXT NOT NULL, recipient TEXT NOT NULL, amount TEXT NOT NULL, currency TEXT NOT NULL, state TEXT NOT NULL)")
            db.execSQL("CREATE TABLE reservation(slot INTEGER PRIMARY KEY CHECK(slot=1), attempt TEXT NOT NULL UNIQUE)")
            db.execSQL("CREATE TABLE events(event_id TEXT PRIMARY KEY, attempt TEXT NOT NULL, occurred INTEGER NOT NULL, from_state TEXT, to_state TEXT NOT NULL, reason TEXT NOT NULL)")
            db.execSQL("INSERT INTO attempts VALUES(?,?,?,?,?,?,?)", arrayOf(id, created, "SYNTHETIC_PRIVATE_NAME", "synthetic.private@upi", "1.00", "INR", state))
            if (state != "CANCELLED") db.execSQL("INSERT INTO reservation VALUES(1,?)", arrayOf(id))
            db.execSQL("INSERT INTO events VALUES(?,?,?,?,?,?)", arrayOf(UUID.randomUUID().toString(), id, created, null, "INITIATED", "USER_AUTHORIZED"))
            db.version = 1
        }
        return id
    }
    fun cleanupJournal(context: Context, name: String) {
        require(name.startsWith("test_"))
        context.deleteDatabase(name)
        cleanup(context, "journal:$name")
    }
    fun cleanupPreferences(context: Context, name: String) {
        require(name.startsWith("test_"))
        context.deleteSharedPreferences(name)
        cleanup(context, "preferences:$name")
    }
    private fun cleanup(context: Context, namespace: String) {
        val file = StorageSeal.path(context, namespace)
        for (suffix in listOf("", ".bak", ".new")) File(file.path + suffix).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KeystoreRecordCipher(namespace).alias) }
    }
}

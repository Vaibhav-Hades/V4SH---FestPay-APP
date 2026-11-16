package com.festpay.app

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.*
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class EncryptedStorageTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val request = PaymentRequest("synthetic.private@upi", "SYNTHETIC_PRIVATE_NAME", "1.00")
    private fun name() = "test_secure_${UUID.randomUUID()}.db"
    private fun rejected(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: Exception) { rejected = true }
        assertTrue("Unsafe storage must block the operation", rejected)
    }

    @Test fun migrationPreservesIdentityReservationAndRemovesPlaintextValues() {
        val name = name()
        try {
            val id = SecureTestStores.legacyJournal(context, name)
            PaymentAttemptJournal(context, name).use {
                it.recover()
                assertEquals(id, it.unresolvedId())
                assertEquals(id, it.records().single().localTransactionId)
                assertEquals(PaymentState.UNKNOWN, it.records().single().state)
                assertNull(it.records().single().bankReference)
                assertEquals("NO_AUTHORIZED_CONFIRMATION", it.records().single().evidenceStatus)
            }
            val path = context.getDatabasePath(name).path
            for (suffix in listOf("", "-journal", "-wal", "-shm")) {
                val file = File(path + suffix)
                if (file.exists()) {
                    val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
                    for (sensitive in listOf(id, "SYNTHETIC_PRIVATE_NAME", "synthetic.private@upi")) assertFalse("Plaintext remains in journal", bytes.contains(sensitive))
                }
            }
            val cipher = KeystoreRecordCipher("journal:$name")
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(cipher.alias, null)
            assertNull("Keystore key must not be exportable", key.encoded)
        } finally { SecureTestStores.cleanupJournal(context, name) }
    }

    @Test fun payloadTamperingReservationDeletionAndStaleSnapshotBlockExecution() {
        for (fault in listOf("ciphertext", "reservation", "rollback")) {
            val name = name()
            try {
                val id = PaymentAttemptJournal(context, name).use { it.create(request) }
                val old = context.getDatabasePath(name).readBytes()
                PaymentAttemptJournal(context, name).use { store ->
                    store.transition(id, PaymentState.PROCESSING, "TEST_ARM")
                    when (fault) {
                        "ciphertext" -> store.writableDatabase.execSQL("UPDATE attempts SET payload=?", arrayOf(byteArrayOf(1, 2, 3)))
                        "reservation" -> store.writableDatabase.execSQL("DELETE FROM reservation")
                    }
                }
                if (fault == "rollback") context.getDatabasePath(name).writeBytes(old)
                PaymentAttemptJournal(context, name).use { store ->
                    rejected { store.records() }
                    rejected { store.create(request) }
                }
            } finally { SecureTestStores.cleanupJournal(context, name) }
        }
    }

    @Test fun missingKeyDatabaseOrSealNeverInitializesAnEmptyReplacement() {
        for (fault in listOf("key", "database", "seal", "interrupted")) {
            val name = name()
            try {
                PaymentAttemptJournal(context, name).use { it.create(request) }
                val cipher = KeystoreRecordCipher("journal:$name")
                when (fault) {
                    "key" -> KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(cipher.alias) }
                    "database" -> context.deleteDatabase(name)
                    "seal" -> StorageSeal.path(context, cipher.namespace).delete()
                    "interrupted" -> StorageSeal(context, cipher).write(StorageSeal.DIRTY)
                }
                PaymentAttemptJournal(context, name).use { store ->
                    rejected { store.records() }
                    rejected { store.create(request) }
                }
                if (fault == "key") assertFalse("Lost key must not be regenerated", cipher.exists())
            } finally { SecureTestStores.cleanupJournal(context, name) }
        }
    }

    @Test fun profileMigrationEncryptsValuesAndKeyLossIsUnavailable() {
        val name = "test_profile_secure_${UUID.randomUUID()}"
        try {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertTrue(prefs.edit().putString("user_vpa", "synthetic.private@upi").putString("user_name", "SYNTHETIC_PRIVATE_NAME").putString("transactions_list", "[]").commit())
            val repository = TransactionRepository(context, name)
            assertEquals("synthetic.private@upi", repository.loadUserVpa())
            assertEquals("SYNTHETIC_PRIVATE_NAME", repository.loadUserName())
            assertFalse(prefs.contains("user_vpa")); assertFalse(prefs.contains("user_name")); assertFalse(prefs.contains("transactions_list"))
            val xml = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertFalse(xml.contains("synthetic.private@upi")); assertFalse(xml.contains("SYNTHETIC_PRIVATE_NAME"))
            val cipher = KeystoreRecordCipher("preferences:$name")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(cipher.alias) }
            rejected { repository.loadUserVpa() }
            assertFalse(repository.saveUserProfile("other@upi", "Other"))
            assertFalse(cipher.exists())
        } finally { SecureTestStores.cleanupPreferences(context, name) }
    }

    @Test fun malformedLegacyDataAndInvalidTimestampFailWithoutReset() {
        val prefsName = "test_bad_json_${UUID.randomUUID()}"
        val databaseName = name()
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            assertTrue(prefs.edit().putString("transactions_list", "{broken").commit())
            rejected { TransactionRepository(context, prefsName).loadTransactions() }
            assertEquals("{broken", prefs.getString("transactions_list", null))
            assertFalse(prefs.contains("protected_v1"))
            SecureTestStores.legacyJournal(context, databaseName, created = -1)
            PaymentAttemptJournal(context, databaseName).use { rejected { it.records() } }
        } finally {
            SecureTestStores.cleanupPreferences(context, prefsName)
            SecureTestStores.cleanupJournal(context, databaseName)
        }
    }

    @Test fun keyInvalidationIsRejectedAndConcurrentStartsRemainExclusive() {
        val plainKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val bytes = AesGcmEnvelope { plainKey }.seal("synthetic".toByteArray(), "row")
        rejected { AesGcmEnvelope { throw KeyPermanentlyInvalidatedException() }.open(bytes, "row") }
        val name = name()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val futures = (1..2).map {
                executor.submit<Boolean> {
                    start.await(10, TimeUnit.SECONDS)
                    PaymentAttemptJournal(context, name).use { store -> try { store.create(request); true } catch (_: IllegalStateException) { false } }
                }
            }
            start.countDown()
            assertEquals(1, futures.count { it.get(30, TimeUnit.SECONDS) })
            PaymentAttemptJournal(context, name).use { assertEquals(1, it.records().size); assertNotNull(it.unresolvedId()) }
        } finally { executor.shutdownNow(); SecureTestStores.cleanupJournal(context, name) }
    }
    @Test fun failedProfileReadCannotBeOverwrittenByReadyJournal() {
        val databaseName = name()
        val preferencesName = "test_latched_${UUID.randomUUID()}"
        val journals = mutableListOf<PaymentAttemptJournal>()
        try {
            val model = PaymentViewModel(
                journalFactory = { PaymentAttemptJournal(it, databaseName).also { store -> journals.add(store) } },
                repositoryFactory = { TransactionRepository(it, preferencesName) }
            )
            model.inspectPaymentReadiness(context)
            assertFalse(model.paymentPreparationBlocked)
            val cipher = KeystoreRecordCipher("preferences:$preferencesName")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(cipher.alias) }
            model.inspectPaymentReadiness(context)
            assertTrue(model.paymentPreparationBlocked)
            assertFalse(model.profileAvailable)
            assertNotNull(model.operationError)
            assertTrue(model.transactionLedger.isEmpty())
        } finally {
            journals.forEach { it.close() }
            SecureTestStores.cleanupJournal(context, databaseName)
            SecureTestStores.cleanupPreferences(context, preferencesName)
        }
    }

    @Test fun unsupportedSchemaAndMalformedLegacyRecipientCannotResetAReservation() {
        for (fault in listOf("schema", "recipient")) {
            val databaseName = name()
            try {
                SecureTestStores.legacyJournal(context, databaseName)
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { db ->
                    if (fault == "schema") db.version = 3
                    else db.execSQL("UPDATE attempts SET recipient='not-a-vpa'")
                }
                PaymentAttemptJournal(context, databaseName).use { store ->
                    rejected { store.records() }
                    rejected { store.create(request) }
                }
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM reservation", null).use { cursor ->
                        assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0))
                    }
                    db.rawQuery("SELECT COUNT(*) FROM attempts", null).use { cursor ->
                        assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0))
                    }
                }
            } finally { SecureTestStores.cleanupJournal(context, databaseName) }
        }
    }
}

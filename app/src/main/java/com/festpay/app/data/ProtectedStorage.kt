package com.festpay.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ProtectedStorageException : IllegalStateException("Protected storage is unavailable. Payment execution is blocked.")

/** Versioned authenticated envelope. Key bytes never need to enter application state. */
class AesGcmEnvelope(private val key: () -> SecretKey) {
    fun seal(plain: ByteArray, context: String): ByteArray = safely {
        if (plain.size > MAX_BYTES) throw ProtectedStorageException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        if (cipher.iv.size != 12) throw ProtectedStorageException()
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }

    fun open(envelope: ByteArray, context: String): ByteArray = safely {
        if (envelope.size !in 29..(MAX_BYTES + 29) || envelope[0] != 1.toByte()) throw ProtectedStorageException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        cipher.doFinal(envelope, 13, envelope.size - 13)
    }

    private fun <T> safely(block: () -> T): T = try { block() } catch (_: Exception) { throw ProtectedStorageException() }
    companion object { const val MAX_BYTES = 1_048_576 }
}

object StorageNames {
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    fun digest(value: String): String = digest(value.toByteArray(Charsets.UTF_8))
    private val locks = ConcurrentHashMap<String, Any>()
    fun lock(namespace: String): Any = locks.getOrPut(namespace) { Any() }
}

/** Initialization is explicit and only permitted by callers creating a genuinely new store. */
class KeystoreRecordCipher(val namespace: String) {
    val alias: String = "v4sh.storage.v1." + StorageNames.digest(namespace)
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun exists(): Boolean = try { store().containsAlias(alias) } catch (_: Exception) { throw ProtectedStorageException() }
    fun initialize() = synchronized(StorageNames.lock(alias)) {
        keyHandles.remove(alias)
        if (!exists()) {
            try {
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build())
                    generateKey()
                }
            } catch (_: Exception) { throw ProtectedStorageException() }
        }
    }
    /**
     * Loading the Keystore and the key handle costs tens of milliseconds on every operation, so the handle is kept for the
     * process. It is a reference into the Keystore; key bytes never enter application memory. A deleted or invalidated key
     * makes every operation fail exactly as before.
     */
    private fun existingKey(): SecretKey = keyHandles[alias] ?: try {
        (store().getKey(alias, null) as? SecretKey ?: throw ProtectedStorageException()).also { keyHandles[alias] = it }
    } catch (_: Exception) { throw ProtectedStorageException() }
    private val envelope = AesGcmEnvelope { existingKey() }
    fun seal(plain: ByteArray, purpose: String): ByteArray = envelope.seal(plain, "$namespace:$purpose")

    /**
     * An unchanged encrypted record decrypts to the same bytes, so its plaintext is remembered in memory (bounded, never
     * written to disk). Every read FIRST confirms the Keystore key still exists (about 1 ms), so losing the key blocks
     * storage immediately even for remembered records. The first read of each record is fully authenticated, and a
     * changed or tampered record is a different blob that is decrypted and authenticated again.
     */
    fun open(bytes: ByteArray, purpose: String): ByteArray {
        if (!exists()) throw ProtectedStorageException()
        val cacheKey = "$namespace:$purpose:" + StorageNames.digest(bytes)
        plaintextCache[cacheKey]?.let { return it.copyOf() }
        val plain = envelope.open(bytes, "$namespace:$purpose")
        plaintextCache[cacheKey] = plain.copyOf()
        return plain
    }

    private companion object {
        val keyHandles = ConcurrentHashMap<String, SecretKey>()
        val plaintextCache: MutableMap<String, ByteArray> = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 512
            })
    }
}

/** Device-local encrypted integrity anchor. Missing/dirty anchors never authorize a fresh journal. */
class StorageSeal(context: Context, private val cipher: KeystoreRecordCipher) {
    val file: File = path(context, cipher.namespace)
    private val atomic = AtomicFile(file)
    fun exists(): Boolean = file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()
    fun read(): String {
        try {
            val bytes = atomic.openRead().use {
                if (it.channel.size() > AesGcmEnvelope.MAX_BYTES + 29) throw ProtectedStorageException()
                it.readBytes()
            }
            return cipher.open(bytes, "anchor").toString(Charsets.UTF_8)
        } catch (_: Exception) { throw ProtectedStorageException() }
    }
    fun write(value: String) {
        val bytes = cipher.seal(value.toByteArray(Charsets.UTF_8), "anchor")
        if (!file.parentFile!!.isDirectory && !file.parentFile!!.mkdirs()) throw ProtectedStorageException()
        val output = try { atomic.startWrite() } catch (_: Exception) { throw ProtectedStorageException() }
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (_: Exception) { atomic.failWrite(output); throw ProtectedStorageException() }
    }
    companion object {
        fun path(context: Context, namespace: String) = File(context.noBackupFilesDir, "protected_storage/${StorageNames.digest(namespace)}.seal")
        const val DIRTY = "INTERRUPTED_WRITE_DO_NOT_EXECUTE"
    }
}

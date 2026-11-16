package com.festpay.app

import com.festpay.app.data.*
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class ProtectedStorageTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun authenticatedEncryptionUsesFreshNoncesAndRejectsWrongContext() {
        val cipher = AesGcmEnvelope { key }
        val plain = "synthetic sensitive record".toByteArray()
        val first = cipher.seal(plain, "attempt:a")
        val second = cipher.seal(plain, "attempt:a")
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, cipher.open(first, "attempt:a"))
        rejected { cipher.open(first, "attempt:b") }
        val changed = first.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        rejected { cipher.open(changed, "attempt:a") }
    }
    @Test fun truncatedUnknownVersionAndUnavailableKeyNeverBecomePlaintext() {
        val cipher = AesGcmEnvelope { key }
        for (bytes in listOf(byteArrayOf(), ByteArray(20), ByteArray(29).apply { this[0] = 2 })) rejected { cipher.open(bytes, "row") }
        val encrypted = cipher.seal("test".toByteArray(), "row")
        rejected { AesGcmEnvelope { throw IllegalStateException("key unavailable") }.open(encrypted, "row") }
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Protected storage must fail closed") } catch (_: ProtectedStorageException) { }
    }
}

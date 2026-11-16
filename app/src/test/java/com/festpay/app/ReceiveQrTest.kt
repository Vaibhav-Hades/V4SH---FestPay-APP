package com.festpay.app

import com.festpay.app.data.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test

class ReceiveQrTest {
    @Test fun `QR decodes to the exact configured account offline`() {
        val payload = ReceiveQr.payload("test@upi", "Test Person")!!
        val matrix = ReceiveQr.encode(payload)
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))))
        assertEquals(payload, decoded.text)
        assertEquals("test@upi", UpiQrParser.parse(decoded.text)!!.vpa)
        assertNull(ReceiveQr.payload("", "Unknown"))
    }
}

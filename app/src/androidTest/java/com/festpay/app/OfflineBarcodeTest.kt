package com.festpay.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.ReceiveQr
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineBarcodeTest {
    @Test fun bundledScannerDecodesWithoutInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        val payload = ReceiveQr.payload("offline-test@upi", "Offline Test")!!
        val matrix = ReceiveQr.encode(payload)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) 0xff000000.toInt() else 0xffffffff.toInt() }
        bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
        val scanner = BarcodeScanning.getClient()
        try {
            val results = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS)
            assertEquals(payload, results.single().rawValue)
        } finally { scanner.close(); bitmap.recycle() }
    }
}

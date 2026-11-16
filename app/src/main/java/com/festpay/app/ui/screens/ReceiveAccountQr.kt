package com.festpay.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.festpay.app.data.ReceiveQr

@Composable
fun ReceiveAccountQr(vpa: String, name: String, amount: String? = null) {
    val bitmap = remember(vpa, name, amount) {
        ReceiveQr.payload(vpa, name, amount)?.let { payload ->
            val matrix = ReceiveQr.encode(payload)
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                setPixels(pixels, 0, 512, 0, 0, 512, 512)
            }
        }
    }
    if (bitmap == null) Text("Save your own valid UPI ID in Profile → UPI Details to generate a QR.")
    else Image(bitmap.asImageBitmap(), "UPI QR for the configured account", Modifier.size(200.dp))
}

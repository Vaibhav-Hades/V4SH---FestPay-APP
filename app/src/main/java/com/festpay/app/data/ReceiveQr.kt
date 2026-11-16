package com.festpay.app.data

import java.net.URLEncoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object ReceiveQr {
    fun payload(vpa: String, name: String): String? = payload(vpa, name, null)

    /** With an amount, the payer's UPI app or V4SH opens the payment already filled in. An invalid amount is ignored. */
    fun payload(vpa: String, name: String, amount: String?): String? {
        if (!PaymentInputValidator.isVpa(vpa)) return null
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val am = amount?.let { PaymentInputValidator.amount(it) }?.let { "&am=${encode(it)}" }.orEmpty()
        return "upi://pay?pa=${encode(vpa)}&pn=${encode(name.take(100))}$am&cu=INR"
    }
    fun encode(payload: String) = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512,
        mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
}

package com.festpay.app.data

import android.util.Log

/**
 * Stage-level diagnostics for the Review → launch chain. Never logs UPI IDs, names, amounts, PINs or screen text;
 * only stage names, outcomes, exception class names and the first 8 characters of an attempt identifier.
 * Android's Log.i is stripped from release builds by the R8 rules, and every call is failure-safe (JVM tests).
 */
object PaymentDiagnostics {
    private const val TAG = "V4SH_PAY"

    fun shortId(id: String?): String = id?.take(8) ?: "none"

    fun stage(stage: String, outcome: String, detail: String = "") {
        runCatching { Log.i(TAG, "stage=$stage outcome=$outcome" + if (detail.isEmpty()) "" else " $detail") }
    }
}

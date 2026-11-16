package com.festpay.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.TelephonyManager

/** Public, permission-minimal device observations. Never bank-registration proof. */
data class CellularReadiness(
    val phoneHardware: Boolean,
    val airplaneMode: Boolean?,
    val sim: SimReadiness,
    val operator: String?
) {
    val launchBlock: String? get() = when {
        !phoneHardware -> "Cellular calling is unavailable on this device."
        airplaneMode == true -> "Turn airplane mode off before using cellular USSD."
        sim == SimReadiness.ABSENT -> "No SIM is available for cellular USSD."
        sim == SimReadiness.LOCKED -> "Unlock your SIM in Android before using cellular USSD."
        else -> null
    }
}

enum class SimReadiness { READY, ABSENT, LOCKED, CANNOT_VERIFY }

object CellularReadinessReader {
    fun read(context: Context): CellularReadiness {
        val hardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val airplane = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) != 0 }.getOrNull()
        val phone = context.getSystemService(TelephonyManager::class.java)
        val sim = if (!hardware) SimReadiness.ABSENT else runCatching {
            when (phone?.simState) {
                TelephonyManager.SIM_STATE_READY -> SimReadiness.READY
                TelephonyManager.SIM_STATE_ABSENT -> SimReadiness.ABSENT
                TelephonyManager.SIM_STATE_PIN_REQUIRED, TelephonyManager.SIM_STATE_PUK_REQUIRED,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> SimReadiness.LOCKED
                else -> SimReadiness.CANNOT_VERIFY
            }
        }.getOrDefault(SimReadiness.CANNOT_VERIFY)
        val operator = runCatching { phone?.networkOperatorName?.trim()?.takeIf { it.isNotEmpty() && it.length <= 80 && it.none(Char::isISOControl) } }.getOrNull()
        return CellularReadiness(hardware, airplane, sim, operator)
    }
}

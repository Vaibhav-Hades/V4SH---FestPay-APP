package com.festpay.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimOption(val subscriptionId: Int, val slot: Int, val name: String, val iccId: String)

/**
 * Lets the owner choose which SIM places the *99# call. Listing SIMs needs READ_PHONE_STATE, which is requested only
 * when the owner opens the chooser. With no choice (or if the chosen SIM cannot be found) Android's default calling SIM is used.
 */
object SimSelection {
    private const val PREFS = "v4sh_settings"
    private const val KEY = "calling_sim_subscription_id"
    private const val TELEPHONY_PACKAGE = "com.android.phone"
    private const val TELEPHONY_SERVICE = "com.android.services.telephony.TelephonyConnectionService"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    // Guarded by hasPermission() on the next line; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    fun options(context: Context): List<SimOption> {
        if (!hasPermission(context)) return emptyList()
        return runCatching {
            context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                .sortedBy { it.simSlotIndex }
                .map { SimOption(it.subscriptionId, it.simSlotIndex, (it.displayName?.toString()?.takeIf { n -> n.isNotBlank() } ?: it.carrierName?.toString()).orEmpty().ifBlank { "SIM ${it.simSlotIndex + 1}" }, it.iccId.orEmpty()) }
        }.getOrDefault(emptyList())
    }

    fun chosen(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).takeIf { it.contains(KEY) }?.getInt(KEY, -1)?.takeIf { it >= 0 }

    fun choose(context: Context, subscriptionId: Int?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (subscriptionId == null) remove(KEY) else putInt(KEY, subscriptionId)
        }.apply()
    }

    /** What the chooser row shows. */
    fun label(context: Context): String {
        val id = chosen(context) ?: return "Phone default"
        val option = options(context).firstOrNull { it.subscriptionId == id }
        return option?.let { "SIM ${it.slot + 1} · ${it.name}" } ?: "Phone default"
    }

    /** The calling account of the chosen SIM, or null to let Android use its default. */
    // Guarded by hasPermission() below; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    fun handleFor(context: Context): PhoneAccountHandle? {
        val id = chosen(context) ?: return null
        if (!hasPermission(context)) return null
        val option = options(context).firstOrNull { it.subscriptionId == id } ?: return null
        // Listing calling accounts needs READ_PHONE_NUMBERS, which V4SH does not ask for. On this phone the SIM accounts are
        // owned by the telephony service and their ids are the subscription numbers, so that handle is built directly
        // (the same form `dumpsys telecom` shows). If the permission happens to be held, the real account list is preferred.
        val listed = runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.callCapablePhoneAccounts.firstOrNull { handle ->
                val hid = handle.id.orEmpty()
                hid == option.subscriptionId.toString() || (option.iccId.isNotEmpty() && hid == option.iccId)
            }
        }.getOrNull()
        return listed ?: PhoneAccountHandle(ComponentName(TELEPHONY_PACKAGE, TELEPHONY_SERVICE), option.subscriptionId.toString())
    }

    /** The one place a `*99#` call is built; both Check Balance and payments use it. */
    fun dialIntent(context: Context, code: String = "*99#", resolve: (Context) -> PhoneAccountHandle? = ::handleFor): Intent =
        Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(code))).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            resolve(context)?.let { putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        }
}

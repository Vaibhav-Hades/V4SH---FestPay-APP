package com.festpay.app.data

import android.content.Context
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.TransactionRecord
import org.json.JSONArray
import org.json.JSONObject

class TransactionRepository(context: Context, preferencesName: String = "v4sh_transactions_pref") {
    private val protected = ProtectedPreferences(context, preferencesName)

    fun loadTransactions(): List<TransactionRecord> {
        val records = protected.snapshot().getJSONArray("transactions_list")
        return buildList {
            for (index in 0 until records.length()) {
                val value = records.getJSONObject(index)
                add(TransactionRecord(value.getString("id"), if (value.isNull("timestamp")) "" else value.getString("timestamp"),
                    value.optString("payeeName"), value.getString("payeeVpa"), value.getString("amount"), value.optString("currency", "INR"), PaymentState.UNKNOWN))
            }
        }
    }

    fun saveTransactions(records: List<TransactionRecord>): Boolean = protected.update { value ->
        value.put("transactions_list", JSONArray().apply {
            records.forEach { record -> put(JSONObject().apply {
                put("id", record.localTransactionId); put("timestamp", record.timestamp); put("payeeName", record.payeeName)
                put("payeeVpa", record.payeeVpa); put("amount", record.amount); put("currency", record.currency); put("state", "UNKNOWN")
            }) }
        })
    }

    fun loadUserVpa(): String = protected.snapshot().getString("user_vpa")
    fun loadUserName(): String = protected.snapshot().getString("user_name")
    fun saveUserProfile(vpa: String, name: String): Boolean = protected.update {
        val profile = PaymentInputValidator.profile(vpa, name) ?: throw ProtectedStorageException()
        it.put("user_vpa", profile.vpa).put("user_name", profile.name)
    }
}

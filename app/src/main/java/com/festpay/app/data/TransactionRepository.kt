// File: TransactionRepository.kt
package com.festpay.app.data

import android.content.Context
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.TransactionRecord
import org.json.JSONArray
import org.json.JSONObject

class TransactionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("v4sh_transactions_pref", Context.MODE_PRIVATE)

    fun loadTransactions(): List<TransactionRecord> {
        val jsonStr = prefs.getString("transactions_list", "[]") ?: "[]"
        val list = mutableListOf<TransactionRecord>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val stateName = obj.optString("state", "UNKNOWN")
                val state = try {
                    PaymentState.valueOf(stateName)
                } catch (_: Exception) {
                    PaymentState.UNKNOWN
                }
                list.add(
                    TransactionRecord(
                        localTransactionId = obj.optString("id"),
                        timestamp = obj.optString("timestamp"),
                        payeeName = obj.optString("payeeName"),
                        payeeVpa = obj.optString("payeeVpa"),
                        amount = obj.optString("amount"),
                        currency = obj.optString("currency", "INR"),
                        state = state
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveTransactions(records: List<TransactionRecord>) {
        try {
            val jsonArray = JSONArray()
            for (record in records) {
                val obj = JSONObject().apply {
                    put("id", record.localTransactionId)
                    put("timestamp", record.timestamp)
                    put("payeeName", record.payeeName)
                    put("payeeVpa", record.payeeVpa)
                    put("amount", record.amount)
                    put("currency", record.currency)
                    put("state", record.state.name)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("transactions_list", jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    fun loadUserVpa(): String {
        return prefs.getString("user_vpa", "vaibhav@upi") ?: "vaibhav@upi"
    }

    fun loadUserName(): String {
        return prefs.getString("user_name", "YMS Vaibhav") ?: "YMS Vaibhav"
    }

    fun saveUserProfile(vpa: String, name: String) {
        prefs.edit().putString("user_vpa", vpa).putString("user_name", name).apply()
    }
}

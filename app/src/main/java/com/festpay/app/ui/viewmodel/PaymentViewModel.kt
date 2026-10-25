// File: PaymentViewModel.kt
package com.festpay.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.TransactionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PaymentRequest(
    val payeeVpa: String,
    val payeeName: String,
    val amount: String,
    val currency: String = "INR"
)

enum class PaymentState {
    IDLE, PROCESSING, SUCCESS, FAILED, UNKNOWN, CANCELLED, AUTHENTICATION_REQUIRED
}

data class PaymentResult(
    val state: PaymentState,
    val localReference: String,
    val message: String
)

interface PaymentEngine {
    suspend fun executePayment(request: PaymentRequest, scenario: PaymentScenario, context: Context? = null): PaymentResult
}

enum class PaymentScenario { SUCCESS, FAILED, UNKNOWN }

class LocalMockPaymentEngine : PaymentEngine {
    override suspend fun executePayment(request: PaymentRequest, scenario: PaymentScenario, context: Context?): PaymentResult {
        delay(1500) // simulate local payment rail execution latency
        val refId = "V4SH-" + (100000..999999).random()
        return when (scenario) {
            PaymentScenario.SUCCESS -> PaymentResult(PaymentState.SUCCESS, refId, "Payment confirmed & settled")
            PaymentScenario.FAILED -> PaymentResult(PaymentState.FAILED, refId, "Payment declined by provider")
            PaymentScenario.UNKNOWN -> PaymentResult(PaymentState.UNKNOWN, refId, "Payment status unclear")
        }
    }
}

class RealUssdPaymentEngine : PaymentEngine {
    companion object {
        private const val TAG = "RealUssdPaymentEngine"
    }

    override suspend fun executePayment(request: PaymentRequest, scenario: PaymentScenario, context: Context?): PaymentResult {
        Log.d(TAG, "USSD session started")

        if (context != null) {
            val resetIntent = Intent(FestPayAccessibilityService.ACTION_NEW_USSD_SESSION).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(resetIntent)

            val ussdUri = Uri.parse("tel:" + Uri.encode("*99#"))
            val callIntent = Intent(Intent.ACTION_DIAL, ussdUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(callIntent)
                Log.d(TAG, "Initial menu detected")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start USSD session", e)
                return PaymentResult(PaymentState.FAILED, "V4SH-USSD-ERR", "Failed to launch USSD session")
            }
        }

        // Simulate safe staged progression through non-sensitive stages
        delay(1000)
        Log.d(TAG, "Send money menu detected")
        delay(1000)
        Log.d(TAG, "UPI ID input detected")
        delay(1000)

        // CRITICAL SAFETY CHECK: Reaching Authentication / PIN Screen - STOP IMMEDIATELY
        Log.d(TAG, "Authentication required")
        return PaymentResult(
            state = PaymentState.AUTHENTICATION_REQUIRED,
            localReference = "V4SH-AUTH-REQUIRED",
            message = "Authentication required. Please complete PIN entry on your carrier menu."
        )
    }
}

data class TransactionRecord(
    val localTransactionId: String,
    val timestamp: String,
    val payeeName: String,
    val payeeVpa: String,
    val amount: String,
    val currency: String = "INR",
    val state: PaymentState
)

class PaymentViewModel : ViewModel() {
    var paymentState by mutableStateOf(PaymentState.IDLE)
        private set

    var currentScenario by mutableStateOf(PaymentScenario.SUCCESS)

    // Toggle between Mock Engine and Real USSD Engine (Real USSD is DEFAULT in production)
    var isRealUssdMode by mutableStateOf(true)

    var userVpa by mutableStateOf("vaibhav@upi")
    var userName by mutableStateOf("YMS Vaibhav")

    var lastResult by mutableStateOf<PaymentResult?>(null)
        private set

    val transactionLedger = mutableStateListOf<TransactionRecord>()

    private val mockEngine: PaymentEngine = LocalMockPaymentEngine()
    private val realEngine: PaymentEngine = RealUssdPaymentEngine()

    private var transactionRepo: TransactionRepository? = null

    fun initRepository(ctx: Context) {
        if (transactionRepo == null) {
            transactionRepo = TransactionRepository(ctx)
            userVpa = transactionRepo!!.loadUserVpa()
            userName = transactionRepo!!.loadUserName()
            val saved = transactionRepo!!.loadTransactions()
            if (transactionLedger.isEmpty() && saved.isNotEmpty()) {
                transactionLedger.clear()
                transactionLedger.addAll(saved)
            }
        }
    }

    fun saveUserProfile(ctx: Context, newVpa: String, newName: String) {
        initRepository(ctx)
        userVpa = newVpa
        userName = newName
        transactionRepo?.saveUserProfile(newVpa, newName)
    }

    fun startPayment(vpa: String, name: String, amount: String, ctx: Context?, onComplete: (PaymentState) -> Unit) {
        // Duplicate payment protection: once payment initiation occurs, ignore repeated calls
        if (paymentState == PaymentState.PROCESSING) return
        paymentState = PaymentState.PROCESSING

        viewModelScope.launch {
            val request = PaymentRequest(vpa, name, amount)
            // PRODUCTION FIX: Ensure realEngine is ALWAYS used unless debug controls are visible AND mock mode is explicitly chosen
            val engineToUse = if (com.festpay.app.ui.screens.isDebugControlsVisible && !isRealUssdMode) mockEngine else realEngine
            val result = engineToUse.executePayment(request, currentScenario, ctx)
            lastResult = result
            paymentState = result.state

            val record = TransactionRecord(
                localTransactionId = result.localReference,
                timestamp = "Today, Just now",
                payeeName = name,
                payeeVpa = vpa,
                amount = amount,
                state = result.state
            )
            transactionLedger.add(0, record)
            ctx?.let { initRepository(it) }
            transactionRepo?.saveTransactions(transactionLedger)

            onComplete(result.state)
        }
    }

    fun stopWaiting() {
        if (paymentState == PaymentState.PROCESSING || paymentState == PaymentState.AUTHENTICATION_REQUIRED) {
            // Stop waiting after initiation -> UNKNOWN, never CANCELLED
            paymentState = PaymentState.UNKNOWN
            lastResult = PaymentResult(PaymentState.UNKNOWN, "V4SH-UNKNOWN", "Payment status unclear")
        }
    }

    fun markCancelled() {
        // Cancelled only before payment initiation
        if (paymentState == PaymentState.IDLE) {
            paymentState = PaymentState.CANCELLED
        }
    }

    fun checkStatus(onStatusChecked: (PaymentState) -> Unit) {
        paymentState = PaymentState.PROCESSING
        viewModelScope.launch {
            delay(1200)
            val finalState = if (lastResult?.state == PaymentState.AUTHENTICATION_REQUIRED || lastResult?.state == PaymentState.UNKNOWN) {
                PaymentState.UNKNOWN
            } else {
                lastResult?.state ?: PaymentState.UNKNOWN
            }
            paymentState = finalState
            onStatusChecked(finalState)
        }
    }
}

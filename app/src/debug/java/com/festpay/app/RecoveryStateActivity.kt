package com.festpay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.festpay.app.data.PaymentAttemptJournal
import com.festpay.app.data.TransactionRepository
import com.festpay.app.ui.theme.V4SHTheme
import com.festpay.app.ui.viewmodel.PaymentViewModel

/** Debug-only lifecycle fixture. No private user store and no payment-launch control. */
class RecoveryStateActivity : ComponentActivity() {
    lateinit var model: PaymentViewModel
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = requireNotNull(intent.getStringExtra("database"))
        val preferences = requireNotNull(intent.getStringExtra("preferences"))
        require(Regex("test_recreate_[a-zA-Z0-9_-]+\\.db").matches(database))
        require(Regex("test_recreate_[a-zA-Z0-9_-]+").matches(preferences))
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PaymentViewModel(
                journalFactory = { PaymentAttemptJournal(it, database) },
                repositoryFactory = { TransactionRepository(it, preferences) }
            ) as T
        })[PaymentViewModel::class.java]
        setContent {
            V4SHTheme {
                LaunchedEffect(Unit) { model.initRepository(this@RecoveryStateActivity) }
                Column {
                    Text("Synthetic recovery fixture")
                    Text("Saved outcome: ${model.transactionLedger.singleOrNull()?.state?.name ?: "LOADING"}")
                    Text("Reservation active: ${model.selectedAttemptId != null}")
                }
            }
        }
    }
}

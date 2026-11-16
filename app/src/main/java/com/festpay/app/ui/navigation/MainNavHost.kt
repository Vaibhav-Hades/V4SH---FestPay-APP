// File: MainNavHost.kt
package com.festpay.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.festpay.app.ui.screens.*
import com.festpay.app.ui.theme.V4SHTheme

/**
 * Root navigation host for the app.
 * Configured with startDestination = "home" for the main app shell,
 * containing all top-level destinations (home, transactions, receive_money, profile),
 * account & information screens (upi_details, app_security, privacy, help_center, contact_support),
 * first-time setup, QR payment flow, and network error states.
 */
@Composable
fun MainNavHost(navController: NavHostController) {
    val paymentViewModel: com.festpay.app.ui.viewmodel.PaymentViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    V4SHTheme {
        NavHost(navController = navController, startDestination = "home") {
            // Top-level destinations (Phase-1 Bottom Navigation)
            composable("home") { HomeScreen(navController, paymentViewModel) }
            composable("transactions") { TransactionsScreen(navController, paymentViewModel) }
            composable("receive_money") { ReceiveMoneyScreen(navController, paymentViewModel) }
            composable("profile") { ProfileScreen(navController, paymentViewModel) }

            // Account / Information & Check Balance screens
            composable("upi_details") { UpiDetailsScreen(navController, paymentViewModel) }
            composable("check_balance") { CheckBalanceScreen(navController, paymentViewModel) }
            composable("upi_payment") { UpiIdPaymentScreen(navController) }
            composable("app_security") { AppSecurityScreen(navController) }
            composable("privacy") { PrivacyScreen(navController) }
            composable("help_center") { HelpCenterScreen(navController) }
            composable("contact_support") { ContactSupportScreen(navController) }

            // Network / Pre-initiation Error states
            composable("no_mobile_network") { NoMobileNetworkScreen(navController) }

            // Setup flow
            composable("welcome_setup") { WelcomeSetupScreen(navController) }
            composable("sleep_mode") { SleepModeScreen(navController) }
            composable("spending") { SpendingScreen(navController, paymentViewModel) }
            composable("language") { LanguageScreen(navController) }
            composable("demo") { DemoPaymentScreen(navController) }
            composable("bank_setup") { BankSetupScreen(navController) }
            composable("guided_setup") { GuidedSetupScreen(navController) }
            composable("payment_readiness") { PaymentReadinessScreen(navController, paymentViewModel) }
            composable("payment_access_setup") { PaymentAccessSetupScreen(navController, paymentViewModel) }
            composable("payment_sim_selection") { PaymentSimSelectionScreen(navController) }

            // Batch 2 & 3: QR + Recipient + Amount Flow
            composable("qr_scanner") { QrScannerScreen(navController) }
            composable("camera_access_required") { CameraAccessRequiredScreen(navController) }
            composable("qr_code_not_recognized") { QrCodeNotRecognizedScreen(navController) }
            composable("unsupported_qr_code") { UnsupportedQrCodeScreen(navController) }
            composable("payment_details_missing") { PaymentDetailsMissingScreen(navController) }
            composable("qr_recipient") { QrRecipientScreen(navController) }
            composable("payment_progress") { PaymentProgressScreen(navController) }
            composable("amount_entry") { AmountEntryScreen(navController, paymentViewModel) }

            // Batch 4 & 5A: Payment Engine Terminal States & Real USSD
            composable("transaction_details") { TransactionDetailsScreen(navController, paymentViewModel) }
        }
    }
}

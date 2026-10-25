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
    V4SHTheme {
        NavHost(navController = navController, startDestination = "home") {
            // Top-level destinations (Phase-1 Bottom Navigation)
            composable("home") { HomeScreen(navController) }
            composable("transactions") { TransactionsScreen(navController) }
            composable("receive_money") { ReceiveMoneyScreen(navController) }
            composable("profile") { ProfileScreen(navController) }

            // Account / Information screens
            composable("upi_details") { UpiDetailsScreen(navController) }
            composable("app_security") { AppSecurityScreen(navController) }
            composable("privacy") { PrivacyScreen(navController) }
            composable("help_center") { HelpCenterScreen(navController) }
            composable("contact_support") { ContactSupportScreen(navController) }

            // Network / Pre-initiation Error states
            composable("no_mobile_network") { NoMobileNetworkScreen(navController) }

            // Setup flow
            composable("welcome_setup") { WelcomeSetupScreen(navController) }
            composable("payment_readiness") { PaymentReadinessScreen(navController) }
            composable("payment_access_setup") { PaymentAccessSetupScreen(navController) }
            composable("payment_sim_selection") { PaymentSimSelectionScreen(navController) }

            // Batch 2 & 3: QR + Recipient + Amount Flow
            composable("qr_scanner") { QrScannerScreen(navController) }
            composable("camera_access_required") { CameraAccessRequiredScreen(navController) }
            composable("qr_code_not_recognized") { QrCodeNotRecognizedScreen(navController) }
            composable("unsupported_qr_code") { UnsupportedQrCodeScreen(navController) }
            composable("payment_details_missing") { PaymentDetailsMissingScreen(navController) }
            composable("qr_recipient") { QrRecipientScreen(navController) }
            composable("amount_entry") { AmountEntryScreen(navController) }
            composable("review_payment") { ReviewPaymentScreen(navController) }

            // Batch 4 & 5A: Payment Engine Terminal States & Real USSD
            composable("payment_in_progress") { PaymentInProgressScreen(navController) }
            composable("payment_successful") { PaymentSuccessfulScreen(navController) }
            composable("payment_failed") { PaymentFailedScreen(navController) }
            composable("payment_status_unclear") { PaymentStatusUnclearScreen(navController) }
            composable("payment_cancelled") { PaymentCancelledScreen(navController) }
            composable("authentication_required") { AuthenticationRequiredScreen(navController) }
            composable("transaction_details") { TransactionDetailsScreen(navController) }
        }
    }
}

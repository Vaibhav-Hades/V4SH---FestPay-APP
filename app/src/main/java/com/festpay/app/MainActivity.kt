package com.festpay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.festpay.app.data.AccessibilityConsent
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.festpay.app.ui.navigation.MainNavHost
import com.festpay.app.ui.navigation.V4SHBottomNavigation
import com.festpay.app.ui.theme.V4SHTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(com.festpay.app.data.AppLanguage.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            V4SHTheme {
                var showDisclosure by remember { mutableStateOf(!AccessibilityConsent.accepted(this@MainActivity)) }
                var consentError by remember { mutableStateOf(false) }
                if (showDisclosure) AlertDialog(
                    onDismissRequest = { showDisclosure = false },
                    title = { Text("Allow cellular screen assistance?") },
                    text = { Text("When you start Check Balance or a payment, V4SH uses Android Accessibility to read non-password text in supported system phone dialogs, enter menu choices and reviewed payment details, and display the carrier's balance response. Processing stays on this device; raw screen content is not logged or shared. V4SH stops automation at authentication and never reads or enters your UPI PIN. Carrier text cannot confirm a payment. This permission is optional: QR preparation, Receive and history remain available. You can withdraw this consent in Payment Readiness and disable the service in Android settings." + if (consentError) " Consent could not be saved." else "") },
                    confirmButton = { TextButton(onClick = {
                        if (AccessibilityConsent.set(this@MainActivity, true)) showDisclosure = false else consentError = true
                    }) { Text("I agree") } },
                    dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Not now") } }
                )
                val navController = rememberNavController()
                Scaffold(bottomBar = { V4SHBottomNavigation(navController) }) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                        MainNavHost(navController)
                    }
                }
            }
        }
    }
}

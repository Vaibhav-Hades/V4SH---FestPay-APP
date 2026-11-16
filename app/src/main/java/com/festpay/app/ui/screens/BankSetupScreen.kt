package com.festpay.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.festpay.app.data.SetupDetails

/** Profile > Advanced > Bank setup: details V4SH may type into the carrier's one-time *99# setup screens. */
@Composable
fun BankSetupScreen(navController: NavHostController) {
    val context = LocalContext.current
    var bank by remember { mutableStateOf(SetupDetails.bankQuery(context).orEmpty()) }
    var last6 by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<String?>(null) }

    val bankOk = bank.isBlank() || SetupDetails.validBank(bank)
    val last6Ok = last6.isEmpty() || SetupDetails.validLast6(last6)
    val expiryOk = expiry.isEmpty() || SetupDetails.validExpiry(expiry)

    Scaffold(topBar = { V4shTopBar(title = "Bank setup", onBack = { navController.popBackStack() }) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = spacing, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Help with first-time *99# setup", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("When a SIM is new to *99#, the carrier asks you to choose your bank and sometimes for debit-card details. V4SH can type these for you on the carrier's screen. It never types an OTP or your UPI PIN: you always do those yourself.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))

            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your bank", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    OutlinedTextField(value = bank, onValueChange = { bank = it.take(24); saved = null }, singleLine = true, isError = !bankOk,
                        label = { Text("Bank name, e.g. State Bank of India") }, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(if (bankOk) "V4SH types the first 3 letters when the carrier asks. No IFSC code is needed. Saved on this phone only." else "Use letters only, at least 3.") })
                    Button(onClick = { SetupDetails.setBankQuery(context, bank.takeIf { it.isNotBlank() }); saved = "Bank saved." },
                        enabled = bankOk, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text("Save bank") }
                }
            }

            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Debit card (only if the carrier asks)", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    Text("Kept in memory for 10 minutes at most. Never saved, never written to history or logs. Erased as soon as V4SH has typed it, when the carrier session ends, or when you tap Erase.",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    OutlinedTextField(value = last6, onValueChange = { last6 = it.filter(Char::isDigit).take(6); saved = null }, singleLine = true, isError = !last6Ok,
                        label = { Text("Last 6 digits of the card") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = expiry, onValueChange = { expiry = it.filter(Char::isDigit).take(4); saved = null }, singleLine = true, isError = !expiryOk,
                        label = { Text("Expiry as MMYY, e.g. 0828") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    Text("V4SH types a card detail only when the screen asks for exactly that one, in a format it states. If a screen asks for both together or in an unclear format, V4SH stops and you type it yourself.",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    Button(onClick = { SetupDetails.setCard(last6.takeIf { it.isNotEmpty() }, expiry.takeIf { it.isNotEmpty() }); saved = "Card details held in memory for up to 10 minutes." },
                        enabled = last6Ok && expiryOk && (last6.isNotEmpty() || expiry.isNotEmpty()), shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text("Use for the next setup") }
                    OutlinedButton(onClick = { SetupDetails.clearSensitive(); last6 = ""; expiry = ""; saved = "Card details erased." }, shape = largeCardShape) { Text("Erase card details now") }
                }
            }
            saved?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy)) }
            Text("Not tested against a real first-time carrier screen. Wording differs between carriers and banks, so V4SH may stop and ask you to continue by hand.",
                style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
        }
    }
}

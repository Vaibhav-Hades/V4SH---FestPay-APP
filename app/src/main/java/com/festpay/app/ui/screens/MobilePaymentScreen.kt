package com.festpay.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.festpay.app.data.PaymentInputValidator
import com.festpay.app.data.VerificationStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiIdPaymentScreen(navController: NavHostController) {
    var upiId by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val normalized = PaymentInputValidator.normalizedVpa(upiId)
    LaunchedEffect(Unit) { SharedPaymentState.selectRecipient(upiId) }
    Scaffold(
        topBar = { V4shTopBar(title = "Pay by UPI ID", onBack = { navController.popBackStack() }, onAction = { navController.navigate("help_center") }) }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = spacing, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Who are you paying?", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("Enter the recipient's UPI ID. A correctly formatted ID does not prove the account exists or who owns it.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            OutlinedTextField(
                upiId, { upiId = it; SharedPaymentState.selectRecipient(it) }, label = { Text("UPI ID") },
                isError = upiId.isNotBlank() && normalized == null,
                supportingText = {
                    Text(if (upiId.isNotBlank() && normalized == null) "Use a full UPI ID such as name@bank. Mobile numbers are not supported."
                        else "For example name@bank")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, shape = largeCardShape,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm, focusedLabelColor = PrimaryBurgundy),
                modifier = Modifier.fillMaxWidth()
            )
            PasteUpiButton(navController) { upiId = it; SharedPaymentState.selectRecipient(it) }
            if (normalized != null) {
                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(normalized, style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy))
                        RecipientVerificationNotice(SharedPaymentState.recipient)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    SharedPaymentState.isMobilePayment = false
                    val ticket = SharedPaymentState.selectRecipient(upiId)
                    scope.launch {
                        SharedPaymentState.resolve(ticket)
                        if (SharedPaymentState.selectionId == ticket.requestId && SharedPaymentState.recipient.verificationStatus != VerificationStatus.INVALID_UPI_ID)
                            navController.navigate("amount_entry")
                    }
                },
                enabled = normalized != null, shape = largeCardShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy, disabledContainerColor = PrimaryBurgundy.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Continue", style = MaterialTheme.typography.labelLarge.copy(color = androidx.compose.ui.graphics.Color.White)) }
            TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = SecondaryUmber))
            }
        }
    }
}

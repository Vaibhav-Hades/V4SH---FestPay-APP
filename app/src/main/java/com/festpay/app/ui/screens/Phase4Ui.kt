package com.festpay.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.festpay.app.data.AppLanguage
import kotlinx.coroutines.delay

@Composable
fun LanguageScreen(navController: NavHostController) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguage.chosen(context)) }
    Scaffold(topBar = { V4shTopBar(title = "Language", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Language", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("Most screen text is translated. Long technical messages, error text and anything with amounts or names stay in English. The translations were not checked by a native speaker, so if a payment screen ever seems unclear, rely on the carrier's own screen and your bank.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            AppLanguage.choices.forEach { choice ->
                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == choice.code, onClick = {
                            if (current != choice.code) {
                                AppLanguage.choose(context, choice.code); current = choice.code
                                (context as? android.app.Activity)?.recreate()
                            }
                        })
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/**
 * A walk-through that touches nothing real: no carrier, no Accessibility, no journal, no money. For new users and reviewers.
 */
@Composable
fun DemoPaymentScreen(navController: NavHostController) {
    val steps = listOf("Opening *99# (demo)", "Choosing Send Money", "Typing the UPI ID", "Typing the amount",
        "Here you would type your UPI PIN on the carrier's own screen. V4SH never types it.", "Carrier replies: payment successful (demo)")
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (shown < steps.size) { delay(1100); shown++ } }
    Scaffold(topBar = { V4shTopBar(title = "Demo", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = cardShape, color = Color(0xFFFFD4BE).copy(alpha = 0.5f)) {
                Text("DEMO · no money moves, nothing is saved and V4SH does not touch your phone's calls or your history.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold), modifier = Modifier.padding(14.dp))
            }
            Text("Paying ₹10 to Demo Shop", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            steps.forEachIndexed { i, s ->
                if (i < shown) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF2B4C3F), modifier = Modifier.size(22.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (shown < steps.size) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBurgundy)
            else {
                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payment successful (demo)", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                        Text("In a real payment this message comes from your carrier, together with a reference number, and V4SH shows it as your result.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { navController.popBackStack() }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

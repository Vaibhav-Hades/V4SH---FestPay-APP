package com.festpay.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentViewModel

/** Home: people the owner pays often. One tap starts a payment to them. */
@Composable
fun PayAgainRow(navController: NavHostController, paymentViewModel: PaymentViewModel) {
    val payees = remember(paymentViewModel.transactionLedger.size, paymentViewModel.transactionLedger.firstOrNull()?.localTransactionId) {
        FrequentPayees.from(paymentViewModel.transactionLedger.filter { it.operation == "PAYMENT" }
            .map { PastPayee(it.payeeVpa, it.payeeName, it.carrierOutcome == "SUCCESS") })
    }
    if (payees.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pay again", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            payees.forEach { p ->
                AssistChip(
                    onClick = {
                        SharedPaymentState.clear()
                        SharedPaymentState.selectRecipient(p.vpa, "", "", p.name.takeIf { it.isNotBlank() })
                        navController.navigate("amount_entry")
                    },
                    label = { Text(p.name.ifBlank { p.vpa }.take(22)) }
                )
            }
        }
    }
}


/** Home: says why Accessibility is off right after V4SH switched itself off. */
@Composable
fun AutoSleepNote() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var show by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        fun refresh() { show = SleepMode.showAutoSleepNote(context, AccessibilityRuntime.connection.snapshot.connected) }
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(o); refresh(); onDispose { lifecycle.removeObserver(o) }
    }
    if (!show) return
    Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF2EE)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("V4SH switched itself off so your banking apps work normally. Turn it on again before your next payment.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF2B4C3F)), modifier = Modifier.weight(1f))
            TextButton(onClick = { SleepMode.dismissAutoSleepNote(context); show = false }) { Text("OK") }
        }
    }
}

/** One screen that walks through everything a first payment needs, with a tick for each. */
@Composable
fun GuidedSetupScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun phone() = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    var hasPhone by remember { mutableStateOf(phone()) }
    var accessibilityOn by remember { mutableStateOf(AccessibilityRuntime.connection.snapshot.connected) }
    var consent by remember { mutableStateOf(AccessibilityConsent.accepted(context)) }
    var simLabel by remember { mutableStateOf(SimSelection.label(context)) }
    var bankSaved by remember { mutableStateOf(SetupDetails.bankQuery(context) != null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPhone = phone() }
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                hasPhone = phone(); consent = AccessibilityConsent.accepted(context)
                accessibilityOn = AccessibilityRuntime.connection.snapshot.connected
                simLabel = SimSelection.label(context); bankSaved = SetupDetails.bankQuery(context) != null
            }
        }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    Scaffold(topBar = { V4shTopBar(title = "Set up V4SH", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Four steps, once", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("V4SH opens *99# and types the menu choices for you. You only type your UPI PIN, on the carrier's own screen.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            SetupStep(1, "Allow phone calls", hasPhone, if (hasPhone) "Allowed" else "Needed to start *99#.", if (hasPhone) null else "Allow") { launcher.launch(Manifest.permission.CALL_PHONE) }
            SetupStep(2, "Turn V4SH on in Accessibility", accessibilityOn && consent,
                when { !consent -> "Reopen V4SH to review the screen-assistance notice first."; accessibilityOn -> "On. It switches itself off after each payment so banking apps work."; else -> "Off. Turn on V4SH, do your payment; it switches itself off afterwards." },
                if (!accessibilityOn && consent) "Open settings" else null) { IssueLog.openAccessibilitySettings(context) }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Choose the SIM linked to your bank", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    Text("Now: $simLabel", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    SimPickerRow()
                }
            }
            SetupStep(4, "Bank setup (only if this SIM is new to *99#)", bankSaved,
                if (bankSaved) "Bank saved." else "Optional. Lets V4SH type your bank's first letters if the carrier asks.", "Open") { navController.navigate("bank_setup") }
            Button(onClick = { navController.navigate("home") { popUpTo("home") { inclusive = false } } }, shape = largeCardShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, ok: Boolean, detail: String, action: String?, onAction: () -> Unit) {
    Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            androidx.compose.material3.Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = if (ok) "Done" else "Needs attention",
                tint = if (ok) Color(0xFF2B4C3F) else Color(0xFF8C5815), modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$number. $title", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                Text(detail, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                if (action != null) Button(onClick = onAction, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text(action) }
            }
        }
    }
}

/** Advanced: builds the problem report and hands it to Android's share sheet. The owner chooses where it goes. */
fun problemReportText(context: android.content.Context): String {
    val read = CellularReadinessReader.read(context)
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
    val text = ProblemReport.build(ProblemReport.Facts(
        appVersion = version, androidSdk = android.os.Build.VERSION.SDK_INT, manufacturer = android.os.Build.MANUFACTURER, model = android.os.Build.MODEL,
        operator = read.operator, phonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
        accessibilityConnected = AccessibilityRuntime.connection.snapshot.connected, autoSleep = SleepMode.autoSleep(context),
        chosenSim = SimSelection.label(context), problemTitles = IssueLog.recent().map { it.title }))
    return text
}

fun shareProblemReport(context: android.content.Context) {
    val text = problemReportText(context)
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "V4SH problem report"); putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(send, "Send problem report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

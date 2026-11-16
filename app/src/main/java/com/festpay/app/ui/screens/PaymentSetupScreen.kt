package com.festpay.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.PaymentIntegrityPolicy
import com.festpay.app.ui.viewmodel.PaymentViewModel
import com.festpay.app.ui.viewmodel.PreparationBlock

@Composable
fun PaymentReadinessScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun phoneGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    var hasPhonePermission by remember { mutableStateOf(phoneGranted()) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var cellular by remember { mutableStateOf(com.festpay.app.data.CellularReadinessReader.read(context)) }
    var settingsEnabled by remember { mutableStateOf(com.festpay.app.data.AccessibilityRuntime.settingsEnabled(context)) }
    val connection = com.festpay.app.data.AccessibilityRuntime.connection.snapshot
    var consent by remember { mutableStateOf(com.festpay.app.data.AccessibilityConsent.accepted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPhonePermission = phoneGranted()
    }
    DisposableEffect(context, lifecycle, paymentViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsEnabled = com.festpay.app.data.AccessibilityRuntime.settingsEnabled(context)
                consent = com.festpay.app.data.AccessibilityConsent.accepted(context)
                com.festpay.app.data.AccessibilityRuntime.diagnostics(context)
                hasPhonePermission = phoneGranted()
                cellular = com.festpay.app.data.CellularReadinessReader.read(context)
                paymentViewModel.inspectPaymentReadiness(context)
            }
        }
        val settingsObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                settingsEnabled = com.festpay.app.data.AccessibilityRuntime.settingsEnabled(context)
                com.festpay.app.data.AccessibilityRuntime.diagnostics(context)
            }
        }
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, settingsObserver)
        paymentViewModel.inspectPaymentReadiness(context)
        com.festpay.app.data.AccessibilityRuntime.diagnostics(context)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); context.contentResolver.unregisterContentObserver(settingsObserver) }
    }
    fun openSettings(action: String) {
        settingsError = null
        try { context.startActivity(Intent(action)) }
        catch (_: Exception) { settingsError = "Could not open Android settings. Open them manually from your device's Settings app." }
    }

    val block = paymentViewModel.preparationBlock
    val verdict = com.festpay.app.data.HomeReadinessEvaluator.evaluate(
        storageOk = block != PreparationBlock.STORAGE,
        earlierHistory = block == PreparationBlock.EARLIER_HISTORY,
        unresolvedAttempt = block == PreparationBlock.UNRESOLVED_ATTEMPT,
        consent = consent, phonePermission = hasPhonePermission,
        accessibilityConnected = connection.connected, cellularBlock = cellular.launchBlock
    )
    var logVersion by remember { mutableStateOf(com.festpay.app.data.IssueLog.version) }
    var operationStuck by remember { mutableStateOf(FestPayAccessibilityService.UssdSessionData.operationActive) }
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { logVersion = com.festpay.app.data.IssueLog.version; operationStuck = FestPayAccessibilityService.UssdSessionData.operationActive }
        }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    fun fix(kind: com.festpay.app.data.IssueFix) {
        when (kind) {
            com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS -> if (!com.festpay.app.data.IssueLog.openAccessibilitySettings(context)) settingsError = "Could not open Android settings. Open them manually from your device's Settings app."
            com.festpay.app.data.IssueFix.PHONE_PERMISSION -> permissionLauncher.launch(Manifest.permission.CALL_PHONE)
            com.festpay.app.data.IssueFix.HISTORY -> navController.navigate("transactions")
            com.festpay.app.data.IssueFix.STOP_STUCK_OPERATION -> { com.festpay.app.data.AccessibilityRuntime.revokeSession(); operationStuck = false; paymentViewModel.inspectPaymentReadiness(context) }
            com.festpay.app.data.IssueFix.NONE -> {}
        }
    }
    fun fixLabel(kind: com.festpay.app.data.IssueFix) = when (kind) {
        com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS -> "Open Accessibility settings"
        com.festpay.app.data.IssueFix.PHONE_PERMISSION -> "Grant Phone permission"
        com.festpay.app.data.IssueFix.HISTORY -> "View history"
        com.festpay.app.data.IssueFix.STOP_STUCK_OPERATION -> "Stop the stuck operation"
        com.festpay.app.data.IssueFix.NONE -> ""
    }
    val needs = buildList {
        if (!hasPhonePermission) add(Triple("Phone permission is off", "V4SH cannot start *99# without it.", com.festpay.app.data.IssueFix.PHONE_PERMISSION))
        if (!connection.connected) add(Triple("Accessibility is off", "V4SH needs it only while a carrier session runs. Turn V4SH on, do your payment, then it switches itself off.", com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS))
        if (operationStuck) add(Triple("A cellular operation is marked active", "If no carrier screen is open, it is stuck and blocks new payments.", com.festpay.app.data.IssueFix.STOP_STUCK_OPERATION))
        if (block == PreparationBlock.UNRESOLVED_ATTEMPT || block == PreparationBlock.EARLIER_HISTORY) add(Triple("An earlier payment needs your review", "Open history and resolve it after checking your bank.", com.festpay.app.data.IssueFix.HISTORY))
    }
    val recent = remember(logVersion) { com.festpay.app.data.IssueLog.recent() }
    Scaffold(topBar = { V4shTopBar(title = "Advanced", onBack = { navController.popBackStack() }) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = spacing, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(verdict.headline, style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text(verdict.detail, style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Needs attention", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    if (needs.isEmpty()) Text("Nothing needs attention right now.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    needs.forEach { (title, detail, kind) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy))
                            Text(detail, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                            Button(onClick = { fix(kind) }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text(fixLabel(kind)) }
                        }
                    }
                }
            }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Recent problems", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    if (recent.isEmpty()) Text("No problems recorded since V4SH was opened.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    recent.forEach { issue ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(issue.title + " · " + android.text.format.DateFormat.format("HH:mm:ss", issue.atMillis), style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy))
                            Text(issue.detail, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                            if (issue.fix != com.festpay.app.data.IssueFix.NONE) OutlinedButton(onClick = { fix(issue.fix) }, shape = largeCardShape) { Text(fixLabel(issue.fix)) }
                        }
                    }
                    if (recent.isNotEmpty()) TextButton(onClick = { com.festpay.app.data.IssueLog.clear(); logVersion = com.festpay.app.data.IssueLog.version }) { Text("Clear this list") }
                }
            }
            OutlinedButton(onClick = { navController.navigate("sleep_mode") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Sleep mode") }
            OutlinedButton(onClick = { navController.navigate("guided_setup") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Guided setup") }
            OutlinedButton(onClick = { shareProblemReport(context) }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Report a problem (share details)") }
            OutlinedButton(onClick = { navController.navigate("bank_setup") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Bank setup (first-time *99#)") }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReadinessRow(hasPhonePermission, "Phone permission",
                        if (hasPhonePermission) "Granted" else "Needed so V4SH can start *99#.")
                    ReadinessRow(consent, "Screen assistance consent",
                        if (consent) "Accepted" else "Not accepted. Reopen V4SH to review the disclosure.")
                    ReadinessRow(connection.connected, "Accessibility service", when {
                        connection.connected -> "On and connected"
                        settingsEnabled -> "Turned on but disconnected. Switch V4SH off and on in Accessibility settings. Reconnecting never starts or retries a payment."
                        else -> "Off. Turn on V4SH in Accessibility settings."
                    })
                    ReadinessRow(cellular.launchBlock == null, "Cellular",
                        cellular.launchBlock ?: ("SIM ready" + (cellular.operator?.let { " · $it" } ?: "") +
                            ". A ready SIM does not prove bank registration or *99# support."))
                    ReadinessRow(block == PreparationBlock.NONE, "Payment protection",
                        paymentViewModel.operationError ?: if (paymentViewModel.paymentPreparationBlocked) "Checking local payment storage."
                        else "No earlier attempt is blocking a new payment.")
                    ReadinessRow(null, "Bank confirmation",
                        "Not available. V4SH cannot confirm settlement, so payment outcomes stay unknown.")
                }
            }
            Text("Cellular coverage, bank registration and carrier compatibility cannot be checked here; they are only confirmed when a payment starts. QR preparation, Receive and history work without internet.",
                style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
            if (paymentViewModel.paymentPreparationBlocked && block != PreparationBlock.STORAGE) {
                OutlinedButton(onClick = { navController.navigate("transactions") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("View existing attempts") }
            }
            if (!hasPhonePermission) Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) }, shape = largeCardShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth()
            ) { Text("Grant Phone permission") }
            OutlinedButton(onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Open Accessibility settings") }
            OutlinedButton(onClick = { navController.navigate("payment_sim_selection") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("SIM guidance") }
            if (consent) TextButton(onClick = {
                com.festpay.app.data.AccessibilityConsent.set(context, false)
                consent = com.festpay.app.data.AccessibilityConsent.accepted(context)
            }) { Text("Withdraw screen assistance consent") }
            settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ReadinessRow(ok: Boolean?, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        val (icon, tint) = when (ok) {
            true -> Icons.Default.CheckCircle to Color(0xFF2B4C3F)
            false -> Icons.Default.Error to Color(0xFF8C5815)
            null -> Icons.Default.Info to SecondaryUmber
        }
        Icon(icon, contentDescription = when (ok) { true -> "Ready"; false -> "Needs attention"; null -> "Note" }, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
        }
    }
}

@Composable
fun PaymentAccessSetupScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    PaymentReadinessScreen(navController, paymentViewModel)
}

@Composable
fun PaymentSimSelectionScreen(navController: NavHostController) {
    val context = LocalContext.current
    var settingsError by remember { mutableStateOf(false) }
    Scaffold(topBar = { V4shTopBar(title = "SIM guidance", onBack = { navController.popBackStack() }) }) { innerPadding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SIM guidance", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("Use the SIM registered with your bank. On a dual-SIM phone, check Android's default calling SIM or choose the correct SIM when Android asks.",
                style = MaterialTheme.typography.bodyMedium)
            Text("V4SH does not select or verify a SIM. The system calling settings and carrier determine routing; this screen does not confirm bank registration or service availability.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)); settingsError = false }
                catch (_: Exception) { settingsError = true }
            }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Open connection settings") }
            if (settingsError) Text("Open SIM or mobile-network settings manually in Android Settings.", color = MaterialTheme.colorScheme.error)
        }
    }
}

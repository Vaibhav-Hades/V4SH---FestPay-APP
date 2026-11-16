package com.festpay.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.AccessibilityRuntime
import com.festpay.app.data.SleepMode

@Composable
fun SleepModeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var running by remember { mutableStateOf(AccessibilityRuntime.connection.snapshot.connected) }
    var enabledInSettings by remember { mutableStateOf(AccessibilityRuntime.settingsEnabled(context)) }
    var auto by remember { mutableStateOf(SleepMode.autoSleep(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                running = AccessibilityRuntime.connection.snapshot.connected
                enabledInSettings = AccessibilityRuntime.settingsEnabled(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val awake = running || enabledInSettings

    Scaffold(topBar = { V4shTopBar(title = "Sleep mode", onBack = { navController.popBackStack() }) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = spacing, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AwakeOrb(awake = running, autoSleepOn = auto) }
            Text(if (awake) "V4SH is awake" else "V4SH is asleep", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text(
                if (awake) "Screen assistance is on. Some banking apps refuse to open while it is on."
                else "Screen assistance is off. Your banking apps are not affected. Wake V4SH up before a payment or balance check.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber)
            )
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Sleep after every payment", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                        Text("Off by default. When on, V4SH switches itself off 9 seconds after a payment or balance check ends.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                    Switch(checked = auto, onCheckedChange = { auto = it; SleepMode.setAutoSleep(context, it) })
                }
            }
            var guard by remember { mutableStateOf(com.festpay.app.data.BankingGuard.enabled(context)) }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Banking-app guard", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                        Text("Off by default. When on, V4SH puts itself to sleep as soon as PhonePe, Google Pay, Paytm, BHIM or a common bank app comes to the front (never during its own payment). That app may need reopening once. The list is not exhaustive.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                    Switch(checked = guard, onCheckedChange = { guard = it; com.festpay.app.data.BankingGuard.setEnabled(context, it) })
                }
            }
            if (awake) Button(
                onClick = {
                    val refusal = SleepMode.canSleepNow(FestPayAccessibilityService.UssdSessionData.operationActive, running)
                    message = when (refusal) {
                        SleepMode.Refusal.SESSION_ACTIVE -> "A carrier session is still running. Try again when it ends."
                        SleepMode.Refusal.NOT_RUNNING -> "V4SH's service is not running. If it is still on in Android settings, switch it off there."
                        SleepMode.Refusal.NONE -> { AccessibilityRuntime.sleepNow(); "Going to sleep." }
                    }
                    running = AccessibilityRuntime.connection.snapshot.connected
                    enabledInSettings = AccessibilityRuntime.settingsEnabled(context)
                },
                shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Put V4SH to sleep now") }
            val canWake = remember { com.festpay.app.data.AccessibilityWake.canWake(context) }
            if (!awake && canWake) Button(
                onClick = {
                    message = if (com.festpay.app.data.AccessibilityWake.wake(context)) "Waking V4SH up." else "Android refused. Use the Settings button below."
                    running = AccessibilityRuntime.connection.snapshot.connected
                    enabledInSettings = AccessibilityRuntime.settingsEnabled(context)
                },
                shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Wake V4SH now") }
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (awake) "Open Android Accessibility settings" else "Wake V4SH up") }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy)) }
            if (android.os.Build.VERSION.SDK_INT >= 33) OutlinedButton(
                onClick = {
                    runCatching {
                        context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                            android.content.ComponentName(context, com.festpay.app.V4SHTileService::class.java), "V4SH",
                            android.graphics.drawable.Icon.createWithResource(context, com.festpay.app.R.drawable.ic_tile_shield), context.mainExecutor) { }
                    }.onFailure { message = "Open Quick Settings, tap the pencil and drag the V4SH tile in." }
                },
                shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Add V4SH to Quick Settings") }
            Text("The Quick Settings tile shows Awake or Asleep and is the fastest way to change it.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
            if (!canWake) Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("One-tap wake (optional, advanced)", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    Text("From a computer with USB debugging, run this once. Afterwards V4SH and its tile can wake V4SH in one tap. V4SH only ever switches its own entry on, and only when you tap Wake.",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    Text(com.festpay.app.data.AccessibilityWake.ADB_GRANT, style = MaterialTheme.typography.bodySmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    TextButton(onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("adb", com.festpay.app.data.AccessibilityWake.ADB_GRANT))
                        message = "Copied."
                    }) { Text("Copy the command") }
                }
            }
            Text("Android only lets you switch Accessibility on yourself, so waking V4SH up opens Android's settings. QR scanning, Receive and history work while V4SH sleeps.",
                style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
        }
    }
}

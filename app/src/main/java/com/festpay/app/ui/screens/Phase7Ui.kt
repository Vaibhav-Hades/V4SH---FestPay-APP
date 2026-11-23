package com.festpay.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentViewModel
import java.math.BigDecimal
import java.util.Locale

// ===========================================================================================================================
// Carrier Doctor
// ===========================================================================================================================

/** Sends one *99# request through Android's own USSD API and reports how the carrier answered. No dialog, no Accessibility. */
@SuppressLint("MissingPermission")
fun runUssdTest(context: Context, subscriptionId: Int?, onResult: (CarrierDoctor.TestResult) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        onResult(CarrierDoctor.TestResult(CarrierDoctor.TestOutcome.NOT_ALLOWED, 0, null)); return
    }
    val handler = Handler(Looper.getMainLooper())
    val start = SystemClock.elapsedRealtime()
    var done = false
    fun finish(response: String?, code: Int?) {
        if (done) return
        done = true; handler.removeCallbacksAndMessages(null)
        onResult(CarrierDoctor.classify(response, code, SystemClock.elapsedRealtime() - start))
    }
    handler.postDelayed({ finish(null, null) }, 45_000L)
    try {
        val base = context.getSystemService(TelephonyManager::class.java)
        val tm = if (subscriptionId != null) base.createForSubscriptionId(subscriptionId) else base
        tm.sendUssdRequest("*99#", object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) = finish(response.toString(), null)
            override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) = finish(null, failureCode)
        }, handler)
    } catch (_: SecurityException) {
        done = true; handler.removeCallbacksAndMessages(null); onResult(CarrierDoctor.TestResult(CarrierDoctor.TestOutcome.NOT_ALLOWED, 0, null))
    } catch (e: Exception) {
        finish(null, -1)
    }
}

@Composable
private fun FindingRow(f: CarrierDoctor.Finding) {
    val (icon, tint) = when (f.level) {
        CarrierDoctor.Level.OK -> Icons.Default.CheckCircle to Color(0xFF2B4C3F)
        CarrierDoctor.Level.WARN -> Icons.Default.Warning to Color(0xFF8C5815)
        CarrierDoctor.Level.FAIL -> Icons.Default.Error to Color(0xFF93000A)
        CarrierDoctor.Level.INFO -> Icons.Default.Info to SecondaryUmber
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        androidx.compose.material3.Icon(icon, contentDescription = f.level.name, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
            Text(f.advice, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
        }
    }
}

@Composable
fun CarrierDoctorScreen(navController: NavHostController) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CarrierDoctor.TestResult?>(null) }
    val simPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val sims = remember(refresh) { SimSelection.options(context) }
    val read = remember(refresh) { CellularReadinessReader.read(context) }
    val facts = remember(refresh, IssueLog.version) {
        CarrierDoctor.Facts(
            phonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
            accessibilityConnected = AccessibilityRuntime.connection.snapshot.connected,
            airplane = read.airplaneMode, simReady = read.sim == SimReadiness.READY,
            activeSimCount = if (SimSelection.hasPermission(context)) sims.size else null,
            chosenSimLabel = SimSelection.label(context), operator = read.operator,
            droppedSessionsRecorded = IssueLog.recent().count { it.detail.contains("answered with only the number") }
        )
    }
    val findings = remember(facts) { CarrierDoctor.analyze(facts) }

    fun start(label: String, subId: Int?) {
        testing = label; result = null
        runUssdTest(context, subId) { r -> result = r; testing = null }
    }

    Scaffold(topBar = { V4shTopBar(title = "Carrier Doctor", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Carrier Doctor", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("Checks your phone's setup for *99#, then can ask the carrier once whether its menu answers. Nothing is paid and nothing is typed.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Phone check", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    findings.forEach { FindingRow(it) }
                }
            }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ask the carrier", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    Text("Sends one *99# request and waits up to 45 seconds for the menu. It opens one session on the network, so wait about two minutes before paying.",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    if (!facts.phonePermission) Button(onClick = { phonePermission.launch(Manifest.permission.CALL_PHONE) }, shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text("Allow phone calls") }
                    else if (testing != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PrimaryBurgundy)
                            Text("Testing $testing…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (sims.size > 1) {
                        sims.forEach { s ->
                            Button(onClick = { start("SIM ${s.slot + 1}", s.subscriptionId) }, shape = largeCardShape,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth()) { Text("Test SIM ${s.slot + 1} · ${s.name}") }
                        }
                    } else {
                        Button(onClick = { start("*99#", sims.firstOrNull()?.subscriptionId) }, shape = largeCardShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth()) { Text("Test *99# now") }
                        if (!SimSelection.hasPermission(context)) TextButton(onClick = { simPermission.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("Let V4SH list my SIMs") }
                    }
                    result?.let { r -> FindingRow(CarrierDoctor.explain(r)) }
                }
            }
        }
    }
}

// ===========================================================================================================================
// Spoken confirmation
// ===========================================================================================================================

private fun languageCode(context: Context): String = context.resources.configuration.locales.let { if (it.isEmpty) "en" else it[0].language }

/** Speaks the sentence once with Android's own text-to-speech, in Hindi or Telugu if the voice is installed, else English. */
fun speakPaymentConfirmation(context: Context, amount: String, payee: String?) {
    val app = context.applicationContext
    var lang = languageCode(app).takeIf { it in setOf("hi", "te") } ?: "en"
    lateinit var tts: TextToSpeech
    tts = TextToSpeech(app) { status ->
        if (status != TextToSpeech.SUCCESS) { tts.shutdown(); return@TextToSpeech }
        if (lang != "en" && tts.isLanguageAvailable(Locale(lang)) < TextToSpeech.LANG_AVAILABLE) lang = "en"
        tts.language = if (lang == "en") Locale.ENGLISH else Locale(lang)
        val text = SpokenConfirmation.message(amount, if (SpokenConfirmation.sayName(app)) payee else null, lang)
        if (text == null) { tts.shutdown(); return@TextToSpeech }
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { tts.shutdown() }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { tts.shutdown() }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "v4sh-paid")
    }
}

/** On the result screen: speaks once, only when the owner turned it on and the carrier itself confirmed the payment. */
@Composable
fun SpeakOnSuccess(success: Boolean, amount: String, payee: String?) {
    val context = LocalContext.current
    var spoken by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(success) {
        if (success && !spoken && SpokenConfirmation.enabled(context)) { spoken = true; speakPaymentConfirmation(context, amount, payee) }
    }
}

@Composable
fun SpokenConfirmationScreen(navController: NavHostController) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(SpokenConfirmation.enabled(context)) }
    var name by remember { mutableStateOf(SpokenConfirmation.sayName(context)) }
    Scaffold(topBar = { V4shTopBar(title = "Spoken confirmation", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Hear your payment", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("After the carrier itself confirms a payment, V4SH says it aloud, like a shop's sound box. It is spoken in Hindi or Telugu if you chose that language and the voice is installed on your phone.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Speak the amount when paid", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { on = it; SpokenConfirmation.setEnabled(context, it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Also say the receiver's name", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                            Text("Off by default: people near you can hear it.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                        }
                        Switch(checked = name, enabled = on, onCheckedChange = { name = it; SpokenConfirmation.setSayName(context, it) })
                    }
                    OutlinedButton(onClick = { speakPaymentConfirmation(context, "10.50", "Demo Shop") }, shape = largeCardShape) { Text("Hear an example") }
                }
            }
            Text("It only speaks for a payment the carrier reported as successful. It never speaks your PIN, balance or UPI ID.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
        }
    }
}

// ===========================================================================================================================
// Split a bill
// ===========================================================================================================================

@Composable
fun SplitBillScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    RefreshHistoryOnResume(paymentViewModel)
    var total by remember { mutableStateOf(SplitSession.total) }
    var rows by remember { mutableStateOf(SplitSession.rows) }
    fun save() { SplitSession.total = total; SplitSession.rows = rows }

    val totalValue = BillSplit.amountText(total)?.let { BigDecimal(it) }
    fun resplit(count: Int) {
        val shares = totalValue?.let { BillSplit.equalShares(it, count) }
        rows = List(count) { i -> rows.getOrNull(i)?.copy(amount = shares?.get(i)?.toPlainString() ?: rows.getOrNull(i)?.amount.orEmpty()) ?: SplitRow(i, amount = shares?.get(i)?.toPlainString().orEmpty()) }
        save()
    }
    val facts = paymentViewModel.transactionLedger
    fun paid(r: SplitRow): Boolean = facts.any { rec ->
        rec.carrierOutcome == "SUCCESS" && rec.payeeVpa.equals(r.vpa.trim(), true) && rec.amount == r.amount &&
            runCatching { java.time.Instant.parse(rec.timestamp).toEpochMilli() >= SplitSession.startedAt }.getOrDefault(false)
    }
    val shareSum = rows.mapNotNull { BillSplit.amountText(it.amount)?.let(::BigDecimal) }.let { BillSplit.sum(it) }
    val paidCount = rows.count { paid(it) }

    Scaffold(topBar = { V4shTopBar(title = "Split a bill", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Split a bill", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("Share one amount between people you pay. You pay each share one at a time, and enter your UPI PIN for each on the carrier's screen. A share counts as paid only when the carrier confirms it.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            OutlinedTextField(total, { total = it.filter { c -> c.isDigit() || c == '.' }.take(10); save() }, singleLine = true, label = { Text("Total amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                isError = total.isNotEmpty() && totalValue == null)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("People to pay: ${rows.size}", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { if (rows.size > 2) resplit(rows.size - 1) }, enabled = rows.size > 2, shape = largeCardShape) { Text("−") }
                OutlinedButton(onClick = { if (rows.size < 10) resplit(maxOf(rows.size + 1, 2)) }, enabled = rows.size < 10, shape = largeCardShape) { Text("+") }
            }
            if (rows.isEmpty()) Button(onClick = { resplit(2) }, enabled = totalValue != null, shape = largeCardShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth()) { Text("Split between 2 people") }
            rows.forEachIndexed { i, r ->
                val vpaOk = PaymentInputValidator.normalizedVpa(r.vpa) != null
                val amountOk = BillSplit.amountText(r.amount) != null
                val isPaid = paid(r)
                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Person ${i + 1}", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            if (isPaid) Text("Paid ✓", style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF2B4C3F), fontWeight = FontWeight.Bold))
                        }
                        OutlinedTextField(r.vpa, { v -> rows = rows.toMutableList().also { it[i] = r.copy(vpa = v.trim()) }; save() }, singleLine = true, label = { Text("UPI ID") },
                            isError = r.vpa.isNotEmpty() && !vpaOk, enabled = !isPaid, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(r.amount, { v -> rows = rows.toMutableList().also { it[i] = r.copy(amount = v.filter { c -> c.isDigit() || c == '.' }.take(10)) }; save() },
                            singleLine = true, label = { Text("Share (₹)") }, isError = r.amount.isNotEmpty() && !amountOk, enabled = !isPaid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            save()
                            SharedPaymentState.clear()
                            SharedPaymentState.selectRecipient(r.vpa.trim(), BillSplit.amountText(r.amount).orEmpty(), "", r.name.takeIf { it.isNotBlank() })
                            navController.navigate("amount_entry")
                        }, enabled = vpaOk && amountOk && !isPaid, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth()) {
                            Text(if (isPaid) "Paid" else "Pay this share")
                        }
                    }
                }
            }
            if (rows.isNotEmpty()) {
                val matches = totalValue != null && shareSum.compareTo(totalValue) == 0
                Text("Shares add up to ₹${shareSum.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}" +
                    (if (totalValue == null) "" else if (matches) " — matches the total." else " — the total is ₹${totalValue.setScale(2).toPlainString()}."),
                    style = MaterialTheme.typography.bodyMedium.copy(color = if (matches) Color(0xFF2B4C3F) else Color(0xFF8C5815)))
                Text("$paidCount of ${rows.size} paid", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                TextButton(onClick = { SplitSession.reset(); total = ""; rows = emptyList() }) { Text("Start a new split") }
            }
        }
    }
}

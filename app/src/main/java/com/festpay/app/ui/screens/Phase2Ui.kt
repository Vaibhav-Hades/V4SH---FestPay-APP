package com.festpay.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.festpay.app.data.*
import com.festpay.app.ui.viewmodel.PaymentViewModel
import com.festpay.app.ui.viewmodel.TransactionRecord
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

fun TransactionRecord.toFact() = PaymentFact(timestamp, payeeName, payeeVpa, amount, carrierOutcome, bankReference, localTransactionId,
    UiPresentation.carrierOutcomeLabel(carrierOutcome) ?: state.name.lowercase().replaceFirstChar { it.uppercase() })

private fun paymentFacts(vm: PaymentViewModel) = vm.transactionLedger.filter { it.operation == "PAYMENT" }.map { it.toFact() }

/** Draws the receipt on a plain bitmap, saves it to the cache and opens Android's share sheet. The owner chooses where it goes. */
fun shareReceipt(context: Context, fact: PaymentFact) {
    val receipt = ReceiptModel.of(fact, UiPresentation.shortTimestamp(fact.timestamp)) ?: return
    val w = 1080; val rowH = 110; val h = 420 + receipt.rows.size * rowH + 220
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(AColor.parseColor("#FFF8F2"))
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#3D0C14"); textSize = 64f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#7A6A60"); textSize = 38f; typeface = Typeface.SERIF }
    val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#1B1B1B"); textSize = 46f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#7A6A60"); textSize = 32f; typeface = Typeface.SERIF }
    c.drawText("V4SH", 60f, 120f, label)
    c.drawText(receipt.title, 60f, 230f, title)
    var y = 360f
    receipt.rows.forEach { (k, v) ->
        c.drawText(k, 60f, y, label)
        c.drawText(v.take(34), 60f, y + 52f, value)
        y += rowH
    }
    y += 40f
    receipt.footer.chunked(52).forEach { line -> c.drawText(line, 60f, y, small); y += 42f }
    val dir = File(context.cacheDir, "receipts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val file = File(dir, "v4sh-receipt.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun ReceiptButton(record: TransactionRecord) {
    if (record.carrierOutcome != "SUCCESS") return
    val context = LocalContext.current
    OutlinedButton(onClick = { shareReceipt(context, record.toFact()) }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Share receipt") }
}

@Composable
fun SpendingScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    val facts = paymentFacts(paymentViewModel)
    val summary = remember(facts) { SpendingSummary.of(facts) }
    var note by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) { note = "Export cancelled."; return@rememberLauncherForActivityResult }
        note = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(HistoryExport.csv(facts).toByteArray(Charsets.UTF_8)) } ?: error("no stream")
            "Saved ${facts.size} payments."
        }.getOrElse { "Could not save the file." }
    }
    Scaffold(topBar = { V4shTopBar(title = "Spending", onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().background(SurfaceCream).padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = spacing, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your spending", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
            Text("Counts only payments the carrier itself reported as successful. Worked out on this phone; not a bank statement.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("By month", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    if (summary.months.isEmpty()) Text("No successful payments yet.", style = MaterialTheme.typography.bodySmall)
                    summary.months.forEach { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) + " · ${m.count}", style = MaterialTheme.typography.bodyMedium)
                            Text("₹" + m.total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paid most", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    if (summary.topPayees.isEmpty()) Text("Nobody yet.", style = MaterialTheme.typography.bodySmall)
                    summary.topPayees.forEach { p ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name.ifBlank { p.vpa } + " · ${p.count}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("₹" + p.total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Export history", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    Text("Saves a CSV file of all your V4SH payments where you choose. The file is NOT encrypted: anyone who opens it can read the names, UPI IDs and amounts. V4SH cannot import it back.",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    Button(onClick = { export.launch("v4sh-history.csv") }, enabled = facts.isNotEmpty(), shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text("Export CSV") }
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = PrimaryBurgundy)) }
                }
            }
        }
    }
}

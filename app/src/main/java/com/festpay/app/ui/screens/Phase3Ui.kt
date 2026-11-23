package com.festpay.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.festpay.app.data.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The same routing the camera scanner uses, for a code that came from a picture or a pasted link. */
fun routeScannedUpi(raw: String, navController: NavHostController, scope: CoroutineScope) {
    when (val result = parseAndValidateUpiQr(raw)) {
        is UpiParseResult.Valid -> {
            val ticket = SharedPaymentState.selectRecipient(result.pa, result.am.orEmpty(), SharedPaymentState.paymentNote, result.pn)
            scope.launch {
                SharedPaymentState.resolve(ticket)
                if (SharedPaymentState.selectionId == ticket.requestId) navController.navigate("qr_recipient")
            }
        }
        is UpiParseResult.MissingDetails -> navController.navigate("payment_details_missing")
        is UpiParseResult.UnsupportedFormat -> navController.navigate("unsupported_qr_code")
        else -> navController.navigate("qr_code_not_recognized")
    }
}

/** Pick a screenshot or photo of a UPI QR. The system picker is used, so V4SH gets only the one picture the owner chooses. */
@Composable
fun GalleryScanButton(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (image == null) { message = "Could not open that picture."; scanner.close(); return@rememberLauncherForActivityResult }
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val raw = codes.firstNotNullOfOrNull { it.rawValue }
                if (raw == null) message = "No QR code found in that picture." else { message = null; routeScannedUpi(raw, navController, scope) }
            }
            .addOnFailureListener { message = "Could not read that picture." }
            .addOnCompleteListener { scanner.close() }
    }
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { message = null; picker.launch("image/*") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Upload QR from gallery")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** On the UPI ID screen: fills in a copied UPI ID, or a copied upi:// link goes the same way as a scanned code. */
@Composable
fun PasteUpiButton(navController: NavHostController, onVpa: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Column {
        OutlinedButton(onClick = {
            val clip = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
            when (val p = ClipboardPayee.parse(text)) {
                is PastedPayee.Vpa -> { message = null; onVpa(p.vpa) }
                is PastedPayee.Link -> { message = null; routeScannedUpi(p.raw, navController, scope) }
                PastedPayee.Nothing -> message = "Nothing copied looks like a UPI ID. Copy it first, then tap Paste."
            }
        }, shape = largeCardShape) { Text("Paste copied UPI ID or link") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** A plain sentence for a technical failure, with the fix button when there is one. Shows nothing when there is no plain wording. */
@Composable
fun ErrorHelpBlock(message: String?, onFix: (IssueFix) -> Unit) {
    val plain = ErrorHelp.friendly(message) ?: return
    Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plain.sentence, style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBurgundy))
            val label = when (plain.fix) {
                IssueFix.ACCESSIBILITY_SETTINGS -> "Open Accessibility settings"
                IssueFix.PHONE_PERMISSION -> "Allow phone calls"
                IssueFix.STOP_STUCK_OPERATION -> "Stop the stuck session"
                else -> null
            }
            if (label != null) Button(onClick = { onFix(plain.fix) }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)) { Text(label) }
        }
    }
}

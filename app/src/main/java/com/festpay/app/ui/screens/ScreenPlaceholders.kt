// File: ScreenPlaceholders.kt
package com.festpay.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.festpay.app.ui.theme.V4SHTheme
import com.festpay.app.ui.viewmodel.PaymentScenario
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.PaymentViewModel
import com.festpay.app.ui.viewmodel.TransactionRecord
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.net.URLDecoder

// ----- Design System Colors & Constants -----
private val PrimaryBurgundy = Color(0xFF3D0C1A)
private val SecondaryUmber = Color(0xFF8C6A58)
private val SurfaceCream = Color(0xFFFFF8F0)
private val SurfaceCreamAlt = Color(0xFFF9F9F9)
private val SurfaceWelcomeGradientStart = Color(0xFFFFF9F5)
private val SurfaceWelcomeGradientEnd = Color(0xFFFAF1EB)
private val SurfaceCardLowest = Color(0xFFFFFFFF)
private val SurfaceCardLow = Color(0xFFF3F3F4)
private val SurfaceContainerHigh = Color(0xFFE8E8E8)
private val OnSurfaceDark = Color(0xFF1A1C1C)
private val OnSurfaceVariant = Color(0xFF524345)
private val OutlineBorder = Color(0xFFEFE7DE)
private val OutlineBorderWarm = Color(0xFFE8DDD2)

private val cardShape = RoundedCornerShape(12.dp)
private val largeCardShape = RoundedCornerShape(16.dp)
private val pillShape = RoundedCornerShape(9999.dp)
private val spacing = 20.dp

// Global developer debug mode state
var isDebugControlsVisible by mutableStateOf(false)

// ----- Shared Payment State for Scanned Data -----
object SharedPaymentState {
    var payeeVpa: String = "aarav@upi"
    var payeeName: String = "Aarav Sharma"
    var paymentAmount: String = "500"
    var paymentNote: String = "College festival food stall"
}

// ----- UPI URI Parser & Validator -----
sealed class UpiParseResult {
    data class Valid(val pa: String, val pn: String?, val am: String?, val cu: String?, val mc: String?) : UpiParseResult()
    object MissingDetails : UpiParseResult()
    object InvalidFormat : UpiParseResult()
    object UnsupportedFormat : UpiParseResult()
    object NonUpiQr : UpiParseResult()
}

fun parseAndValidateUpiQr(rawValue: String): UpiParseResult {
    val trimmed = rawValue.trim()
    Log.d("V4SH_QR", "QR detected and evaluated")

    val uri = try {
        Uri.parse(trimmed)
    } catch (e: Exception) {
        null
    }

    val isUpiUri = uri?.scheme?.lowercase() == "upi" || trimmed.lowercase().startsWith("upi://") || trimmed.contains("pa=")
    if (!isUpiUri) {
        Log.d("V4SH_QR", "QR rejected: non-UPI or unrecognized format")
        return UpiParseResult.NonUpiQr
    }

    val pa = uri?.getQueryParameter("pa") ?: extractParam(trimmed, "pa")
    val pn = uri?.getQueryParameter("pn") ?: extractParam(trimmed, "pn")
    val am = uri?.getQueryParameter("am") ?: extractParam(trimmed, "am")
    val cu = uri?.getQueryParameter("cu") ?: extractParam(trimmed, "cu")
    val mc = uri?.getQueryParameter("mc") ?: extractParam(trimmed, "mc")

    if (pa.isNullOrBlank()) {
        Log.d("V4SH_QR", "QR rejected: missing payee VPA")
        return UpiParseResult.MissingDetails
    }

    if (!pa.contains("@")) {
        Log.d("V4SH_QR", "QR rejected: invalid VPA format")
        return UpiParseResult.InvalidFormat
    }

    if (cu != null && cu.uppercase() != "INR") {
        Log.d("V4SH_QR", "QR rejected: unsupported currency")
        return UpiParseResult.UnsupportedFormat
    }

    Log.d("V4SH_QR", "UPI QR validated successfully")
    return UpiParseResult.Valid(pa = pa.trim(), pn = pn?.trim(), am = am?.trim(), cu = cu?.trim(), mc = mc?.trim())
}

private fun extractParam(url: String, paramName: String): String? {
    return try {
        val regex = Regex("$paramName=([^&]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(url)
        match?.groupValues?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
    } catch (_: Exception) {
        null
    }
}

// ---------- Shared Top App Bar Component ----------
@Composable
private fun V4shTopBar(
    title: String = "",
    onBack: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(color = SurfaceCream, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = spacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBurgundy)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryBurgundy),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
                if (title.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                }
            }
            if (onAction != null) {
                IconButton(onClick = onAction) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = SecondaryUmber)
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
        }
    }
}

// ==========================================
// TOP-LEVEL & SETUP SCREENS
// ==========================================

@Composable
fun HomeScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        paymentViewModel.initRepository(context)
    }

    V4SHTheme {
        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCreamAlt)
                    .padding(innerPadding)
                    .padding(horizontal = spacing),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Good morning, ${paymentViewModel.userName.split(" ").firstOrNull() ?: "Vaibhav"}.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SecondaryUmber,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            // Network Status Pill
                            Surface(
                                shape = pillShape,
                                color = Color(0xFFEBF2EE)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2B4C3F)))
                                    Text(
                                        text = "Mobile Network Active",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2B4C3F), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "What would you like to do?",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = PrimaryBurgundy,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }

                item {
                    Card(
                        shape = largeCardShape,
                        colors = CardDefaults.cardColors(containerColor = PrimaryBurgundy),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("qr_scanner") }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(cardShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan & Pay",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Scan & Pay",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "Scan any UPI QR code",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            shape = largeCardShape,
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(142.dp)
                                .clickable { navController.navigate("upi_details") }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = PrimaryBurgundy)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryUmber)
                                }
                                Column {
                                    Text("Pay with UPI ID", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                    Text("Enter a UPI ID", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                                }
                            }
                        }

                        Card(
                            shape = largeCardShape,
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(142.dp)
                                .clickable { navController.navigate("receive_money") }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = PrimaryBurgundy)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryUmber)
                                }
                                Column {
                                    Text("Receive Money", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                    Text("Show your UPI QR", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Activity", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.SemiBold))
                        TextButton(onClick = { navController.navigate("transactions") }) {
                            Text("View all", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }

                val recentLedger = paymentViewModel.transactionLedger.take(5)
                if (recentLedger.isEmpty()) {
                    item {
                        Card(
                            shape = largeCardShape,
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = SecondaryUmber, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No recent payment activity", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                Text("Scan a UPI QR code to make your first payment.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                } else {
                    items(recentLedger) { record ->
                        TransactionRecordRowItem(record)
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TransactionRecordRowItem(item: TransactionRecord) {
    Card(
        shape = largeCardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceCardLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.payeeName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = PrimaryBurgundy), maxLines = 1)
                    Text(item.timestamp, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${item.amount}", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                val (bg, txt, label) = when (item.state) {
                    PaymentState.SUCCESS -> Triple(Color(0xFFEBF2EE), Color(0xFF2B4C3F), "Confirmed & Settled")
                    PaymentState.FAILED -> Triple(Color(0xFFFBEBEB), Color(0xFF93000A), "Failed")
                    else -> Triple(Color(0xFFFBF3E8), Color(0xFF8C5815), "Payment status unclear")
                }
                Surface(shape = RoundedCornerShape(6.dp), color = bg, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = label,
                        color = txt,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionsScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        paymentViewModel.initRepository(context)
    }

    V4SHTheme {
        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCreamAlt)
                    .padding(innerPadding)
                    .padding(horizontal = spacing),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Transactions", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                    Text(text = "Payments initiated through V4SH", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                if (paymentViewModel.transactionLedger.isEmpty()) {
                    item {
                        Card(
                            shape = largeCardShape,
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = SecondaryUmber, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No transactions initiated", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                Text("Transactions initiated through V4SH will appear here.", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                } else {
                    items(paymentViewModel.transactionLedger) { record ->
                        TransactionRecordRowItem(record)
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun ReceiveMoneyScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        paymentViewModel.initRepository(context)
    }

    V4SHTheme {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Receive Money", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text(text = "Show your UPI QR code to receive payments.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                Card(
                    shape = largeCardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(200.dp).clip(cardShape).background(SurfaceCardLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(120.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(paymentViewModel.userName, style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                        Text(paymentViewModel.userVpa, style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Explicit Non-Custodial Disclaimer
                Surface(
                    shape = cardShape,
                    color = Color(0xFFFFD4BE).copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Payments go to your linked UPI account. V4SH does not hold your money.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDark)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        paymentViewModel.initRepository(context)
    }
    var tapCount by remember { mutableStateOf(0) }

    V4SHTheme {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Profile", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                    // Secret discreet tap to toggle developer controls
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                tapCount++
                                if (tapCount >= 3) {
                                    isDebugControlsVisible = !isDebugControlsVisible
                                    tapCount = 0
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("V", style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    }
                }
                Text(text = "Manage your V4SH account and preferences.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                // User Identity Card
                Card(
                    shape = largeCardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(PrimaryBurgundy),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Column {
                                Text(paymentViewModel.userName, style = MaterialTheme.typography.headlineSmall.copy(color = OnSurfaceDark, fontWeight = FontWeight.Bold))
                                Text(paymentViewModel.userVpa, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Group 1: Payment Account
                Text("PAYMENT ACCOUNT", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "UPI Details", subtitle = "Your linked UPI ID & handle", icon = Icons.Default.AlternateEmail) {
                    navController.navigate("upi_details")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Group 2: Security & Privacy
                Text("SECURITY & PRIVACY", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileMenuCard(title = "App Security", subtitle = "Authentication & protection controls", icon = Icons.Default.Shield) {
                        navController.navigate("app_security")
                    }
                    ProfileMenuCard(title = "Privacy", subtitle = "Local data & privacy safeguards", icon = Icons.Default.Lock) {
                        navController.navigate("privacy")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Group 3: Help & Support
                Text("HELP & SUPPORT", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileMenuCard(title = "Help Center", subtitle = "Guides on payment, QR & network", icon = Icons.Default.HelpOutline) {
                        navController.navigate("help_center")
                    }
                    ProfileMenuCard(title = "Contact Support", subtitle = "Get assistance with V4SH", icon = Icons.Default.SupportAgent) {
                        navController.navigate("contact_support")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Group 4: About V4SH
                Card(
                    shape = largeCardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryBurgundy),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("V", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Column {
                                Text("V4SH Fintech Interface", style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                Text("Version 1.0.0 (Release Candidate)", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "V4SH is an interface layer for your UPI payments. Your payment provider and issuing bank remain exclusively responsible for authenticating and settling transactions.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = OnSurfaceVariant)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileMenuCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceCardLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.labelLarge.copy(color = OnSurfaceDark, fontWeight = FontWeight.Bold))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryUmber)
        }
    }
}

@Composable
fun UpiDetailsScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        paymentViewModel.initRepository(context)
    }

    var vpaInput by remember { mutableStateOf(paymentViewModel.userVpa) }
    var nameInput by remember { mutableStateOf(paymentViewModel.userName) }
    var isSaved by remember { mutableStateOf(false) }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "UPI Details", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Your UPI details", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text(text = "Configure your linked UPI payment handle.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("YMS Vaibhav") },
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = vpaInput,
                    onValueChange = { vpaInput = it },
                    label = { Text("UPI ID / VPA") },
                    placeholder = { Text("username@upi") },
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (vpaInput.contains("@") && nameInput.isNotBlank()) {
                            paymentViewModel.saveUserProfile(context, vpaInput.trim(), nameInput.trim())
                            isSaved = true
                        }
                    },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (isSaved) "UPI Details Saved" else "Save UPI Details")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = cardShape,
                    color = Color(0xFFFFD4BE).copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(20.dp))
                        Text(
                            text = "V4SH does not hold your money or create a wallet balance. Your UPI ID links directly to your bank account.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDark)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// ACCOUNT & INFORMATION SCREENS
// ==========================================

@Composable
fun AppSecurityScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "App Security", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("App Security", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Control how V4SH protects access to your personal payment interface.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD4BE).copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryBurgundy)
                            Text("Security Status: Protected", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your banking and payment credentials are never collected, processed, or retained in V4SH.", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("SECURITY CONTROLS", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.ScreenLockPortrait, contentDescription = null, tint = PrimaryBurgundy)
                            Column {
                                Text("Device Screen Lock", style = MaterialTheme.typography.labelLarge.copy(color = OnSurfaceDark, fontWeight = FontWeight.Bold))
                                Text("V4SH uses system passcode or biometric gate to protect app access.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("PAYMENT AUTHENTICATION", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Authentication happens through your payment provider", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("V4SH does not collect, store, or simulate your UPI PIN or banking passwords. V4SH will never ask for your UPI PIN, banking password, card PIN, CVV, or OTP. Every transaction is authenticated securely inside your payment provider's boundary.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PrivacyScreen(navController: NavHostController) {
    var clearedText by remember { mutableStateOf(false) }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Privacy", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Privacy", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Understand what V4SH stores and how your transaction experience remains within your personal control.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Your privacy comes first", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("V4SH is an interface layer for your UPI payments. We only use the minimum information needed to provide a graceful app experience on your device.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("DATA STORED LOCALLY", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = PrimaryBurgundy)
                            Column {
                                Text("Payment activity", style = MaterialTheme.typography.labelLarge.copy(color = OnSurfaceDark, fontWeight = FontWeight.Bold))
                                Text("Records of payment attempts initiated through V4SH, kept for your personal ledger.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("NEVER STORED BY V4SH", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD4BE).copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("• UPI PIN\n• Banking passwords\n• Card PINs & CVV\n• One-Time Passwords (OTP)", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark, fontWeight = FontWeight.Medium))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = { clearedText = true },
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (clearedText) "Local activity refreshed" else "Clear local activity cache", color = PrimaryBurgundy)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun HelpCenterScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Help Center", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("How can we help?", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Find answers about payments, QR scanning, and connectivity.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                HelpTopicCard("Making a payment", "V4SH provides a camera scanner for standard UPI QR codes. Verify payee name and amount before continuing.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("Payment status unclear", "Check your official bank SMS or ledger before re-initiating to avoid duplicate payments.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("No mobile network", "V4SH requires active mobile network connectivity to communicate payment requests.")

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { navController.navigate("contact_support") },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Contact Support")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HelpTopicCard(title: String, body: String) {
    Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
        }
    }
}

@Composable
fun ContactSupportScreen(navController: NavHostController) {
    var issueDesc by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Contact Support", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Need a hand?", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Tell us what happened and we'll help you understand your V4SH payment experience.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                OutlinedTextField(
                    value = issueDesc,
                    onValueChange = { issueDesc = it },
                    label = { Text("Describe the issue") },
                    placeholder = { Text("Tell us what happened...") },
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD4BE).copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBurgundy)
                        Text("Never share sensitive credentials. V4SH support will never ask for your UPI PIN or OTP.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDark))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { submitted = true },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (submitted) "Support Request Logged" else "Submit Support Request")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun NoMobileNetworkScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Connectivity", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(SecondaryUmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SignalCellularConnectedNoInternet0Bar, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Mobile Network", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("V4SH needs a mobile network connection to communicate with the payment service.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No payment was started", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("V4SH could not begin the payment flow because a mobile network connection was unavailable.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { navController.popBackStack() },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Check Connection")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Try Again", color = PrimaryBurgundy)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun WelcomeSetupScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Welcome")
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(SurfaceWelcomeGradientStart, SurfaceWelcomeGradientEnd, Color(0xFFFAF1EB))))
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Welcome to V4SH", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                Text("Your UPI. Your account. Your payment.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 32.dp))

                Button(
                    onClick = { navController.navigate("payment_readiness") },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Check setup")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { navController.navigate("home") },
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("SET UP LATER", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun PaymentReadinessScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Payment Readiness", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Payment Readiness", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Verify system readiness before making payments.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Button(onClick = { navController.navigate("payment_access_setup") }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Complete setup")
                }
            }
        }
    }
}

@Composable
fun PaymentAccessSetupScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Payment Access", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Payment Access Setup", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Connect V4SH to your payment flow.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Button(onClick = { navController.navigate("payment_sim_selection") }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Open Android settings")
                }
            }
        }
    }
}

@Composable
fun PaymentSimSelectionScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "SIM Selection", onBack = { navController.popBackStack() })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Choose payment SIM", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Select the bank-registered SIM for USSD execution.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Button(onClick = { navController.navigate("home") }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Confirm SIM")
                }
            }
        }
    }
}


// ==========================================
// BATCH 3 & 4: QR SCANNING & PAYMENT ENGINE
// ==========================================

@Composable
fun QrScannerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            navController.navigate("camera_access_required")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scannedOnce by remember { mutableStateOf(false) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(hasCameraPermission) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {}
        }
    }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Scan & Pay", onBack = { navController.popBackStack() }, onAction = { /* Help */ })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Column {
                        Text("Scan & Pay", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                        Text("Scan any UPI QR code", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 2.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(largeCardShape)
                        .background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val executor = ContextCompat.getMainExecutor(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalyzer = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(executor) { imageProxy ->
                                                processImageProxy(imageProxy) { rawValue ->
                                                    if (!scannedOnce) {
                                                        scannedOnce = true
                                                        when (val result = parseAndValidateUpiQr(rawValue)) {
                                                            is UpiParseResult.Valid -> {
                                                                SharedPaymentState.payeeVpa = result.pa
                                                                SharedPaymentState.payeeName = result.pn ?: "UPI Merchant"
                                                                if (!result.am.isNullOrEmpty()) {
                                                                    SharedPaymentState.paymentAmount = result.am
                                                                }
                                                                navController.navigate("qr_recipient")
                                                            }
                                                            is UpiParseResult.MissingDetails -> {
                                                                navController.navigate("payment_details_missing")
                                                            }
                                                            is UpiParseResult.UnsupportedFormat -> {
                                                                navController.navigate("unsupported_qr_code")
                                                            }
                                                            is UpiParseResult.InvalidFormat,
                                                            is UpiParseResult.NonUpiQr -> {
                                                                navController.navigate("qr_code_not_recognized")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalyzer
                                        )
                                    } catch (e: Exception) {
                                        Log.e("V4SH_QR", "Camera binding failed", e)
                                    }
                                }, executor)
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .border(BorderStroke(3.dp, PrimaryBurgundy), largeCardShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(2.dp)
                                .background(PrimaryBurgundy)
                        )
                    }
                    Surface(
                        shape = pillShape,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (hasCameraPermission) "Camera Active" else "Permission Required",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SecondaryUmber),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Align the QR code inside the frame", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Medium))
                Text("Keep the QR code steady", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 2.dp))

                // Developer Test Error Controls (Isolated under debug trigger)
                if (isDebugControlsVisible) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = {
                                if (!scannedOnce) {
                                    scannedOnce = true
                                    SharedPaymentState.payeeVpa = "aarav@upi"
                                    SharedPaymentState.payeeName = "Aarav Sharma"
                                    SharedPaymentState.paymentAmount = "500"
                                    navController.navigate("qr_recipient")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy)
                        ) {
                            Text("Simulate Success")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Simulate QR Error States:", style = MaterialTheme.typography.labelMedium.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { navController.navigate("camera_access_required") }, modifier = Modifier.weight(1f)) { Text("Camera Error", fontSize = 11.sp) }
                        OutlinedButton(onClick = { navController.navigate("qr_code_not_recognized") }, modifier = Modifier.weight(1f)) { Text("Unrecognized", fontSize = 11.sp) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { navController.navigate("unsupported_qr_code") }, modifier = Modifier.weight(1f)) { Text("Unsupported", fontSize = 11.sp) }
                        OutlinedButton(onClick = { navController.navigate("payment_details_missing") }, modifier = Modifier.weight(1f)) { Text("Missing Data", fontSize = 11.sp) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy, onBarcodeDetected: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onBarcodeDetected(value)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {
                Log.e("V4SH_QR", "Barcode scan failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun CameraAccessRequiredScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "Camera Error", onBack = { navController.popBackStack() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Surface(shape = pillShape, color = Color(0xFFFFD4BE).copy(alpha = 0.5f)) {
                    Text("DEVICE PERMISSIONS · CAMERA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Camera access required", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                Text("V4SH needs camera access to scan a UPI QR code.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Enable camera in Android settings", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. Tap 'Allow camera access' below or open device Settings", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                        Text("2. Select App permissions → Camera", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                        Text("3. Choose 'Allow only while using the app'", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = { navController.popBackStack() }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Allow camera access")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { navController.navigate("upi_details") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Enter UPI ID instead", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun QrCodeNotRecognizedScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "QR Error", onBack = { navController.popBackStack() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("QR code not recognized", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                Text("We couldn’t recognize this QR code. Make sure the entire code is visible and try scanning again.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDBCA).copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No payment was started", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        Text("V4SH could not read a recipient from this QR code. No payment has been initiated or deducted.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = { navController.popBackStack() }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Scan again")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { navController.popBackStack() }, shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Go back", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun UnsupportedQrCodeScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "QR Error", onBack = { navController.popBackStack() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Unsupported QR code", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                Text("This QR code isn’t a supported UPI payment QR.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Supported QR formats", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy))
                        Text("V4SH supports standard Bharat QR and UPI payment codes issued by banks, verified merchants, and individuals.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = { navController.popBackStack() }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Scan another QR")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { navController.popBackStack() }, shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Go back", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun PaymentDetailsMissingScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "QR Error", onBack = { navController.popBackStack() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(horizontal = spacing).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Payment details missing", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                Text("Some payment details are missing from this QR code. Scan a complete UPI payment QR or enter the UPI ID manually.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("QR read summary", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy))
                        Text("• Recipient VPA / UPI ID: Missing\n• Payee name: Unspecified\n• Routing info: Incomplete", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = { navController.popBackStack() }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Scan again")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { navController.popBackStack() }, shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Go back", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun QrRecipientScreen(navController: NavHostController) {
    val payeeName = SharedPaymentState.payeeName
    val payeeVpa = SharedPaymentState.payeeVpa

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Recipient", onBack = { navController.popBackStack() }, onAction = { /* Help */ })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Recipient", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text(text = "Review the payment destination before continuing.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(
                    shape = largeCardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF9F2EB))
                                    .border(1.dp, OutlineBorderWarm, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = payeeName.take(2).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold)
                                )
                            }
                            Column {
                                Text(text = payeeName, style = MaterialTheme.typography.headlineSmall.copy(color = OnSurfaceDark, fontWeight = FontWeight.Bold))
                                Text(text = "STANDARD UPI PAYEE", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, letterSpacing = 1.sp))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = OutlineBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(text = "UPI ID", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(shape = cardShape, color = Color(0xFFFDFCFA), border = BorderStroke(1.dp, OutlineBorder)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = payeeVpa, style = MaterialTheme.typography.bodyLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Medium))
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SecondaryUmber, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F1EA)), border = BorderStroke(1.dp, Color(0xFFE5DACF)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Scanned from UPI QR", style = MaterialTheme.typography.labelLarge.copy(color = OnSurfaceDark, fontWeight = FontWeight.Bold))
                            Text("Recipient details were read from the scanned QR code.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.navigate("amount_entry") },
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Continue to Amount", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Scan Again", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                    }
                }
            }
        }
    }
}

@Composable
fun AmountEntryScreen(navController: NavHostController) {
    var amount by remember { mutableStateOf(SharedPaymentState.paymentAmount) }
    var note by remember { mutableStateOf(SharedPaymentState.paymentNote) }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val isValidAmount = amountValue > 0.0

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Amount", onBack = { navController.popBackStack() }, onAction = { /* Help */ })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFFD4BE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(SharedPaymentState.payeeName.take(2).uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(SharedPaymentState.payeeName, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp, color = OnSurfaceDark))
                                Surface(shape = pillShape, color = SurfaceContainerHigh) {
                                    Text("Standard UPI", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text(SharedPaymentState.payeeVpa, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "Enter amount", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text(text = "How much would you like to pay?", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                // Editable Amount Field
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amount = newVal
                        }
                    },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Text("₹", style = MaterialTheme.typography.headlineMedium.copy(color = PrimaryBurgundy), modifier = Modifier.padding(start = 12.dp)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = largeCardShape,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBurgundy,
                        unfocusedBorderColor = OutlineBorderWarm,
                        focusedLabelColor = PrimaryBurgundy
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick amount convenience buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf("100", "200", "500", "1000").forEach { preset ->
                        val isSelected = amount == preset
                        Surface(
                            shape = pillShape,
                            color = if (isSelected) PrimaryBurgundy else SurfaceCardLow,
                            modifier = Modifier.padding(horizontal = 4.dp).clickable { amount = preset }
                        ) {
                            Text(
                                text = "₹$preset",
                                style = MaterialTheme.typography.labelMedium.copy(color = if (isSelected) Color.White else OnSurfaceDark, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Add a note (optional)") },
                    placeholder = { Text("What's this payment for?") },
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm)
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isValidAmount) {
                                SharedPaymentState.paymentAmount = amount
                                SharedPaymentState.paymentNote = note
                                navController.navigate("review_payment")
                            }
                        },
                        enabled = isValidAmount,
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy, disabledContainerColor = PrimaryBurgundy.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Continue to Review", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = SecondaryUmber))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewPaymentScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val amount = SharedPaymentState.paymentAmount
    val note = SharedPaymentState.paymentNote
    var isPayButtonEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Review Payment", onBack = { navController.popBackStack() }, onAction = { /* Help */ })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Review payment", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text(text = "Check the details before you pay.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                // Developer Execution Mode Selector (Isolated under debug trigger)
                if (isDebugControlsVisible) {
                    Card(
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E4DA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚙️ Execution Mode (Debug)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isReal = paymentViewModel.isRealUssdMode
                                OutlinedButton(
                                    onClick = { paymentViewModel.isRealUssdMode = false },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (!isReal) PrimaryBurgundy else Color.Transparent,
                                        contentColor = if (!isReal) Color.White else PrimaryBurgundy
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mock Engine", fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = { paymentViewModel.isRealUssdMode = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isReal) PrimaryBurgundy else Color.Transparent,
                                        contentColor = if (isReal) Color.White else PrimaryBurgundy
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Real USSD (*99#)", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    if (!paymentViewModel.isRealUssdMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = cardShape,
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E4DA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🛠️ Dev Test Scenario (Mock Engine)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaymentScenario.entries.forEach { scenario ->
                                        val selected = paymentViewModel.currentScenario == scenario
                                        OutlinedButton(
                                            onClick = { paymentViewModel.currentScenario = scenario },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (selected) PrimaryBurgundy else Color.Transparent,
                                                contentColor = if (selected) Color.White else PrimaryBurgundy
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(scenario.name, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Card(
                    shape = largeCardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(SharedPaymentState.payeeName.take(2).uppercase(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                            }
                            Column {
                                Text(SharedPaymentState.payeeName, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark))
                                Text("Standard UPI Payee", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber))
                                Text(SharedPaymentState.payeeVpa, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Medium))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(shape = cardShape, color = SurfaceCardLow) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(16.dp))
                                Text("Recipient verified from scanned UPI QR", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDark))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = largeCardShape,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AMOUNT", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₹$amount", style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = PrimaryBurgundy))

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = OutlineBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (note.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Note", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                                Text(note, style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark, fontWeight = FontWeight.Medium))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Payment method", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Surface(shape = cardShape, color = SurfaceContainerHigh) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(14.dp))
                                    Text(if (paymentViewModel.isRealUssdMode) "USSD *99#" else "UPI", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = largeCardShape,
                    color = Color(0xFFFFD4BE).copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFFD4BE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("Before you pay", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = OnSurfaceDark))
                            Text("Review the recipient and amount carefully. You will complete authentication through your payment provider.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (isPayButtonEnabled) {
                                isPayButtonEnabled = false
                                navController.navigate("payment_in_progress")
                                paymentViewModel.startPayment(
                                    vpa = SharedPaymentState.payeeVpa,
                                    name = SharedPaymentState.payeeName,
                                    amount = SharedPaymentState.paymentAmount,
                                    ctx = context
                                ) { state ->
                                    isPayButtonEnabled = true
                                    when (state) {
                                        PaymentState.SUCCESS -> navController.navigate("payment_successful") { popUpTo("home") }
                                        PaymentState.FAILED -> navController.navigate("payment_failed")
                                        PaymentState.UNKNOWN -> navController.navigate("payment_status_unclear")
                                        PaymentState.AUTHENTICATION_REQUIRED -> navController.navigate("authentication_required")
                                        else -> {}
                                    }
                                }
                            }
                        },
                        enabled = isPayButtonEnabled,
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Pay ₹$amount", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    TextButton(
                        onClick = {
                            paymentViewModel.markCancelled()
                            navController.navigate("payment_cancelled")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium.copy(color = SecondaryUmber)
                        )
                    }

                    Text("You’ll be asked to authenticate through the payment service.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun PaymentInProgressScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Payment in Progress", onAction = { /* Help */ })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Payment in progress", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Waiting for payment confirmation. Please don't close the app.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 32.dp))

                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBurgundy, strokeWidth = 4.dp, modifier = Modifier.fillMaxSize())
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SharedPaymentState.payeeName, style = MaterialTheme.typography.headlineSmall.copy(color = OnSurfaceDark))
                            Text("₹${SharedPaymentState.paymentAmount}", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                        }
                        Text(SharedPaymentState.payeeVpa, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        paymentViewModel.stopWaiting()
                        navController.navigate("payment_status_unclear")
                    },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardLow),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Stop waiting", color = PrimaryBurgundy, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("This stops V4SH waiting on this screen. It does not cancel a payment already initiated.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = SecondaryUmber), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun PaymentSuccessfulScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val result = paymentViewModel.lastResult
    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Payment Successful")
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(PrimaryBurgundy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Payment successful", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Your payment was completed successfully.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL SETTLED", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold))
                        Text("₹${SharedPaymentState.paymentAmount}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = OutlineBorder)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SharedPaymentState.payeeName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = OnSurfaceDark))
                            Text("Confirmed & Settled", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF2B4C3F), fontWeight = FontWeight.SemiBold))
                        }
                        if (result != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Ref: ${result.localReference}", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(shape = cardShape, color = Color(0xFFFFD4BE).copy(alpha = 0.3f)) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(18.dp))
                        Text("This receipt records a payment initiated through V4SH. It is not a bank statement.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
        }
    }
}

@Composable
fun PaymentFailedScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "Payment Failed") }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF93000A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Payment failed", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("The payment provider confirmed that this payment could not be completed.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SharedPaymentState.payeeName, style = MaterialTheme.typography.headlineSmall.copy(color = OnSurfaceDark))
                            Surface(shape = pillShape, color = Color(0xFFFBEBEB)) {
                                Text("Failed", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF93000A), fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        Text("₹${SharedPaymentState.paymentAmount}", style = MaterialTheme.typography.displayMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { navController.popBackStack() },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Try again", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Go Home", style = MaterialTheme.typography.labelMedium.copy(color = SecondaryUmber))
                }
            }
        }
    }
}

@Composable
fun PaymentStatusUnclearScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    var checkingStatus by remember { mutableStateOf(false) }
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "Payment Status") }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(SecondaryUmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Payment status unclear", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("We couldn't confirm whether this payment was completed.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF3E8)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Don't pay again yet", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        Text("Your payment may already have been processed. Check the payment status before starting another payment.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        checkingStatus = true
                        paymentViewModel.checkStatus { state ->
                            checkingStatus = false
                            if (state == PaymentState.SUCCESS) {
                                navController.navigate("payment_successful") { popUpTo("home") }
                            }
                        }
                    },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (checkingStatus) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check Payment Status", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { navController.navigate("transactions") },
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("View Transactions", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun PaymentCancelledScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "Payment Cancelled") }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Payment cancelled", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("The payment flow was stopped before the payment was initiated.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Button(
                    onClick = { navController.popBackStack() },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Return to Payment", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Go Home", color = PrimaryBurgundy)
                }
            }
        }
    }
}

@Composable
fun AuthenticationRequiredScreen(navController: NavHostController) {
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "Authentication Required") }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(PrimaryBurgundy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Authentication required", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                Text("Please complete PIN entry on your carrier menu. V4SH has stopped automation for your security.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Security Safeguard", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        Text("V4SH never enters, stores, or automates your UPI PIN. Complete authentication securely on your carrier USSD prompt.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
        }
    }
}

@Composable
fun TransactionDetailsScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val lastRecord = paymentViewModel.transactionLedger.lastOrNull()
    V4SHTheme {
        Scaffold(
            topBar = { V4shTopBar(title = "Transaction Details", onBack = { navController.popBackStack() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transaction Details", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Spacer(modifier = Modifier.height(24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Payee", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord?.payeeName ?: SharedPaymentState.payeeName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = OnSurfaceDark))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("UPI ID", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord?.payeeVpa ?: SharedPaymentState.payeeVpa, style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text("₹${lastRecord?.amount ?: SharedPaymentState.paymentAmount}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Status", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord?.state?.name ?: "UNKNOWN", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reference ID", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord?.localTransactionId ?: "V4SH-MOCK", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                        }
                    }
                }
            }
        }
    }
}

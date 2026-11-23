// File: ScreenPlaceholders.kt
package com.festpay.app.ui.screens
import kotlinx.coroutines.launch

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.festpay.app.ui.theme.V4SHTheme
import com.festpay.app.ui.viewmodel.PaymentScenario
import com.festpay.app.ui.viewmodel.PaymentState
import com.festpay.app.ui.viewmodel.PaymentViewModel
import com.festpay.app.ui.viewmodel.TransactionRecord
import com.festpay.app.FestPayAccessibilityService
import com.festpay.app.data.TransactionPresentation
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.net.URLDecoder

// ----- Design System Colors & Constants -----
internal val PrimaryBurgundy = Color(0xFF3D0C1A)
internal val SecondaryUmber = Color(0xFF8C6A58)
internal val SurfaceCream = Color(0xFFFFF8F0)
private val SurfaceWelcomeGradientStart = Color(0xFFFFF9F5)
private val SurfaceWelcomeGradientEnd = Color(0xFFFAF1EB)
internal val SurfaceCardLowest = Color(0xFFFFFFFF)
private val SurfaceCardLow = Color(0xFFF3F3F4)
private val SurfaceContainerHigh = Color(0xFFE8E8E8)
internal val OnSurfaceDark = Color(0xFF1A1C1C)
internal val OnSurfaceVariant = Color(0xFF524345)
private val OutlineBorder = Color(0xFFEFE7DE)
internal val OutlineBorderWarm = Color(0xFFE8DDD2)

internal val cardShape = RoundedCornerShape(12.dp)
internal val largeCardShape = RoundedCornerShape(16.dp)
internal val pillShape = RoundedCornerShape(9999.dp)
internal val spacing = 20.dp

// Global developer debug mode state

var lastQrIssue by mutableStateOf(com.festpay.app.data.QrIssue.NONE)

// ----- Shared Payment State for Scanned Data -----
object SharedPaymentState {
    private val draft = com.festpay.app.data.PaymentPreparation()
    var isMobilePayment = false
    val recipient get() = draft.recipient
    val selectionId get() = draft.selectionId
    val payeeVpa get() = recipient.upiId
    val payeeName get() = recipient.displayName
    var paymentAmount: String
        get() = draft.amount
        set(value) { draft.editAmount(value) }
    var paymentNote: String
        get() = draft.note
        set(value) { draft.editNote(value) }
    fun selectRecipient(vpa: String, amount: String = "", note: String = "", qrSuppliedName: String? = null) = draft.select(vpa, amount, note, qrSuppliedName)
    fun clear() { isMobilePayment = false; draft.clear() }
    suspend fun resolve(ticket: com.festpay.app.data.BeneficiaryQuery) = draft.resolve(ticket)
    fun review() = draft.review()
    fun isCurrent(review: com.festpay.app.data.ReviewedPayment) = draft.isCurrent(review)
}

@Composable
fun RecipientVerificationNotice(recipient: com.festpay.app.data.PaymentRecipient) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    recipient.nameSourceLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(com.festpay.app.data.UiPresentation.verificationLabel(recipient.verificationStatus), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    if (recipient.verificationStatus == com.festpay.app.data.VerificationStatus.VERIFIED) {
        Text("Verification source: ${recipient.verificationSource}", style = MaterialTheme.typography.bodySmall)
    } else {
        Text(when (recipient.verificationStatus) {
            com.festpay.app.data.VerificationStatus.PENDING -> "Waiting for authorized beneficiary validation."
            com.festpay.app.data.VerificationStatus.INVALID_UPI_ID -> "Enter a valid UPI ID. Local syntax checks do not prove the account exists."
            com.festpay.app.data.VerificationStatus.ERROR -> "Beneficiary validation could not be completed. No verified name is available. Independently check the UPI ID."
            else -> "No authorized beneficiary-validation source is connected. Independently check the UPI ID before proceeding."
        }, style = MaterialTheme.typography.bodySmall)
    }
    }
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
    SharedPaymentState.clear()

    val analysis = com.festpay.app.data.UpiQrParser.analyze(rawValue)
    lastQrIssue = analysis.issue
    Log.d("V4SH_QR", "QR_PARSE_RESULT=${analysis.issue.name}")
    val result = analysis.value ?: return when (analysis.issue) {
        com.festpay.app.data.QrIssue.UNSUPPORTED_SCHEME, com.festpay.app.data.QrIssue.UNSUPPORTED_ACTION,
        com.festpay.app.data.QrIssue.UNSUPPORTED_CURRENCY -> UpiParseResult.UnsupportedFormat
        com.festpay.app.data.QrIssue.MISSING_RECIPIENT -> UpiParseResult.MissingDetails
        else -> UpiParseResult.InvalidFormat
    }
    SharedPaymentState.selectRecipient(result.vpa, result.amount.orEmpty(), result.note.orEmpty(), result.name)
    // Retain QR pn separately; it is never a verified bank beneficiary name.
    return UpiParseResult.Valid(result.vpa, result.name, result.amount, result.currency, null)
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
internal fun V4shTopBar(
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
internal fun RefreshHistoryOnResume(paymentViewModel: PaymentViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(paymentViewModel, context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) paymentViewModel.refreshTransactionHistory(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
fun HomeScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    RefreshHistoryOnResume(paymentViewModel)
    val homeContext = LocalContext.current
    val homeLifecycle = LocalLifecycleOwner.current.lifecycle
    var readiness by remember { mutableStateOf<com.festpay.app.data.HomeReadiness?>(null) }
    DisposableEffect(paymentViewModel, homeContext, homeLifecycle) {
        fun refresh() {
            paymentViewModel.inspectPaymentReadiness(homeContext)
            val block = paymentViewModel.preparationBlock
            readiness = com.festpay.app.data.HomeReadinessEvaluator.evaluate(
                storageOk = block != com.festpay.app.ui.viewmodel.PreparationBlock.STORAGE,
                earlierHistory = block == com.festpay.app.ui.viewmodel.PreparationBlock.EARLIER_HISTORY,
                unresolvedAttempt = block == com.festpay.app.ui.viewmodel.PreparationBlock.UNRESOLVED_ATTEMPT,
                consent = com.festpay.app.data.AccessibilityConsent.accepted(homeContext),
                phonePermission = ContextCompat.checkSelfPermission(homeContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
                accessibilityConnected = com.festpay.app.data.AccessibilityRuntime.connection.snapshot.connected,
                cellularBlock = com.festpay.app.data.CellularReadinessReader.read(homeContext).launchBlock
            )
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        homeLifecycle.addObserver(observer)
        refresh()
        onDispose { homeLifecycle.removeObserver(observer) }
    }

    V4SHTheme {
        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
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
                                text = com.festpay.app.data.UiPresentation.greeting(java.time.LocalTime.now().hour, paymentViewModel.userName),
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
                                        text = "Offline tools",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2B4C3F), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.festpay.app.R.string.home_title),
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
                                        text = androidx.compose.ui.res.stringResource(com.festpay.app.R.string.scan_pay),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.festpay.app.R.string.scan_pay_sub),
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
                    val state = readiness
                    if (state != null) {
                        val dot = when (state.state) {
                            com.festpay.app.data.HomeReadinessState.READY -> Color(0xFF2B4C3F)
                            com.festpay.app.data.HomeReadinessState.SETUP_NEEDED -> Color(0xFF8C5815)
                            com.festpay.app.data.HomeReadinessState.BLOCKED -> Color(0xFF93000A)
                        }
                        val homeContext = androidx.compose.ui.platform.LocalContext.current
                        Card(
                            shape = largeCardShape,
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                // When Accessibility is the only thing missing, go straight to Android's switch.
                                val onlyAccessibility = state.state == com.festpay.app.data.HomeReadinessState.SETUP_NEEDED &&
                                    state.detail.contains("Accessibility settings")
                                if (!(onlyAccessibility && com.festpay.app.data.IssueLog.openAccessibilitySettings(homeContext))) navController.navigate("payment_readiness")
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dot))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(state.headline, style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                    Text(state.detail, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open payment readiness", tint = SecondaryUmber)
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = largeCardShape,
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { com.festpay.app.data.BalanceQuick.request(); navController.navigate("check_balance") }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryBurgundy)
                                Column {
                                    Text(androidx.compose.ui.res.stringResource(com.festpay.app.R.string.check_balance), style = MaterialTheme.typography.labelLarge.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                                    Text(androidx.compose.ui.res.stringResource(com.festpay.app.R.string.check_balance_sub), style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SecondaryUmber)
                        }
                    }
                }

                item { AutoSleepNote() }
                item { PayAgainRow(navController, paymentViewModel) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            shape = largeCardShape,
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 142.dp).fillMaxHeight()
                                .clickable { navController.navigate("upi_payment") }
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
                                .heightIn(min = 142.dp).fillMaxHeight()
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
                paymentViewModel.historyError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (recentLedger.isEmpty() && paymentViewModel.historyError == null) {
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
                        TransactionRecordRowItem(record) { paymentViewModel.selectedAttemptId = record.localTransactionId; navController.navigate("transaction_details") }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TransactionRecordRowItem(item: TransactionRecord, onClick: () -> Unit = {}) {
    Card(
        shape = largeCardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
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
                    if (item.payeeName.isNotBlank()) Text(com.festpay.app.data.UiPresentation.initial(item.payeeName), style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                    else Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(com.festpay.app.data.UiPresentation.payeeTitle(item.payeeName, item.payeeVpa), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = PrimaryBurgundy),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(com.festpay.app.data.UiPresentation.shortTimestamp(item.timestamp), style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 150.dp)) {
                Text("₹${item.amount}", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
                val ownerLabel = com.festpay.app.data.UiPresentation.ownerResolutionLabel(item.ownerResolution)
                val carrierBadge = com.festpay.app.data.UiPresentation.carrierResultBadge(item.carrierResult)
                val closedLabel = com.festpay.app.data.UiPresentation.carrierOutcomeLabel(item.carrierOutcome)
                val unconfirmed = item.noCarrierConfirmation && item.ownerResolution == null && closedLabel == null
                val (bg, txt, label) = if (unconfirmed) Triple(Color(0xFFFBEBEB), Color(0xFF93000A), "Unsuccessful · not confirmed by carrier") else if (closedLabel != null) (if (item.carrierOutcome == "SUCCESS") Triple(Color(0xFFEBF2EE), Color(0xFF2B4C3F), closedLabel) else Triple(Color(0xFFFBEBEB), Color(0xFF93000A), closedLabel)) else if (ownerLabel != null) Triple(Color(0xFFF3F3F4), Color(0xFF524345), ownerLabel) else if (carrierBadge != null) Triple(Color(0xFFF3F3F4), Color(0xFF524345), carrierBadge) else when (com.festpay.app.data.PaymentIntegrityPolicy.restoredState(item.state)) {
                    PaymentState.SUCCESS -> Triple(Color(0xFFEBF2EE), Color(0xFF2B4C3F), "Confirmed & Settled")
                    PaymentState.FAILED -> Triple(Color(0xFFFBEBEB), Color(0xFF93000A), "Failed")
                    else -> Triple(Color(0xFFFBF3E8), Color(0xFF8C5815), "Payment status unclear")
                }
                Surface(shape = RoundedCornerShape(6.dp), color = bg, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = label,
                        textAlign = TextAlign.End,
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
    RefreshHistoryOnResume(paymentViewModel)

    V4SHTheme {
        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceCream)
                    .padding(innerPadding)
                    .padding(horizontal = spacing),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Transactions", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                    Text(text = "Local payment attempts, not a bank statement. Taps blocked before initiation do not create new attempts.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    TextButton(onClick = { paymentViewModel.refreshTransactionHistory(context) }) { Text("Refresh history") }
                }
                paymentViewModel.historyError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (paymentViewModel.transactionLedger.isEmpty() && paymentViewModel.historyError == null) {
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
                        TransactionRecordRowItem(record) { paymentViewModel.selectedAttemptId = record.localTransactionId; navController.navigate("transaction_details") }
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
    var requestAmount by remember { mutableStateOf("") }
    val requestedAmount = com.festpay.app.data.PaymentInputValidator.amount(requestAmount)
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
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(200.dp).clip(cardShape).background(SurfaceCardLow),
                            contentAlignment = Alignment.Center
                        ) {
                            if (paymentViewModel.profileAvailable) {
                                ReceiveAccountQr(paymentViewModel.userVpa, paymentViewModel.userName, requestedAmount)
                            } else Text("Saved UPI details are unavailable. Check Profile; your data has not been reset.")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(paymentViewModel.userName, style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                        Text(paymentViewModel.userVpa, style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(requestAmount, { requestAmount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, singleLine = true,
                            label = { Text("Ask for an amount (optional)") }, isError = requestAmount.isNotEmpty() && requestedAmount == null,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            supportingText = { Text(if (requestedAmount != null) "The QR now asks for ₹$requestedAmount." else "Leave empty for a QR that lets the payer type the amount.") },
                            modifier = Modifier.fillMaxWidth())
                        val shareable = paymentViewModel.profileAvailable && com.festpay.app.data.ReceiveQr.payload(paymentViewModel.userVpa, paymentViewModel.userName) != null
                        if (shareable) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                val label = paymentViewModel.userName.ifBlank { "me" }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Pay $label" + (requestedAmount?.let { " ₹$it" } ?: "") + " on UPI: ${paymentViewModel.userVpa}" + (com.festpay.app.data.ReceiveQr.payload(paymentViewModel.userVpa, paymentViewModel.userName, requestedAmount)?.let { "\n$it" } ?: ""))
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, "Share UPI ID")) }
                            }, shape = largeCardShape) { Text("Share UPI ID") }
                        }
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
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Advanced", subtitle = "Problems, fixes, permissions and Accessibility", icon = Icons.Default.Settings) {
                    navController.navigate("payment_readiness")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Spending & export", subtitle = "Totals by month, top payees, CSV export", icon = Icons.Default.Settings) {
                    navController.navigate("spending")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Language", subtitle = "English, हिन्दी, తెలుగు", icon = Icons.Default.Settings) {
                    navController.navigate("language")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Try a demo", subtitle = "See a payment walk-through. No money, nothing saved", icon = Icons.Default.Settings) {
                    navController.navigate("demo")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Carrier Doctor", subtitle = "Check why *99# is not answering", icon = Icons.Default.Settings) {
                    navController.navigate("doctor")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Split a bill", subtitle = "Share one amount between people", icon = Icons.Default.Settings) {
                    navController.navigate("split_bill")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Spoken confirmation", subtitle = "Hear the amount when a payment is confirmed", icon = Icons.Default.Settings) {
                    navController.navigate("spoken")
                }
                Spacer(modifier = Modifier.height(8.dp))
                ProfileMenuCard(title = "Sleep mode", subtitle = "Switch V4SH off so your banking apps are not disturbed", icon = Icons.Default.Lock) {
                    navController.navigate("sleep_mode")
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
                                Text("Payment confirmation unavailable", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
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
    var vpaInput by rememberSaveable { mutableStateOf("") }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var fieldsLoaded by rememberSaveable { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        paymentViewModel.initRepository(context)
        if (!fieldsLoaded) {
            vpaInput = paymentViewModel.userVpa
            nameInput = paymentViewModel.userName
            fieldsLoaded = true
        }
    }

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
                Text(text = "Save your own UPI ID for Receive. Saving it does not link or verify a bank account.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; isSaved = false; saveError = null },
                    label = { Text("Display Name") },
                    placeholder = { Text("Your name") },
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = vpaInput,
                    onValueChange = { vpaInput = it; isSaved = false; saveError = null },
                    label = { Text("UPI ID / VPA") },
                    placeholder = { Text("username@upi") },
                    shape = cardShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBurgundy, unfocusedBorderColor = OutlineBorderWarm)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isSaved = paymentViewModel.saveUserProfile(context, vpaInput, nameInput)
                        saveError = paymentViewModel.profileSaveError
                    },
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (isSaved) "UPI Details Saved" else "Save UPI Details")
                }
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

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
                            text = "V4SH generates a Receive QR from the UPI ID you provide. Verify that it is your own ID; V4SH cannot verify account ownership.",
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
                            Text("PIN handling", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
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
                                Text("V4SH has no separate app lock. Configure Android's screen lock to protect access to your device.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
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
                        Text("V4SH does not collect, store, or simulate your UPI PIN or banking passwords. Enter your UPI PIN manually only on the carrier or payment provider's interface. V4SH cannot verify authentication or settlement and will never ask you to share your PIN, banking password, card PIN, CVV, or OTP.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PrivacyScreen(navController: NavHostController) {

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

                Text("Unresolved attempts are retained to prevent duplicate payments. Clearing cache does not resolve a payment. App backup and device-transfer backup are disabled for this data.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))

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

                HelpTopicCard("Making a payment", "V4SH prepares supported UPI QR or manually entered UPI ID requests. Review the recipient and amount. Preparation does not verify bank ownership or start a payment.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("Payment status unclear", "V4SH could not receive a final confirmation from the payment service. Check your bank/UPI confirmation before trying again. Don't pay again yet. An unresolved attempt blocks another payment until you check your own bank record and mark it resolved in Transaction Details. V4SH cannot verify your answer.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("Offline use", "QR preparation, Receive and local history work without internet. USSD balance checks require cellular signal and a supported bank-registered SIM, with airplane mode off. Wi-Fi and mobile data may remain off.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("Check Balance and PIN", "V4SH assists the supported *99# menu. Enter your UPI PIN manually on the carrier interface only. V4SH does not read, store or enter it. A displayed balance does not prove any payment settled.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("QR names", "A QR-supplied name comes from the payment request and is not independently verified by V4SH. A manually entered UPI ID is also unverified; check the recipient before authenticating.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("Calling SIM", "Use the SIM registered with your bank. Android and the carrier control *99# routing. V4SH does not select or verify your bank SIM, and cannot guarantee dual-SIM routing, coverage or carrier support.")
                Spacer(modifier = Modifier.height(8.dp))
                HelpTopicCard("Sharing a support note", "Contact Support opens Android's share chooser when you choose to share a note. V4SH has no connected support inbox and does not create a ticket or confirm delivery. You choose the app and recipient; that app may need internet.")

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
    val context = LocalContext.current
    var issueDesc by remember { mutableStateOf("") }
    var shareError by remember { mutableStateOf(false) }

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
                Text("Your note goes to V4SH support on 96180 99307 through your own SMS or WhatsApp app, only when you tap Send there. V4SH itself sends nothing. SMS charges from your SIM may apply.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

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

                var withDetails by remember { mutableStateOf(true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withDetails, onCheckedChange = { withDetails = it })
                    Text("Add my phone model and V4SH settings (no names, UPI IDs, amounts or PINs)", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
                }
                Spacer(modifier = Modifier.height(8.dp))
                val supportText = com.festpay.app.data.SupportContact.message(issueDesc, if (withDetails) problemReportText(context) else null)
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(com.festpay.app.data.SupportContact.smsUri(supportText))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            shareError = false
                        } catch (_: Exception) { shareError = true }
                    },
                    enabled = issueDesc.isNotBlank(),
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Send to V4SH support by SMS") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.festpay.app.data.SupportContact.whatsAppUrl(supportText))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            shareError = false
                        } catch (_: Exception) { shareError = true }
                    },
                    enabled = issueDesc.isNotBlank(),
                    shape = largeCardShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Send on WhatsApp") }
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "V4SH support note")
                                putExtra(Intent.EXTRA_TEXT, issueDesc.trim())
                            }, "Share support note"))
                            shareError = false
                        } catch (_: Exception) { shareError = true }
                    },
                    enabled = issueDesc.isNotBlank(),
                    shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Share support note")
                }
                if (shareError) Text("No sharing app could be opened. The note has not been sent.", color = MaterialTheme.colorScheme.error)

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
                Text("Cellular USSD requires signal and a supported bank-registered SIM. Mobile data and Wi-Fi are not required; QR preparation, Receive and saved history work offline.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No payment was started", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("If cellular service is unavailable, USSD cannot connect. This screen does not prove whether an earlier payment reached the bank; unresolved attempts remain blocked.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant))
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
                    onClick = { navController.navigate("guided_setup") },
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

// ==========================================
// BATCH 3 & 4: QR SCANNING & PAYMENT ENGINE
// ==========================================

@Composable
fun QrScannerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val recipientScope = rememberCoroutineScope()
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
    var cameraActive by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var scannerDisposed by remember { mutableStateOf(false) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scannedOnce = false
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(scanner) {
        scannerDisposed = false
        onDispose { scannerDisposed = true; scanner.close() }
    }

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
                V4shTopBar(title = "Scan & Pay", onBack = { navController.popBackStack() }, onAction = { navController.navigate("help_center") })
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
                                    if (scannerDisposed) return@addListener
                                    try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalyzer = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(executor) { imageProxy ->
                                                processImageProxy(imageProxy, scanner) { rawValue ->
                                                    if (!scannerDisposed && !scannedOnce && lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                                        scannedOnce = true
                                                        when (val result = parseAndValidateUpiQr(rawValue)) {
                                                            is UpiParseResult.Valid -> {
                                                                val ticket = SharedPaymentState.selectRecipient(result.pa, result.am.orEmpty(), SharedPaymentState.paymentNote, result.pn)
                                                                recipientScope.launch {
                                                                    SharedPaymentState.resolve(ticket)
                                                                    if (!scannerDisposed && SharedPaymentState.selectionId == ticket.requestId && lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigate("qr_recipient")
                                                                }
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
                                        cameraActive = true
                                        cameraError = false
                                    } catch (e: Exception) {
                                        cameraActive = false
                                        cameraError = true
                                        Log.e("V4SH_QR", "CAMERA_BIND_FAILED=true")
                                    }
                                    } catch (_: Exception) {
                                        cameraActive = false
                                        cameraError = true
                                        Log.e("V4SH_QR", "CAMERA_START_FAILED=true")
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
                            text = when { !hasCameraPermission -> "Permission Required"; cameraError -> "Camera unavailable"; cameraActive -> "Camera Active"; else -> "Starting camera" },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SecondaryUmber),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Align the QR code inside the frame", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Medium))
                Text("Keep the QR code steady", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber), modifier = Modifier.padding(top = 2.dp))
                if (cameraError) {
                    Text("Camera could not start. Close other camera apps and reopen the scanner, or enter the UPI ID manually.", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Always shown, right under the camera: a QR saved as a screenshot or photo can be uploaded and read.
                GalleryScanButton(navController)
                TextButton(onClick = { navController.navigate("upi_payment") }) { Text("Enter UPI ID instead") }

                // Developer Test Error Controls (Isolated under debug trigger)


                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy, scanner: BarcodeScanner, onBarcodeDetected: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        try { scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onBarcodeDetected(value)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {
                Log.e("V4SH_QR", "BARCODE_SCAN_FAILED=true")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
        } catch (_: Exception) {
            imageProxy.close()
            Log.e("V4SH_QR", "BARCODE_START_FAILED=true")
        }
    } else {
        imageProxy.close()
    }
}

@Composable
fun CameraAccessRequiredScreen(navController: NavHostController) {
    val context = LocalContext.current
    var settingsError by remember { mutableStateOf(false) }
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

                Button(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        settingsError = false
                    } catch (_: Exception) { settingsError = true }
                }, shape = largeCardShape, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Open app permission settings")
                }
                if (settingsError) Text("Open V4SH permissions manually in Android Settings.", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { navController.navigate("upi_payment") }, shape = largeCardShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
                Text("The QR was detected, but its payment details could not be accepted. ${com.festpay.app.data.UiPresentation.qrIssueLabel(lastQrIssue)} Try a standard UPI payment QR.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

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
                Text(com.festpay.app.data.UiPresentation.qrIssueLabel(lastQrIssue).ifBlank { "This QR code isn’t a supported UPI payment QR." }, style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Supported QR formats", style = MaterialTheme.typography.headlineSmall.copy(color = PrimaryBurgundy))
                        Text("V4SH reads standard upi://pay payment requests. Other QR formats are unsupported; a scanned name does not verify account ownership.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
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

/** Shown if Android reclaimed the app in the background and the in-memory draft is gone. No payment exists. */
@Composable
private fun StaleDraftNotice(navController: NavHostController) {
    V4SHTheme {
        Scaffold(topBar = { V4shTopBar(title = "Payment", onBack = { navController.popBackStack() }) }) { innerPadding ->
            Column(
                Modifier.fillMaxSize().background(SurfaceCream).padding(innerPadding).padding(spacing),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Start again", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("The recipient details are no longer available, for example because the app was closed in the background. No payment was started.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                Button(onClick = { navController.navigate("home") { popUpTo("home") { inclusive = false } } }, shape = largeCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Back to Home") }
            }
        }
    }
}

@Composable
fun QrRecipientScreen(navController: NavHostController) {
    if (SharedPaymentState.payeeVpa.isBlank()) { StaleDraftNotice(navController); return }
    val payeeName = SharedPaymentState.payeeName
    val payeeVpa = SharedPaymentState.payeeVpa

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Recipient", onBack = { navController.popBackStack() }, onAction = { navController.navigate("help_center") })
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
                RecipientVerificationNotice(SharedPaymentState.recipient)

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
                                    text = com.festpay.app.data.UiPresentation.initial(SharedPaymentState.recipient.verifiedBeneficiaryName ?: SharedPaymentState.recipient.qrSuppliedName),
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
                            Text("The UPI ID and payment fields came from the QR. Its supplied name is not used as bank identity.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
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
fun AmountEntryScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    if (SharedPaymentState.payeeVpa.isBlank()) { StaleDraftNotice(navController); return }
    val payContext = LocalContext.current
    var opening by remember { mutableStateOf(false) }
    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(paymentViewModel, payContext) { paymentViewModel.inspectPaymentReadiness(payContext) }
    val selectionId = SharedPaymentState.selectionId
    var amount by remember(selectionId) { mutableStateOf(SharedPaymentState.paymentAmount) }
    var note by remember(selectionId) { mutableStateOf(SharedPaymentState.paymentNote) }

    val isValidAmount = com.festpay.app.data.PaymentInputValidator.amount(amount) != null

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Amount", onBack = { navController.popBackStack() }, onAction = { navController.navigate("help_center") })
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
                            Text(com.festpay.app.data.UiPresentation.initial(SharedPaymentState.recipient.verifiedBeneficiaryName ?: SharedPaymentState.recipient.qrSuppliedName), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(SharedPaymentState.payeeName, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp, color = OnSurfaceDark),
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Surface(shape = pillShape, color = SurfaceContainerHigh) {
                                    Text("Standard UPI", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            Text(SharedPaymentState.payeeVpa, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                            SharedPaymentState.recipient.nameSourceLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber)) }
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
                    isError = com.festpay.app.data.UiPresentation.amountProblem(amount) != null,
                    supportingText = com.festpay.app.data.UiPresentation.amountProblem(amount)?.let { message -> { Text(message) } },
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
                    label = { Text("Local note (not sent to carrier)") },
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
                    // Why Pay cannot start (earlier unresolved attempt, Accessibility off, permission...), in plain words.
                    paymentViewModel.operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    ErrorHelpBlock(paymentViewModel.operationError) { fix ->
                        when (fix) {
                            com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS -> com.festpay.app.data.IssueLog.openAccessibilitySettings(payContext)
                            com.festpay.app.data.IssueFix.PHONE_PERMISSION -> phonePermission.launch(Manifest.permission.CALL_PHONE)
                            else -> {}
                        }
                    }
                    if (paymentViewModel.startFailure == com.festpay.app.ui.viewmodel.StartFailure.OPERATION_ACTIVE) {
                        OutlinedButton(onClick = {
                            com.festpay.app.data.AccessibilityRuntime.revokeSession()
                            paymentViewModel.clearStartRefusal()
                        }, shape = largeCardShape) { Text("Stop the stuck operation") }
                    }
                    SimPickerRow()
                    Button(
                        onClick = {
                            if (!isValidAmount || opening) return@Button
                            SharedPaymentState.paymentAmount = amount
                            SharedPaymentState.paymentNote = note
                            if (ContextCompat.checkSelfPermission(payContext, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                phonePermission.launch(Manifest.permission.CALL_PHONE)
                                return@Button
                            }
                            val reviewed = SharedPaymentState.review() ?: return@Button
                            opening = true
                            // The request is frozen here and reused unchanged: the journal, the carrier session and the typed values.
                            paymentViewModel.startPayment(
                                vpa = reviewed.recipient.upiId,
                                name = reviewed.recipient.verifiedBeneficiaryName ?: reviewed.recipient.qrSuppliedName.orEmpty(),
                                amount = reviewed.amount,
                                ctx = payContext,
                                reviewCurrent = { SharedPaymentState.isCurrent(reviewed) }
                            ) { _ ->
                                opening = false
                                if (paymentViewModel.startFailure == com.festpay.app.ui.viewmodel.StartFailure.NONE) {
                                    SharedPaymentState.clear()
                                    navController.navigate("payment_progress") { popUpTo("home") { inclusive = false } }
                                }
                            }
                        },
                        enabled = isValidAmount && !opening && !paymentViewModel.paymentPreparationBlocked,
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy, disabledContainerColor = PrimaryBurgundy.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (opening) "Opening carrier…" else if (isValidAmount) "Pay ₹$amount" else "Pay", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
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
    val reviewed = remember { SharedPaymentState.review() }
    if (reviewed == null) {
        Column(Modifier.padding(20.dp)) {
            Text("Payment review unavailable")
            Text("Return to recipient and amount entry. No payment was started.")
            TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
        }
        return
    }
    val amount = reviewed.amount
    val note = reviewed.note
    val reviewCurrent = SharedPaymentState.isCurrent(reviewed)
    var isPayButtonEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(paymentViewModel, context) {
        com.festpay.app.data.PaymentDiagnostics.stage("review.screen", "SHOWN", "reviewCurrent=$reviewCurrent")
        paymentViewModel.inspectPaymentReadiness(context)
    }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Review Payment", onBack = { navController.popBackStack() }, onAction = { navController.navigate("help_center") })
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
                if (!reviewCurrent) Text("Payment details changed. Return to recipient and amount entry for a fresh Review. No payment was started.", color = MaterialTheme.colorScheme.error)
                paymentViewModel.operationError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
                }
                if (paymentViewModel.paymentPreparationBlocked) {
                    TextButton(onClick = { navController.navigate("transactions") }) {
                        Text("View existing attempts")
                    }
                }

                // Developer Execution Mode Selector (Isolated under debug trigger)


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
                                Text(com.festpay.app.data.UiPresentation.initial(reviewed.recipient.verifiedBeneficiaryName ?: reviewed.recipient.qrSuppliedName), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                            }
                            Column {
                                Text(reviewed.recipient.displayName, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark))
                                Text("Standard UPI Payee", style = MaterialTheme.typography.labelSmall.copy(color = SecondaryUmber))
                                Text(reviewed.recipient.upiId, style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber, fontWeight = FontWeight.Medium))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(shape = cardShape, color = SurfaceCardLow) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBurgundy, modifier = Modifier.size(16.dp))
                                RecipientVerificationNotice(reviewed.recipient)
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
                        Text("Currency: INR", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = OutlineBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (note.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Local note (not sent)", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
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
                                    Text("USSD *99#", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
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
                            com.festpay.app.data.PaymentDiagnostics.stage("review.payButton", "CLICK", "enabledFlag=$isPayButtonEnabled")
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                phonePermission.launch(Manifest.permission.CALL_PHONE)
                            } else if (isPayButtonEnabled && SharedPaymentState.isCurrent(reviewed)) {
                                isPayButtonEnabled = false
                                paymentViewModel.startPayment(
                                    vpa = reviewed.recipient.upiId,
                                    name = reviewed.recipient.verifiedBeneficiaryName ?: reviewed.recipient.qrSuppliedName.orEmpty(),
                                    amount = reviewed.amount,
                                    ctx = context,
                                    reviewCurrent = { SharedPaymentState.isCurrent(reviewed) }
                                ) { state ->
                                    isPayButtonEnabled = true
                                    when (state) {
                                        PaymentState.SUCCESS -> navController.navigate("payment_successful") { popUpTo("home") }
                                        PaymentState.FAILED -> navController.navigate("payment_failed")
                                        PaymentState.UNKNOWN -> navController.navigate(
                                            if (FestPayAccessibilityService.UssdSessionData.operationActive &&
                                                !FestPayAccessibilityService.UssdSessionData.isBalanceCheck &&
                                                FestPayAccessibilityService.UssdSessionData.attemptId == paymentViewModel.selectedAttemptId)
                                                "payment_journey" else "payment_status_unclear")
                                        PaymentState.AUTHENTICATION_REQUIRED -> navController.navigate("authentication_required")
                                        else -> {}
                                    }
                                }
                            }
                        },
                        enabled = isPayButtonEnabled && reviewCurrent && !paymentViewModel.paymentPreparationBlocked,
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = if (isPayButtonEnabled) "Pay ₹$amount" else "Preparing carrier launch…", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    TextButton(
                        onClick = {
                            if (paymentViewModel.markCancelled()) navController.navigate("payment_cancelled")
                            else navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "Close review",
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
    val presentationState = FestPayAccessibilityService.UssdSessionData.currentPresentationState
    LaunchedEffect(paymentViewModel.paymentState, presentationState) {
        when (paymentViewModel.paymentState) {
            PaymentState.UNKNOWN, PaymentState.SUCCESS, PaymentState.FAILED -> {
                // No authorized confirmation adapter exists. Neither screen labels
                // nor carrier observations can authorize a financial terminal page.
                navController.navigate("payment_status_unclear") { popUpTo("payment_in_progress") { inclusive = true } }
            }
            PaymentState.PROCESSING -> if (presentationState == "AUTHENTICATION_REQUIRED") {
                navController.navigate("authentication_required")
            }
            else -> Unit
        }
    }

    val (titleText, bodyText) = when (presentationState) {
        "CONNECTING" -> "Connecting to payment service" to "Establishing secure USSD session..."
        "SEND_MONEY" -> "Preparing payment" to "Connecting securely to your bank payment service."
        "RECIPIENT_ENTRY" -> "Adding recipient" to "Supplying recipient VPA: ${SharedPaymentState.payeeVpa}"
        "AMOUNT_ENTRY" -> "Preparing amount" to "Supplying payment amount: ₹${SharedPaymentState.paymentAmount}"
        "REVIEW", "CONFIRMING" -> "Confirming payment" to "Reviewing payment details with the payment service."
        "AUTHENTICATION_REQUIRED" -> "Authentication required" to "Complete your UPI PIN entry on the carrier payment screen.\nV4SH never enters, stores, or sees your UPI PIN."
        "WAITING_RESULT" -> "Confirming payment" to "Waiting for the payment service to return a final result."
        else -> "Payment in progress" to "Waiting for payment confirmation. Please don't close the app."
    }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Payment in Progress", onAction = { navController.navigate("help_center") })
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
                Text(
                    text = if (presentationState == "AUTHENTICATION_REQUIRED") "Authentication required" else titleText,
                    style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
                )

                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBurgundy, strokeWidth = 4.dp, modifier = Modifier.fillMaxSize())
                    Icon(
                        imageVector = if (presentationState == "AUTHENTICATION_REQUIRED") Icons.Default.Lock else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = PrimaryBurgundy,
                        modifier = Modifier.size(36.dp)
                    )
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
    if (!com.festpay.app.data.PaymentIntegrityPolicy.confirmationAvailable || paymentViewModel.paymentState != PaymentState.SUCCESS) {
        PaymentStatusUnclearScreen(navController, paymentViewModel)
        return
    }
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
                            Text("V4SH Attempt ID: ${result.localReference}", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
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
fun PaymentFailedScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    if (!com.festpay.app.data.PaymentIntegrityPolicy.confirmationAvailable || paymentViewModel.paymentState != PaymentState.FAILED) {
        PaymentStatusUnclearScreen(navController, paymentViewModel)
        return
    }
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
                Text("V4SH could not receive a final confirmation from the payment service.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Text("Check your bank/UPI confirmation before trying again.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Text(paymentViewModel.operationError ?: paymentViewModel.lastResult?.message ?: "Authorized reconciliation is unavailable; this attempt remains reserved.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    if (checkingStatus) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check Payment Status", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White), textAlign = TextAlign.Center)
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
                TextButton(onClick = { navController.navigate("home") { popUpTo("home"); launchSingleTop = true } }) { Text("Done") }
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
                Text("Review closed", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy))
                Text("Closing review does not cancel a payment already initiated. Existing attempts keep their recorded status in Transactions.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

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
    RefreshHistoryOnResume(paymentViewModel)
    val lastRecord = paymentViewModel.transactionLedger.firstOrNull { it.localTransactionId == paymentViewModel.selectedAttemptId }
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
                paymentViewModel.historyError?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
                if (lastRecord == null) {
                    Text("The selected saved attempt is unavailable. Open Transactions to select an existing record.")
                    TextButton(onClick = { navController.navigate("transactions") }) { Text("View Transactions") }
                } else Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Name (unverified)", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord.payeeName.ifBlank { "Not recorded" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = OnSurfaceDark))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("UPI ID", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord.payeeVpa, style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text("₹${lastRecord.amount}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Status", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(com.festpay.app.data.UiPresentation.carrierOutcomeLabel(lastRecord.carrierOutcome) ?: com.festpay.app.data.UiPresentation.ownerResolutionLabel(lastRecord.ownerResolution) ?: com.festpay.app.data.UiPresentation.outcomeLabel(lastRecord.state), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryBurgundy))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Created", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(com.festpay.app.data.UiPresentation.shortTimestamp(lastRecord.timestamp), style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("V4SH Attempt ID", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                            Text(lastRecord.localTransactionId, style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber))
                        }
                        Text("Type: ${lastRecord.operation.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall)
                        Text("Confirmation: ${com.festpay.app.data.UiPresentation.evidenceLabel(lastRecord.evidenceStatus)}", style = MaterialTheme.typography.bodySmall)
                        Text(if (lastRecord.bankReference != null) "Transaction reference: ${lastRecord.bankReference} (printed by the carrier)" else "Transaction reference: Not available", style = MaterialTheme.typography.bodySmall)
                        if (lastRecord.carrierOutcome != null) {
                            Text("Taken from the carrier's own final message. V4SH does not query your bank.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                        com.festpay.app.data.UiPresentation.carrierResultBadge(lastRecord.carrierResult)?.let {
                            Text("Carrier's last message: ${it.removePrefix("Carrier said: ")}. This is not a bank confirmation.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                        if (lastRecord.ownerResolution != null) {
                            Text("This is your own statement after checking your bank. V4SH did not verify it.", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                        }
                    }
                }
                if (lastRecord != null) { Spacer(modifier = Modifier.height(16.dp)); ReceiptButton(lastRecord) }
                if (lastRecord != null && lastRecord.state == PaymentState.UNKNOWN && lastRecord.ownerResolution == null && lastRecord.carrierOutcome == null) {
                    var confirming by remember(lastRecord.localTransactionId) { mutableStateOf(false) }
                    val detailsContext = LocalContext.current
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { confirming = true }, shape = largeCardShape, modifier = Modifier.fillMaxWidth()) { Text("Resolve this attempt") }
                    paymentViewModel.operationError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    if (confirming) AlertDialog(
                        onDismissRequest = { confirming = false },
                        title = { Text("Check your bank first") },
                        text = { Text("Open your bank app or SMS and look for the ₹${lastRecord.amount} payment to ${lastRecord.payeeVpa}. V4SH cannot confirm it. Your answer is saved as your own statement, keeps this record as it is, and lets new payments start. It cannot be undone. If you are not sure, do not pay again.") },
                        confirmButton = {
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = {
                                    paymentViewModel.resolveAttemptByOwner(detailsContext, lastRecord.localTransactionId, com.festpay.app.data.OwnerResolution.DEBITED); confirming = false
                                }) { Text("Money was debited") }
                                TextButton(onClick = {
                                    paymentViewModel.resolveAttemptByOwner(detailsContext, lastRecord.localTransactionId, com.festpay.app.data.OwnerResolution.NOT_DEBITED); confirming = false
                                }) { Text("Money was not debited") }
                            }
                        },
                        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

@Composable
fun CheckBalanceScreen(navController: NavHostController, paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf(false) }
    var presentationState by remember { mutableStateOf("IDLE") }
    var parsedBalance by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedMessage = false
            checking = true
            paymentViewModel.startBalanceCheck(context) { state -> if (state == PaymentState.IDLE) checking = false }
        } else {
            permissionDeniedMessage = true
        }
    }

    LaunchedEffect(Unit) {
        // Opened from Home's balance card: start straight away, but only when the phone permission is already granted.
        if (com.festpay.app.data.BalanceQuick.consume() && !checking &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            checking = true
            paymentViewModel.startBalanceCheck(context) { state -> if (state == PaymentState.IDLE) checking = false }
        }
    }

    LaunchedEffect(checking, FestPayAccessibilityService.UssdSessionData.currentPresentationState,
        FestPayAccessibilityService.UssdSessionData.parsedBalance) {
        if (checking) {
            presentationState = FestPayAccessibilityService.UssdSessionData.currentPresentationState
            parsedBalance = FestPayAccessibilityService.UssdSessionData.parsedBalance
            if (presentationState in listOf("BALANCE_SUCCESS", "BALANCE_UNKNOWN", "FAILED")) {
                if (presentationState != "BALANCE_SUCCESS") com.festpay.app.data.IssueLog.record("Balance unavailable",
                    FestPayAccessibilityService.UssdSessionData.errorMessage ?: "V4SH could not identify the balance.")
                checking = false
            }
        }
    }

    V4SHTheme {
        Scaffold(
            topBar = {
                V4shTopBar(title = "Check Balance", onBack = { navController.popBackStack() })
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
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(PrimaryBurgundy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (presentationState != "BALANCE_SUCCESS") { SimPickerRow(); Spacer(modifier = Modifier.height(16.dp)) }

                if (presentationState == "BALANCE_SUCCESS" && parsedBalance != null) {
                    Text("Available Balance", style = MaterialTheme.typography.labelLarge.copy(color = SecondaryUmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    Text("₹$parsedBalance", style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold, color = PrimaryBurgundy), modifier = Modifier.padding(vertical = 8.dp))
                    Text("Your current account balance", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant), textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { navController.popBackStack() },
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    }
                } else if (presentationState == "BALANCE_UNKNOWN" || presentationState == "FAILED") {
                    Text("Balance unavailable", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                    Text(FestPayAccessibilityService.UssdSessionData.errorMessage ?: "V4SH could not safely identify the account balance. Check the carrier response and cellular signal before trying again. Mobile data and Wi-Fi are not required.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

                    ErrorHelpBlock(FestPayAccessibilityService.UssdSessionData.errorMessage) { fix ->
                        when (fix) {
                            com.festpay.app.data.IssueFix.ACCESSIBILITY_SETTINGS -> com.festpay.app.data.IssueLog.openAccessibilitySettings(context)
                            com.festpay.app.data.IssueFix.PHONE_PERMISSION -> permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            com.festpay.app.data.IssueFix.STOP_STUCK_OPERATION -> com.festpay.app.data.AccessibilityRuntime.revokeSession()
                            else -> {}
                        }
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open Accessibility Settings") }

                    Button(
                        onClick = {
                            presentationState = "IDLE"
                            parsedBalance = null
                            val hasCallPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                            if (hasCallPhone) {
                                checking = true
                                paymentViewModel.startBalanceCheck(context) { state -> if (state == PaymentState.IDLE) checking = false }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        },
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Try Again", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        shape = largeCardShape,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Done", color = PrimaryBurgundy)
                    }
                } else {
                    Text("Check Bank Balance", style = MaterialTheme.typography.headlineLarge.copy(color = PrimaryBurgundy), textAlign = TextAlign.Center)
                    Text("V4SH will dial *99# and select Check Balance (3). Mobile data and Wi-Fi are not required; keep cellular signal available. Enter your UPI PIN only on the carrier screen.", style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryUmber), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 32.dp))

                    if (permissionDeniedMessage) {
                        Card(
                            shape = cardShape,
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBEBEB)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Phone permission required", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF93000A)))
                                Text("Phone-call permission is required to initiate the USSD balance check automatically without manual dialing.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val hasCallPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                            if (hasCallPhone) {
                                permissionDeniedMessage = false
                                checking = true
                                paymentViewModel.startBalanceCheck(context) { state -> if (state == PaymentState.IDLE) checking = false }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        },
                        shape = largeCardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBurgundy),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        if (checking) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (permissionDeniedMessage) "Try Again" else "Check Balance on *99#", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                }
            }
        }
    }
}

package com.festpay.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.festpay.app.data.SimSelection

/** "Calling SIM" row: opens a chooser. The permission to list SIMs is requested only here, when the owner asks. */
@Composable
internal fun SimPickerRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { refresh++; open = true } }
    val label = remember(refresh) { SimSelection.label(context) }
    Card(shape = largeCardShape, colors = CardDefaults.cardColors(containerColor = SurfaceCardLowest), modifier = Modifier.fillMaxWidth().clickable {
        if (SimSelection.hasPermission(context)) open = true else permission.launch(Manifest.permission.READ_PHONE_STATE)
    }) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Calling SIM", style = MaterialTheme.typography.bodySmall.copy(color = SecondaryUmber))
                Text(label, style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBurgundy, fontWeight = FontWeight.Bold))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Choose SIM", tint = SecondaryUmber)
        }
    }
    if (open) {
        val options = remember(refresh) { SimSelection.options(context) }
        val chosen = SimSelection.chosen(context)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Choose the SIM for *99#") },
            text = {
                Column {
                    Text("This SIM places the call. Your bank must be registered on it.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    @Composable fun choice(title: String, selected: Boolean, onPick: () -> Unit) {
                        Row(Modifier.fillMaxWidth().clickable { onPick(); refresh++; open = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = { onPick(); refresh++; open = false })
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    choice("Phone default", chosen == null) { SimSelection.choose(context, null) }
                    options.forEach { o -> choice("SIM ${o.slot + 1} · ${o.name}", chosen == o.subscriptionId) { SimSelection.choose(context, o.subscriptionId) } }
                    if (options.isEmpty()) Text("No active SIM was found.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}

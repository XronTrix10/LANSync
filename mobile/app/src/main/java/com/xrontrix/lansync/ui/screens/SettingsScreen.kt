package com.xrontrix.lansync.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrontrix.lansync.ui.theme.*
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDeviceName: String,
    currentDownloadFolderUri: String,
    currentExposedFolderUri: String,
    onSaveConfig: (String, String, String) -> Unit
) {
    var deviceNameInput by remember { mutableStateOf(currentDeviceName) }
    var downloadUri by remember { mutableStateOf(currentDownloadFolderUri) }
    var exposedUri by remember { mutableStateOf(currentExposedFolderUri) }

    // Native Folder Pickers
    val downloadFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { downloadUri = it.toString() }
    }
    val exposedFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { exposedUri = it.toString() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        Spacer(modifier = Modifier.height(24.dp))

        // ─── DEVICE PROFILE CARD ───
        Text("DEVICE PROFILE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Display Name", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("How this phone appears to your Desktop.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

                OutlinedTextField(
                    value = deviceNameInput,
                    onValueChange = { deviceNameInput = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Surface,
                        focusedContainerColor = BgBase, unfocusedContainerColor = BgBase,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── DIRECTORY CONFIGURATION CARD ───
        Text("DIRECTORY CONFIGURATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Download Folder Selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Download Location", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Where files from PC are saved.", color = TextMuted, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = BgBase, shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Surface),
                    modifier = Modifier.fillMaxWidth().clickable { downloadFolderLauncher.launch(null) }
                ) {
                    Text(
                        text = formatUriDisplay(downloadUri, "Select Download Folder..."),
                        color = if (downloadUri.isEmpty()) TextMuted else GreenAccent,
                        fontSize = 14.sp, modifier = Modifier.padding(16.dp), maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Exposed Folder Selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FolderShared, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Exposed Network Folder", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Which folder can the PC browse?", color = TextMuted, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = BgBase, shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Surface),
                    modifier = Modifier.fillMaxWidth().clickable { exposedFolderLauncher.launch(null) }
                ) {
                    Text(
                        text = formatUriDisplay(exposedUri, "Select Folder to Expose..."),
                        color = if (exposedUri.isEmpty()) TextMuted else GreenAccent,
                        fontSize = 14.sp, modifier = Modifier.padding(16.dp), maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ─── SAVE CONFIG BUTTON ───
        Button(
            onClick = { onSaveConfig(deviceNameInput, downloadUri, exposedUri) },
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BgBase),
            modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom nav
    }
}

// Helper to clean up Android's ugly DocumentTree URIs for the UI
fun formatUriDisplay(uriString: String, fallback: String): String {
    if (uriString.isBlank()) return fallback
    return try {
        val decoded = URLDecoder.decode(uriString, "UTF-8")
        // Extracts the actual path part from "content://.../tree/primary:Downloads/LanSync"
        decoded.substringAfterLast(":")
    } catch (e: Exception) {
        "Custom Folder Selected"
    }
}
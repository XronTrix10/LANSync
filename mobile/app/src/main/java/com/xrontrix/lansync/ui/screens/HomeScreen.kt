package com.xrontrix.lansync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrontrix.lansync.data.RecentDevice
import com.xrontrix.lansync.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    deviceName: String,
    isNetworkAvailable: Boolean,
    localIP: String,
    activeDeviceIP: String?,
    recentDevices: List<RecentDevice>,
    isConnecting: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRemoveRecentDevice: (String) -> Unit
) {
    var ipInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color(0xFF3d4d63))) { append("Lan") }
                withStyle(style = SpanStyle(color = GreenAccent)) { append("Sync") }
            },
            fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp
        )
        Text(text = deviceName, fontSize = 14.sp, color = TextMuted)

        Surface(
            color = Panel, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(if (isNetworkAvailable) GreenAccent else RedAccent, RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isNetworkAvailable) "IP: $localIP" else "No Network", fontSize = 12.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!isNetworkAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedAccent.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.WifiOff, contentDescription = "No Wifi", tint = RedAccent, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Network Disconnected", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Please connect to Wi-Fi to use LanSync.", color = TextMuted, textAlign = TextAlign.Center, fontSize = 13.sp)
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                border = androidx.compose.foundation.BorderStroke(1.dp, Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Connect to Device", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        placeholder = { Text("192.168.1.X", color = TextMuted.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Surface,
                            focusedContainerColor = BgBase, unfocusedContainerColor = BgBase,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        ),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onConnect(ipInput) },
                        enabled = ipInput.isNotBlank() && !isConnecting,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(alpha = 0.15f), contentColor = Accent),
                        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
                        } else {
                            Text("Connect", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── CONNECTED DEVICE ───
        if (activeDeviceIP != null) {
            val activeDevice = recentDevices.find { it.ip == activeDeviceIP } ?: RecentDevice(activeDeviceIP, "Connected PC")

            Text("CONNECTED DEVICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenAccent, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 8.dp))
            Surface(
                color = GreenAccent.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Computer, contentDescription = "PC", tint = GreenAccent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeDevice.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(activeDevice.ip, color = GreenAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = onDisconnect, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Disconnect", tint = RedAccent)
                    }
                }
            }
        }

        // ─── RECENT DEVICES LIST ───
        val filteredRecent = recentDevices.filter { it.ip != activeDeviceIP }
        if (filteredRecent.isNotEmpty()) {
            Text("RECENT DEVICES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredRecent) { device ->
                    Surface(
                        color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Surface),
                        modifier = Modifier.fillMaxWidth().clickable { onConnect(device.ip) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.History, contentDescription = "History", tint = TextMuted)
                            Spacer(modifier = Modifier.width(16.dp))

                            // Emphasize the Name, deprioritize the IP
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(device.ip, color = TextMuted.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
                            }

                            // ─── CLEAR BUTTON ───
                            IconButton(
                                onClick = { onRemoveRecentDevice(device.ip) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextMuted.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
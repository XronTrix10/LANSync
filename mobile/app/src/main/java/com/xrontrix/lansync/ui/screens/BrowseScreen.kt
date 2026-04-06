package com.xrontrix.lansync.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrontrix.lansync.ui.theme.*
import kotlin.math.log10
import kotlin.math.pow

data class FileInfo(val name: String, val path: String, val size: Long, val isDir: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    activeDeviceIP: String?,
    currentPath: String,
    parentPath: String,
    files: List<FileInfo>,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    onShareClipboardClick: () -> Unit,
    onUploadFiles: (List<Uri>) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDownloadFiles: (List<FileInfo>) -> Unit,
    onRefresh: () -> Unit
) {
    if (activeDeviceIP == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Cable, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No device connected", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        return
    }

    // ─── STATE MANAGEMENT ───
    var selectedFiles by remember { mutableStateOf(setOf<FileInfo>()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // Clear selection if we navigate to a new folder
    LaunchedEffect(currentPath) {
        selectedFiles = emptySet()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onUploadFiles(uris)
    }

    // ─── DIALOG ───
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = newFolderName, onValueChange = { newFolderName = it },
                    placeholder = { Text("Folder Name", color = TextMuted.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Surface,
                        focusedContainerColor = BgBase, unfocusedContainerColor = BgBase,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName)
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BgBase)
                ) { Text("Create", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel", color = TextMuted) } },
            containerColor = Panel
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── DYNAMIC TOP HEADER ───
            if (selectedFiles.isNotEmpty()) {
                // Contextual Action Bar (Selection Mode)
                Surface(color = Accent.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedFiles = emptySet() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${selectedFiles.size} selected", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))

                        // Batch Download Button
                        IconButton(
                            onClick = {
                                onDownloadFiles(selectedFiles.toList())
                                selectedFiles = emptySet()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Accent)
                        }
                    }
                }
            } else {
                // Standard Breadcrumbs Bar
                Surface(color = Surface.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onNavigate(if (parentPath.isEmpty()) "/" else parentPath) }, enabled = currentPath != "/", modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Up", tint = if (currentPath == "/") TextMuted.copy(alpha = 0.3f) else TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(currentPath, color = TextMuted, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)

                        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Accent)
                        }
                    }
                }
            }
            Divider(color = Panel)

            // ─── FILE LIST ───
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(files) { file ->
                        val isSelected = selectedFiles.contains(file)
                        FileRowItem(
                            file = file,
                            isSelected = isSelected,
                            onClick = {
                                if (file.isDir) {
                                    onNavigate(file.path)
                                } else {
                                    // Toggle selection
                                    selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                                }
                            }
                        )
                    }
                }
            }
        }

        // ─── FLOATING ACTION BUTTONS ───
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(
                onClick = onShareClipboardClick,
                containerColor = Color(0xFFa78bfa).copy(alpha = 0.15f), contentColor = Color(0xFFa78bfa),
                modifier = Modifier.padding(bottom = 12.dp), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "Share Clipboard")
            }

            var showAddMenu by remember { mutableStateOf(false) }
            Box {
                FloatingActionButton(onClick = { showAddMenu = true }, containerColor = Accent, contentColor = BgBase, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Actions")
                }

                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }, modifier = Modifier.background(Panel)) {
                    DropdownMenuItem(
                        text = { Text("Upload File", color = TextPrimary) },
                        onClick = {
                            showAddMenu = false
                            filePickerLauncher.launch("*/*")
                        },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, tint = Accent, contentDescription = null) }
                    )
                    Divider(color = Surface, modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Create Folder", color = TextPrimary) },
                        onClick = {
                            showAddMenu = false
                            showCreateFolderDialog = true
                        },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, tint = GreenAccent, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileRowItem(file: FileInfo, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Accent.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show CheckCircle if selected, otherwise show normal file/folder icon
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = Accent, modifier = Modifier.size(24.dp))
        } else {
            Icon(imageVector = if (file.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile, contentDescription = null, tint = if (file.isDir) Accent else TextMuted, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = if (file.isDir) TextPrimary else (if (isSelected) Accent else TextMuted), fontSize = 14.sp, fontWeight = if (file.isDir || isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            if (!file.isDir) {
                Text(formatSize(file.size), color = TextMuted.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    Divider(color = Panel, modifier = Modifier.padding(start = 60.dp))
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
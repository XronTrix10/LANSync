package com.xrontrix.lansync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import bridge.Bridge
import bridge.BridgeCallback
import com.xrontrix.lansync.data.PreferencesManager
import com.xrontrix.lansync.data.RecentDevice
import com.xrontrix.lansync.ui.screens.BrowseScreen
import com.xrontrix.lansync.ui.screens.HomeScreen
import com.xrontrix.lansync.ui.theme.LansyncTheme
import com.xrontrix.lansync.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.provider.OpenableColumns
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.content.Intent

class MainActivity : ComponentActivity(), BridgeCallback {

    private var isNetworkAvailable = mutableStateOf(false)
    private var isConnecting = mutableStateOf(false)
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var prefsManager: PreferencesManager

    private val recentDevicesState = mutableStateOf<List<RecentDevice>>(emptyList())
    private var currentPath = mutableStateOf("/")
    private var parentPath = mutableStateOf("")
    private var remoteFiles = mutableStateOf<List<com.xrontrix.lansync.ui.screens.FileInfo>>(emptyList())
    private var isLoadingFiles = mutableStateOf(false)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // Automatically check if we have a valid IP whenever network state changes
        override fun onAvailable(network: Network) {
            isNetworkAvailable.value = getLocalIPAddress() != null
        }
        override fun onLost(network: Network) {
            isNetworkAvailable.value = getLocalIPAddress() != null
        }
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Listen to ALL network types (including Cellular for Mobile Hotspots), not just Wi-Fi
        val request = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Initial check: If we have an IP address that isn't localhost, the network is ready
        val ip = getLocalIPAddress()
        isNetworkAvailable.value = (ip != null && ip != "127.0.0.1")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Ask for Native File Permissions immediately
        checkAndRequestStoragePermissions()

        prefsManager = PreferencesManager(this)
        recentDevicesState.value = prefsManager.getRecentDevices()

        setupNetworkMonitoring()
        Bridge.startupWithCallback(this)

        // 2. FETCH SAVED SETTINGS & BOOT THE GO BACKEND!
        val sharedPrefs = getSharedPreferences("lansync_prefs", Context.MODE_PRIVATE)
        val exposedUri = sharedPrefs.getString("exposed_folder", "") ?: ""
        val realExposedPath = getRealPathFromURI(exposedUri)

        try {
            // This starts your local Go server running silently in the background!
            Bridge.startMobileServer(realExposedPath)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            LansyncTheme {
                val navController = rememberNavController()
                var activeDeviceIP by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        NavigationBar(containerColor = Surface, contentColor = TextMuted) {
                            NavigationBarItem(icon = { Icon(Icons.Filled.Home, "Home") }, label = { Text("Home") }, selected = currentRoute == "home", onClick = { navController.navigate("home") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = 0.15f), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))

                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Folder, "Browse") },
                                label = { Text("Browse") },
                                selected = currentRoute == "browse",
                                onClick = {
                                    if (activeDeviceIP != null) {
                                        navController.navigate("browse")
                                    } else {
                                        Toast.makeText(this@MainActivity, "Connect to a PC first!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = 0.15f), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                            )

                            NavigationBarItem(icon = { Icon(Icons.Filled.Settings, "Settings") }, label = { Text("Settings") }, selected = currentRoute == "settings", onClick = { navController.navigate("settings") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = 0.15f), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
                        }
                    },
                    containerColor = BgBase
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
                        composable("home") {
                            val sharedPrefs = getSharedPreferences("lansync_prefs", Context.MODE_PRIVATE)
                            val savedName = sharedPrefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL

                            HomeScreen(
                                deviceName = savedName,
                                isNetworkAvailable = isNetworkAvailable.value,
                                localIP = getLocalIPAddress() ?: "127.0.0.1",
                                activeDeviceIP = activeDeviceIP,
                                recentDevices = recentDevicesState.value,
                                isConnecting = isConnecting.value,
                                onConnect = { ip -> connectToDevice(ip) { success -> if (success) activeDeviceIP = ip } },
                                onDisconnect = {
                                    activeDeviceIP?.let { ip -> disconnectFromDevice(ip) }
                                    activeDeviceIP = null
                                },
                                // ── FIX: Wired up the Remove logic! ──
                                onRemoveRecentDevice = { ipToRemove ->
                                    prefsManager.removeDevice(ipToRemove)
                                    recentDevicesState.value = prefsManager.getRecentDevices()
                                    Toast.makeText(this@MainActivity, "Device removed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        composable("browse") {
                            LaunchedEffect(activeDeviceIP) {
                                activeDeviceIP?.let { ip -> fetchRemoteFiles(ip, currentPath.value) }
                            }

                            BrowseScreen(
                                activeDeviceIP = activeDeviceIP,
                                currentPath = currentPath.value,
                                parentPath = parentPath.value,
                                files = remoteFiles.value,
                                isLoading = isLoadingFiles.value,
                                onNavigate = { newPath -> activeDeviceIP?.let { ip -> fetchRemoteFiles(ip, newPath) } },
                                onShareClipboardClick = { activeDeviceIP?.let { ip -> shareMobileTextWithDesktop(ip, "34931") } },
                                onCreateFolder = { folderName ->
                                    activeDeviceIP?.let { ip -> createRemoteFolder(ip, currentPath.value, folderName) }
                                },
                                onUploadFiles = { uris ->
                                    activeDeviceIP?.let { ip -> uploadFilesToDesktop(ip, currentPath.value, uris) }
                                },
                                onDownloadFiles = { selectedFiles ->
                                    activeDeviceIP?.let { ip -> downloadFilesFromDesktop(ip, selectedFiles) }
                                },
                                onRefresh = {
                                    activeDeviceIP?.let { ip -> fetchRemoteFiles(ip, currentPath.value) }
                                }
                            )
                        }
                        composable("settings") {
                            val sharedPrefs = getSharedPreferences("lansync_prefs", Context.MODE_PRIVATE)
                            val savedName = sharedPrefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
                            val savedDownloadUri = sharedPrefs.getString("download_folder", "") ?: ""
                            val savedExposedUri = sharedPrefs.getString("exposed_folder", "") ?: ""

                            com.xrontrix.lansync.ui.screens.SettingsScreen(
                                currentDeviceName = savedName,
                                currentDownloadFolderUri = savedDownloadUri,
                                currentExposedFolderUri = savedExposedUri,
                                onSaveConfig = { name, download, exposed ->
                                    sharedPrefs.edit()
                                        .putString("device_name", name)
                                        .putString("download_folder", download)
                                        .putString("exposed_folder", exposed)
                                        .apply()

                                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    try {
                                        if (download.isNotBlank()) {
                                            contentResolver.takePersistableUriPermission(Uri.parse(download), takeFlags)
                                        }
                                        if (exposed.isNotBlank()) {
                                            contentResolver.takePersistableUriPermission(Uri.parse(exposed), takeFlags)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    Toast.makeText(this@MainActivity, "Configuration Saved!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getLocalIPAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) { }
        return null
    }

    private fun connectToDevice(ip: String, onResult: (Boolean) -> Unit) {
        isConnecting.value = true
        Toast.makeText(this, "Asking to connect...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                // FIX: This now returns the actual Hostname of your Mac/PC!
                val deviceName = Bridge.requestConnection(ip, "34931")

                runOnUiThread {
                    isConnecting.value = false
                    if (deviceName.isNotEmpty()) {
                        Toast.makeText(this, "Connected securely!", Toast.LENGTH_SHORT).show()

                        // Cleanly save the real name, completely dropping the redundant "Desktop (IP)"
                        prefsManager.saveRecentDevice(ip, deviceName)
                        recentDevicesState.value = prefsManager.getRecentDevices()

                        onResult(true)
                    } else {
                        Toast.makeText(this, "Connection declined", Toast.LENGTH_SHORT).show()
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isConnecting.value = false
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            }
        }.start()
    }

    override fun onClipboardDataReceived(data: ByteArray?, contentType: String?) {
        if (data != null && contentType?.startsWith("text/") == true) {
            val text = String(data, Charsets.UTF_8)
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            runOnUiThread {
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("LanSync", text))
                Toast.makeText(this@MainActivity, "Desktop text copied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchRemoteFiles(ip: String, path: String) {
        isLoadingFiles.value = true

        Thread {
            try {
                val jsonString = Bridge.getRemoteFilesJson(ip, "34931", path)

                val jsonObject = JSONObject(jsonString)
                val newCurrentPath = jsonObject.optString("path", "/")
                val newParentPath = jsonObject.optString("parent", "")
                val filesArray: JSONArray? = jsonObject.optJSONArray("files")

                val parsedFiles = mutableListOf<com.xrontrix.lansync.ui.screens.FileInfo>()
                if (filesArray != null) {
                    for (i in 0 until filesArray.length()) {
                        val f = filesArray.getJSONObject(i)
                        parsedFiles.add(
                            com.xrontrix.lansync.ui.screens.FileInfo(
                                name = f.getString("name"),
                                path = f.getString("path"),
                                size = f.optLong("size", 0),
                                isDir = f.getBoolean("isDir")
                            )
                        )
                    }
                }

                runOnUiThread {
                    currentPath.value = newCurrentPath
                    parentPath.value = newParentPath
                    remoteFiles.value = parsedFiles
                    isLoadingFiles.value = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to load files: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoadingFiles.value = false
                }
            }
        }.start()
    }

    private fun shareMobileTextWithDesktop(targetIP: String, port: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (!clipboardManager.hasPrimaryClip() || clipboardManager.primaryClip?.getItemAt(0)?.text == null) {
            runOnUiThread { Toast.makeText(this, "Mobile clipboard is empty", Toast.LENGTH_SHORT).show() }
            return
        }
        val text = clipboardManager.primaryClip!!.getItemAt(0).text.toString()
        Thread {
            try {
                Bridge.shareMobileClipboard(targetIP, port, text.toByteArray(Charsets.UTF_8), "text/plain")
                runOnUiThread { Toast.makeText(this@MainActivity, "Sent to Desktop!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Share failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun createRemoteFolder(ip: String, currentPath: String, folderName: String) {
        Thread {
            try {
                // Call the Go function we just exposed
                Bridge.makeDirectory(ip, "34931", currentPath, folderName)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Folder created!", Toast.LENGTH_SHORT).show()
                    fetchRemoteFiles(ip, currentPath) // Refresh the UI
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun uploadFilesToDesktop(ip: String, currentPath: String, uris: List<Uri>) {
        val token = Bridge.getSessionToken(ip)
        if (token.isEmpty()) {
            Toast.makeText(this, "Session expired.", Toast.LENGTH_SHORT).show()
            return
        }

        isLoadingFiles.value = true // Show the spinner while uploading

        Thread {
            var successCount = 0
            val encodedPath = URLEncoder.encode(currentPath, "UTF-8")

            for (uri in uris) {
                try {
                    // 1. Extract the actual file name from Android's weird content:// URI format
                    var fileName = "upload.file"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }

                    // 2. Open an HTTP POST connection to the Desktop's Go Server
                    val url = URL("http://$ip:34931/api/files/upload?dir=$encodedPath")
                    val connection = url.openConnection() as HttpURLConnection
                    val boundary = "Boundary-${System.currentTimeMillis()}"

                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connection.doOutput = true
                    // Prevents OutOfMemory errors on massive files by streaming chunks
                    connection.setChunkedStreamingMode(1024 * 1024)

                    // 3. Write the Multipart Form Data
                    DataOutputStream(connection.outputStream).use { outputStream ->
                        outputStream.writeBytes("--$boundary\r\n")
                        outputStream.writeBytes("Content-Disposition: form-data; name=\"files\"; filename=\"$fileName\"\r\n")
                        outputStream.writeBytes("Content-Type: application/octet-stream\r\n\r\n")

                        // Stream the file directly from Android storage to the PC
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }

                        outputStream.writeBytes("\r\n--$boundary--\r\n")
                        outputStream.flush()
                    }

                    if (connection.responseCode == 200) successCount++
                    connection.disconnect()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Uploaded $successCount of ${uris.size} files", Toast.LENGTH_SHORT).show()
                fetchRemoteFiles(ip, currentPath) // Refresh the folder view
            }
        }.start()
    }

    private fun downloadFilesFromDesktop(ip: String, files: List<com.xrontrix.lansync.ui.screens.FileInfo>) {
        val token = Bridge.getSessionToken(ip)
        if (token.isEmpty()) {
            Toast.makeText(this, "Session expired.", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        var startedCount = 0

        for (file in files) {
            try {
                val encodedPath = java.net.URLEncoder.encode(file.path, "UTF-8")
                val url = "http://$ip:34931/api/files/download?path=$encodedPath"

                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                    .setTitle(file.name)
                    .setDescription("Downloading via LanSync")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "LanSync/${file.name}")
                    .addRequestHeader("Authorization", "Bearer $token")

                downloadManager.enqueue(request)
                startedCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Toast.makeText(this, "Started $startedCount download(s)", Toast.LENGTH_SHORT).show()
    }

    // 1. Prompts the user to grant "All Files Access" if they haven't already
    private fun checkAndRequestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    // 2. Translates SAF URI to a Go-friendly Linux path
    private fun getRealPathFromURI(uriString: String): String {
        val defaultPath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath + "/LanSync"
        if (uriString.isBlank()) return defaultPath

        return try {
            val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")
            if (decoded.contains("primary:")) {
                val path = decoded.substringAfterLast("primary:")
                "/storage/emulated/0/$path"
            } else {
                defaultPath
            }
        } catch (e: Exception) {
            defaultPath
        }
    }

    private fun disconnectFromDevice(ip: String) {
        try {
            Bridge.disconnectDevice(ip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    override fun onDeviceDropped(ip: String?) {
        runOnUiThread { Toast.makeText(this, "Device disconnected: $ip", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        Bridge.shutdown()
    }
}
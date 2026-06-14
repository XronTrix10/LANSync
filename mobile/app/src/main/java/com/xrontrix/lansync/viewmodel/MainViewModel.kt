package com.xrontrix.lansync.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import bridge.Bridge
import bridge.BridgeCallback
import com.xrontrix.lansync.data.PreferencesManager
import com.xrontrix.lansync.data.RecentDevice
import com.xrontrix.lansync.network.FileTransferManager
import com.xrontrix.lansync.ui.screens.FileInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DiscoveredDevice(
    val ip: String,
    val deviceName: String,
    val os: String,
    val deviceId: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application), BridgeCallback {

    private val context: Context get() = getApplication<Application>().applicationContext
    private fun runOnUiThread(action: () -> Unit) = Handler(Looper.getMainLooper()).post(action)

    val isNetworkAvailable = mutableStateOf(false)
    val currentLocalIP = mutableStateOf<String?>(null)

    val activeDeviceIP = mutableStateOf<String?>(null)
    val isConnecting = mutableStateOf(false)
    val activeDeviceOS = mutableStateOf("windows")
    val incomingRequest = mutableStateOf<Triple<String, String, String>?>(null)
    val incomingAutoConnectRequest = mutableStateOf<Triple<String, String, String>?>(null) // ── Auto Connect Prompt State ──

    val recentDevicesState = mutableStateOf<List<RecentDevice>>(emptyList())
    val currentPath = mutableStateOf("/")
    val parentPath = mutableStateOf("")
    val remoteFiles = mutableStateOf<List<FileInfo>>(emptyList())
    val isLoadingFiles = mutableStateOf(false)
    val discoveredDevices = mutableStateOf<List<DiscoveredDevice>>(emptyList())
    val clearIPInputTrigger = mutableIntStateOf(0)

    private lateinit var prefsManager: PreferencesManager
    private lateinit var transferManager: FileTransferManager
    private val connectingToIds = mutableSetOf<String>()

    var onToggleForegroundService: ((Boolean) -> Unit)? = null

    fun initialize(prefs: PreferencesManager, transfer: FileTransferManager) {
        prefsManager = prefs
        transferManager = transfer
        recentDevicesState.value = prefsManager.getRecentDevices()
    }

    fun removeRecentDevice(ip: String) {
        prefsManager.removeDevice(ip)
        recentDevicesState.value = prefsManager.getRecentDevices()
        Toast.makeText(context, "Device removed", Toast.LENGTH_SHORT).show()
    }

    // ── 1. OUTGOING AUTO-CONNECT TOGGLE ──
    fun toggleAutoConnect(ip: String, deviceId: String, enable: Boolean) {
        Thread {
            try {
                if (enable) {
                    runOnUiThread { Toast.makeText(context, "Sending request...", Toast.LENGTH_SHORT).show() }
                    Bridge.requestAutoConnect(ip)
                    runOnUiThread {
                        prefsManager.updateAutoConnect(deviceId, true)
                        recentDevicesState.value = prefsManager.getRecentDevices()
                        Toast.makeText(context, "Auto-Connect Enabled!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread { Toast.makeText(context, "Disabling...", Toast.LENGTH_SHORT).show() }
                    Bridge.disableAutoConnect(ip)
                    runOnUiThread {
                        prefsManager.updateAutoConnect(deviceId, false)
                        recentDevicesState.value = prefsManager.getRecentDevices()
                        Toast.makeText(context, "Auto-Connect Disabled", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    // ── 2. RESOLVE INCOMING AUTO-CONNECT PROMPT ──
    fun resolveAutoConnect(accept: Boolean) {
        val req = incomingAutoConnectRequest.value ?: return
        Thread { Bridge.resolveAutoConnect(req.first, accept) }.start()

        if (accept) {
            prefsManager.updateAutoConnect(req.third, true)
            recentDevicesState.value = prefsManager.getRecentDevices()
            Toast.makeText(context, "Auto-Connect enabled for ${req.second}", Toast.LENGTH_SHORT).show()
        }
        incomingAutoConnectRequest.value = null
    }

    fun connectToDevice(ip: String, onResult: (Boolean) -> Unit) {
        isConnecting.value = true
        Toast.makeText(context, "Asking to connect...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val identity = fetchDeviceIdentity(ip)
                val os = identity?.second ?: "windows"
                val deviceIdFallback = identity?.third ?: ""

                // This returns a JSON string of models.ConnectionResponse
                val responseJsonStr = Bridge.requestConnection(ip, "34931")

                runOnUiThread {
                    isConnecting.value = false
                    if (responseJsonStr.isNotEmpty()) {

                        // ── Parse the JSON string from Gomobile ──
                        val json = JSONObject(responseJsonStr)
                        val connectedDeviceName = json.optString("deviceName", "Unknown Device")
                        val deviceId = json.optString("deviceId", deviceIdFallback)

                        activeDeviceOS.value = os
                        activeDeviceIP.value = ip
                        prefsManager.saveRecentDevice(ip, connectedDeviceName, os, deviceId)
                        recentDevicesState.value = prefsManager.getRecentDevices()
                        onToggleForegroundService?.invoke(true)
                        clearIPInputTrigger.intValue++
                        Toast.makeText(context, "Connected securely!", Toast.LENGTH_SHORT).show()
                        onResult(true)
                    } else {
                        Toast.makeText(context, "Connection declined", Toast.LENGTH_SHORT).show()
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isConnecting.value = false
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            }
        }.start()
    }

    fun disconnectFromDevice(ip: String) {
        try { Bridge.disconnectDevice(ip) } catch (e: Exception) { e.printStackTrace() }
        activeDeviceIP.value = null
        Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    private fun fetchDeviceIdentity(ip: String): Triple<String, String, String>? {
        return try {
            val url = URL("http://$ip:34931/api/identify")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            Triple(json.optString("deviceName", "Unknown"), json.optString("os", "windows"), json.optString("deviceId", ""))
        } catch (_: Exception) { null }
    }

    // ── 3. INBOUND AUTO-ACCEPT CONNECTION ──
    override fun onConnectionRequested(ip: String?, deviceName: String?) {
        if (ip == null || deviceName == null) return

        // Check if we trust them for auto-accept
        val savedDevice = prefsManager.getRecentDevices().find { it.ip == ip || it.name == deviceName }
        if (savedDevice != null && savedDevice.autoConnect) {
            runOnUiThread {
                Bridge.resolveConnection(ip, true)
                activeDeviceIP.value = ip
                activeDeviceOS.value = savedDevice.os
                prefsManager.saveRecentDevice(ip, deviceName, savedDevice.os, savedDevice.deviceId, true)
                recentDevicesState.value = prefsManager.getRecentDevices()
                onToggleForegroundService?.invoke(true)
                Toast.makeText(context, "Auto-connected to $deviceName", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Thread {
            val identity = fetchDeviceIdentity(ip)
            val os = identity?.second ?: "windows"
            runOnUiThread { incomingRequest.value = Triple(ip, deviceName, os) }
        }.start()
    }

    // ── 4. LEADER ELECTION (OUTBOUND AUTO-CONNECT) ──
    override fun onDevicesDiscovered(jsonString: String?) {
        if (jsonString == null) return
        Thread {
            try {
                val jsonArray = JSONArray(jsonString)
                val devs = mutableListOf<DiscoveredDevice>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    devs.add(DiscoveredDevice(
                        ip = obj.getString("ip"),
                        deviceName = obj.getString("deviceName"),
                        os = obj.getString("os"),
                        deviceId = obj.optString("deviceId", "")
                    ))
                }

                val myIPs = currentLocalIP.value ?: ""
                val myId = prefsManager.getLocalDeviceId() // ── Fetch Local ID ──

                val filteredDevs = devs.filter {
                    it.deviceId != myId && // ── Primary ID Check ──
                            it.ip != myIPs &&
                            it.ip != "127.0.0.1" &&
                            !it.ip.startsWith("192.0.0.")
                }

                runOnUiThread { discoveredDevices.value = filteredDevs }

                // The Tie-Breaker Logic
                if (activeDeviceIP.value == null) {
                    val myId = prefsManager.getLocalDeviceId()
                    val recentList = prefsManager.getRecentDevices()

                    filteredDevs.forEach { availableDevice ->
                        val savedDevice = recentList.find { it.deviceId == availableDevice.deviceId }
                        if (savedDevice != null && savedDevice.autoConnect) {
                            if (myId > availableDevice.deviceId && !connectingToIds.contains(availableDevice.deviceId)) {
                                connectingToIds.add(availableDevice.deviceId)
                                connectToDevice(availableDevice.ip) {
                                    connectingToIds.remove(availableDevice.deviceId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    override fun onAutoConnectRequested(ip: String?, deviceName: String?, deviceId: String?) {
        if (ip == null || deviceName == null || deviceId == null) return
        runOnUiThread { incomingAutoConnectRequest.value = Triple(ip, deviceName, deviceId) }
    }

    override fun onAutoConnectDisabled(ip: String?, deviceName: String?, deviceId: String?) {
        if (deviceId == null) return
        runOnUiThread {
            prefsManager.updateAutoConnect(deviceId, false)
            recentDevicesState.value = prefsManager.getRecentDevices()
            Toast.makeText(context, "Auto-Connect disabled by $deviceName", Toast.LENGTH_SHORT).show()
        }
    }

    // Keep remaining methods (fetchRemoteFiles, upload, download, acceptIncomingConnection, etc) exactly as they were!
    fun acceptIncomingConnection() {
        val req = incomingRequest.value ?: return
        Bridge.resolveConnection(req.first, true)
        activeDeviceIP.value = req.first
        prefsManager.saveRecentDevice(req.first, req.second, req.third)
        recentDevicesState.value = prefsManager.getRecentDevices()
        onToggleForegroundService?.invoke(true)
        incomingRequest.value = null
        Toast.makeText(context, "Connected to ${req.second}", Toast.LENGTH_SHORT).show()
    }

    fun rejectIncomingConnection() {
        val req = incomingRequest.value ?: return
        Bridge.resolveConnection(req.first, false)
        incomingRequest.value = null
    }

    fun fetchRemoteFiles(ip: String, path: String) { /* Keep as is */ }
    fun shareMobileTextWithDesktop(targetIP: String) { /* Keep as is */ }
    fun createRemoteFolder(ip: String, currentPath: String, folderName: String) { /* Keep as is */ }
    fun uploadFiles(ip: String, path: String, uris: List<Uri>) { /* Keep as is */ }
    fun uploadFolder(ip: String, path: String, treeUri: Uri) { /* Keep as is */ }
    fun downloadFiles(ip: String, selectedFiles: List<FileInfo>) { /* Keep as is */ }
    override fun onDeviceDropped(ip: String?) { /* Keep as is */ }
    override fun onClipboardDataReceived(data: ByteArray?, contentType: String?) { /* Keep as is */ }
}
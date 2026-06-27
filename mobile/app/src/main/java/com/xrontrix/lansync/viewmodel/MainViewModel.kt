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
    val incomingAutoConnectRequest = mutableStateOf<Triple<String, String, String>?>(null)

    val recentDevicesState = mutableStateOf<List<RecentDevice>>(emptyList())
    val currentPath = mutableStateOf("/")
    val parentPath = mutableStateOf("")
    val remoteFiles = mutableStateOf<List<FileInfo>>(emptyList())
    val isLoadingFiles = mutableStateOf(false)
    val discoveredDevices = mutableStateOf<List<DiscoveredDevice>>(emptyList())
    val clearIPInputTrigger = mutableIntStateOf(0)

    private lateinit var prefsManager: PreferencesManager
    private lateinit var transferManager: FileTransferManager

    // ── THE IN-MEMORY SESSION LOCK ──
    private val autoHandledSessionIds = mutableSetOf<String>()

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

                val responseJsonStr = Bridge.requestConnection(ip, "34931")

                runOnUiThread {
                    isConnecting.value = false
                    if (responseJsonStr.isNotEmpty()) {
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

    override fun onConnectionRequested(ip: String?, deviceName: String?) {
        if (ip == null || deviceName == null) return

        val savedDevice = prefsManager.getRecentDevices().find { it.ip == ip || it.name == deviceName }

        // ── INBOUND SESSION CHECK ──
        if (savedDevice != null && savedDevice.autoConnect) {
            if (!autoHandledSessionIds.contains(savedDevice.deviceId)) {
                autoHandledSessionIds.add(savedDevice.deviceId) // Lock it
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
        }

        // ── FALLBACK TO MANUAL PROMPT ──
        Thread {
            val identity = fetchDeviceIdentity(ip)
            val os = identity?.second ?: "windows"
            runOnUiThread { incomingRequest.value = Triple(ip, deviceName, os) }
        }.start()
    }

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
                val myId = prefsManager.getLocalDeviceId()

                val filteredDevs = devs.filter {
                    it.deviceId != myId &&
                            it.ip != myIPs &&
                            it.ip != "127.0.0.1" &&
                            !it.ip.startsWith("192.0.0.")
                }

                runOnUiThread { discoveredDevices.value = filteredDevs }

                if (activeDeviceIP.value == null) {
                    val recentList = prefsManager.getRecentDevices()

                    filteredDevs.forEach { availableDevice ->
                        val savedDevice = recentList.find { it.deviceId == availableDevice.deviceId }
                        if (savedDevice != null && savedDevice.autoConnect) {
                            if (myId > availableDevice.deviceId) {
                                // ── OUTBOUND SESSION CHECK ──
                                if (!autoHandledSessionIds.contains(availableDevice.deviceId)) {
                                    autoHandledSessionIds.add(availableDevice.deviceId) // Lock it
                                    connectToDevice(availableDevice.ip) { }
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

    fun fetchRemoteFiles(ip: String, path: String) { /* ... */ }
    fun shareMobileTextWithDesktop(targetIP: String) { /* ... */ }
    fun createRemoteFolder(ip: String, currentPath: String, folderName: String) { /* ... */ }
    fun uploadFiles(ip: String, path: String, uris: List<Uri>) { /* ... */ }
    fun uploadFolder(ip: String, path: String, treeUri: Uri) { /* ... */ }
    fun downloadFiles(ip: String, selectedFiles: List<FileInfo>) { /* ... */ }
    override fun onDeviceDropped(ip: String?) { /* ... */ }
    override fun onClipboardDataReceived(data: ByteArray?, contentType: String?) { /* ... */ }
}
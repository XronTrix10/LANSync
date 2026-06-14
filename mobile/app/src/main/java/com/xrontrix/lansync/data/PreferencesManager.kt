package com.xrontrix.lansync.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import androidx.core.content.edit

data class RecentDevice(
    val ip: String,
    val name: String,
    val os: String = "windows",
    val deviceId: String = "",
    val autoConnect: Boolean = false
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lansync_prefs", Context.MODE_PRIVATE)

    fun getLocalDeviceId(): String {
        var id = prefs.getString("local_device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit { putString("local_device_id", id) }
        }
        return id
    }

    fun saveRecentDevice(ip: String, name: String, os: String = "windows", deviceId: String = "", autoConnect: Boolean? = null) {
        val currentDevices = getRecentDevices().toMutableList()
        val existing = currentDevices.find { it.ip == ip || it.name == name }

        val finalAutoConnect = autoConnect ?: existing?.autoConnect ?: false
        val finalDeviceId = deviceId.ifEmpty { existing?.deviceId ?: "" }

        currentDevices.removeAll { it.ip == ip || it.name == name }
        currentDevices.add(0, RecentDevice(ip, name, os, finalDeviceId, finalAutoConnect))
        val trimmedDevices = currentDevices.take(5)

        prefs.edit().apply {
            putString("recent_ips", trimmedDevices.joinToString(",") { it.ip })
            trimmedDevices.forEach {
                putString("device_name_${it.ip}", it.name)
                putString("device_os_${it.ip}", it.os)
                putString("device_id_${it.ip}", it.deviceId)
                putBoolean("device_auto_${it.ip}", it.autoConnect)
            }
            apply()
        }
    }

    fun updateAutoConnect(deviceId: String, autoConnect: Boolean) {
        val currentDevices = getRecentDevices().map {
            if (it.deviceId == deviceId) it.copy(autoConnect = autoConnect) else it
        }
        prefs.edit().apply {
            currentDevices.forEach { putBoolean("device_auto_${it.ip}", it.autoConnect) }
            apply()
        }
    }

    fun getRecentDevices(): List<RecentDevice> {
        val ipString = prefs.getString("recent_ips", "") ?: ""
        if (ipString.isBlank()) return emptyList()

        return ipString.split(",").map { ip ->
            val name = prefs.getString("device_name_$ip", "Unknown Device") ?: "Unknown Device"
            val os = prefs.getString("device_os_$ip", "windows") ?: "windows"
            val deviceId = prefs.getString("device_id_$ip", "") ?: ""
            val autoConnect = prefs.getBoolean("device_auto_$ip", false)
            RecentDevice(ip, name, os, deviceId, autoConnect)
        }
    }

    fun removeDevice(ip: String) {
        val currentIPs = getRecentDevices().map { it.ip }.toMutableList()
        currentIPs.remove(ip)
        prefs.edit().apply {
            putString("recent_ips", currentIPs.joinToString(","))
            remove("device_name_$ip")
            remove("device_auto_$ip")
            apply()
        }
    }
}
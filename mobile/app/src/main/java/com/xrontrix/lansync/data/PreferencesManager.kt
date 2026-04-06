package com.xrontrix.lansync.data

import android.content.Context
import android.content.SharedPreferences

// Add the OS field with a default fallback so it doesn't break old saves
data class RecentDevice(val ip: String, val name: String, val os: String = "windows")

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lansync_prefs", Context.MODE_PRIVATE)

    fun saveRecentDevice(ip: String, name: String) {
        // Keep a history of the last 5 connected IPs
        val currentIPs = getRecentDevices().map { it.ip }.toMutableList()
        currentIPs.remove(ip) // Remove if exists to move it to the top
        currentIPs.add(0, ip)

        val trimmedIPs = currentIPs.take(5)

        prefs.edit().apply {
            putString("recent_ips", trimmedIPs.joinToString(","))
            putString("device_name_$ip", name)
            apply()
        }
    }

    fun getRecentDevices(): List<RecentDevice> {
        val ipString = prefs.getString("recent_ips", "") ?: ""
        if (ipString.isBlank()) return emptyList()

        return ipString.split(",").mapNotNull { ip ->
            val name = prefs.getString("device_name_$ip", "Unknown Device") ?: "Unknown Device"
            RecentDevice(ip, name)
        }
    }

    fun removeDevice(ip: String) {
        val currentIPs = getRecentDevices().map { it.ip }.toMutableList()
        currentIPs.remove(ip)
        prefs.edit().apply {
            putString("recent_ips", currentIPs.joinToString(","))
            remove("device_name_$ip")
            apply()
        }
    }
}
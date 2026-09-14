package com.jarvis.assistant.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceContextSnapshot(
    val timestamp: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val currentApp: String,
    val locationSummary: String,
    val connectionType: String,
    val brightnessPercent: Int,
    val volumePercent: Int,
    val dateText: String,
    val timeText: String
) {
    fun toMemorySummary(): String = listOf(
        "battery=$batteryPercent%",
        "charging=${isCharging}",
        "current_app=${currentApp}",
        "network=${connectionType}",
        "brightness=${brightnessPercent}%",
        "volume=${volumePercent}%",
        "time=${timeText}",
        "date=${dateText}"
    ).joinToString(" | ")
}

object DeviceContextSnapshotBuilder {
    fun build(context: Context): DeviceContextSnapshot {
        val battery = getBatteryPercent(context)
        val isCharging = getChargingState(context)
        val currentApp = getCurrentAppLabel(context)
        val connectionType = getConnectionType(context)
        val brightness = getBrightnessPercent(context)
        val volume = getVolumePercent(context)
        val now = Date()
        val dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now)
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        return DeviceContextSnapshot(
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now),
            batteryPercent = battery,
            isCharging = isCharging,
            currentApp = currentApp,
            locationSummary = "approximate device context",
            connectionType = connectionType,
            brightnessPercent = brightness,
            volumePercent = volume,
            dateText = dateText,
            timeText = timeText
        )
    }

    private fun getBatteryPercent(context: Context): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return 0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100f).toInt() else 0
    }

    private fun getChargingState(context: Context): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getCurrentAppLabel(context: Context): String {
        return try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val running = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activityManager?.runningAppProcesses?.firstOrNull()?.processName ?: "unknown"
            } else {
                "unknown"
            }
            running.substringAfterLast('.').ifBlank { running }
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getConnectionType(context: Context): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val network = connectivity.activeNetwork ?: return "offline"
        val caps = connectivity.getNetworkCapabilities(network) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "unknown"
        }
    }

    private fun getBrightnessPercent(context: Context): Int {
        val value = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return ((value / 255.0f) * 100f).toInt().coerceIn(0, 100)
    }

    private fun getVolumePercent(context: Context): Int {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return 0
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        return if (max > 0) ((current.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100) else 0
    }
}

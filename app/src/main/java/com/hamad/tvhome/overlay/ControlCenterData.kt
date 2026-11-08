package com.hamad.tvhome.overlay

import android.app.AppOpsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Process
import android.provider.Settings as AndroidSettings
import androidx.core.graphics.drawable.toBitmap

data class RecentApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
)

data class ControlSnapshot(
    val wifiStatus: String,
    val wifiDetail: String?,
    val bluetoothStatus: String?,
    val recents: List<RecentApp>,
    val usageGranted: Boolean,
)

/**
 * Everything the Control Center shows is read from the device: the real Wi-Fi
 * transport, the real Bluetooth adapter, and the real usage-stats recents list.
 * Nothing is synthesised when a source is unavailable — the row is simply left
 * out or says that access is missing.
 */
object ControlCenterData {

    private const val RECENTS_LIMIT = 8

    fun snapshot(context: Context): ControlSnapshot {
        val wifiManager = context.getSystemService(WifiManager::class.java)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val wifiEnabled = wifiManager?.isWifiEnabled == true
        val wifiStatus = when {
            wifiConnected -> "Connected"
            wifiEnabled -> "On"
            else -> "Off"
        }
        val ip = if (wifiConnected) {
            cm.getLinkProperties(network)?.linkAddresses
                ?.firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress }
                ?.address?.hostAddress
        } else {
            null
        }
        val bluetoothStatus = runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.let { if (it.isEnabled) "On" else "Off" }
        }.getOrNull()
        val usageGranted = hasUsageAccess(context)
        return ControlSnapshot(
            wifiStatus = wifiStatus,
            wifiDetail = ip,
            bluetoothStatus = bluetoothStatus,
            recents = if (usageGranted) recents(context) else emptyList(),
            usageGranted = usageGranted,
        )
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun recents(context: Context): List<RecentApp> {
        val usage = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usage.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60 * 60 * 1000,
                now,
            )
        }.getOrNull().orEmpty()
        val pm = context.packageManager
        return stats
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { it.lastTimeUsed > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .mapNotNull { stat -> entry(context, pm, stat.packageName) }
            .take(RECENTS_LIMIT)
            .toList()
    }

    private fun entry(context: Context, pm: PackageManager, packageName: String): RecentApp? {
        launchIntent(pm, packageName) ?: return null
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull() ?: return null
        val icon = runCatching {
            pm.getApplicationIcon(packageName).toBitmap(96, 96)
        }.getOrNull()
        return RecentApp(packageName, label, icon)
    }

    fun launchRecent(context: Context, packageName: String): Boolean {
        val intent = launchIntent(context.packageManager, packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun launchIntent(pm: PackageManager, packageName: String): Intent? =
        runCatching { pm.getLeanbackLaunchIntentForPackage(packageName) }.getOrNull()
            ?: runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull()

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = AndroidSettings.Secure.getString(
            context.contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val target = "${context.packageName}/${TvAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(target, ignoreCase = true) }
    }

    fun openWifiSettings(context: Context) {
        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openBluetoothSettings(context: Context) {
        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

package com.hamad.tvhome.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val removable: Boolean,
    val system: Boolean,
    val special: String? = null,
) {
    val key: String get() = packageName
}

class AppRepository(private val context: Context) {

    fun launchIntent(packageName: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
    }

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun loadLaunchableApps(prefs: TvPrefs): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val found = LinkedHashMap<String, ApplicationInfo>()

        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            pm.queryIntentActivities(intent, 0).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) return@forEach
                if (pkg == "com.android.settings") return@forEach
                if (pkg == "com.android.tv.settings") return@forEach
                if (found.containsKey(pkg)) return@forEach
                found[pkg] = ri.activityInfo.applicationInfo
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)

        val hidden = prefs.hiddenApps
        val order = prefs.appOrder

        // The stock YouTube TV app refuses to run on this box: it requires Play
        // services 20.34+ while the device ships 15.1.81. SmartTube is a standalone
        // client that works without Play services, so it replaces the stock tile.
        val smartTube = "org.smarttube.stable"
        val hasSmartTube = isInstalled(smartTube)
        val suppressed = if (hasSmartTube) setOf("com.google.android.youtube.tv") else emptySet()
        val labelOverrides = if (hasSmartTube) mapOf(smartTube to "YouTube") else emptyMap()

        // Vendor leftovers with launcher icons that are not usable TV apps.
        val junk = setOf(
            "com.android.wallpaper",
            "com.android.gallery3d",
            "com.android.music",
        )

        val entries = found.values.mapNotNull { ai ->
            if (ai.packageName in hidden) return@mapNotNull null
            if (ai.packageName in suppressed) return@mapNotNull null
            if (ai.packageName in junk) return@mapNotNull null
            try {
                val label = labelOverrides[ai.packageName]
                    ?: runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)
                val banner = runCatching {
                    pm.getApplicationBanner(ai)?.toSafeBitmap(480, 270)
                }.getOrNull()
                val icon = runCatching { pm.getApplicationIcon(ai).toSafeBitmap(192, 192) }.getOrNull()
                AppEntry(
                    packageName = ai.packageName,
                    label = label,
                    banner = banner,
                    icon = icon,
                    removable = (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0,
                    system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            } catch (_: Throwable) {
                null
            }
        }

        val byKey = entries.associateBy { it.packageName }
        val ordered = ArrayList<AppEntry>(entries.size)
        order.forEach { pkg -> byKey[pkg]?.let { ordered.add(it) } }
        entries.filter { it.packageName !in order || order.indexOf(it.packageName) < 0 }
            .sortedBy { it.label.lowercase() }
            .forEach { ordered.add(it) }
        android.util.Log.i("TvHomeApps", "loaded=" + ordered.size)
        ordered
    }

    private fun Drawable.toSafeBitmap(w: Int, h: Int): ImageBitmap? = runCatching {
        toBitmap(w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()
}

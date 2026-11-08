package com.hamad.tvhome.settings

import android.content.Intent
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hamad.tvhome.BuildConfig
import com.hamad.tvhome.aerial.AerialPreviewActivity
import com.hamad.tvhome.data.TmdbConfigured
import com.hamad.tvhome.data.TvPrefs
import com.hamad.tvhome.overlay.ControlCenterData
import com.hamad.tvhome.overlay.TvAccessibilityService
import com.hamad.tvhome.prayer.PrayerPreviewActivity
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.LocalTvType
import com.hamad.tvhome.ui.TvColor
import com.hamad.tvhome.ui.TvMetrics
import com.hamad.tvhome.ui.TvText
import com.hamad.tvhome.ui.TvType
import com.hamad.tvhome.ui.tvType

private enum class Pane { Sidebar, Content }

private enum class Section(val title: String) {
    GENERAL("General"),
    SCREENSAVER("Screen Saver"),
    PRAYER("Prayer Times"),
    NETWORK("Network"),
    ABOUT("About"),
}

private enum class RowKind { Info, Toggle, Cycle, Open }

private data class SettingRow(
    val label: String,
    val value: String,
    val kind: RowKind,
    val action: (() -> Unit)? = null,
)

private data class City(val name: String, val lat: Float, val lon: Float, val zone: String)

private val cities = listOf(
    City("Riyadh", 24.7136f, 46.6753f, "Asia/Riyadh"),
    City("Makkah", 21.3891f, 39.8579f, "Asia/Riyadh"),
    City("Cairo", 30.0444f, 31.2357f, "Africa/Cairo"),
    City("Dubai", 25.2048f, 55.2708f, "Asia/Dubai"),
    City("Istanbul", 41.0082f, 28.9784f, "Europe/Istanbul"),
)

private val regions = listOf("US" to "United States", "GB" to "United Kingdom", "DE" to "Germany", "SA" to "Saudi Arabia", "AE" to "UAE", "EG" to "Egypt")

private val methods = listOf(
    "MWL" to "Muslim World League",
    "UMM_AL_QURA" to "Umm al-Qura",
    "EGYPTIAN" to "Egyptian Authority",
    "KARACHI" to "University of Karachi",
)

private val screensavers = listOf("prayer" to "Prayer Times", "aerial" to "Aerial")

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: TvPrefs

    private var section by mutableIntStateOf(0)
    private var item by mutableIntStateOf(0)
    private var pane by mutableStateOf(Pane.Sidebar)
    private var revision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = TvPrefs(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                val metrics = remember(maxHeight) {
                    TvMetrics((maxHeight.value / 1080f).coerceAtLeast(0.35f))
                }
                val type = remember(metrics) { TvType(metrics) }
                CompositionLocalProvider(LocalTvMetrics provides metrics, LocalTvType provides type) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        // revision is read so that pref changes trigger a recomposition.
                        val rows = remember(revision, section) { buildRows() }
                        SettingsScreen(
                            sections = Section.entries,
                            sectionIndex = section,
                            itemIndex = item,
                            pane = pane,
                            title = Section.entries[section].title,
                            rows = rows,
                        )
                    }
                }
            }
        }
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) move(1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) {
                if (pane == Pane.Sidebar) {
                    pane = Pane.Content
                    item = 0
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) {
                if (pane == Pane.Content) {
                    pane = Pane.Sidebar
                    item = 0
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (down) activate()
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                if (down) {
                    if (pane == Pane.Content) {
                        pane = Pane.Sidebar
                        item = 0
                    } else {
                        finish()
                    }
                }
                return true
            }
        }
        return true
    }

    private fun move(delta: Int) {
        if (pane == Pane.Sidebar) {
            section = (section + delta).mod(Section.entries.size)
            item = 0
        } else {
            val count = buildRows().size
            if (count > 0) item = (item + delta).mod(count)
        }
    }

    private fun activate() {
        if (pane == Pane.Sidebar) {
            pane = Pane.Content
            item = 0
            return
        }
        val rows = buildRows()
        val row = rows.getOrNull(item) ?: return
        row.action?.invoke()
        revision++
    }

    private fun buildRows(): List<SettingRow> = when (Section.entries[section]) {
        Section.GENERAL -> listOf(
            SettingRow(
                "Region",
                regions.firstOrNull { it.first == prefs.region }?.second ?: prefs.region,
                RowKind.Cycle,
            ) {
                val index = regions.indexOfFirst { it.first == prefs.region }.coerceAtLeast(0)
                prefs.region = regions[(index + 1) % regions.size].first
            },
            SettingRow("Clock", if (prefs.showSeconds) "Hours, minutes, seconds" else "Hours and minutes", RowKind.Toggle) {
                prefs.showSeconds = !prefs.showSeconds
            },
            SettingRow(
                "Control Center",
                if (ControlCenterData.isServiceEnabled(this@SettingsActivity)) {
                    "Active \u2014 long-press Home"
                } else {
                    "Off \u2014 enable the accessibility service"
                },
                RowKind.Open,
            ) {
                ControlCenterData.openAccessibilitySettings(this@SettingsActivity)
            },
            SettingRow(
                "Preview Control Center",
                if (TvAccessibilityService.isConnected()) {
                    "Show the overlay over Settings"
                } else {
                    "Enable the accessibility service first"
                },
                RowKind.Open,
            ) {
                if (!TvAccessibilityService.showPreview()) {
                    ControlCenterData.openAccessibilitySettings(this@SettingsActivity)
                }
            },
            SettingRow(
                "Recent apps access",
                if (ControlCenterData.hasUsageAccess(this@SettingsActivity)) "Granted" else "Not granted",
                RowKind.Open,
            ) {
                ControlCenterData.openUsageAccessSettings(this@SettingsActivity)
            },
            SettingRow("Set tvhome as Home", "Open system Home settings", RowKind.Open) {
                runCatching { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) }
            },
            SettingRow("Android TV settings", "Open system settings", RowKind.Open) {
                runCatching { startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
            },
        )

        Section.SCREENSAVER -> listOf(
            SettingRow("Screen saver", screensavers.firstOrNull { it.first == prefs.screenSaverId }?.second ?: "Prayer Times", RowKind.Cycle) {
                val index = screensavers.indexOfFirst { it.first == prefs.screenSaverId }.coerceAtLeast(0)
                prefs.screenSaverId = screensavers[(index + 1) % screensavers.size].first
            },
            SettingRow("Preview", "Open the selected screen saver", RowKind.Open) {
                val target = if (prefs.screenSaverId == "aerial") {
                    AerialPreviewActivity::class.java
                } else {
                    PrayerPreviewActivity::class.java
                }
                startActivity(Intent(this@SettingsActivity, target))
            },
            SettingRow("System screen saver", "Open Android TV daydream settings", RowKind.Open) {
                runCatching { startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS)) }
            },
        )

        Section.PRAYER -> {
            val methodName = methods.firstOrNull { it.first == prefs.prayerMethod }?.second ?: prefs.prayerMethod
            listOf(
                SettingRow("City", prefs.city, RowKind.Cycle) {
                    val index = cities.indexOfFirst { it.name.equals(prefs.city, ignoreCase = true) }
                    val city = cities[((if (index >= 0) index + 1 else 0) % cities.size)]
                    prefs.city = city.name
                    prefs.latitude = city.lat
                    prefs.longitude = city.lon
                    prefs.timezone = city.zone
                },
                SettingRow("Latitude", String.format(java.util.Locale.US, "%.4f", prefs.latitude), RowKind.Info),
                SettingRow("Longitude", String.format(java.util.Locale.US, "%.4f", prefs.longitude), RowKind.Info),
                SettingRow("Time zone", prefs.timezone, RowKind.Info),
                SettingRow("Calculation", methodName, RowKind.Cycle) {
                    val index = methods.indexOfFirst { it.first == prefs.prayerMethod }.coerceAtLeast(0)
                    prefs.prayerMethod = methods[(index + 1) % methods.size].first
                },
                SettingRow("Preview", "Open the Arabic prayer screen", RowKind.Open) {
                    startActivity(Intent(this@SettingsActivity, PrayerPreviewActivity::class.java))
                },
            )
        }

        Section.NETWORK -> {
            val ip = localIpv4()
            listOf(
                SettingRow("Status", if (ip != null) "Connected" else "No IPv4 connection", RowKind.Info),
                SettingRow("IP address", ip ?: "Unavailable", RowKind.Info),
                SettingRow("Wi-Fi settings", "Open system network settings", RowKind.Open) {
                    runCatching { startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS)) }
                },
            )
        }

        Section.ABOUT -> buildList {
            add(SettingRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", RowKind.Info))
            add(SettingRow("TMDB", if (TmdbConfigured) "Token configured" else "Token missing", RowKind.Info))
            add(SettingRow("TMDB attribution", "Uses the TMDB API; not endorsed or certified by TMDB", RowKind.Info))
            add(SettingRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}".trim(), RowKind.Info))
            add(SettingRow("Android", "API ${Build.VERSION.SDK_INT}", RowKind.Info))
            if (prefs.instagramHandle.isNotBlank()) {
                add(SettingRow("Instagram", "@${prefs.instagramHandle.removePrefix("@")}", RowKind.Info))
            }
        }
    }

    private fun localIpv4(): String? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        return props.linkAddresses
            .firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress }
            ?.address?.hostAddress
    }

    private fun hideSystemUi() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun SettingsScreen(
    sections: List<Section>,
    sectionIndex: Int,
    itemIndex: Int,
    pane: Pane,
    title: String,
    rows: List<SettingRow>,
) {
    val m = LocalTvMetrics.current
    val tv = tvType()
    val sidebarItem = if (pane == Pane.Sidebar) itemIndex else -1

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = m.dp(80f), vertical = m.dp(60f)),
    ) {
        Column(Modifier.width(m.dp(420f)).fillMaxHeight()) {
            TvText("Settings", style = tv.title2, color = TvColor.label)
            Spacer(Modifier.height(m.dp(30f)))
            sections.forEachIndexed { index, section ->
                MenuRow(
                    label = section.title,
                    value = "",
                    focused = pane == Pane.Sidebar && sectionIndex == index,
                    selected = sectionIndex == index,
                    showValue = false,
                )
                Spacer(Modifier.height(m.dp(8f)))
            }
        }

        Spacer(Modifier.width(m.dp(56f)))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            TvText(title, style = tv.headline, color = TvColor.labelSecondary)
            Spacer(Modifier.height(m.dp(26f)))
            rows.forEachIndexed { index, row ->
                MenuRow(
                    label = row.label,
                    value = row.value,
                    focused = pane == Pane.Content && itemIndex == index,
                    selected = false,
                    showValue = true,
                )
                Spacer(Modifier.height(m.dp(8f)))
            }
            Spacer(Modifier.weight(1f))
            TvText(
                "Press Back to " + if (pane == Pane.Content) "return to the list" else "close Settings",
                style = tv.caption2,
                color = TvColor.labelTertiary,
            )
            Spacer(Modifier.height(m.dp(4f)))
            TvText(
                if (pane == Pane.Content) "Press Select to change the value" else "Press Right or Select to open a section",
                style = tv.caption2,
                color = TvColor.labelTertiary,
            )
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    value: String,
    focused: Boolean,
    selected: Boolean,
    showValue: Boolean,
) {
    val m = LocalTvMetrics.current
    val tv = tvType()
    val shape = RoundedCornerShape(m.dp(14f))
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    val labelColor = if (focused) Color.Black else TvColor.label
    val valueColor = if (focused) Color.Black.copy(alpha = 0.62f) else TvColor.labelTertiary

    Row(
        Modifier
            .fillMaxWidth()
            .height(m.dp(70f))
            .then(if (focused) Modifier.shadow(18.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(background)
            .then(if (focused) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), shape))
            .padding(horizontal = m.dp(26f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvText(
            label,
            style = tv.body,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showValue) {
            TvText(
                value,
                style = tv.callout,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

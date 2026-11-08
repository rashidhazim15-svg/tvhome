package com.hamad.tvhome.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

class TvPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

    var region: String
        get() = sp.getString("region", "US") ?: "US"
        set(value) = sp.edit { putString("region", value.uppercase(Locale.US)) }

    var clickSound: Boolean
        get() = sp.getBoolean("click_sound", true)
        set(value) = sp.edit { putBoolean("click_sound", value) }

    var autoPlayTopShelf: Boolean
        get() = sp.getBoolean("autoplay_top_shelf", true)
        set(value) = sp.edit { putBoolean("autoplay_top_shelf", value) }

    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        set(value) = sp.edit { putStringSet("hidden_apps", value) }

    var appOrder: List<String>
        get() = sp.getString("app_order", "")!!.split('\u0001').filter { it.isNotBlank() }
        set(value) = sp.edit { putString("app_order", value.joinToString("\u0001")) }

    var city: String
        get() = sp.getString("prayer_city", "Riyadh") ?: "Riyadh"
        set(value) = sp.edit { putString("prayer_city", value) }

    var latitude: Float
        get() = sp.getFloat("prayer_lat", 24.7136f)
        set(value) = sp.edit { putFloat("prayer_lat", value) }

    var longitude: Float
        get() = sp.getFloat("prayer_lon", 46.6753f)
        set(value) = sp.edit { putFloat("prayer_lon", value) }

    var prayerMethod: String
        get() = sp.getString("prayer_method", "MWL") ?: "MWL"
        set(value) = sp.edit { putString("prayer_method", value) }

    var timezone: String
        get() = sp.getString("prayer_tz", "Asia/Riyadh") ?: "Asia/Riyadh"
        set(value) = sp.edit { putString("prayer_tz", value) }

    var instagramHandle: String
        get() = sp.getString("instagram_handle", "") ?: ""
        set(value) = sp.edit { putString("instagram_handle", value) }

    var screenSaverId: String
        get() = sp.getString("screensaver_id", "prayer") ?: "prayer"
        set(value) = sp.edit { putString("screensaver_id", value) }

    var showSeconds: Boolean
        get() = sp.getBoolean("clock_seconds", false)
        set(value) = sp.edit { putBoolean("clock_seconds", value) }
}

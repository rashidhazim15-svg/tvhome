package com.hamad.tvhome.aerial

import android.content.Context
import androidx.compose.runtime.Immutable
import com.hamad.tvhome.R
import com.hamad.tvhome.data.defaultHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

@Immutable
data class AerialClip(
    val id: String,
    val label: String,
    val videoUrl: String,
)

/**
 * Apple's Aerial catalogue as shipped with tvOS. The live catalogue is a tar
 * archive containing `entries.json`; the copy bundled in the APK (captured from
 * the same archive) makes the screensaver start instantly and work offline,
 * while [refresh] updates the cache for later launches.
 */
class AerialRepository(context: Context, base: OkHttpClient = defaultHttp()) {

    private val appContext = context.applicationContext
    val http: OkHttpClient = AerialTls.client(appContext, base)
    private val cache = File(appContext.filesDir, "aerial_entries_26.json")

    /** The best catalogue available without touching the network. */
    suspend fun clips(): List<AerialClip> = withContext(Dispatchers.IO) {
        val cached = runCatching { cache.takeIf { it.exists() }?.readText()?.let(AerialCatalogue::parse) }.getOrNull()
        val bundled = runCatching { appContext.resources.openRawResource(R.raw.aerial_catalog).use { AerialCatalogue.parse(it.readBytes().toString(StandardCharsets.UTF_8)) } }.getOrNull()
        val clips = cached?.takeIf { it.isNotEmpty() }
            ?: bundled?.takeIf { it.isNotEmpty() }
            ?: refresh().orEmpty()
        if (clips.isEmpty()) throw IOException("Aerial catalogue is unavailable")
        clips
    }

    fun needsRefresh(): Boolean =
        !cache.exists() || System.currentTimeMillis() - cache.lastModified() > REFRESH_INTERVAL_MS

    /** Downloads the current tvOS catalogue; returns null when it can't be read. */
    suspend fun refresh(): List<AerialClip>? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(CATALOGUE).build()
            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Aerial HTTP ${response.code}")
                response.body?.bytes() ?: throw IOException("Aerial response was empty")
            }
            val json = AerialCatalogue.entriesFromTar(body) ?: throw IOException("Aerial archive has no entries.json")
            val clips = AerialCatalogue.parse(json)
            if (clips.isEmpty()) throw IOException("Aerial catalogue has no playable clips")
            runCatching { cache.writeText(json) }
            clips
        }.getOrNull()
    }

    private companion object {
        const val CATALOGUE =
            "https://sylvan.apple.com/itunes-assets/Aerials126/v4/c0/45/d9/" +
                "c045d9d0-9606-1535-62fe-189edb4f79eb/resources-atv-23J-2.tar"
        const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }
}

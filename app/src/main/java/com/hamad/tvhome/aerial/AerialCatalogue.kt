package com.hamad.tvhome.aerial

import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Pure parsing for Apple's Aerial catalogue: extracting `entries.json` from the
 * shipping tar and turning it into playable clips. Kept free of Android types so
 * it can be unit tested on the JVM.
 */
internal object AerialCatalogue {

    /**
     * Reads `entries.json` out of an Apple catalogue tar. The archive is a plain
     * ustar stream: 512-byte header (name at 0, octal size at 124), then the
     * file padded to a 512-byte boundary. AppleDouble `._` sidecars are skipped.
     */
    fun entriesFromTar(bytes: ByteArray): String? {
        var offset = 0
        while (offset + 512 <= bytes.size) {
            val header = bytes.copyOfRange(offset, offset + 512)
            if (header.all { it == 0.toByte() }) break
            val name = String(header, 0, 100, StandardCharsets.UTF_8)
                .substringBefore('\u0000')
                .trim()
            val size = String(header, 124, 12, StandardCharsets.US_ASCII)
                .trim('\u0000', ' ')
                .toLongOrNull(8)
                ?: 0L
            if (name == "entries.json" || name == "./entries.json") {
                val start = offset + 512
                val end = (start + size.toInt()).coerceAtMost(bytes.size)
                return String(bytes, start, end - start, StandardCharsets.UTF_8)
            }
            offset += 512 + (((size + 511) / 512) * 512).toInt()
        }
        return null
    }

    /**
     * Only entries that carry a 1080p asset become clips. H.264 is preferred
     * because the box's decoder handles it in software, with HEVC/SDR as the
     * fallback; duplicate ids are dropped.
     */
    fun parse(body: String): List<AerialClip> {
        val assets = runCatching { JSONObject(body) }.getOrNull()?.optJSONArray("assets")
            ?: return emptyList()
        val seen = HashSet<String>()
        return buildList {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val id = asset.optString("id").takeIf { it.isNotBlank() } ?: continue
                val url = sequenceOf("url-1080-H264", "url-1080-SDR")
                    .map { asset.optString(it) }
                    .firstOrNull { it.startsWith("https://") } ?: continue
                if (!seen.add(id)) continue
                add(
                    AerialClip(
                        id = id,
                        label = asset.optString("accessibilityLabel").takeIf { it.isNotBlank() }
                            ?: "Aerial",
                        videoUrl = url,
                    ),
                )
            }
        }
    }
}

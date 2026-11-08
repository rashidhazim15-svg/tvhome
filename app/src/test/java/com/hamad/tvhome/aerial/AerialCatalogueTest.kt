package com.hamad.tvhome.aerial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class AerialCatalogueTest {

    private fun header(name: String, size: Int): ByteArray {
        val header = ByteArray(512)
        name.toByteArray(StandardCharsets.UTF_8).copyInto(header, 0)
        val octal = Integer.toOctalString(size).padStart(11, '0') + "\u0000"
        octal.toByteArray(StandardCharsets.US_ASCII).copyInto(header, 124)
        return header
    }

    private fun entry(name: String, body: String): ByteArray {
        val content = body.toByteArray(StandardCharsets.UTF_8)
        val padding = (512 - content.size % 512) % 512
        return header(name, content.size) + content + ByteArray(padding)
    }

    private val json = """
        {
          "version": 1,
          "assets": [
            {
              "id": "a",
              "accessibilityLabel": "Seals",
              "url-1080-H264": "https://cdn.example/a.mov",
              "url-1080-SDR": "https://cdn.example/a-hevc.mov"
            },
            {
              "id": "b",
              "url-1080-SDR": "https://cdn.example/b.mov"
            },
            {
              "id": "a",
              "accessibilityLabel": "Duplicate",
              "url-1080-H264": "https://cdn.example/dup.mov"
            },
            {
              "id": "c",
              "url-1080-H264": "http://insecure.example/c.mov"
            },
            {
              "accessibilityLabel": "No id",
              "url-1080-H264": "https://cdn.example/orphan.mov"
            },
            { "id": "d", "accessibilityLabel": "No video" }
          ]
        }
    """.trimIndent()

    @Test
    fun `entries json is read out of an apple tar with a leading sidecar`() {
        val tar = entry("./._entries.json", "\u0000\u0005\u0016\u0007") +
            entry("./entries.json", json) +
            ByteArray(1024)

        assertEquals(json, AerialCatalogue.entriesFromTar(tar))
    }

    @Test
    fun `missing entries json returns null`() {
        val tar = entry("./other.json", "{}") + ByteArray(1024)
        assertNull(AerialCatalogue.entriesFromTar(tar))
        assertNull(AerialCatalogue.entriesFromTar(ByteArray(0)))
    }

    @Test
    fun `parse prefers h264, falls back to sdr and de-duplicates`() {
        val clips = AerialCatalogue.parse(json)
        assertEquals(2, clips.size)
        assertEquals("a", clips[0].id)
        assertEquals("Seals", clips[0].label)
        assertEquals("https://cdn.example/a.mov", clips[0].videoUrl)
        assertEquals("b", clips[1].id)
        assertEquals("Aerial", clips[1].label)
        assertEquals("https://cdn.example/b.mov", clips[1].videoUrl)
    }

    @Test
    fun `empty or malformed catalogues produce no clips`() {
        assertTrue(AerialCatalogue.parse("{}").isEmpty())
        assertTrue(AerialCatalogue.parse("not json").isEmpty())
    }

    @Test
    fun `the bundled catalogue is complete`() {
        val bundled = java.io.File("src/main/res/raw/aerial_catalog.json").readText()
        val clips = AerialCatalogue.parse(bundled)
        assertTrue("bundled catalogue had ${clips.size} clips", clips.size > 100)
        assertTrue(clips.all { it.videoUrl.startsWith("https://") })
        assertEquals(clips.size, clips.map { it.id }.distinct().size)
        assertEquals(clips.size, clips.map { it.videoUrl }.distinct().size)
    }

    @Test
    fun `ids and urls stay unique when the tar round trips`() {
        val assets = (1..20).joinToString(",") { index ->
            """{"id":"id$index","accessibilityLabel":"Clip $index","url-1080-H264":"https://cdn.example/$index.mov"}"""
        }
        val body = """{"assets":[$assets]}"""
        val tar = entry("./entries.json", body) + ByteArray(1024)
        val parsed = AerialCatalogue.parse(AerialCatalogue.entriesFromTar(tar)!!)
        assertEquals(20, parsed.size)
        assertEquals(20, parsed.map { it.id }.distinct().size)
        assertEquals(20, parsed.map { it.videoUrl }.distinct().size)
    }
}

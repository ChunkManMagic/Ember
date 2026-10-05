package com.ember.companion

import com.ember.companion.data.PageMedia
import com.ember.companion.data.PageMediaScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The injected page script is the untrusted half of this feature, so these
 * cover the shapes real sites actually publish (JSON-LD in an @graph, OpenGraph
 * fallback, string/object/array thumbnails) plus the malformed input a hostile
 * or broken page could return.
 */
class PageMediaScanTest {

    @Test
    fun parsesIso8601Durations() {
        assertEquals(754_000L, PageMedia.parseIsoDuration("PT12M34S"))
        assertEquals(3_723_000L, PageMedia.parseIsoDuration("PT1H2M3S"))
        assertEquals(45_000L, PageMedia.parseIsoDuration("PT45S"))
        assertEquals(120_000L, PageMedia.parseIsoDuration("120"))
        assertEquals(120_500L, PageMedia.parseIsoDuration("120.5"))
        assertEquals(0L, PageMedia.parseIsoDuration("nonsense"))
        assertEquals(0L, PageMedia.parseIsoDuration(""))
        assertEquals(0L, PageMedia.parseIsoDuration(null))
        // A bare clock time is not a duration; must not be half-parsed.
        assertEquals(0L, PageMedia.parseIsoDuration("12:34"))
    }

    @Test
    fun formatsDurationLabels() {
        assertEquals("12:34", PageMedia.formatDuration(754_000L))
        assertEquals("1:02:03", PageMedia.formatDuration(3_723_000L))
        assertEquals("", PageMedia.formatDuration(0L))
    }

    @Test
    fun readsVideoObjectWithRealMetadata() {
        val json = """
        [{"url":"https://cdn.example.com/video/clip.mp4",
          "type":"VideoObject",
          "title":"Sample Clip",
          "description":"A description",
          "thumbnailUrl":"https://cdn.example.com/thumbs/clip.jpg",
          "duration":"PT12M34S",
          "width":1280,
          "height":720,
          "uploadDate":"2026-09-01",
          "fromMetadata":true}]
        """.trimIndent()
        val result = PageMediaScan.parse(json, "https://example.com/watch/1")
        assertEquals(1, result.size)
        val m = result.first()
        assertEquals("Sample Clip", m.title)
        assertEquals(754_000L, m.durationMs)
        assertEquals("12:34", m.durationLabel)
        assertEquals(720, m.height)
        assertEquals("720p", m.resolutionLabel)
        assertEquals("video/mp4", m.mimeType)
        assertTrue(m.isVideo)
        assertTrue(m.fromMetadata)
    }

    @Test
    fun walksGraphContainerAndArrayThumbnail() {
        val json = """
        {"url":"https://x.test/v.mp4",
         "thumbnailUrl":[{"url":"https://x.test/t.jpg"}],
         "type":"VideoObject",
         "title":"Graph Node",
         "duration":"PT1M","height":1080,"fromMetadata":true}
        """.trimIndent()
        val result = PageMediaScan.parse(json, "https://x.test/")
        assertEquals(1, result.size)
        assertEquals("Graph Node", result.first().title)
        assertEquals("https://x.test/t.jpg", result.first().thumbnailUrl)
        assertEquals("1080p", result.first().resolutionLabel)
    }

    @Test
    fun mergesDuplicateUrlsKeepingMetadata() {
        // Same file surfaced once bare, once with details: the rich entry wins.
        val json = """
        [{"url":"https://x.test/a.mp4","type":"","fromMetadata":false},
         {"url":"https://x.test/a.mp4#t=10","type":"VideoObject","title":"Has Title",
          "duration":"PT30S","fromMetadata":true}]
        """.trimIndent()
        val result = PageMediaScan.parse(json, "https://x.test/")
        assertEquals(1, result.size)
        assertEquals("Has Title", result.first().title)
        assertEquals(30_000L, result.first().durationMs)
    }

    @Test
    fun videosSortAboveImages() {
        val json = """
        [{"url":"https://x.test/pic.jpg","type":"ImageObject","title":"Pic","fromMetadata":true},
         {"url":"https://x.test/clip.mp4","type":"VideoObject","title":"Clip","fromMetadata":true}]
        """.trimIndent()
        val result = PageMediaScan.parse(json, "https://x.test/")
        assertEquals("Clip", result.first().title)
    }

    @Test
    fun survivesGarbageAndHostileInput() {
        assertTrue(PageMediaScan.parse(null, "https://x.test/").isEmpty())
        assertTrue(PageMediaScan.parse("null", "https://x.test/").isEmpty())
        assertTrue(PageMediaScan.parse("", "https://x.test/").isEmpty())
        assertTrue(PageMediaScan.parse("{not json", "https://x.test/").isEmpty())
        // Non-http schemes must never reach the download manager.
        assertTrue(PageMediaScan.parse("""[{"url":"javascript:alert(1)","fromMetadata":false}]""", "https://x.test/").isEmpty())
        assertTrue(PageMediaScan.parse("""[{"url":"file:///etc/passwd"}]""", "https://x.test/").isEmpty())
    }

    @Test
    fun unwrapsTheDoubleEncodedJavascriptReturn() {
        // evaluateJavascript returns a JSON string literal, not a bare array.
        val raw = "\"[{\\\"url\\\":\\\"https://x.test/v.mp4\\\",\\\"type\\\":\\\"VideoObject\\\"," +
            "\\\"title\\\":\\\"Escaped\\\",\\\"fromMetadata\\\":true}]\""
        val result = PageMediaScan.parse(raw, "https://x.test/")
        assertEquals(1, result.size)
        assertEquals("Escaped", result.first().title)
    }

    @Test
    fun bareUrlsStillWorkWithoutMetadata() {
        val json = """
        [{"url":"https://x.test/thumb.jpg","title":"","fromMetadata":false}]
        """.trimIndent()
        val result = PageMediaScan.parse(json, "https://x.test/")
        assertEquals(1, result.size)
        val m = result.first()
        assertTrue(!m.fromMetadata)
        assertEquals("image/jpeg", m.mimeType)
        assertTrue(m.isImage)
        // A file with no stated title must still show something readable.
        assertTrue(m.displayTitle.isNotBlank())
    }
}
package com.ember.companion

import com.ember.companion.data.HlsPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Playlists are the most loosely-specified input the download feature touches:
 * every packager emits slightly different dialects, and relative URIs are the
 * rule rather than the exception. These tests cover the shapes that actually
 * appear in the wild, plus the malformed cases a hostile or broken origin could
 * serve instead of a playlist.
 */
class HlsPlaylistTest {

    private val base = "https://cdn.example.com/media/hls/720p.m3u8"

    @Test
    fun parsesMasterPlaylistAndRanksByResolution() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=600000,RESOLUTION=426x240
            240p.m3u8
        """.trimIndent()

        val parsed = HlsPlaylist.parse(text, base)
        val master = parsed as? HlsPlaylist.Parsed.Master
        assertNotNull("expected a master playlist, got $parsed", master)
        assertEquals(3, master!!.variants.size)

        val best = HlsPlaylist.bestVariant(master)
        assertNotNull(best)
        assertEquals("1280x720", best!!.resolution)
        assertEquals(2500000, best.bandwidth)
        // Relative URIs must be resolved against the playlist's own URL,
        // otherwise the downloader requests a path that does not exist.
        assertEquals("https://cdn.example.com/media/hls/720p.m3u8", best.url)
    }

    @Test
    fun keepsQuotedCodecListWithEmbeddedCommaIntact() {
        // A naive split-on-comma breaks CODECS in half here and leaks the
        // second codec out as its own attribute.
        val attrs = HlsPlaylist.parseAttributes(
            """BANDWIDTH=2500000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720""",
        )
        assertEquals("avc1.4d401f,mp4a.40.2", attrs["CODECS"])
        assertEquals("1280x720", attrs["RESOLUTION"])
        assertEquals("2500000", attrs["BANDWIDTH"])
        assertNull(attrs["mp4a.40.2"])
    }

    @Test
    fun parsesMediaPlaylistSegmentsWithDurations() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:9.009,
            seg0.ts
            #EXTINF:9.009,
            seg1.ts
            #EXTINF:3.003,
            seg2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as? HlsPlaylist.Parsed.Media
        assertNotNull(media)
        assertEquals(3, media!!.segments.size)
        assertEquals(9.009, media.segments[0].durationSec, 0.0001)
        assertEquals(3.003, media.segments[2].durationSec, 0.0001)
        assertEquals("https://cdn.example.com/media/hls/seg2.ts", media.segments[2].url)
        assertTrue("VOD playlist should be marked complete", media.isComplete)
        assertEquals(0L, media.segments[0].sequenceNumber)
        assertEquals(2L, media.segments[2].sequenceNumber)
    }

    @Test
    fun honoursMediaSequenceForImplicitIv() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXTINF:4.0,
            a.ts
            #EXTINF:4.0,
            b.ts
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Media
        // AES-128 derives the IV from this number when no IV attribute exists,
        // so it must survive parsing.
        assertEquals(7L, media.segments[0].sequenceNumber)
        assertEquals(8L, media.segments[1].sequenceNumber)
    }

    @Test
    fun attachesAes128KeyAndExplicitIv() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x000102030405060708090a0b0c0d0e0f
            #EXTINF:4.0,
            enc0.ts
            #EXTINF:4.0,
            enc1.ts
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Media
        val key = media.segments[0].key
        assertNotNull("segments after a KEY line must inherit the key", key)
        assertEquals("AES-128", key!!.method)
        assertEquals("https://cdn.example.com/media/hls/key.bin", key.uri)
        assertEquals(16, key.iv!!.size)
        assertEquals(0x0f.toByte(), key.iv!![15])

        // The key stays in force until another KEY line replaces it.
        assertEquals(key, media.segments[1].key)
    }

    @Test
    fun keyLineWithoutUriClearsEncryption() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:4.0,
            enc.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.0,
            clear.ts
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Media
        assertNotNull(media.segments[0].key)
        assertNull("METHOD=NONE must not leave a key attached", media.segments[1].key)
    }

    @Test
    fun rejectsDrmCiphersInsteadOfDownloadingNoise() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://key"
            #EXTINF:4.0,
            enc.ts
        """.trimIndent()

        try {
            HlsPlaylist.parse(text, base)
            fail("expected a DRM refusal")
        } catch (e: HlsPlaylist.UnsupportedCipherException) {
            assertEquals("SAMPLE-AES", e.method)
        }
    }

    @Test
    fun readsFmp4InitSegment() {
        val text = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            seg1.m4s
            #EXTINF:4.0,
            seg2.m4s
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Media
        val init = media.initSegment
        assertNotNull("EXT-X-MAP must be captured", init)
        assertEquals("https://cdn.example.com/media/hls/init.mp4", init!!.url)
        // fMP4 segments are already a valid file; the downloader needs to know.
        assertTrue(media.segments[0].url.endsWith(".m4s"))
    }

    @Test
    fun parsesByteRangesIncludingImplicitOffsets() {
        val text = """
            #EXTM3U
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:75232@0
            all.ts
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:82112
            all.ts
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Media
        val first = media.segments[0]
        val second = media.segments[1]
        assertEquals(75232L, first.byteLength)
        assertEquals(0L, first.byteOffset)
        assertTrue(first.isPartial)
        // A range with no offset continues where the previous one ended.
        assertEquals(75232L, second.byteOffset)
        assertEquals(82112L, second.byteLength)
    }

    @Test
    fun resolvesEveryRelativeUriForm() {
        assertEquals(
            "https://other.example.com/abs.m3u8",
            HlsPlaylist.resolveUrl(base, "https://other.example.com/abs.m3u8"),
        )
        assertEquals(
            "https://cdn.example.com/root/seg.ts",
            HlsPlaylist.resolveUrl(base, "/root/seg.ts"),
        )
        assertEquals(
            "https://cdn.example.com/media/hls/seg.ts",
            HlsPlaylist.resolveUrl(base, "seg.ts"),
        )
        assertEquals(
            "https://cdn.example.com/media/seg.ts",
            HlsPlaylist.resolveUrl(base, "../seg.ts"),
        )
        assertEquals(
            "https://cdn.example.com/media/hls/seg.ts",
            HlsPlaylist.resolveUrl(base, "//cdn.example.com/media/hls/seg.ts"),
        )
    }

    @Test
    fun rejectsNonPlaylistsAndEmptyBodies() {
        val html = "<html><body>Not a playlist</body></html>"
        val htmlResult = HlsPlaylist.parse(html, base)
        assertTrue(htmlResult is HlsPlaylist.Parsed.Unusable)

        assertTrue(HlsPlaylist.parse("", base) is HlsPlaylist.Parsed.Unusable)
        assertTrue(HlsPlaylist.parse(null, base) is HlsPlaylist.Parsed.Unusable)

        // A valid header with nothing in it is still not downloadable.
        val empty = HlsPlaylist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10\n", base)
        assertTrue(empty is HlsPlaylist.Parsed.Unusable)
    }

    @Test
    fun toleratesBomAndCarriageReturns() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:4.0,\r\nseg.ts\r\n#EXT-X-ENDLIST\r\n"
        val media = HlsPlaylist.parse(text, base) as? HlsPlaylist.Parsed.Media
        assertNotNull("a BOM and CRLF endings are normal in the wild", media)
        assertEquals(1, media!!.segments.size)
    }

    @Test
    fun readsByteRangedInitSegment() {
        // Apple's fMP4 example ships every segment and the init segment inside a
        // single main.mp4, addressed by range. Ignoring the range would prepend
        // a second copy of the whole movie to the output.
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:1
            #EXT-X-MAP:URI="main.mp4",BYTERANGE="719@0"
            #EXTINF:6.00000,
            #EXT-X-BYTERANGE:1508000@719
            main.mp4
            #EXTINF:6.00000,
            #EXT-X-BYTERANGE:1510244@1508719
            main.mp4
        """.trimIndent()

        val media = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Media
        val init = media.initSegment!!
        assertEquals(719L, init.byteLength)
        assertEquals(0L, init.byteOffset)
        assertEquals(1508000L, media.segments[0].byteLength)
        assertEquals(719L, media.segments[0].byteOffset)
        assertEquals(1510244L, media.segments[1].byteLength)
        assertEquals(1508719L, media.segments[1].byteOffset)
    }

    @Test
    fun skipsTrickPlayVariants() {
        val text = """
            #EXTM3U
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=100000,RESOLUTION=640x360,URI="iframe.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360p.m3u8
        """.trimIndent()

        val master = HlsPlaylist.parse(text, base) as HlsPlaylist.Parsed.Master
        assertEquals("IFRAME-only playlists have no audio and are not downloadable", 1, master.variants.size)
    }
}

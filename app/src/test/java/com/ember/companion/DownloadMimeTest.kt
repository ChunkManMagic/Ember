package com.ember.companion

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the rules that decide a download's file type and name.
 *
 * The failure this exists to prevent: "could not discern file type" on links that
 * work perfectly well. Every real media host uses opaque paths, so the answer has
 * to come from the server or from the page — never from a guess about the URL.
 */
class DownloadMimeTest {

    private fun extensionOf(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        if (!last.contains('.')) return null
        val ext = last.substringAfterLast('.').lowercase()
        return ext.takeIf { it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
    }

    private fun String.isIgnorableType(): Boolean {
        val value = substringBefore(';').trim().lowercase()
        return value.isEmpty() ||
            value == "application/octet-stream" ||
            value.startsWith("text/html") ||
            value.startsWith("application/xhtml") ||
            value == "text/plain"
    }

    /** Mirrors resolveMime's order: declared, then served, then extension, then keywords. */
    private fun resolveMime(
        url: String,
        declared: String,
        served: String? = null,
        fromExt: (String) -> String? = { null },
    ): String? {
        declared.takeIf { it.isNotBlank() && !it.isIgnorableType() }?.let { return it }
        served?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotBlank() && !it.isIgnorableType() }
            ?.let { return it }
        extensionOf(url)?.let { fromExt(it)?.let { return it } }
        val lower = url.lowercase()
        return when {
            lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains("/thumb") -> "image/jpeg"
            lower.contains(".png") -> "image/png"
            lower.contains(".webp") -> "image/webp"
            lower.contains(".mp4") || lower.contains("/video") || lower.contains("stream") -> "video/mp4"
            lower.contains(".webm") -> "video/webm"
            lower.contains(".mp3") || lower.contains("/audio") -> "audio/mpeg"
            else -> null
        }
    }

    @Test
    fun servedContentTypeBeatsEverythingElse() {
        // An opaque CDN path with a real Content-Type is the common case.
        val url = "https://cdn3.example.net/a/bcdef123456"
        val mime = resolveMime(url, "", "video/mp4") { null }
        assertEquals("video/mp4", mime)
    }

    @Test
    fun charsetIsStrippedFromServedType() {
        assertEquals(
            "video/mp4",
            resolveMime("https://x.test/a/b", "", "video/mp4; charset=binary") { null },
        )
    }

    @Test
    fun pageDeclaredTypeWins() {
        assertEquals(
            "image/jpeg",
            resolveMime("https://x.test/a/b", "image/jpeg", "video/mp4") { null },
        )
    }

    @Test
    fun htmlFromTheServerIsNeverTrustedAsTheFileType() {
        // An error or interstitial page answers 200 with text/html; that is not
        // the media, and treating it as the type would save the wrong thing.
        assertEquals(
            null,
            resolveMime("https://x.test/a/b", "", "text/html; charset=utf-8") { null },
        )
        assertEquals(
            null,
            resolveMime("https://x.test/a/b", "text/html") { null },
        )
    }

    @Test
    fun octetStreamIsTreatedAsNoInformation() {
        // What a server sends when it genuinely does not know — not a reason
        // to refuse, but not a reason to believe either.
        assertEquals(
            null,
            resolveMime("https://x.test/a/b", "application/octet-stream", "application/octet-stream") { null },
        )
        assertEquals(
            null,
            resolveMime("https://x.test/a/b", "", "text/plain") { null },
        )
    }

    @Test
    fun extensionStillWorksWhenThereIsAServerAnswer() {
        val mime = resolveMime("https://x.test/clip.mp4", "", "video/webm") {
            if (it == "mp4") "video/mp4" else null
        }
        assertEquals("video/webm", mime)
    }

    @Test
    fun unclassifiableLinkYieldsNullAndMustNotBeRefused() {
        // The caller must fall through to a generic type rather than refusing.
        assertEquals(null, resolveMime("https://x.test/a/b", "", "") { null })
    }

    @Test
    fun extensionComesFromThePathNotTheQuery() {
        assertEquals("mp4", extensionOf("https://cdn.test/a/video.mp4?token=abc.def&x=.zip"))
        assertEquals("jpg", extensionOf("https://cdn.test/thumb.jpg?v=2"))
        assertEquals(null, extensionOf("https://cdn.test/video?id=12345&token=xyz"))
        assertEquals(null, extensionOf("https://cdn.test/"))
    }

    @Test
    fun absurdExtensionsAreRejected() {
        assertEquals(null, extensionOf("https://cdn.test/file.a1b2c3d4e5f6"))
        assertEquals(null, extensionOf("https://cdn.test/x.verylongextension"))
    }
}
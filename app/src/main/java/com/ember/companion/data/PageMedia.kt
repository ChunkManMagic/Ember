package com.ember.companion.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One media candidate found on a page, with whatever metadata the page was
 * willing to state about it.
 *
 * This is the difference between Ember's old "scan" (which returned a bare list
 * of <img>/<video> srcs, so everything saved with a junk filename and no
 * duration/resolution) and something you can actually browse: most video pages
 * publish a schema.org `VideoObject` or OpenGraph tags in the markup, which
 * costs one injected script to read and gives real titles, runtimes, poster
 * frames and dimensions for free.
 *
 * Every field except [url] is optional — a page may publish only some of them,
 * and a missing value must never stop the download.
 */
data class PageMedia(
    /** The direct media URL. Always present; this is what gets downloaded. */
    val url: String,
    /** schema.org type: VideoObject, ImageObject, AudioObject, ... */
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    /** Runtime in milliseconds. 0 when the page didn't state one. */
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val uploadDate: String = "",
    /** Site name, from OpenGraph `og:site_name`. */
    val siteName: String = "",
    val mimeType: String = "",
    /** True when this entry came from page metadata rather than a bare DOM src. */
    val fromMetadata: Boolean = false,
) {
    val isVideo: Boolean
        get() = type.contains("video", ignoreCase = true) ||
            VIDEO_EXTENSIONS.any { url.substringBefore('?').endsWith(it, ignoreCase = true) } ||
            mimeType.startsWith("video/")

    val isImage: Boolean
        get() = type.contains("image", ignoreCase = true) ||
            IMAGE_EXTENSIONS.any { url.substringBefore('?').endsWith(it, ignoreCase = true) }

    /** "12:34" / "1:02:03" for display. Blank when the page gave no duration. */
    val durationLabel: String
        get() = formatDuration(durationMs)

    /** "1080p" style badge, blank when unknown. */
    val resolutionLabel: String
        get() = when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            height > 0 -> "${height}p"
            else -> ""
        }

    /** What to show in the results list. Never blank, never a raw CDN filename. */
    val displayTitle: String
        get() = title.ifBlank {
            description.take(80).ifBlank {
                url.substringAfterLast('/').substringBefore('?').ifBlank { "Untitled" }
            }
        }

    companion object {
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".m4v", ".webm", ".mov", ".mkv", ".avi", ".flv", ".m3u8", ".m3u")
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")

        /**
         * ISO-8601 duration as used by schema.org: "PT12M34S", "PT1H2M3S",
         * "PT45S", and occasionally a bare number of seconds. Returns 0 for
         * anything unparseable rather than guessing.
         */
        fun parseIsoDuration(raw: String?): Long {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return 0L
            // Plain seconds, e.g. "754" or "754.0".
            value.toDoubleOrNull()?.let { secs ->
                return if (secs > 0) (secs * 1000).toLong() else 0L
            }
            if (!value.startsWith("PT", ignoreCase = true)) return 0L
            val body = value.substring(2)
            if (body.isEmpty()) return 0L
            val re = Regex("(\\d+(?:\\.\\d+)?)(H|M|S)")
            var totalSeconds = 0.0
            var matched = false
            for (m in re.findAll(body.uppercase())) {
                val amount = m.groupValues[1].toDoubleOrNull() ?: continue
                matched = true
                totalSeconds += when (m.groupValues[2]) {
                    "H" -> amount * 3600
                    "M" -> amount * 60
                    else -> amount
                }
            }
            return if (matched && totalSeconds > 0) (totalSeconds * 1000).toLong() else 0L
        }

        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return ""
            val totalSeconds = ms / 1000
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%d:%02d", m, s)
        }

        fun mimeFor(url: String): String {
            val path = url.substringBefore('?').lowercase()
            return when {
                path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
                path.endsWith(".webm") -> "video/webm"
                path.endsWith(".mov") -> "video/quicktime"
                path.endsWith(".mkv") -> "video/x-matroska"
                path.endsWith(".avi") -> "video/x-msvideo"
                path.endsWith(".flv") -> "video/x-flv"
                path.endsWith(".m3u8") || path.endsWith(".m3u") -> "application/vnd.apple.mpegurl"
                path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".webp") -> "image/webp"
                path.endsWith(".gif") -> "image/gif"
                path.endsWith(".m4a") || path.endsWith(".mp3") -> "audio/mpeg"
                path.endsWith(".mp4a") -> "audio/mp4"
                else -> ""
            }
        }

        /**
         * Reads one JSON-LD node. Handles the shapes these pages actually emit:
         * a single object, an array, or an @graph container.
         */
        fun fromJsonLd(node: JSONObject, pageUrl: String): PageMedia? {
            // Content URLs are often protocol-relative.
            fun abs(raw: String?): String {
                val v = raw?.trim().orEmpty()
                if (v.isEmpty()) return ""
                if (v.startsWith("//")) return "https:$v"
                if (v.startsWith("http://") || v.startsWith("https://")) return v
                return runCatching {
                    val base = java.net.URI(pageUrl)
                    java.net.URI(base.scheme, base.host, v, null).toString()
                }.getOrDefault(v)
            }

            val type = node.optString("@type", "")
            // contentUrl first; some nodes only carry an embed/page URL, which is
            // still better than nothing for identification.
            val contentUrl = abs(
                node.optString("contentUrl", "").ifEmpty {
                    node.optString("embedUrl", "").ifEmpty { node.optString("url", "") }
                }
            )
            // thumbnailUrl is emitted as a string, an object, or an array by
            // different generators, so all three shapes are handled.
            val thumbRaw = when (val th = node.opt("thumbnailUrl")) {
                is String -> abs(th)
                is JSONObject -> abs(th.optString("url", ""))
                is JSONArray -> abs(th.optJSONObject(0)?.optString("url", "").orEmpty())
                else -> abs(node.optString("thumbnailUrl", ""))
            }
            val desc = node.optString("description", "")
            val width = node.optInt("width", 0)
            val height = node.optInt("height", 0)

            val hasMedia = contentUrl.isNotEmpty() || thumbRaw.isNotEmpty()
            if (!hasMedia) return null

            return PageMedia(
                url = contentUrl.ifEmpty { thumbRaw },
                type = type,
                title = node.optString("name", ""),
                description = desc,
                thumbnailUrl = thumbRaw,
                durationMs = parseIsoDuration(node.optString("duration", "")),
                width = width,
                height = height,
                uploadDate = node.optString("uploadDate", ""),
                mimeType = mimeFor(contentUrl),
                fromMetadata = true,
            )
        }

        /** Fallback entry for a bare <img>/<video> src with no metadata at all. */
        fun bareUrl(url: String): PageMedia =
            PageMedia(url = url, mimeType = mimeFor(url), fromMetadata = false)
    }
}
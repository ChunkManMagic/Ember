package com.ember.companion.data

/**
 * A parser for HLS playlists (`.m3u8`), covering the parts a phone actually has
 * to understand to download a video.
 *
 * Why this exists in the app at all: a stream URL found on a page is nearly
 * always a *playlist*, not a file. Saving the playlist itself produces a text
 * file no player will open, which is exactly the failure Ember had before — the
 * download "succeeded" and the result was useless. The playlist has to be read,
 * the best quality chosen, and its segments fetched and joined into one file.
 *
 * Deliberately pure Kotlin with no Android dependencies so it can be exercised
 * directly by unit tests; playlists are the most malformed input in the whole
 * feature and the failure modes are all in here.
 *
 * Supported: master playlists with variant streams (and alternate audio groups),
 * media playlists with `#EXTINF` segments, `#EXT-X-BYTERANGE`, `#EXT-X-MAP`
 * initialisation segments for fMP4, and `#EXT-X-KEY` AES-128 encryption.
 * Not supported (and rejected rather than silently mis-downloaded): SAMPLE-AES
 * and any other cipher, because those keys live in DRM and cannot be fetched.
 */
object HlsPlaylist {

    /** One quality level offered by a master playlist. */
    data class Variant(
        /** Absolute URL of the media playlist for this rendition. */
        val url: String,
        /** Bits per second, as advertised. 0 when the playlist omitted it. */
        val bandwidth: Int = 0,
        /** "1280x720" as stated by the playlist, blank when absent. */
        val resolution: String = "",
        /** CODECS attribute, kept for display only. */
        val codecs: String = "",
        /**
         * Group ids of alternate audio tracks routed alongside this variant,
         * from the `#EXT-X-MEDIA` entries that share its `AUDIO` attribute.
         */
        val audioGroupIds: List<String> = emptyList(),
    ) {
        /** Pixel count, used to rank renditions the same way a player would. */
        val pixelCount: Int
            get() {
                val parts = resolution.split('x')
                if (parts.size != 2) return 0
                val w = parts[0].trim().toIntOrNull() ?: return 0
                val h = parts[1].trim().toIntOrNull() ?: return 0
                return w * h
            }
    }

    /** How a segment's bytes are encrypted, if they are. */
    data class Key(
        /** Always `AES-128` here; anything else is refused at parse time. */
        val method: String,
        /** Absolute URL the 16-byte key is fetched from. */
        val uri: String,
        /** Initialisation vector, or null when it must come from the sequence number. */
        val iv: ByteArray?,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Key && method == other.method && uri == other.uri &&
                iv.contentEquals(other.iv))

        override fun hashCode(): Int =
            (method.hashCode() * 31 + uri.hashCode()) * 31 + (iv?.contentHashCode() ?: 0)
    }

    /** One downloadable chunk of a media playlist. */
    data class Segment(
        val url: String,
        /** Duration in seconds as declared by `#EXTINF`. */
        val durationSec: Double = 0.0,
        val key: Key? = null,
        /** `#EXT-X-BYTERANGE` length, 0 when the segment is whole. */
        val byteLength: Long = 0L,
        /** `#EXT-X-BYTERANGE` offset, 0 when the segment is whole. */
        val byteOffset: Long = 0L,
        /**
         * Media sequence number of this segment, which AES-128 needs when the
         * playlist omits an explicit IV.
         */
        val sequenceNumber: Long = 0L,
    ) {
        val isPartial: Boolean get() = byteLength > 0L
    }

    /** What a playlist turned out to be. */
    sealed class Parsed {
        /** A list of renditions; the real playlist is one of [variants]. */
        data class Master(val variants: List<Variant>) : Parsed()

        /** A playable sequence of segments. */
        data class Media(
            val segments: List<Segment>,
            val targetDurationSec: Double = 0.0,
            /** `#EXT-X-MAP` initialisation segment for fragmented-MP4 streams. */
            val initSegment: Segment? = null,
            /** True when `#EXT-X-ENDLIST` was present, i.e. this is a whole VOD asset. */
            val isComplete: Boolean = false,
        ) : Parsed()

        /** Not an M3U8 at all, or a playlist with nothing downloadable in it. */
        data class Unusable(val reason: String) : Parsed()
    }

    /**
     * Reads a playlist.
     *
     * [playlistUrl] is required and is not optional because relative segment URIs
     * are the norm — a playlist that lists `seg1.ts` is meaningless without
     * knowing where the playlist itself came from.
     */
    fun parse(text: String?, playlistUrl: String): Parsed {
        if (text.isNullOrBlank()) return Parsed.Unusable("The playlist was empty")
        val body = text.removePrefix("\uFEFF")
        if (!body.trimStart().startsWith("#EXTM3U")) {
            return Parsed.Unusable("That link is not an HLS playlist")
        }

        // Alternate audio is declared with #EXT-X-MEDIA before the variants that
        // reference it, so it has to be collected in a first pass.
        val audioGroups = mutableMapOf<String, MutableSet<String>>()
        val variants = mutableListOf<Variant>()
        val segments = mutableListOf<Segment>()
        var targetDuration = 0.0
        var isComplete = false
        var initSegment: Segment? = null

        var currentKey: Key? = null
        var pendingVariantAttrs: Map<String, String>? = null
        var pendingDuration = 0.0
        var pendingByteLength = 0L
        var pendingByteOffset = 0L
        var sequenceNumber = 0L
        // A #EXT-X-BYTERANGE with no offset continues from the end of the
        // previous sub-range of the same resource, per the spec.
        var lastRangeEnd = 0L

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                when {
                    line.startsWith("#EXT-X-STREAM-INF:") -> {
                        pendingVariantAttrs = parseAttributes(line.substringAfter(':'))
                    }

                    line.startsWith("#EXT-X-I-FRAME-STREAM-INF:") -> {
                        // Trick-play streams carry no audio and are rarely what
                        // anyone downloading wants; skipping them keeps the
                        // variant list to real renditions.
                    }

                    line.startsWith("#EXT-X-MEDIA:") -> {
                        val attrs = parseAttributes(line.substringAfter(':'))
                        val type = attrs["TYPE"].orEmpty()
                        val groupId = attrs["GROUP-ID"].orEmpty()
                        if (type.equals("AUDIO", ignoreCase = true) && groupId.isNotEmpty()) {
                            // The set holds the renditions in the group, keyed by
                            // group id. It used to add groupId to its own set, which
                            // made the contents meaningless — only containsKey was
                            // ever read, and the set said nothing about whether a
                            // real rendition existed behind the group.
                            val name = attrs["NAME"].orEmpty().ifBlank { attrs["URI"].orEmpty() }
                            if (name.isNotEmpty()) {
                                audioGroups.getOrPut(groupId) { mutableSetOf() }.add(name)
                            }
                        }
                    }

                    line.startsWith("#EXT-X-KEY:") || line == "#EXT-X-KEY" -> {
                        currentKey = parseKey(line, playlistUrl)
                        // KEYINFO for the same URI resets the IV; handled by
                        // simply always taking the most recent KEY line.
                    }

                    line.startsWith("#EXT-X-MAP:") -> {
                        val attrs = parseAttributes(line.substringAfter(':'))
                        val uri = attrs["URI"].orEmpty()
                        if (uri.isNotEmpty()) {
                            // A byteranged init segment shares its file with the
                            // media segments; fetching the whole file here would
                            // prepend a duplicate copy of the movie to the output.
                            val range = parseByteRange(attrs["BYTERANGE"].orEmpty())
                            initSegment = Segment(
                                url = resolveUrl(playlistUrl, uri),
                                key = currentKey,
                                byteLength = range.first,
                                byteOffset = range.second,
                            )
                        }
                    }

                    line.startsWith("#EXTINF:") -> {
                        val raw = line.substringAfter(':').substringBefore(',').trim()
                        pendingDuration = raw.toDoubleOrNull() ?: 0.0
                    }

                    line.startsWith("#EXT-X-BYTERANGE:") -> {
                        val range = parseByteRange(line.substringAfter(':'))
                        // An absent offset means "continue from where the last
                        // sub-range of this resource ended", which the caller
                        // supplies via lastRangeEnd.
                        pendingByteLength = range.first
                        pendingByteOffset = if (range.first > 0L && range.second < 0L) {
                            lastRangeEnd
                        } else {
                            range.second.coerceAtLeast(0L)
                        }
                    }

                    line.startsWith("#EXT-X-TARGETDURATION:") -> {
                        targetDuration = line.substringAfter(':').trim().toDoubleOrNull() ?: 0.0
                    }

                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        sequenceNumber = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                    }

                    line == "#EXT-X-ENDLIST" -> isComplete = true

                    // Anything else (DISCONTINUITY, PLAYLIST-TYPE, VERSION,
                    // INDEPENDENT-SEGMENTS, PROGRAM-DATE-TIME, ...) carries no
                    // information this downloader needs.
                }
                continue
            }

            // A non-comment line is the URI the most recent tag described.
            val absolute = resolveUrl(playlistUrl, line)
            val attrs = pendingVariantAttrs
            if (attrs != null) {
                val resolution = attrs["RESOLUTION"].orEmpty()
                val audioGroup = attrs["AUDIO"].orEmpty()
                variants += Variant(
                    url = absolute,
                    bandwidth = attrs["BANDWIDTH"]?.trim()?.toDoubleOrNull()?.toInt() ?: 0,
                    resolution = resolution,
                    codecs = attrs["CODECS"].orEmpty(),
                    // Only recorded when the group actually lists a rendition. A dangling
                    // AUDIO group names audio that does not exist behind it.
                    audioGroupIds = if (audioGroup.isNotEmpty() &&
                        audioGroups[audioGroup].orEmpty().isNotEmpty()
                    ) {
                        listOf(audioGroup)
                    } else {
                        emptyList()
                    },
                )
                pendingVariantAttrs = null
                continue
            }

            segments += Segment(
                url = absolute,
                durationSec = pendingDuration,
                key = currentKey,
                byteLength = pendingByteLength,
                byteOffset = pendingByteOffset,
                sequenceNumber = sequenceNumber,
            )
            if (pendingByteLength > 0L) lastRangeEnd = pendingByteOffset + pendingByteLength
            sequenceNumber++
            pendingDuration = 0.0
            pendingByteLength = 0L
            pendingByteOffset = 0L
        }

        // A master playlist has no segments of its own; a media playlist has no
        // variants. Both forms appear in the wild under the same URL shape, so
        // the only reliable discriminator is which list came out populated.
        if (variants.isNotEmpty()) return Parsed.Master(variants)
        if (segments.isEmpty()) return Parsed.Unusable("The playlist had no playable segments")
        return Parsed.Media(
            segments = segments,
            targetDurationSec = targetDuration,
            initSegment = initSegment,
            isComplete = isComplete,
        )
    }

    /**
     * Reads a `<length>[@<offset>]` byte-range spec, returning `length` and
     * `offset` where an omitted offset is reported as -1 so the caller can apply
     * the spec's "continue from the previous sub-range" rule. Both forms carry
     * this shape: the bare `#EXT-X-BYTERANGE:82112@75232` tag and the quoted
     * `BYTERANGE="719@0"` attribute on `#EXT-X-MAP`.
     */
    private fun parseByteRange(raw: String): Pair<Long, Long> {
        val text = raw.trim().trim('"')
        if (text.isEmpty()) return 0L to -1L
        val at = text.indexOf('@')
        val lenText = if (at >= 0) text.substring(0, at) else text
        val length = lenText.trim().toLongOrNull() ?: 0L
        if (at < 0) return length to -1L
        val offset = text.substring(at + 1).trim().toLongOrNull() ?: -1L
        return length to offset
    }

    /**
     * Picks the rendition to download.
     *
     * Highest pixel count wins, with bandwidth as the tie-break, because that is
     * what a player would pick and what the user sees advertised on the page.
     * Playlists that state neither fall back to the last entry, which by
     * convention is the highest quality.
     *
     * A variant whose audio lives in a separate rendition is deprioritised, not
     * preferred. HlsDownloader only ever fetches the variant URL, so choosing a
     * split-audio variant over an equally-sized muxed one produced a download
     * with no audio track at all.
     */
    fun bestVariant(master: Parsed.Master): Variant? {
        if (master.variants.isEmpty()) return null
        return master.variants.maxWithOrNull(
            compareBy<Variant> { it.pixelCount }
                .thenBy { it.bandwidth }
                .thenBy { !it.audioGroupIds.isNotEmpty() },
        )
    }

    /**
     * Splits an M3U8 attribute list into name/value pairs.
     *
     * Splitting on commas is the obvious approach and it is wrong: CODECS is a
     * quoted list — `CODECS="avc1.4d401f,mp4a.40.2"` — and a naive split breaks
     * it into a bogus `mp4a.40.2` attribute and leaves CODECS truncated. Only
     * commas outside quotes separate attributes.
     */
    internal fun parseAttributes(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        val n = raw.length
        while (i < n) {
            while (i < n && (raw[i] == ',' || raw[i] == ' ')) i++
            val nameStart = i
            while (i < n && raw[i] != '=' && raw[i] != ',') i++
            if (i >= n || raw[i] != '=') {
                // A bare token with no value; nothing useful to record.
                if (i > nameStart) out[raw.substring(nameStart, i).trim()] = ""
                continue
            }
            val name = raw.substring(nameStart, i).trim()
            i++ // skip '='
            val value: String
            if (i < n && raw[i] == '"') {
                i++
                val start = i
                while (i < n && raw[i] != '"') i++
                value = raw.substring(start, minOf(i, n))
                if (i < n) i++ // skip closing quote
            } else {
                val start = i
                while (i < n && raw[i] != ',') i++
                value = raw.substring(start, i).trim()
            }
            if (name.isNotEmpty()) out[name] = value
            while (i < n && raw[i] != ',') i++
        }
        return out
    }

    private fun parseKey(line: String, playlistUrl: String): Key? {
        // A KEY line with no attributes clears encryption for later segments.
        val at = line.indexOf(':')
        if (at < 0) return null
        val attrs = parseAttributes(line.substring(at + 1))
        val method = attrs["METHOD"].orEmpty().trim('"')
        if (method.isEmpty()) return null
        // METHOD=NONE is the spec's way of saying "these segments are clear" and
        // commonly appears to declare the unencrypted default. It must clear any
        // inherited key, not be reported as DRM.
        if (method.equals("NONE", ignoreCase = true)) return null
        if (!method.equals("AES-128", ignoreCase = true)) {
            // SAMPLE-AES and friends are DRM: the key is not fetchable, and
            // pretending otherwise produces a file of noise.
            throw UnsupportedCipherException(method)
        }
        val uri = attrs["URI"].orEmpty()
        if (uri.isEmpty()) return null
        val iv = attrs["IV"]?.trim()?.removePrefix("0x")?.removePrefix("0X")
            ?.takeIf { it.isNotEmpty() && it.length <= 32 }
            ?.let { hex ->
                // An IV is 128 bits, left-padded with zeros when the playlist
                // wrote a shorter form.
                val padded = hex.padStart(32, '0')
                ByteArray(16) { idx ->
                    val hi = padded[idx * 2]
                    val lo = padded[idx * 2 + 1]
                    (((hi.digitToIntOrNull(16) ?: 0) shl 4) or (lo.digitToIntOrNull(16) ?: 0))
                        .toByte()
                }
            }
        return Key(method = method.uppercase(), uri = resolveUrl(playlistUrl, uri), iv = iv)
    }

    /** Raised when a playlist uses a cipher whose key cannot be fetched. */
    class UnsupportedCipherException(val method: String) :
        RuntimeException("Unsupported encryption: $method")

    /**
     * Resolves a playlist-relative URI against the playlist's own URL.
     *
     * Handles the four forms that actually appear: absolute, protocol-relative
     * (`//host/x`), origin-relative (`/x`) and path-relative (`../x`), the last
     * of which is common in CDNs that shard media into dated folders.
     */
    internal fun resolveUrl(base: String, reference: String): String {
        val ref = reference.trim()
        if (ref.isEmpty()) return ""
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        if (ref.startsWith("//")) return "https:$ref"

        return runCatching {
            val baseUri = java.net.URI(base)
            val resolved = baseUri.resolve(ref)
            resolved.toString()
        }.getOrElse {
            // Fall back to naive joining so a malformed playlist URL still
            // yields something fetchable rather than an empty string.
            val slash = base.indexOf("//")
            val origin = if (slash < 0) base else {
                val pathStart = base.indexOf('/', slash + 2)
                if (pathStart < 0) base else base.substring(0, pathStart)
            }
            val dir = base.substringBeforeLast('/', "")
            when {
                ref.startsWith("/") -> "$origin$ref"
                dir.isEmpty() -> ref
                else -> "$dir/$ref"
            }
        }
    }
}

package com.ember.companion.media

import com.ember.companion.data.HlsPlaylist
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads an HLS stream and turns it into one playable file.
 *
 * The pipeline is: read the playlist, pick the best rendition, fetch each
 * segment, undo AES-128 where it is used, join the segments, then hand the
 * result to [TsRemuxer] when it is MPEG-TS.
 *
 * Two cases come out of this and they are genuinely different:
 *  - MPEG-TS segments, which are what most sites serve. These are joined and
 *    remuxed to fragmented MP4, because a raw `.ts` concatenation plays in some
 *    players and not others.
 *  - Fragmented-MP4 segments (`#EXT-X-MAP`), which are already a valid MP4 and
 *    only need the initialisation segment placed in front. Remuxing those would
 *    destroy them, so they are copied through untouched.
 *
 * Cookies and the page's User-Agent travel with every request: the playlist URL
 * was only revealed to Ember *after* the WebView passed an age gate or a login,
 * and the CDN rejects requests that arrive without that session.
 */
object HlsDownloader {

    /** Where a download has got to, for the queue UI. */
    data class Progress(
        val segmentsDone: Int,
        val segmentsTotal: Int,
        val bytesDone: Long,
        /** Unknown until the server states it; -1 when it does not. */
        val bytesTotal: Long,
    ) {
        /** 0..100, or null when the total size is unknown (no fabricated bar). */
        val percent: Int?
            get() = if (bytesTotal > 0) ((bytesDone * 100) / bytesTotal).toInt().coerceIn(0, 100) else null
    }

    /** What a completed download produced. */
    sealed class Outcome {
        data class Success(
            val file: File,
            val bytes: Long,
            /** True when TS segments were remuxed, false when fMP4 was copied. */
            val remuxed: Boolean,
            val segments: Int,
            /** Resolution of the chosen rendition, for the library entry. */
            val resolution: String = "",
        ) : Outcome()

        data class Failure(val reason: String) : Outcome()
    }

    /** Per-request headers, supplied by the caller so this stays testable. */
    data class Session(
        val userAgent: String = "Mozilla/5.0",
        val cookie: String? = null,
        val referer: String? = null,
    ) {
        fun applyTo(builder: Request.Builder, url: String) {
            builder.header("User-Agent", userAgent)
            cookie?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
            referer?.takeIf { it.isNotBlank() }?.let { builder.header("Referer", it) }
            // Some CDNs vary their response on Accept and serve an XML error
            // document instead of media when it is missing.
            builder.header("Accept", "*/*")
        }
    }

    /**
     * Runs the whole download.
     *
     * [onProgress] is called as segments land, on a background dispatcher, so it
     * must be cheap; the UI layer marshals it to the main thread.
     */
    suspend fun download(
        playlistUrl: String,
        outputFile: File,
        session: Session = Session(),
        onProgress: (Progress) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val workDir = File(outputFile.parentFile, ".hls-${outputFile.name}")
        try {
            val masterText = fetchText(client, playlistUrl, session)
                ?: return@withContext Outcome.Failure("Could not read the playlist")

            val playlist = try {
                HlsPlaylist.parse(masterText, playlistUrl)
            } catch (e: HlsPlaylist.UnsupportedCipherException) {
                return@withContext Outcome.Failure("This stream is DRM-protected and can't be downloaded")
            }

            val mediaUrl: String
            var resolution = ""
            when (playlist) {
                is HlsPlaylist.Parsed.Master -> {
                    val variant = HlsPlaylist.bestVariant(playlist)
                        ?: return@withContext Outcome.Failure("The playlist offered no playable quality")
                    mediaUrl = variant.url
                    resolution = variant.resolution
                }

                is HlsPlaylist.Parsed.Media -> mediaUrl = playlistUrl

                is HlsPlaylist.Parsed.Unusable ->
                    return@withContext Outcome.Failure(playlist.reason)
            }

            // A master can point at another master; follow one level so a
            // two-stage manifest does not read as "no segments".
            var media = readMedia(client, mediaUrl, session)
            if (media is HlsPlaylist.Parsed.Master) {
                val variant = HlsPlaylist.bestVariant(media)
                    ?: return@withContext Outcome.Failure("The playlist offered no playable quality")
                resolution = variant.resolution.ifBlank { resolution }
                media = readMedia(client, variant.url, session)
            }
            val segments = (media as? HlsPlaylist.Parsed.Media)?.segments
                ?: return@withContext Outcome.Failure("The playlist had no playable segments")

            if (segments.isEmpty()) return@withContext Outcome.Failure("The playlist had no playable segments")

            workDir.mkdirs()
            val joined = File(workDir, "joined.bin")

            // fMP4 streams are already a playable file once the init segment is
            // in front; TS streams need the remuxer to become one.
            val isFmp4 = (media as HlsPlaylist.Parsed.Media).initSegment != null
            var done = 0L
            val keyCache = HashMap<String, ByteArray>()

            joined.outputStream().buffered(256 * 1024).use { sink ->
                if (isFmp4) {
                    val init = (media as HlsPlaylist.Parsed.Media).initSegment!!
                    val bytes = fetchSegment(client, init.url, session, init.key, keyCache, 0L, 0L, init.sequenceNumber)
                        ?: return@withContext Outcome.Failure("Could not download the stream header")
                    sink.write(bytes)
                    done += bytes.size
                }

                for ((index, segment) in segments.withIndex()) {
                    val bytes = fetchSegment(
                        client, segment.url, session, segment.key, keyCache,
                        segment.byteLength, segment.byteOffset, segment.sequenceNumber,
                    ) ?: return@withContext Outcome.Failure("Download stopped at segment ${index + 1} of ${segments.size}")

                    sink.write(bytes)
                    done += bytes.size
                    onProgress(Progress(index + 1, segments.size, done, -1L))
                }
            }

            val outFile: File
            if (isFmp4) {
                if (outputFile.exists()) outputFile.delete()
                if (!joined.renameTo(outputFile)) {
                    joined.copyTo(outputFile, overwrite = true)
                    joined.delete()
                }
                outFile = outputFile
                onProgress(Progress(segments.size, segments.size, done, done))
                return@withContext Outcome.Success(outFile, done, remuxed = false, segments.size, resolution)
            }

            val remuxed = File(workDir, "out.mp4")
            when (val result = TsRemuxer.remux(joined, remuxed)) {
                is TsRemuxer.Result.Success -> {
                    if (outputFile.exists()) outputFile.delete()
                    if (!remuxed.renameTo(outputFile)) {
                        remuxed.copyTo(outputFile, overwrite = true)
                        remuxed.delete()
                    }
                    Outcome.Success(outputFile, remuxed.length(), remuxed = true, segments.size, resolution)
                }

                is TsRemuxer.Result.Failure ->
                    Outcome.Failure(result.reason)
            }
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "The download did not finish")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun readMedia(client: OkHttpClient, url: String, session: Session): HlsPlaylist.Parsed {
        val text = fetchText(client, url, session) ?: return HlsPlaylist.Parsed.Unusable("Could not read the playlist")
        return try {
            HlsPlaylist.parse(text, url)
        } catch (e: HlsPlaylist.UnsupportedCipherException) {
            HlsPlaylist.Parsed.Unusable("This stream is DRM-protected and can't be downloaded")
        }
    }

    private fun fetchText(client: OkHttpClient, url: String, session: Session): String? = runCatching {
        val builder = Request.Builder().url(url).get()
        session.applyTo(builder, url)
        client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) return@use null
            res.body?.string()
        }
    }.getOrNull()

    /**
     * Fetches one segment, decrypting it when the playlist says it is encrypted.
     *
     * A zero [byteLength] means "the whole resource"; otherwise the request asks
     * for exactly the declared range, which is how low-latency playlists reuse
     * one file across many segments.
     */
    private fun fetchSegment(
        client: OkHttpClient,
        url: String,
        session: Session,
        key: HlsPlaylist.Key?,
        keyCache: MutableMap<String, ByteArray>,
        byteLength: Long,
        byteOffset: Long,
        /**
         * This segment's media sequence number. AES-128 falls back to it when
         * the playlist omits an explicit IV, so it has to be the real number
         * rather than a default of zero — otherwise every segment but the
         * first decrypts to noise.
         */
        sequenceNumber: Long,
    ): ByteArray? = runCatching {
        val builder = Request.Builder().url(url).get()
        session.applyTo(builder, url)
        if (byteLength > 0L) {
            val end = byteOffset + byteLength - 1
            builder.header("Range", "bytes=$byteOffset-$end")
        }
        val body = client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) return@use null
            res.body?.bytes()
        } ?: return@runCatching null

        if (key == null) return@runCatching body

        val keyBytes = keyCache.getOrPut(key.uri) {
            val keyBuilder = Request.Builder().url(key.uri).get()
            session.applyTo(keyBuilder, key.uri)
            client.newCall(keyBuilder.build()).execute().use { res ->
                res.body?.bytes() ?: ByteArray(0)
            }
        }
        if (keyBytes.size != 16) return@runCatching null

        // Without an explicit IV, AES-128 uses the segment's media sequence
        // number as a 128-bit big-endian integer.
        val iv = key.iv ?: ByteArray(16).also { ivBytes ->
            var v = sequenceNumber
            for (i in 15 downTo 0) {
                ivBytes[i] = (v and 0xFF).toByte()
                v = v ushr 8
            }
        }
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
            doFinal(body)
        }
    }.getOrNull()
}

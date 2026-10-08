package com.ember.companion.media

import com.ember.companion.core.Diag
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Rewrites an MPEG-TS byte stream as a fragmented MP4, without transcoding.
 *
 * Why this exists: HLS delivers a video as a playlist of `.ts` segments. Saving
 * those and handing them to the system downloader produces a `.mp4` no player
 * will open, or a `.ts` that Android's gallery and media scanner ignore
 * outright. The codecs inside are already correct — H.264/H.265 and AAC, which
 * every Android plays — so the fix is a container change, not a re-encode. Copy
 * the elementary streams across and wrap them in ISO-BMFF.
 *
 * Fragmented MP4 (`moof`+`mdat` per fragment) rather than one big `moov`, for
 * two reasons: a `moov` needs every sample's size and duration up front, which
 * means buffering the entire video before writing a byte of it; and a
 * fragmented file starts playing as soon as its first fragment lands.
 *
 * Codecs handled: H.264/AVC and H.265/HEVC video, AAC (ADTS or LATM) and
 * AC-3/E-AC-3 audio. Anything else fails with a reason rather than emitting a
 * file that looks fine and will not play.
 *
 * Deliberately free of Android imports so the same file can be compiled and
 * exercised on a desktop JVM. The container arithmetic is the part worth
 * testing, and it needs no device to be correct.
 */
object TsRemuxer {

    /** MPEG-TS timestamps run at 90 kHz; video tracks keep that as their timescale. */
    const val VIDEO_TIMESCALE = 90_000L

    private const val TS_PACKET_SIZE = 188
    private const val SYNC_BYTE = 0x47

    /** Cap on one fragment's buffered payload, so memory stays bounded. */
    private const val FRAGMENT_BYTE_LIMIT = 4L * 1024 * 1024

    /** Samples per fragment, roughly one second at 60fps. Keeps audio interleaved. */
    private const val FRAGMENT_SAMPLE_LIMIT = 64

    /**
     * Samples to buffer before giving up on ever finding an audio track.
     *
     * The `moov` must be written before any `moof`, so both tracks' codec
     * configuration has to be known up front — including whether there *is* an
     * audio track. Waiting for that in a stream with no audio would mean
     * buffering the whole video, so after this many video samples the header is
     * written video-only and a late audio track is ignored.
     */
    private const val AUDIO_WAIT_SAMPLE_LIMIT = 240

    private const val ST_H264 = 0x1B
    private const val ST_H265 = 0x24
    private const val ST_AAC_ADTS = 0x0F
    private const val ST_AAC_LATM = 0x11
    private const val ST_MPEG1_AUDIO = 0x03
    private const val ST_MPEG2_AUDIO = 0x04
    private const val ST_AC3 = 0x81
    private const val ST_EC3 = 0x87

    /** Outcome of a remux, with enough detail to explain a refusal in plain words. */
    sealed class Result {
        data class Success(
            val videoSamples: Int,
            val audioSamples: Int,
            val width: Int,
            val height: Int,
            val hasAudio: Boolean,
            val bytesWritten: Long,
        ) : Result()

        data class Failure(val reason: String) : Result()
    }

    fun remux(inputFile: File, outputFile: File): Result {
        val tmp = File(outputFile.parentFile, outputFile.name + ".part")
        return try {
            val written = inputFile.inputStream().use { input ->
                BufferedInputStream(input, 256 * 1024).use { buffered ->
                    tmp.outputStream().use { raw ->
                        val counting = CountingStream(raw)
                        val out = BufferedOutputStream(counting, 256 * 1024)
                        val stats = Muxer(out).use { it.run(buffered) }
                        out.flush()
                        stats to counting.total
                    }
                }
            }
            if (written.first.videoSamples == 0) {
                tmp.delete()
                return Result.Failure("The stream had no video frames in it")
            }
            if (outputFile.exists()) outputFile.delete()
            if (!tmp.renameTo(outputFile)) {
                tmp.copyTo(outputFile, overwrite = true)
                tmp.delete()
            }
            Result.Success(
                videoSamples = written.first.videoSamples,
                audioSamples = written.first.audioSamples,
                width = written.first.width,
                height = written.first.height,
                hasAudio = written.first.audioSamples > 0,
                bytesWritten = written.second,
            )
        } catch (e: RemuxException) {
            tmp.delete()
            Result.Failure(e.reason)
        } catch (e: Exception) {
            tmp.delete()
            Result.Failure(e.message ?: "Could not rewrite the stream")
        }
    }

    private class CountingStream(private val delegate: OutputStream) : OutputStream() {
        var total: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            total++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            total += len
        }

        override fun flush() = delegate.flush()
    }

    private class RemuxException(val reason: String) : IOException(reason)

    private class Sample(val data: ByteArray, val dts: Long, val sync: Boolean)

    private data class Stats(
        val videoSamples: Int,
        val audioSamples: Int,
        val width: Int,
        val height: Int,
    )

    // ------------------------------------------------------------------
    // Muxer: owns the output and both tracks
    // ------------------------------------------------------------------

    private class Muxer(private val out: OutputStream) : Closeable {

        private var headerWritten = false

        /** Whether the written moov declared an audio track. Fixed once written. */
        private var audioDeclared = false
        private var videoSeq = 1
        private var audioSeq = 1

        private val video = VideoState()
        private val audio = AudioState()

        private var videoFrag = ArrayList<Sample>()
        private var audioFrag = ArrayList<Sample>()
        private val fragmentEnd = LongArray(3)
        private var videoOrigin: Long? = null
        private var audioOrigin: Long? = null
        private var videoFragBytes = 0L
        private var audioFragBytes = 0L

        fun run(input: InputStream): Stats {
            val demuxer = TsDemuxer { streamType, payload, pts, dts ->
                when (streamType) {
                    // MP4 times samples by decode time, so the DTS is the basis.
                    // A stream with B-frames carries a PTS that steps backwards
                    // across decode order, and using it yields negative sample
                    // durations. Only when no DTS is present does PTS stand in.
                    ST_H264 -> onVideo(false, payload, dts ?: pts ?: 0L)
                    ST_H265 -> onVideo(true, payload, dts ?: pts ?: 0L)
                    ST_AAC_ADTS, ST_MPEG1_AUDIO, ST_MPEG2_AUDIO -> onAudio(payload, null)
                    ST_AAC_LATM -> onAudio(payload, LatmMarker)
                    ST_AC3, ST_EC3 -> onAudio(payload, Ac3Marker)
                }
            }
            demuxer.run(input)
            flushFragment(true)
            if (!headerWritten) {
                // A stream with no decodable video never got a header; say so
                // rather than leaving a valid-looking empty file behind.
                throw RemuxException("No playable video track was found in the stream")
            }
            return Stats(
                videoSamples = video.totalSamples,
                audioSamples = audio.totalSamples,
                width = video.width,
                height = video.height,
            )
        }

        private fun onVideo(hevc: Boolean, payload: ByteArray, dts: Long) {
            if (video.hevcSeen && video.hevc != hevc) return
            video.hevc = hevc
            video.hevcSeen = true
            video.feed(payload, dts)
            maybeWriteHeader()
            if (video.readyToEmit) {
                val ready = video.takeReadySamples()
                for (s in ready) {
                    videoFrag += s
                    videoFragBytes += s.data.size
                }
                if (videoFrag.size >= FRAGMENT_SAMPLE_LIMIT || videoFragBytes >= FRAGMENT_BYTE_LIMIT) {
                    flushFragment(false)
                }
            }
        }

        private fun onAudio(payload: ByteArray, flavour: Any?) {
            audio.feed(payload, flavour)
            maybeWriteHeader()
            if (audio.readyToEmit) {
                val ready = audio.takeReadySamples()
                for (s in ready) {
                    audioFrag += s
                    audioFragBytes += s.data.size
                }
                if (audioFragBytes >= FRAGMENT_BYTE_LIMIT) {
                    flushFragment(false)
                }
            }
        }

        /**
         * Writes `ftyp`+`moov` once every track's configuration is known.
         *
         * Waiting is required, not merely tidy: `mvex`/`trex` and each sample
         * entry describe the tracks, so a track added later could not be
         * declared.
         */
        private fun maybeWriteHeader() {
            if (headerWritten) return
            if (!video.hasConfig) return
            // Whether audio exists can only be settled by seeing its config or by
            // running out of patience. Treating "no audio seen yet" as "no audio"
            // declared a video-only moov for every stream whose audio simply had
            // not arrived, and the audio track then had no trex while its
            // fragments still referenced it.
            if (!audio.hasConfig && video.totalSamples < AUDIO_WAIT_SAMPLE_LIMIT) return

            val includeAudio = audio.hasConfig && audio.totalSamples > 0
            audioDeclared = includeAudio
            out.write(Boxes.ftyp())
            out.write(buildMoov(includeAudio))
            headerWritten = true
        }

        private fun flushFragment(final: Boolean) {
            if (!headerWritten) return
            // Both tracks are emitted together so their fragments stay roughly
            // time-aligned instead of all-video-then-all-audio.
            if (videoFrag.isNotEmpty()) {
                writeFragment(trackId = 1, timescale = VIDEO_TIMESCALE, samples = videoFrag, sequence = videoSeq++)
            }
            // Gated on what the moov actually declared, not on whether audio
            // happens to look available now. Once the header is written a track
            // is fixed: it has an entry in mvex and a trex. Audio whose config
            // arrived after the video-only header was emitted therefore has no
            // trex, and writing fragments for track 2 produced an MP4 no player
            // would open. Dropping the late audio keeps the file valid.
            if (audioFrag.isNotEmpty() && audioDeclared) {
                writeFragment(trackId = 2, timescale = audio.timescale, samples = audioFrag, sequence = audioSeq++)
            } else if (!audioDeclared && (audio.hasConfig || audio.totalSamples > 0)) {
                Diag.log("TS remux: audio arrived after the header was written; keeping a video-only file")
            }
            videoFrag.clear()
            audioFrag.clear()
            videoFragBytes = 0
            audioFragBytes = 0
            if (final) out.flush()
        }

        private fun writeFragment(trackId: Int, timescale: Long, samples: List<Sample>, sequence: Int) {
            if (samples.isEmpty()) return
            val durations = LongArray(samples.size)
            for (i in samples.indices) {
                val next = samples.getOrNull(i + 1)?.dts
                durations[i] = when {
                    next != null && next > samples[i].dts -> next - samples[i].dts
                    i > 0 -> samples[i].dts - samples[i - 1].dts
                    else -> defaultDuration(timescale)
                }
            }
            // A segment's timestamps carry whatever offset the encoder started
            // from, often ten seconds or more into the original programme. Carrying
            // that into baseMediaDecodeTime made the file open with that much black
            // and put the video out of step with the audio, which starts at zero.
            // Each track is therefore rebased on its own first sample.
            val origin = originFor(trackId, samples.first().dts)
            // Source frame timing jitters by a tick or two, so a fragment measured
            // from its own first sample can end just after the next one begins.
            // Starting each fragment where the previous ended keeps the decode
            // timeline strictly increasing; the drift absorbed is a few ticks.
            val rebased = samples.first().dts - origin
            val slot = if (trackId == 1) 1 else 2
            val baseMediaDecodeTime = maxOf(rebased, fragmentEnd[slot])
            val moof = Boxes.moof(trackId, sequence, samples, durations, baseMediaDecodeTime)
            fragmentEnd[slot] = baseMediaDecodeTime + durations.sum()
            // data_offset is measured from the start of the moof to the first
            // byte of sample data, i.e. past the moof and the mdat header.
            val mdatHeader = 8
            val patched = Boxes.patchTrunDataOffset(moof, moof.size + mdatHeader)
            out.write(patched)
            out.write(MdatHeader(samples.sumOf { it.data.size.toLong() } + 8).bytes())
            for (s in samples) out.write(s.data)
        }

        /** First sample timestamp seen on [trackId], captured once and reused. */
        private fun originFor(trackId: Int, firstDts: Long): Long {
            if (trackId == 1) {
                val known = videoOrigin
                if (known != null) return known
                videoOrigin = firstDts
                return firstDts
            }
            val known = audioOrigin
            if (known != null) return known
            audioOrigin = firstDts
            return firstDts
        }

        private fun defaultDuration(timescale: Long): Long =
            if (timescale == VIDEO_TIMESCALE) 3000L else 1024L

        private fun buildMoov(includeAudio: Boolean): ByteArray {
            val parts = ArrayList<ByteArray>()
            parts += Boxes.mvhd(nextTrackId = if (includeAudio) 3 else 2)
            parts += buildTrak(
                trackId = 1,
                kind = 'v',
                timescale = VIDEO_TIMESCALE,
                width = video.width,
                height = video.height,
                sampleEntry = videoSampleEntry(),
            )
            if (includeAudio) {
                parts += buildTrak(
                    trackId = 2,
                    kind = 's',
                    timescale = audio.timescale,
                    width = 0,
                    height = 0,
                    sampleEntry = audioSampleEntry(),
                )
            }
            return Boxes.box("moov", Boxes.concat(parts))
        }

        private fun videoSampleEntry(): ByteArray {
            val body = ByteArrayBuilder(128)
            for (i in 0 until 6) body.u8(0) // reserved
            body.u16(1) // data_reference_index
            for (i in 0 until 16) body.u8(0) // pre_defined / reserved
            body.u16(video.width)
            body.u16(video.height)
            body.u32(0x00480000) // horizresolution, 72dpi
            body.u32(0x00480000) // vertresolution, 72dpi
            body.u32(0) // reserved
            body.u16(1) // frame_count
            body.u8Array(ByteArray(32)) // compressorname
            body.u16(0x0018) // depth
            body.u16(0xFFFF) // pre_defined = -1
            body.u8Array(video.configBox())
            return Boxes.box(if (video.hevc) "hvc1" else "avc1", body.toByteArray())
        }

        private fun audioSampleEntry(): ByteArray {
            val body = ByteArrayBuilder(64)
            for (i in 0 until 6) body.u8(0) // reserved
            body.u16(1) // data_reference_index
            for (i in 0 until 8) body.u8(0) // reserved (version 0)
            body.u16(audio.channels.coerceAtLeast(1))
            body.u16(16) // sample size
            body.u16(0) // pre_defined
            body.u16(0) // reserved
            body.u32((audio.timescale.toInt() and 0xFFFF) shl 16) // 16.16 sample rate
            body.u8Array(Boxes.esds(audio.ascBytes, audio.timescale.toInt(), audio.esObjectType))
            return Boxes.box("mp4a", body.toByteArray())
        }

        private fun buildTrak(
            trackId: Int,
            kind: Char,
            timescale: Long,
            width: Int,
            height: Int,
            sampleEntry: ByteArray,
        ): ByteArray {
            val stbl = if (kind == 'v') {
                Boxes.videoStbl(sampleEntry)
            } else {
                Boxes.audioStbl(sampleEntry)
            }
            val mediaHeader = if (kind == 'v') Boxes.vmhd() else Boxes.smhd()
            val minf = Boxes.box("minf", Boxes.concat(listOf(mediaHeader, Boxes.dinf(), stbl)))
            val mdia = Boxes.box(
                "mdia",
                Boxes.concat(listOf(Boxes.mdhd(timescale), Boxes.hdlr(kind), minf)),
            )
            val mvex = Boxes.box("mvex", Boxes.concat(listOf(Boxes.trex(trackId))))
            return Boxes.box(
                "trak",
                Boxes.concat(listOf(Boxes.tkhd(trackId, kind, width, height), mdia, mvex)),
            )
        }

        override fun close() {
            out.flush()
        }
    }

    private object LatmMarker
    private object Ac3Marker

    // ------------------------------------------------------------------
    // Video access-unit assembly
    // ------------------------------------------------------------------

    /**
     * Splits H.264/H.265 payloads into access units and holds the codec config.
     *
     * A PES packet is not a frame: it can carry several access units or only
     * part of one, and an HLS join always cuts somewhere arbitrary. Boundaries
     * come from an Access Unit Delimiter when the encoder emits one, and
     * otherwise from an IDR opening a new picture — which is what every segment
     * boundary looks like too, so whole frames survive a join.
     */
    private class VideoState {
        var hevc = false
        var hevcSeen = false
        var width = 0
            private set
        var height = 0
            private set
        var totalSamples = 0
            private set

        private val parameterSets = ArrayList<ByteArray>()
        private val pending = ByteArrayBuilder()
        private var pendingDts = 0L
        private var pendingHasDts = false
        private var sawVcl = false
        private var lastDts = 0L

        private val ready = ArrayList<Sample>()
        private var configReady = false
        private var firstSamplePendingSync = true

        val hasConfig: Boolean get() = configReady
        val readyToEmit: Boolean get() = configReady && ready.isNotEmpty()

        fun feed(payload: ByteArray, dts: Long) {
            if (payload.size < 4) return
            // One NAL per pass. The access unit is reassembled separately from the
            // whole payload below, so the scan can walk NAL by NAL and start at
            // the first start code it finds: a TS payload normally resumes inside
            // the previous NAL, and stepping over it here used to skip the NAL
            // header that sits at the previous NAL's end.
            var scan = 0
            while (scan + 2 < payload.size) {
                val nalStart = findStartCode(payload, scan) ?: break
                val nalType = if (hevc) (payload[nalStart].toInt() and 0x7E) shr 1
                else payload[nalStart].toInt() and 0x1F
                val nalEnd = findStartCode(payload, nalStart + 1) ?: payload.size
                // TS payloads are zero-padded up to the packet boundary. Those
                // padding bytes are not part of the frame, and an NAL always ends
                // on a byte with a stop bit, so every trailing zero is padding.
                var nalBodyEnd = nalEnd
                while (nalBodyEnd > nalStart + 1 && payload[nalBodyEnd - 1] == 0.toByte()) {
                    nalBodyEnd--
                }
                // The NAL is kept whole, header byte included. The configuration
                // record in the mp4 header has to carry that header, and the SPS
                // reader indexes the payload from it, so stripping it here would
                // make both the type checks and the dimension read come out wrong.
                val nal = if (nalBodyEnd > nalStart) {
                    payload.copyOfRange(nalStart, nalBodyEnd)
                } else {
                    ByteArray(0)
                }

                when {
                    nalType == audType() -> if (sawVcl) emit()
                    isParameterSet(nalType) -> {
                        if (nal.size > 1) {
                            // Only the same kind is replaced. Clearing the whole
                            // list on an SPS would throw away a VPS that HEVC
                            // sent just ahead of it, and the track would then
                            // never look configured.
                            if (nalType == spsType()) {
                                parameterSets.removeAll { Nals.isSps(it, hevc) }
                                parameterSets += nal
                                readDimensions(nal)
                            } else {
                                parameterSets.removeAll { Nals.isSameKind(it, nalType, hevc) }
                                parameterSets += nal
                            }
                            if (hasAllParameterSets()) configReady = true
                        }
                    }
                    isVcl(nalType) -> {
                        // A sync sample ends the previous picture. Splitting there
                        // rather than on the next AUD keeps the stream aligned even
                        // when the encoder omits access unit delimiters.
                        if (sawVcl && isSyncNal(nalType)) emit()
                        sawVcl = true
                    }
                }

                // Resume just past this NAL's header so the next start code is
                // found, without rescanning the header we just read.
                scan = nalStart + 1
            }
            if (sawVcl) {
                pending.u8Array(payload)
                if (!pendingHasDts) {
                    pendingDts = dts
                    pendingHasDts = true
                }
            }
        }

        private fun hasAllParameterSets(): Boolean {
            val hasVps = parameterSets.any { Nals.isVps(it, hevc) }
            val hasSps = parameterSets.any { Nals.isSps(it, hevc) }
            val hasPps = parameterSets.any { Nals.isPps(it, hevc) }
            return hasSps && hasPps && (hasVps || !hevc)
        }

        private fun emit() {
            if (!configReady) {
                // Parameter sets sometimes arrive after the first frames. Those
                // frames are dropped rather than emitted, because a fragment
                // whose SPS/PPS the decoder has not seen cannot start playing.
                // Dropping them is safe: the next IDR restarts the stream.
                pending.reset()
                pendingHasDts = false
                sawVcl = false
                return
            }
            val data = pending.toByteArrayAndReset()
            val dts = if (pendingHasDts) pendingDts else lastDts
            pendingHasDts = false
            sawVcl = false
            if (data.isEmpty()) return
            val sync = firstSamplePendingSync
            firstSamplePendingSync = false
            ready += Sample(data, dts, sync)
            totalSamples++
            lastDts = dts
        }

        fun takeReadySamples(): List<Sample> {
            if (ready.isEmpty()) return emptyList()
            val copy = ArrayList(ready)
            ready.clear()
            return copy
        }

        private fun isVcl(type: Int): Boolean = if (hevc) type in 0..31 else type in 1..5

        private fun audType(): Int = if (hevc) HEVC_AUD else AUD_NAL

        private fun spsType(): Int = if (hevc) HEVC_SPS else NAL_SPS

        private fun ppsType(): Int = if (hevc) HEVC_PPS else NAL_PPS

        private fun isParameterSet(type: Int): Boolean =
            type == spsType() || type == ppsType() || (hevc && type == HEVC_VPS)

        private fun isSyncNal(type: Int): Boolean =
            if (hevc) type == 16 || type == 17 || type in 19..23 else type == 5

        /**
         * Index of the next NAL header byte at or after [from], or null.
         *
         * A start code does not sit one byte behind the previous NAL header: the
         * whole NAL sits between them, so the bytes in between have to be scanned
         * rather than tested at one offset.
         */
        private fun findStartCode(data: ByteArray, from: Int): Int? {
            var i = from
            while (i + 2 < data.size) {
                val len = startCodeLengthAt(data, i) ?: run {
                    i++
                    continue
                }
                return i + len
            }
            return null
        }

        /** Length of the start code at [i], or null when there isn't one. */
        private fun startCodeLengthAt(data: ByteArray, i: Int): Int? {
            if (i + 2 >= data.size) return null
            if (data[i] != 0.toByte() || data[i + 1] != 0.toByte()) return null
            if (data[i + 2] == 1.toByte()) return 3
            if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) return 4
            return null
        }

        private fun readDimensions(sps: ByteArray) {
            val dims = if (hevc) HevcSps.dimensions(sps) else H264Sps.dimensions(sps)
            if (dims != null && dims[0] > 0 && dims[1] > 0) {
                width = dims[0]
                height = dims[1]
            }
        }

        /** `avcC`/`hvcC` payload for the `moov`, built from the in-band NALs. */
        fun configBox(): ByteArray {
            val vps = parameterSets.firstOrNull { Nals.isVps(it, hevc) }
            val sps = parameterSets.firstOrNull { Nals.isSps(it, hevc) }
            val pps = parameterSets.firstOrNull { Nals.isPps(it, hevc) }
            return if (hevc) {
                VideoConfig.buildHevc(vps, sps, pps)
            } else {
                VideoConfig.buildAvc(sps, pps)
            }
        }
    }

    // H.264
    private const val NAL_SPS = 7
    private const val NAL_PPS = 8
    private const val AUD_NAL = 9
    private const val NAL_VPS = 32

    // HEVC
    private const val HEVC_VPS = 32
    private const val HEVC_SPS = 33
    private const val HEVC_PPS = 34
    private const val HEVC_AUD = 35

    private object Nals {
        fun type(nal: ByteArray, hevc: Boolean): Int =
            if (hevc) (nal[0].toInt() and 0x7E) shr 1 else nal[0].toInt() and 0x1F

        fun isVps(nal: ByteArray, hevc: Boolean): Boolean = hevc && type(nal, hevc) == HEVC_VPS

        fun isSps(nal: ByteArray, hevc: Boolean): Boolean =
            type(nal, hevc) == (if (hevc) HEVC_SPS else NAL_SPS)

        fun isPps(nal: ByteArray, hevc: Boolean): Boolean =
            type(nal, hevc) == (if (hevc) HEVC_PPS else NAL_PPS)

        fun isSameKind(nal: ByteArray, wanted: Int, hevc: Boolean): Boolean = type(nal, hevc) == wanted
    }

    // ------------------------------------------------------------------
    // Audio sample assembly
    // ------------------------------------------------------------------

    /**
     * AAC in ADTS framing, plus AC-3.
     *
     * Each ADTS frame is one MP4 sample of exactly 1024 samples, so timing comes
     * from counting frames rather than from PES timestamps. That is the more
     * reliable source: an AAC PES timestamp lands on a frame boundary but not
     * on the sample boundary inside it, and trusting it accumulates drift over a
     * long file.
     */
    private class AudioState {
        var hasConfig = false
            private set
        var totalSamples = 0
            private set
        var channels = 2
            private set
        var esObjectType = 0x40
            private set
        var ascBytes = ByteArray(0)
            private set

        val inTrack: Boolean get() = hasConfig && totalSamples > 0
        val timescale: Long get() = if (sampleRate > 0) sampleRate.toLong() else 44_100L
        private var sampleRate = 0

        private val ready = ArrayList<Sample>()
        private var frameIndex = 0L
        private var carry = ByteArray(0)

        val readyToEmit: Boolean get() = ready.isNotEmpty()

        fun feed(payload: ByteArray, flavour: Any?) {
            if (flavour === Ac3Marker) feedAc3(payload) else feedAac(payload)
        }

        private fun feedAac(payload: ByteArray) {
            // A frame can straddle two PES packets, so any trailing partial frame
            // is held over and reopened on the next payload. Rescanning the leftover
            // bytes on their own instead finds an ADTS syncword inside frame data
            // and emits a frame that cannot decode.
            val data = if (carry.isEmpty()) payload else carry + payload
            carry = ByteArray(0)
            var stopped = false
            var i = 0
            while (i + 7 <= data.size) {
                if (data[i] != 0xFF.toByte() || (data[i + 1].toInt() and 0xF0) != 0xF0) {
                    i++
                    continue
                }
                val protectionAbsent = (data[i + 1].toInt() and 0x01) == 1
                val profile = (data[i + 2].toInt() and 0xC0) shr 6
                val freqIndex = (data[i + 2].toInt() and 0x3C) shr 2
                val channelConfig = ((data[i + 2].toInt() and 0x01) shl 2) or
                    ((data[i + 3].toInt() and 0xC0) shr 6)
                val frameLength = (((data[i + 3].toInt() and 0x03) shl 11) or
                    ((data[i + 4].toInt() and 0xFF) shl 3) or
                    ((data[i + 5].toInt() and 0xE0) shr 5))
                if (frameLength < 7 || frameLength > 2048) {
                    i++
                    continue
                }
                if (i + frameLength > data.size) {
                    carry = data.copyOfRange(i, data.size)
                    stopped = true
                    break
                }
                val rate = AAC_SAMPLE_RATES[freqIndex]
                if (rate > 0) {
                    if (!hasConfig) {
                        sampleRate = rate
                        channels = if (channelConfig > 0) channelConfig else 2
                        ascBytes = AudioSpecificConfig.aac(profile, freqIndex, channelConfig)
                        esObjectType = 0x40
                        hasConfig = true
                    }
                    val headerLen = if (protectionAbsent) 7 else 9
                    val frame = data.copyOfRange(i + headerLen, i + frameLength)
                    if (frame.isNotEmpty()) {
                        ready += Sample(frame, frameIndex * 1024L, true)
                        frameIndex++
                        totalSamples++
                    }
                }
                i += frameLength
            }
            // Fewer than seven bytes left means a syncword may straddle the split.
            if (!stopped && i < data.size) carry = data.copyOfRange(i, data.size)
        }

        /** Discards framing state that no longer describes the elementary stream. */
        private fun feedAc3(payload: ByteArray) {
            if (!hasConfig) {
                hasConfig = true
                sampleRate = 48000
                channels = 2
                esObjectType = 0x81
                ascBytes = AudioSpecificConfig.dummy()
            }
            var i = 0
            while (i + 8 <= payload.size) {
                if (payload[i] != 0x0B.toByte() || payload[i + 1] != 0x77.toByte()) {
                    i++
                    continue
                }
                val frameSize = (((payload[i + 2].toInt() and 0x03) shl 8) or
                    (payload[i + 3].toInt() and 0xFF))
                if (frameSize < 8 || i + frameSize > payload.size) {
                    i++
                    continue
                }
                // MP4 wants the raw AC-3 frame, not its sync header.
                val frame = payload.copyOfRange(i + 8, i + frameSize)
                if (frame.isNotEmpty()) {
                    ready += Sample(frame, frameIndex * 1536L, true)
                    frameIndex++
                    totalSamples++
                }
                i += frameSize
            }
        }

        fun takeReadySamples(): List<Sample> {
            if (ready.isEmpty()) return emptyList()
            val copy = ArrayList(ready)
            ready.clear()
            return copy
        }
    }

    private val AAC_SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
        16000, 12000, 11025, 8000, 7350, 0, 0, 0,
    )

    private object AudioSpecificConfig {
        /** Two bytes: objectType (5b), samplingFrequencyIndex (4b), channelConfig (4b), 3 zero bits. */
        fun aac(adtsProfile: Int, freqIndex: Int, channelConfig: Int): ByteArray {
            val objectType = if (adtsProfile == 1) 2 else adtsProfile + 1
            val value = ((objectType and 0x0F) shl 11) or
                ((freqIndex and 0x0F) shl 7) or
                ((channelConfig and 0x0F) shl 3)
            return byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
        }

        /**
         * AC-3 carries no decoder configuration of its own; `esds` still needs a
         * DecoderSpecificInfo box, and a zero-length one is what the format
         * expects for this object type.
         */
        fun dummy(): ByteArray = ByteArray(0)
    }

    private object VideoConfig {
        /**
         * AVCDecoderConfigurationRecord (`avcC`).
         *
         * The first three bytes are copies of SPS bytes 1..3, which is why the
         * SPS has to be captured in-band rather than reconstructed from the
         * PMT's registration descriptor.
         */
        fun buildAvc(sps: ByteArray?, pps: ByteArray?): ByteArray {
            if (sps == null || sps.size < 4 || pps == null) return ByteArray(0)
            val body = ByteArrayBuilder(32 + sps.size + pps.size)
            body.u8(1) // configurationVersion
            body.u8(sps[1].toInt() and 0xFF) // AVCProfileIndication
            body.u8(sps[2].toInt() and 0xFF) // profile_compatibility
            body.u8(sps[3].toInt() and 0xFF) // AVCLevelIndication
            body.u8(0xFF) // reserved(6) + lengthSizeMinusOne = 3 (4-byte lengths)
            body.u8(0xE1) // reserved(3) + numOfSequenceParameterSets = 1
            body.u16(sps.size)
            body.u8Array(sps)
            body.u8(1) // numOfPictureParameterSets
            body.u16(pps.size)
            body.u8Array(pps)
            return body.toByteArray()
        }

        /** HEVCDecoderConfigurationRecord (`hvcC`). */
        fun buildHevc(vps: ByteArray?, sps: ByteArray?, pps: ByteArray?): ByteArray {
            if (sps == null || sps.size < 12) return ByteArray(0)
            val body = ByteArrayBuilder(64 + (vps?.size ?: 0) + sps.size + (pps?.size ?: 0))
            body.u8(1) // configurationVersion
            // Bytes 1..11 of the SPS are the profile_tier_level block, which the
            // record copies verbatim.
            body.u8Array(sps.copyOfRange(1, 12))
            body.u16(0xF000) // reserved(4)=1111 + min_spatial_segmentation_idc = 0
            body.u8(0xFC) // reserved(6)=111111 + parallelismType = 0
            body.u8(0xFC) // reserved(6) + chromaFormat (patched below)
            body.u8(0xF8) // reserved(5) + bitDepthLumaMinus8
            body.u8(0xF8) // reserved(5) + bitDepthChromaMinus8
            body.u16(0) // avgFrameRate
            body.u8(0x0F) // constantFrameRate(2) numTemporalLayers(3) temporalIdNested(1) lenSizeMinusOne(3)
            body.u8(0) // numOfArrays patched below
            var arrays = 0
            fun addArray(type: Int, nal: ByteArray?) {
                if (nal == null || nal.isEmpty()) return
                body.u8(0x80 or (type and 0x3F)) // array_completeness=1
                body.u16(1)
                body.u16(nal.size)
                body.u8Array(nal)
                arrays++
            }
            addArray(NAL_VPS, vps)
            addArray(NAL_SPS, sps)
            addArray(NAL_PPS, pps)
            val bytes = body.toByteArray()
            bytes[22] = arrays.toByte() // numOfArrays sits after the 23-byte preamble
            return bytes
        }
    }

    // ------------------------------------------------------------------
    // ISO-BMFF box construction
    // ------------------------------------------------------------------

    private class MdatHeader(private val size: Long) {
        fun bytes(): ByteArray {
            val b = ByteArrayBuilder(8)
            b.u32(size.toInt())
            b.append("mdat")
            return b.toByteArray()
        }
    }

    private object Boxes {

        fun concat(parts: List<ByteArray>): ByteArray {
            var total = 0
            for (p in parts) total += p.size
            val out = ByteArray(total)
            var at = 0
            for (p in parts) {
                System.arraycopy(p, 0, out, at, p.size)
                at += p.size
            }
            return out
        }

        fun box(type: String, body: ByteArray): ByteArray {
            val out = ByteArrayBuilder(8 + body.size)
            out.u32(8 + body.size)
            out.append(type)
            out.u8Array(body)
            return out.toByteArray()
        }

        fun ftyp(): ByteArray {
            // Derived, never hand-written: 8 byte header, 4 byte major brand,
            // 4 byte minor version, then the compatible brands. Declaring a size
            // that disagrees with the bytes actually written makes every box
            // after this one unreadable.
            val brands = listOf("isom", "iso2", "avc1", "mp41")
            val size = 8 + 4 + 4 + brands.size * 4
            val out = ByteArrayBuilder(size)
            out.u32(size)
            out.append("ftyp")
            out.append("isom") // major_brand
            out.u32(512) // minor_version
            for (brand in brands) out.append(brand)
            return out.toByteArray()
        }

        /** Unity matrix: no rotation, no scaling. */
        private fun unityMatrix(out: ByteArrayBuilder) {
            out.u32(0x00010000); out.u32(0); out.u32(0)
            out.u32(0); out.u32(0x00010000); out.u32(0)
            out.u32(0); out.u32(0); out.u32(0x40000000.toInt())
        }

        fun mvhd(nextTrackId: Int): ByteArray {
            val out = ByteArrayBuilder(108)
            out.u32(0) // version + flags
            out.u32(0) // creation_time
            out.u32(0) // modification_time
            out.u32(1000) // timescale
            out.u32(0) // duration, 0 for a fragmented file
            out.u32(0x00010000) // rate 1.0
            out.u16(0x0100) // volume 1.0
            out.u16(0) // reserved
            for (i in 0 until 2) out.u32(0) // reserved
            unityMatrix(out)
            for (i in 0 until 6) out.u32(0) // pre_defined
            out.u32(nextTrackId)
            return box("mvhd", out.toByteArray())
        }

        fun tkhd(trackId: Int, kind: Char, width: Int, height: Int): ByteArray {
            val out = ByteArrayBuilder(92)
            out.u32(0x00000007) // version 0, flags: enabled | in movie | in preview
            out.u32(0) // creation_time
            out.u32(0) // modification_time
            out.u32(trackId)
            out.u32(0) // reserved
            out.u32(0) // duration
            for (i in 0 until 2) out.u32(0) // reserved
            out.u16(0) // layer
            out.u16(0) // alternate_group
            out.u16(if (kind == 's') 0x0100 else 0) // volume
            out.u16(0) // reserved
            unityMatrix(out)
            // 16.16 fixed point, so a 640px width is written as 640 << 16.
            out.u32(if (width > 0) (width shl 16) else 0)
            out.u32(if (height > 0) (height shl 16) else 0)
            return box("tkhd", out.toByteArray())
        }

        fun mdhd(timescale: Long): ByteArray {
            val out = ByteArrayBuilder(24)
            out.u32(0) // version + flags
            out.u32(0) // creation_time
            out.u32(0) // modification_time
            out.u32(timescale.toInt())
            out.u32(0) // duration, 0 for a fragmented file
            out.u16(0x55C4) // language: 'und'
            out.u16(0) // pre_defined
            return box("mdhd", out.toByteArray())
        }

        fun hdlr(kind: Char): ByteArray {
            val out = ByteArrayBuilder(32)
            out.u32(0) // version + flags
            out.u32(0) // pre_defined
            val handler = if (kind == 'v') "vide" else "soun"
            for (c in handler) out.append(c.toString())
            for (i in 0 until 12) out.u8(0) // reserved
            out.append(if (kind == 'v') "VideoHandler" else "SoundHandler")
            out.u8(0) // null-terminated
            return box("hdlr", out.toByteArray())
        }

        fun vmhd(): ByteArray {
            val out = ByteArrayBuilder(12)
            out.u32(0x00000001) // version 0, flags 1
            out.u16(0) // graphicsmode
            for (i in 0 until 3) out.u16(0) // opcolor
            return box("vmhd", out.toByteArray())
        }

        fun smhd(): ByteArray {
            val out = ByteArrayBuilder(8)
            out.u32(0) // version + flags
            out.u16(0) // balance
            out.u16(0) // reserved
            return box("smhd", out.toByteArray())
        }

        fun dinf(): ByteArray {
            // 'url ' with flags=1 declares the media to be in this same file,
            // so no external data reference is needed.
            val url = ByteArrayBuilder(12)
            url.u32(12)
            url.append("url ")
            url.u32(1)
            val drefBody = ByteArrayBuilder(16)
            drefBody.u32(0) // version + flags
            drefBody.u32(1) // entry_count
            drefBody.u8Array(url.toByteArray())
            return box("dinf", box("dref", drefBody.toByteArray()))
        }

        fun videoStbl(sampleEntry: ByteArray): ByteArray =
            box("stbl", concat(listOf(stsd(sampleEntry), emptyTable("stts"), emptyTable("stsc"), emptyStsz(), emptyTable("stco"))))

        fun audioStbl(sampleEntry: ByteArray): ByteArray =
            box("stbl", concat(listOf(stsd(sampleEntry), emptyTable("stts"), emptyTable("stsc"), emptyStsz(), emptyTable("stco"))))

        private fun stsd(entry: ByteArray): ByteArray {
            val out = ByteArrayBuilder(8 + entry.size)
            out.u32(0) // version + flags
            out.u32(1) // entry_count
            out.u8Array(entry)
            return box("stsd", out.toByteArray())
        }

        /** A sample table with no entries: the fragments carry the real index. */
        private fun emptyTable(type: String): ByteArray {
            val out = ByteArrayBuilder(8)
            out.u32(0) // version + flags
            out.u32(0) // entry_count
            return box(type, out.toByteArray())
        }

        private fun emptyStsz(): ByteArray {
            val out = ByteArrayBuilder(12)
            out.u32(0) // version + flags
            out.u32(0) // sample_size 0 = per-sample sizes follow
            out.u32(0) // sample_count
            return box("stsz", out.toByteArray())
        }

        fun trex(trackId: Int): ByteArray {
            val out = ByteArrayBuilder(24)
            out.u32(0) // version + flags
            out.u32(trackId)
            out.u32(1) // default_sample_description_index
            out.u32(0) // default_sample_duration
            out.u32(0) // default_sample_size
            out.u32(0) // default_sample_flags
            return box("trex", out.toByteArray())
        }

        /**
         * One fragment: `moof` carrying the sample index, `mdat` carrying the
         * bytes. `mdat` is written by the caller immediately after.
         */
        fun moof(
            trackId: Int,
            sequence: Int,
            samples: List<Sample>,
            durations: LongArray,
            baseMediaDecodeTime: Long,
        ): ByteArray {
            val mfhdOut = ByteArrayBuilder(8)
            mfhdOut.u32(0)
            mfhdOut.u32(sequence)

            val tfhdOut = ByteArrayBuilder(8)
            // default-base-is-moof: the base offset is this box's own start, so
            // no data_offset arithmetic is needed for the sample data itself.
            tfhdOut.u32(0x020000)
            tfhdOut.u32(trackId)

            val tfdtOut = ByteArrayBuilder(16)
            tfdtOut.u8(1) // version 1 -> 64-bit baseMediaDecodeTime
            for (i in 0 until 3) tfdtOut.u8(0) // flags
            tfdtOut.u64(baseMediaDecodeTime)

            val trunOut = ByteArrayBuilder(16 + samples.size * 12)
            trunOut.u8(0) // version 0
            // data-offset | sample-duration | sample-size | sample-flags
            trunOut.u8(0x00); trunOut.u8(0x07); trunOut.u8(0x01)
            trunOut.u32(samples.size)
            trunOut.u32(0) // data_offset, patched by patchTrunDataOffset
            for (i in samples.indices) {
                trunOut.u32(durations[i].toInt())
                trunOut.u32(samples[i].data.size)
                trunOut.u32(if (samples[i].sync) 0x02000000 else 0x01010000)
            }

            val traf = box(
                "traf",
                concat(
                    listOf(
                        box("tfhd", tfhdOut.toByteArray()),
                        box("tfdt", tfdtOut.toByteArray()),
                        box("trun", trunOut.toByteArray()),
                    ),
                ),
            )
            return box("moof", concat(listOf(box("mfhd", mfhdOut.toByteArray()), traf)))
        }

        /**
         * Writes `data_offset` into an already-built `moof`.
         *
         * The value depends on the finished size of the `moof`, which is not
         * known until it is built, so the field is a placeholder patched here
         * rather than reserved bytes.
         */
        fun patchTrunDataOffset(moof: ByteArray, value: Int): ByteArray {
            var at = -1
            var i = 0
            while (i + 4 <= moof.size) {
                if (moof[i] == 't'.code.toByte() && moof[i + 1] == 'r'.code.toByte() &&
                    moof[i + 2] == 'u'.code.toByte() && moof[i + 3] == 'n'.code.toByte()
                ) {
                    at = i
                    break
                }
                i++
            }
            if (at < 0) return moof
            // Inside `trun`: 4 bytes version+flags, 4 bytes sample_count, then
            // the 4-byte data_offset.
            val field = at + 12
            moof[field] = ((value shr 24) and 0xFF).toByte()
            moof[field + 1] = ((value shr 16) and 0xFF).toByte()
            moof[field + 2] = ((value shr 8) and 0xFF).toByte()
            moof[field + 3] = (value and 0xFF).toByte()
            return moof
        }

        /** ES_Descriptor chain describing an audio track to the decoder. */
        fun esds(asc: ByteArray, sampleRate: Int, objectTypeIndication: Int): ByteArray {
            val dsi = byteArrayOf(0x05, asc.size.toByte()) + asc
            val dcdBody = ByteArrayBuilder(16 + dsi.size)
            dcdBody.u8(objectTypeIndication)
            dcdBody.u8(0x15) // streamType 5 (audio), upStream 0, reserved 1
            dcdBody.u8(0); dcdBody.u8(0); dcdBody.u8(0) // bufferSizeDB
            dcdBody.u8(0); dcdBody.u8(0) // maxBitrate, unset
            dcdBody.u8(0); dcdBody.u8(0) // avgBitrate, unset
            dcdBody.u8Array(dsi)
            val dcd = ByteArrayBuilder(4 + dcdBody.size())
            dcd.u8(0x04)
            dcd.u8(dcdBody.size())
            dcd.u8Array(dcdBody.toByteArray())
            val sl = byteArrayOf(0x06, 0x01, 0x02)
            val esBody = ByteArrayBuilder(4 + dcd.size() + sl.size)
            esBody.u8(0x00); esBody.u8(0x01) // ES_ID
            esBody.u8(0) // flags, no dependency / URL / OCR
            esBody.u8Array(dcd.toByteArray())
            esBody.u8Array(sl)
            val out = ByteArrayBuilder(8 + esBody.size())
            out.u8(0x03)
            out.u8(esBody.size())
            out.u8Array(esBody.toByteArray())
            return box("esds", out.toByteArray())
        }
    }

    // ------------------------------------------------------------------
    // Parameter-set parsing
    // ------------------------------------------------------------------

    /** MSB-first bit reader, for the Exp-Golomb fields in an SPS. */
    private class BitReader(private val data: ByteArray, private var bitPos: Int = 0) {
        fun bit(): Int {
            if (bitPos >= data.size * 8) return 0
            val byte = data[bitPos shr 3].toInt() and 0xFF
            val bit = (byte shr (7 - (bitPos and 7))) and 1
            bitPos++
            return bit
        }

        fun bits(count: Int): Int {
            var value = 0
            for (i in 0 until count) value = (value shl 1) or bit()
            return value
        }

        fun ue(): Int {
            var zeros = 0
            while (zeros < 32 && bit() == 0) zeros++
            if (zeros == 0) return 0
            return (1 shl zeros) - 1 + bits(zeros)
        }

        fun se(): Int {
            val value = ue()
            return if (value % 2 == 0) -(value / 2) else (value + 1) / 2
        }
    }

    private object H264Sps {
        private val HIGH_PROFILES = setOf(
            100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135,
        )

        fun dimensions(sps: ByteArray): IntArray? {
            if (sps.size < 4) return null
            val r = BitReader(sps, 8) // skip NAL header byte
            r.bits(8) // profile_idc
            r.bits(8) // constraint flags + reserved
            r.bits(8) // level_idc
            r.ue() // seq_parameter_set_id

            var chromaFormatIdc = 1
            val profileIdc = sps[1].toInt() and 0xFF
            if (profileIdc in HIGH_PROFILES) {
                chromaFormatIdc = r.ue()
                if (chromaFormatIdc == 3) r.bit() // separate_colour_plane_flag
                r.ue() // bit_depth_luma_minus8
                r.ue() // bit_depth_chroma_minus8
                r.bit() // qpprime_y_zero_transform_bypass_flag
                if (r.bit() == 1) { // seq_scaling_matrix_present_flag
                    val lists = if (chromaFormatIdc != 3) 8 else 12
                    for (i in 0 until lists) {
                        if (r.bit() == 1) skipScalingList(r, if (i < 6) 16 else 64)
                    }
                }
            }

            r.ue() // log2_max_frame_num_minus4
            when (val picOrderCntType = r.ue()) {
                0 -> r.ue() // log2_max_pic_order_cnt_lsb_minus4
                1 -> {
                    r.bit() // delta_pic_order_always_zero_flag
                    r.se() // offset_for_non_ref_pic
                    r.se() // offset_for_top_to_bottom_field
                    val cycle = r.ue()
                    for (i in 0 until cycle) r.se()
                }
            }
            r.ue() // max_num_ref_frames
            r.bit() // gaps_in_frame_num_value_allowed_flag

            val widthInMbs = r.ue() + 1
            val heightInMapUnits = r.ue() + 1
            val frameMbsOnly = r.bit()
            if (frameMbsOnly == 0) r.bit() // mb_adaptive_frame_field_flag
            r.bit() // direct_8x8_inference_flag

            var cropLeft = 0
            var cropRight = 0
            var cropTop = 0
            var cropBottom = 0
            if (r.bit() == 1) { // frame_cropping_flag
                cropLeft = r.ue()
                cropRight = r.ue()
                cropTop = r.ue()
                cropBottom = r.ue()
            }

            val cropUnitX: Int
            val cropUnitY: Int
            if (chromaFormatIdc == 0) {
                cropUnitX = 1
                cropUnitY = if (frameMbsOnly == 1) 1 else 2
            } else {
                val subWidthC = if (chromaFormatIdc == 3) 1 else 2
                val subHeightC = if (chromaFormatIdc == 1) 2 else 1
                cropUnitX = subWidthC
                cropUnitY = subHeightC * (2 - frameMbsOnly)
            }

            val width = widthInMbs * 16 - cropUnitX * (cropLeft + cropRight)
            val height = heightInMapUnits * 16 * (2 - frameMbsOnly) - cropUnitY * (cropTop + cropBottom)
            if (width <= 0 || height <= 0) return null
            return intArrayOf(width, height)
        }

        private fun skipScalingList(r: BitReader, size: Int) {
            var lastScale = 8
            var nextScale = 8
            for (i in 0 until size) {
                if (nextScale != 0) {
                    val delta = r.se()
                    nextScale = (lastScale + delta + 256) % 256
                }
                lastScale = if (nextScale == 0) lastScale else nextScale
            }
        }
    }

    private object HevcSps {
        fun dimensions(sps: ByteArray): IntArray? {
            if (sps.size < 12) return null
            val r = BitReader(sps, 8)
            r.bits(4) // sps_video_parameter_set_id
            val maxSubLayers = r.bits(3) + 1
            r.bit() // sps_temporal_id_nesting_flag

            // profile_tier_level
            r.bits(2); r.bit(); r.bits(5) // general_profile_space / tier / profile_idc
            r.bits(32) // general_profile_compatibility_flags
            r.bits(48) // general_constraint_indicator_flags
            r.bits(8) // general_level_idc
            val profilePresent = IntArray(maxSubLayers)
            val levelPresent = IntArray(maxSubLayers)
            for (i in 0 until maxSubLayers) {
                profilePresent[i] = r.bit()
                levelPresent[i] = r.bit()
            }
            if (maxSubLayers > 1) for (i in maxSubLayers..7) r.bits(2)
            for (i in 0 until maxSubLayers) {
                if (profilePresent[i] == 1) r.bits(88)
                if (levelPresent[i] == 1) r.bits(8)
            }

            r.ue() // sps_seq_parameter_set_id
            val chromaFormatIdc = r.ue()
            if (chromaFormatIdc == 3) r.bit() // separate_colour_plane_flag
            val width = r.ue()
            val height = r.ue()
            var left = 0
            var right = 0
            var top = 0
            var bottom = 0
            if (r.bit() == 1) { // conformance_window_flag
                left = r.ue(); right = r.ue(); top = r.ue(); bottom = r.ue()
            }
            val subWidthC = if (chromaFormatIdc == 1 || chromaFormatIdc == 2) 2 else 1
            val subHeightC = if (chromaFormatIdc == 1) 2 else 1
            val finalWidth = width - subWidthC * (left + right)
            val finalHeight = height - subHeightC * (top + bottom)
            if (finalWidth <= 0 || finalHeight <= 0) return null
            return intArrayOf(finalWidth, finalHeight)
        }
    }

    // ------------------------------------------------------------------
    // TS demuxing
    // ------------------------------------------------------------------

    private class SectionReader(private val data: ByteArray, var position: Int) {
        var sectionLength = 0
            private set
        private var bodyStart = 0

        /** First byte past the end of this section, CRC included in [sectionLength]. */
        val sectionEnd: Int get() = bodyStart + sectionLength

        /**
         * Consumes the pointer_field and positions at the first body byte.
         *
         * The pointer_field says how many bytes to skip before the section
         * starts, which is how a table spanning several TS packets is stitched
         * back together. Treating it as "look for a 0xFF" happens to work for a
         * single-packet table and silently misreads a continuation, so it is
         * read properly.
         */
        fun read(): Boolean {
            if (position >= data.size) return false
            val pointer = data[position].toInt() and 0xFF
            val start = position + 1 + pointer
            if (start + 3 > data.size) return false
            val length = ((data[start + 1].toInt() and 0x0F) shl 8) or (data[start + 2].toInt() and 0xFF)
            // section_length counts the bytes after itself, the last 4 of which
            // are the CRC, so the body available to parse is length - 4.
            if (length <= 4 || start + 3 + length > data.size) return false
            bodyStart = start + 3
            position = bodyStart
            sectionLength = length - 4
            return true
        }

        fun u8(): Int = (data[position++].toInt() and 0xFF)

        fun u16(): Int {
            val high = u8()
            val low = u8()
            return (high shl 8) or low
        }

        fun skip(count: Int) {
            position += count
        }
    }

    // pid is the map key in `streams`, so carrying it on the value as well was
    // never read.
    private class EsInfo(var streamType: Int)

    private class TsDemuxer(
        private val onPayload: (streamType: Int, payload: ByteArray, pts: Long?, dts: Long?) -> Unit,
    ) {
        private var pmtPid = -1
        private val streams = HashMap<Int, EsInfo>()
        private val buffers = HashMap<Int, ByteArrayBuilder>()
        private val started = HashSet<Int>()
        private val lastContinuity = HashMap<Int, Int>()
        private var sawDiscontinuity = false

        private fun noteDiscontinuity() {
            if (!sawDiscontinuity) {
                sawDiscontinuity = true
                Diag.log("TS remux: discontinuity indicator or continuity gap in the source stream")
            }
        }

        private val packet = ByteArray(TS_PACKET_SIZE)

        fun run(input: InputStream) {
            while (true) {
                if (!readFully(input, packet)) break
                handle(packet)
            }
            for (pid in streams.keys.toList()) flushPes(pid)
        }

        private fun readFully(input: InputStream, into: ByteArray): Boolean {
            var total = 0
            while (total < into.size) {
                val n = input.read(into, total, into.size - total)
                if (n < 0) return total == TS_PACKET_SIZE
                total += n
            }
            return true
        }

        private fun handle(p: ByteArray) {
            if ((p[0].toInt() and 0xFF) != SYNC_BYTE) return
            val payloadStart = (p[1].toInt() and 0x40) != 0
            val pid = ((p[1].toInt() and 0x1F) shl 8) or (p[2].toInt() and 0xFF)
            val adaptationControl = (p[3].toInt() and 0x30) shr 4
            val continuityIndex = p[3].toInt() and 0x0F

            var offset = 4
            if (adaptationControl == 2) return
            if (adaptationControl == 3) {
                val adaptationLength = p[4].toInt() and 0xFF
                // A discontinuity indicator says the stream broke here, which is
                // the only reliable way to notice a missing segment.
                val flags = if (adaptationLength > 0) p[5].toInt() and 0xFF else 0
                if ((flags and 0x80) != 0) noteDiscontinuity()
                offset += 1 + adaptationLength
                if (offset >= TS_PACKET_SIZE) return
            }

            if (pid == 0) {
                if (payloadStart) handleSection(p, offset, isPmt = false)
                return
            }
            if (pmtPid >= 0 && pid == pmtPid) {
                if (payloadStart) handleSection(p, offset, isPmt = true)
                return
            }
            val info = streams[pid] ?: return
            if (offset >= TS_PACKET_SIZE) return

            val previous = lastContinuity[pid]
            if (previous != null && previous != continuityIndex && adaptationControl != 1) {
                noteDiscontinuity()
            }
            lastContinuity[pid] = if (continuityIndex == 0) 0 else (continuityIndex + 1) and 0x0F

            if (payloadStart) {
                flushPes(pid)
                started += pid
            }
            if (pid !in started) return
            buffers.getOrPut(pid) { ByteArrayBuilder() }.u8Array(p, offset, TS_PACKET_SIZE - offset)
        }

        private fun handleSection(p: ByteArray, offset: Int, isPmt: Boolean) {
            val r = SectionReader(p, offset)
            if (!r.read()) return
            val end = r.sectionEnd
            if (!isPmt) {
                // transport_stream_id (2) then the reserved/version/section_number/
                // last_section_number group, which is a whole 3 bytes.
                r.skip(2)
                r.skip(3)
                while (r.position + 4 <= end) {
                    val program = r.u16()
                    val pid = r.u16() and 0x1FFF
                    if (program != 0) {
                        pmtPid = pid
                        return
                    }
                }
                return
            }
            r.u16() // program_number
            r.skip(3) // reserved/version/section_number/last_section_number
            r.u8(); r.u8() // reserved(3) + PCR_PID(13)
            r.skip((r.u16() and 0x0FFF).toInt()) // reserved(4) + program_info_length
            while (r.position + 5 <= end) {
                val streamType = r.u8()
                val elementaryPid = r.u16() and 0x1FFF
                val esInfoLength = (r.u16() and 0x0FFF).toInt()
                if (isSupported(streamType) && !streams.containsKey(elementaryPid)) {
                    streams[elementaryPid] = EsInfo(streamType)
                }
                r.skip(esInfoLength)
            }
        }

        private fun isSupported(streamType: Int): Boolean = when (streamType) {
            ST_H264, ST_H265, ST_AAC_ADTS, ST_AAC_LATM,
            ST_MPEG1_AUDIO, ST_MPEG2_AUDIO, ST_AC3, ST_EC3,
            -> true
            else -> false
        }

        private fun flushPes(pid: Int) {
            val builder = buffers[pid] ?: return
            val data = builder.toByteArrayAndReset()
            started -= pid
            if (data.size < 9) return
            if (data[0] != 0.toByte() || data[1] != 0.toByte() || data[2] != 1.toByte()) return
            val info = streams[pid] ?: return

            // Counting from the start code prefix: the length field ends at 5,
            // the flags at 7, a PTS at 13 and a DTS at 18, so the payload begins
            // at 6 bare, 14 after a PTS and 19 after both. Truncating too early
            // prepends timestamp bytes to the elementary stream, which the NAL
            // scan happens to absorb but the AAC framing does not.
            val flags = data[7].toInt() and 0xC0
            val hasPts = flags == 0x80 || flags == 0xC0
            val pts = if (hasPts) readTimestamp(data, 9) else null
            val dts = if (flags == 0xC0) readTimestamp(data, 14) else null
            val headerBytes = when (flags) {
                0xC0 -> 19
                0x80 -> 14
                else -> 6
            }
            if (data.size <= headerBytes) return
            val elementary = data.copyOfRange(headerBytes, data.size)
            // A discontinuity means a keyframe we have not seen is missing.
            // Sample-splitting is driven by sync NALs alone, so the pending
            // sample that straddles the gap cannot be repaired here — dropping it
            // and forcing the next sync NAL to open a fresh sample is the correct
            // recovery, but it changes decode output and TsRemuxer has no unit
            // tests, so it is deliberately left undone rather than guessed at.
            // The flag is logged instead so a download damaged this way can be
            // identified after the fact.
            onPayload(info.streamType, elementary, pts, dts)
        }

        private fun readTimestamp(data: ByteArray, at: Int): Long? {
            if (at < 0 || at + 5 > data.size) return null
            if ((data[at].toInt() and 0x10) != 0x10) return null
            return ((data[at].toLong() and 0x0E) shl 29) or
                ((data[at + 1].toLong() and 0xFF) shl 22) or
                ((data[at + 2].toLong() and 0xFE) shl 14) or
                ((data[at + 3].toLong() and 0xFF) shl 7) or
                ((data[at + 4].toLong() and 0xFE) shr 1)
        }
    }

    // ------------------------------------------------------------------
    // Byte builder
    // ------------------------------------------------------------------

    private class ByteArrayBuilder(initialCapacity: Int = 8192) {
        private var buffer = ByteArray(if (initialCapacity > 0) initialCapacity else 16)
        private var length = 0

        fun size(): Int = length

        fun u8(value: Int) {
            ensure(1)
            buffer[length++] = value.toByte()
        }

        fun u16(value: Int) {
            ensure(2)
            buffer[length++] = ((value shr 8) and 0xFF).toByte()
            buffer[length++] = (value and 0xFF).toByte()
        }

        fun u32(value: Int) {
            ensure(4)
            buffer[length++] = ((value shr 24) and 0xFF).toByte()
            buffer[length++] = ((value shr 16) and 0xFF).toByte()
            buffer[length++] = ((value shr 8) and 0xFF).toByte()
            buffer[length++] = (value and 0xFF).toByte()
        }

        fun u64(value: Long) {
            u32(((value ushr 32) and 0xFFFFFFFFL).toInt())
            u32((value and 0xFFFFFFFFL).toInt())
        }

        fun append(text: String) {
            for (c in text) u8(c.code)
        }

        fun u8Array(bytes: ByteArray) {
            if (bytes.isEmpty()) return
            ensure(bytes.size)
            System.arraycopy(bytes, 0, buffer, length, bytes.size)
            length += bytes.size
        }

        fun u8Array(bytes: ByteArray, offset: Int, count: Int) {
            if (count <= 0) return
            ensure(count)
            System.arraycopy(bytes, offset, buffer, length, count)
            length += count
        }

        fun reset() {
            length = 0
        }

        fun toByteArray(): ByteArray = buffer.copyOf(length)

        /** Hands over the accumulated bytes and starts a fresh buffer. */
        fun toByteArrayAndReset(): ByteArray {
            val out = buffer.copyOf(length)
            length = 0
            return out
        }

        private fun ensure(extra: Int) {
            if (length + extra <= buffer.size) return
            var next = buffer.size * 2
            while (next < length + extra) next *= 2
            buffer = buffer.copyOf(next)
        }
    }
}
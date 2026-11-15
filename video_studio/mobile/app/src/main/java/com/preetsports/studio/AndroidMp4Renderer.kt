package com.preetsports.studio

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Small-memory, on-device MP4 renderer. It encodes still-scene motion at 2 fps
 * (a slow pan/zoom is applied to each card) and AAC narration, then muxes both.
 * Encoded packets are spooled to disk so a 5-7 minute project is not retained in RAM.
 */
class AndroidMp4Renderer(private val context: Context) {
    data class Result(val file: File, val durationMs: Long, val width: Int, val height: Int)

    private data class Packet(val pts: Long, val flags: Int, val offset: Long, val size: Int)
    private data class TrackSpool(val format: MediaFormat, val data: File, val packets: List<Packet>)

    fun render(
        project: File,
        wavFile: File,
        scenes: List<StudioScene>,
        portrait: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Result {
        require(wavFile.isFile && wavFile.length() > 44) { "Narration WAV is missing or empty." }
        require(scenes.isNotEmpty()) { "No scene cards are available." }
        val wav = readPcmWav(wavFile)
        require(wav.channels == 1 || wav.channels == 2) { "Only mono or stereo PCM WAV is supported." }
        val width = if (portrait) 720 else 1280
        val height = if (portrait) 1280 else 720
        val durationUs = wav.pcm.size.toLong() * 1_000_000L / (wav.sampleRate * wav.channels * 2L)
        val video = encodeVideo(project, scenes, width, height, durationUs) { onProgress(it / 2) }
        val audio = encodeAudio(project, wav) { onProgress(50 + it / 2) }
        val output = File(project, if (portrait) "preet-sports-9x16.mp4" else "preet-sports-16x9.mp4")
        mux(output, video, audio)
        onProgress(100)
        return Result(output, durationUs / 1000L, width, height)
    }

    private data class Pcm(val sampleRate: Int, val channels: Int, val pcm: ByteArray)

    private fun readPcmWav(file: File): Pcm {
        val bytes = file.readBytes()
        require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
            "Voice file is not a valid PCM WAV."
        }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var rate = 0
        var channels = 0
        var bits = 0
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = b.getInt(offset + 4).coerceAtLeast(0)
            val start = offset + 8
            if (start + size > bytes.size) break
            when (id) {
                "fmt " -> if (size >= 16) {
                    val encoding = b.getShort(start).toInt() and 0xffff
                    channels = b.getShort(start + 2).toInt() and 0xffff
                    rate = b.getInt(start + 4)
                    bits = b.getShort(start + 14).toInt() and 0xffff
                    require(encoding == 1 && bits == 16) { "WAV must contain 16-bit PCM audio." }
                }
                "data" -> { dataOffset = start; dataSize = size; break }
            }
            offset = start + size + (size and 1)
        }
        require(rate > 0 && channels > 0 && dataOffset >= 0) { "WAV format/data chunk is incomplete." }
        return Pcm(rate, channels, bytes.copyOfRange(dataOffset, dataOffset + dataSize))
    }

    private fun encodeVideo(project: File, scenes: List<StudioScene>, width: Int, height: Int, durationUs: Long, progress: (Int) -> Unit): TrackSpool {
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val encoder = MediaCodec.createEncoderByType(mime)
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_600_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 2)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val spool = File(project, "video.samples")
        val packets = mutableListOf<Packet>()
        var outputFormat: MediaFormat? = null
        var eos = false
        var frame = 0
        val frameDuration = 500_000L
        val totalFrames = max(1, ((durationUs + frameDuration - 1) / frameDuration).toInt())
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            spool.outputStream().buffered().use { out ->
                var spoolOffset = 0L
                while (!eos) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = encoder.getInputBuffer(inputIndex)!!
                        input.clear()
                        if (frame < totalFrames) {
                            val sceneIndex = min(scenes.lastIndex, frame * scenes.size / totalFrames)
                            val bitmap = CricketSceneRenderer().draw(scenes[sceneIndex], width, height, frame)
                            val yuv = bitmapToNv12(bitmap)
                            bitmap.recycle()
                            input.put(yuv, 0, min(yuv.size, input.remaining()))
                            encoder.queueInputBuffer(inputIndex, 0, yuv.size, frame.toLong() * frameDuration, 0)
                            frame++
                            progress(frame * 100 / totalFrames)
                        } else {
                            encoder.queueInputBuffer(inputIndex, 0, 0, durationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                    val info = MediaCodec.BufferInfo()
                    var index = encoder.dequeueOutputBuffer(info, 10_000)
                    while (index >= 0) {
                        val buffer = encoder.getOutputBuffer(index)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.get(bytes)
                            val pos = spoolOffset
                            out.write(bytes)
                            spoolOffset += bytes.size
                            packets += Packet(info.presentationTimeUs, info.flags, pos, bytes.size)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                        encoder.releaseOutputBuffer(index, false)
                        index = encoder.dequeueOutputBuffer(info, 0)
                    }
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outputFormat = encoder.outputFormat
                }
            }
        } finally { try { encoder.stop() } catch (_: Exception) {}; encoder.release() }
        return TrackSpool(outputFormat ?: error("Video encoder did not publish an output format."), spool, packets)
    }

    private fun bitmapToNv12(bitmap: Bitmap): ByteArray {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h * 3 / 2)
        var yIndex = 0; var uvIndex = w * h
        for (row in 0 until h) for (col in 0 until w) {
            val c = pixels[row * w + col]
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            out[yIndex++] = ((66*r + 129*g + 25*b + 128 shr 8) + 16).coerceIn(0,255).toByte()
            if (row % 2 == 0 && col % 2 == 0) {
                val u = ((-38*r - 74*g + 112*b + 128 shr 8) + 128).coerceIn(0,255)
                val v = ((112*r - 94*g - 18*b + 128 shr 8) + 128).coerceIn(0,255)
                out[uvIndex++] = u.toByte(); out[uvIndex++] = v.toByte()
            }
        }
        return out
    }

    private fun encodeAudio(project: File, pcm: Pcm, progress: (Int) -> Unit): TrackSpool {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val encoder = MediaCodec.createEncoderByType(mime)
        val format = MediaFormat.createAudioFormat(mime, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val spool = File(project, "audio.samples")
        val packets = mutableListOf<Packet>()
        var outputFormat: MediaFormat? = null
        var inputOffset = 0
        var eos = false
        val chunk = 8192
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            spool.outputStream().buffered().use { out ->
                var spoolOffset = 0L
                while (!eos) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = encoder.getInputBuffer(inputIndex)!!
                        input.clear()
                        if (inputOffset < pcm.pcm.size) {
                            val count = min(min(input.remaining(), chunk), pcm.pcm.size - inputOffset)
                            input.put(pcm.pcm, inputOffset, count)
                            val pts = inputOffset.toLong() * 1_000_000L / (pcm.sampleRate * pcm.channels * 2L)
                            encoder.queueInputBuffer(inputIndex, 0, count, pts, 0)
                            inputOffset += count
                            progress(inputOffset * 100 / pcm.pcm.size)
                        } else {
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                    val info = MediaCodec.BufferInfo()
                    var index = encoder.dequeueOutputBuffer(info, 10_000)
                    while (index >= 0) {
                        val buffer = encoder.getOutputBuffer(index)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.get(bytes)
                            val pos = spoolOffset
                            out.write(bytes); spoolOffset += bytes.size
                            packets += Packet(info.presentationTimeUs, info.flags, pos, bytes.size)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                        encoder.releaseOutputBuffer(index, false)
                        index = encoder.dequeueOutputBuffer(info, 0)
                    }
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outputFormat = encoder.outputFormat
                }
            }
        } finally { try { encoder.stop() } catch (_: Exception) {}; encoder.release() }
        return TrackSpool(outputFormat ?: error("AAC encoder did not publish an output format."), spool, packets)
    }

    private fun mux(output: File, video: TrackSpool, audio: TrackSpool) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val videoTrack = muxer.addTrack(video.format)
            val audioTrack = muxer.addTrack(audio.format)
            muxer.start()
            val all = (video.packets.map { Triple(it, video.data, videoTrack) } +
                audio.packets.map { Triple(it, audio.data, audioTrack) }).sortedBy { it.first.pts }
            all.forEach { (packet, file, track) ->
                val bytes = ByteArray(packet.size)
                java.io.RandomAccessFile(file, "r").use { raf -> raf.seek(packet.offset); raf.readFully(bytes) }
                val info = MediaCodec.BufferInfo().apply {
                    set(0, bytes.size, packet.pts, packet.flags and (MediaCodec.BUFFER_FLAG_KEY_FRAME or MediaCodec.BUFFER_FLAG_CODEC_CONFIG))
                }
                muxer.writeSampleData(track, ByteBuffer.wrap(bytes), info)
            }
        } finally { try { muxer.stop() } catch (_: Exception) {}; muxer.release() }
    }

    fun publishToMovies(file: File): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Preet Sports")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Android could not create the Movies export entry.")
        try {
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Android could not open the export destination.")
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}

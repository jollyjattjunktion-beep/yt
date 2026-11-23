package com.preetsports.studio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device Roman Hinglish narrator for Preet Sports pre-match analysis videos. */
class AndroidHinglishTts(private val context: Context) {
    suspend fun synthesize(script: String, outputWav: File, onProgress: (String) -> Unit = {}): File {
        require(script.isNotBlank()) { "The pre-match script is empty." }
        outputWav.parentFile?.mkdirs()
        val workDir = File(outputWav.parentFile, "android-tts-work").apply { deleteRecursively(); mkdirs() }
        val chunks = splitForSpeech(script, 850)
        require(chunks.isNotEmpty()) { "No speakable script text was found." }
        val engine = withContext(Dispatchers.Main) { createEngine() }
        try {
            val language = withContext(Dispatchers.Main) { engine.setLanguage(Locale("en", "IN")) }
            require(language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED) {
                "No Indian-English TTS voice is installed. Install one in Android Text-to-Speech settings."
            }
            withContext(Dispatchers.Main) {
                engine.setSpeechRate(0.93f)
                engine.setPitch(0.98f)
                chooseIndianEnglishVoice(engine)?.let { engine.voice = it }
            }
            val files = mutableListOf<File>()
            chunks.forEachIndexed { index, chunk ->
                onProgress("Android voiceover: speaking segment ${index + 1}/${chunks.size}…")
                val segment = File(workDir, "segment-%03d.wav".format(index + 1))
                withContext(Dispatchers.Main) { synthesizeSegment(engine, chunk, segment) }
                require(segment.isFile && segment.length() > 44L) {
                    "Android TTS produced no audio for segment ${index + 1}. Check the installed voice engine."
                }
                files += segment
            }
            onProgress("Combining ${files.size} voice segments into the final WAV…")
            // Join the PCM WAV payloads in Kotlin. Do not launch FFmpeg/native code
            // between TTS and video rendering: some Android devices terminate here
            // after the final synthesized segment because of native codec pressure.
            withContext(Dispatchers.IO) { concatenatePcmWav(files, outputWav) }
            require(outputWav.isFile && outputWav.length() > 44L) {
                "Android TTS output WAV is missing or empty."
            }
            onProgress("On-device Hinglish voiceover ready.")
            return outputWav
        } finally {
            withContext(Dispatchers.Main) { engine.shutdown() }
        }
    }

    private data class WavPart(
        val sampleRate: Int, val channels: Int, val bitsPerSample: Int,
        val dataOffset: Long, val dataLength: Long
    )

    private fun concatenatePcmWav(files: List<File>, output: File) {
        require(files.isNotEmpty()) { "No synthesized voice segments are available." }
        val parts = files.map { readWavPart(it) }
        val first = parts.first()
        require(parts.all {
            it.sampleRate == first.sampleRate &&
                it.channels == first.channels &&
                it.bitsPerSample == first.bitsPerSample
        }) { "Android TTS changed audio format between segments; regenerate the voiceover." }
        require(first.bitsPerSample == 16) { "Android TTS must produce 16-bit PCM audio." }
        val totalData = parts.sumOf { it.dataLength }
        require(totalData <= 0xFFFFFFFFL - 36L) { "Voiceover is too large for a standard WAV file." }
        output.parentFile?.mkdirs()
        output.outputStream().buffered().use { raw ->
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36L + totalData).toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(first.channels.toShort())
            header.putInt(first.sampleRate)
            val byteRate = first.sampleRate * first.channels * first.bitsPerSample / 8
            header.putInt(byteRate)
            header.putShort((first.channels * first.bitsPerSample / 8).toShort())
            header.putShort(first.bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(totalData.toInt())
            raw.write(header.array())
            val buffer = ByteArray(64 * 1024)
            files.zip(parts).forEach { (file, part) ->
                java.io.RandomAccessFile(file, "r").use { input ->
                    input.seek(part.dataOffset)
                    var remaining = part.dataLength
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        require(count > 0) { "A synthesized TTS segment ended unexpectedly." }
                        raw.write(buffer, 0, count)
                        remaining -= count
                    }
                }
            }
        }
    }

    private fun readWavPart(file: File): WavPart {
        java.io.RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 44) { "TTS segment is not a valid WAV file: ${file.name}" }
            fun readAscii(count: Int): String {
                val bytes = ByteArray(count); input.readFully(bytes); return String(bytes, Charsets.US_ASCII)
            }
            require(readAscii(4) == "RIFF") { "TTS segment has no RIFF header: ${file.name}" }
            input.skipBytes(4)
            require(readAscii(4) == "WAVE") { "TTS segment has no WAVE header: ${file.name}" }
            var sampleRate = 0
            var channels = 0
            var bits = 0
            var dataOffset = -1L
            var dataLength = 0L
            while (input.filePointer + 8 <= input.length()) {
                val id = readAscii(4)
                val size = java.nio.ByteBuffer.wrap(ByteArray(4).also(input::readFully))
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val start = input.filePointer
                when (id) {
                    "fmt " -> {
                        require(size >= 16 && size <= 4096) { "Unsupported WAV format chunk." }
                        val fmt = ByteArray(size.toInt()); input.readFully(fmt)
                        val b = java.nio.ByteBuffer.wrap(fmt).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        require((b.short.toInt() and 0xFFFF) == 1) { "TTS segment is not uncompressed PCM." }
                        channels = b.short.toInt() and 0xFFFF
                        sampleRate = b.int
                        b.int
                        b.short
                        bits = b.short.toInt() and 0xFFFF
                    }
                    "data" -> {
                        dataOffset = start
                        dataLength = minOf(size, input.length() - start)
                        break
                    }
                    else -> input.seek(start + size + (size and 1L))
                }
            }
            require(sampleRate > 0 && channels in 1..2 && bits == 16 && dataOffset >= 0 && dataLength > 0) {
                "TTS segment WAV format/data is incomplete: ${file.name}"
            }
            return WavPart(sampleRate, channels, bits, dataOffset, dataLength)
        }
    }

    private suspend fun createEngine(): TextToSpeech = suspendCancellableCoroutine { continuation ->
        var created: TextToSpeech? = null
        created = TextToSpeech(context.applicationContext) { status ->
            if (!continuation.isActive) created?.shutdown()
            else if (status == TextToSpeech.SUCCESS) continuation.resume(created!!)
            else {
                created?.shutdown()
                continuation.resumeWithException(IllegalStateException("Android Text-to-Speech engine failed to initialise."))
            }
        }
        continuation.invokeOnCancellation { created?.shutdown() }
    }

    private suspend fun synthesizeSegment(engine: TextToSpeech, text: String, file: File) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val id = "preet-${UUID.randomUUID()}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    if (utteranceId == id && continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Android TTS failed while speaking a script segment.")
                    )
                }
                override fun onError(utteranceId: String, errorCode: Int) {
                    if (utteranceId == id && continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Android TTS error $errorCode while speaking a script segment.")
                    )
                }
            })
            val result = engine.synthesizeToFile(text, Bundle(), file, id)
            if (result != TextToSpeech.SUCCESS && continuation.isActive) continuation.resumeWithException(
                IllegalStateException("Android TTS rejected a script segment (code $result).")
            )
            continuation.invokeOnCancellation { engine.stop() }
        }
    }

    private fun chooseIndianEnglishVoice(engine: TextToSpeech): Voice? =
        engine.voices?.filter { it.locale.language == "en" && it.locale.country.equals("IN", true) }
            ?.sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality })
            ?.firstOrNull()

    private fun splitForSpeech(source: String, maxChars: Int): List<String> {
        val sentences = source.replace("\r\n", "\n").replace("\r", "\n")
            .split(Regex("(?<=[.!?।])\\s+|\\n+")).map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotEmpty()) { result += current.toString().trim(); current.setLength(0) } }
        for (sentence in sentences) {
            var remaining = sentence
            while (remaining.length > maxChars) {
                val cut = remaining.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
                val piece = remaining.substring(0, cut).trim()
                if (piece.isNotBlank()) { flush(); result += piece }
                remaining = remaining.substring(cut).trim()
            }
            if (remaining.isNotBlank()) {
                if (current.isNotEmpty() && current.length + remaining.length + 1 > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(remaining)
            }
        }
        flush()
        return result
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

package com.preetsports.studio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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
            val manifest = File(workDir, "segments.txt")
            manifest.writeText(files.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" } + "\n")
            val command = listOf(
                "-y", "-hide_banner", "-loglevel", "error", "-f", "concat", "-safe", "0",
                "-i", quote(manifest.absolutePath), "-vn", "-ac", "1", "-ar", "24000",
                "-c:a", "pcm_s16le", quote(outputWav.absolutePath)
            ).joinToString(" ")
            val session = withContext(Dispatchers.IO) { FFmpegKit.execute(command) }
            require(ReturnCode.isSuccess(session.returnCode) && outputWav.isFile && outputWav.length() > 44L) {
                "Could not assemble Android TTS audio: ${session.allLogsAsString.takeLast(700)}"
            }
            onProgress("On-device Hinglish voiceover ready.")
            return outputWav
        } finally {
            withContext(Dispatchers.Main) { engine.shutdown() }
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

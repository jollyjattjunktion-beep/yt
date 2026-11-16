package com.preetsports.studio

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun post(model: String, payload: JSONObject): JSONObject {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val retryable = setOf(429, 500, 502, 503, 504)
        var lastError = "Gemini request failed."
        for (attempt in 0..3) {
            val request = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) return JSONObject(body)
                    lastError = when (response.code) {
                        503 -> "Gemini is temporarily busy (503)."
                        429 -> "Gemini rate limit reached (429). Check your usage limits."
                        else -> "Gemini request failed (${response.code}): ${body.take(500)}"
                    }
                    if (response.code !in retryable || attempt == 3) {
                        error(if (response.code == 503) "$lastError Please wait a minute and try again." else lastError)
                    }
                }
            } catch (e: java.io.IOException) {
                lastError = "Network error contacting Gemini: ${e.message ?: "connection failed"}"
                if (attempt == 3) error(lastError)
            }
            // Retry temporary overload and network failures with bounded backoff.
            Thread.sleep(1000L shl attempt)
        }
        error(lastError)
    }

    suspend fun createScript(match: String, format: String, minutes: Int, researchContext: String = ""): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings first." }
        val prompt = """
            You are the Roman Hinglish cricket presenter for Preet Sports.
            Write a complete spoken cricket match preview for $match ($format), approximately $minutes minutes,
            at 130-140 spoken words per minute. Use Roman Hinglish only, natural Indian conversational rhythm,
            strong opening hook, smooth transitions, tactical analysis, key player roles, pitch and head-to-head
            only when facts are supplied, and a balanced prediction section. Do not invent current squads,
            injuries, statistics, pitch conditions, venue facts or head-to-head records. If facts are unknown,
            clearly say they are not verified and focus on conditional tactical analysis.
            Use the following imported match research as source material. Do not treat navigation labels or page metadata as verified facts.
            $researchContext
            Output only the script.
        """.trimIndent()
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        val response = post("gemini-3.8-flash", JSONObject().put("contents", contents))
        response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }

    suspend fun convertRomanHinglishForHindiVoice(script: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini key is required to prepare text for the experimental Hindi voice." }
        require(script.isNotBlank()) { "Script is empty; generate the script before voice conversion." }
        val prompt = HinglishSpeechPreprocessor.buildConversionPrompt(script)
        val contents = JSONArray().put(
            JSONObject().put("role", "user").put(
                "parts", JSONArray().put(JSONObject().put("text", prompt))
            )
        )
        val response = post(
            "gemini-3.8-flash",
            JSONObject().put("contents", contents)
                .put("generationConfig", JSONObject().put("temperature", 0.1))
        )
        val converted = response.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        HinglishSpeechPreprocessor.validate(script, converted)
    }

    suspend fun createVoice(script: String, output: File, model: String = "gemini-3.8-flash-lite-tts", voice: String = "Charon", onProgress: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings first." }
            require(script.isNotBlank()) { "Script is empty; generate the script before voice." }
            val chunks = splitForSpeech(script, 850)
            output.parentFile?.mkdirs()
            val providerFile = File(output.parentFile, "voiceover-provider.pcm")
            val mimeFile = File(output.parentFile, "voiceover-mime.txt")
            var sampleRate = 24000
            var totalPcmBytes = 0L

            java.io.RandomAccessFile(output, "rw").use { wav ->
                wav.setLength(0)
                wav.write(ByteArray(44))
                chunks.forEachIndexed { index, chunk ->
                    val instruction = "Read this Roman Hinglish cricket presenter script verbatim. Use a warm, energetic, confident Indian sports presenter delivery with natural pauses. Do not translate or paraphrase. This is part \${index + 1} of \${chunks.size}; continue naturally.\n\n$chunk"
                    val parts = JSONArray().put(JSONObject().put("text", instruction))
                    val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
                    val voiceConfig = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
                    val config = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                        .put("speechConfig", JSONObject().put("voiceConfig", voiceConfig))
                    val response = post(model, JSONObject().put("contents", contents).put("generationConfig", config))
                    val responseParts = response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    val inline = (0 until responseParts.length()).asSequence()
                        .map { responseParts.getJSONObject(it) }
                        .firstOrNull { it.has("inlineData") }
                        ?.getJSONObject("inlineData")
                        ?: error("Gemini returned no audio for voice segment \${index + 1}.")
                    val mime = inline.optString("mimeType", "audio/L16;rate=24000")
                    val rate = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                    if (index == 0) {
                        sampleRate = rate
                        mimeFile.writeText(mime)
                        providerFile.writeBytes(ByteArray(0))
                    } else require(rate == sampleRate) { "Gemini changed audio sample rate between voice segments." }

                    val pcm = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                    if (mime.contains("L16", ignoreCase = true)) {
                        var i = 0
                        while (i + 1 < pcm.size) {
                            val first = pcm[i]
                            pcm[i] = pcm[i + 1]
                            pcm[i + 1] = first
                            i += 2
                        }
                    }
                    wav.seek(44L + totalPcmBytes)
                    wav.write(pcm)
                    totalPcmBytes += pcm.size
                    providerFile.appendBytes(pcm)
                }
                require(totalPcmBytes > 0) { "Gemini returned an empty voice recording." }
                require(totalPcmBytes <= 0xffffffffL - 36L) { "Voice recording is too large for standard WAV." }
                wav.seek(0)
                wav.write(wavHeader(totalPcmBytes.toInt(), sampleRate, 1, 16))
                wav.fd.sync()
            }
            output
        }

    private fun splitForSpeech(text: String, maxChars: Int): List<String> {
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\n{2,}"))
            .map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                result += current.toString().trim()
                current.setLength(0)
            }
        }
        paragraphs.forEach { paragraph ->
            if (paragraph.length <= maxChars) {
                if (current.isNotEmpty() && current.length + paragraph.length + 2 > maxChars) flush()
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            } else {
                flush()
                paragraph.split(Regex("(?<=[.!?।])\\s+")).forEach { sentence ->
                    if (sentence.length > maxChars) {
                        var remaining = sentence
                        while (remaining.length > maxChars) {
                            val cut = remaining.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
                            result += remaining.substring(0, cut).trim()
                            remaining = remaining.substring(cut).trim()
                        }
                        if (remaining.isNotBlank()) result += remaining
                    } else {
                        if (current.isNotEmpty() && current.length + sentence.length + 1 > maxChars) flush()
                        if (current.isNotEmpty()) current.append(' ')
                        current.append(sentence)
                    }
                }
                flush()
            }
        }
        flush()
        return result
    }

    private fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int, bits: Int): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(dataSize + 36); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(channels.toShort())
        h.putInt(sampleRate); h.putInt(byteRate); h.putShort(blockAlign.toShort()); h.putShort(bits.toShort())
        h.put("data".toByteArray()); h.putInt(dataSize)
        return h.array()
    }
}
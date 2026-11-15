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

    suspend fun createScript(match: String, format: String, minutes: Int): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings first." }
        val prompt = """
            You are the Roman Hinglish cricket presenter for Preet Sports.
            Write a complete spoken cricket match preview for $match ($format), approximately $minutes minutes,
            at 130-140 spoken words per minute. Use Roman Hinglish only, natural Indian conversational rhythm,
            strong opening hook, smooth transitions, tactical analysis, key player roles, pitch and head-to-head
            only when facts are supplied, and a balanced prediction section. Do not invent current squads,
            injuries, statistics, pitch conditions, venue facts or head-to-head records. If facts are unknown,
            clearly say they are not verified and focus on conditional tactical analysis. Output only the script.
        """.trimIndent()
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        val response = post("gemini-3.8-flash", JSONObject().put("contents", contents))
        response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }

    suspend fun createVoice(script: String, output: File, model: String = "gemini-3.8-flash-lite-tts", voice: String = "Charon"): File =
        withContext(Dispatchers.IO) {
            val instruction = "Read this Roman Hinglish cricket presenter script verbatim. Use a warm, energetic, confident Indian sports presenter delivery with natural pauses. Do not translate or paraphrase.\n\n$script"
            val parts = JSONArray().put(JSONObject().put("text", instruction))
            val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
            val voiceConfig = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
            val config = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voiceConfig))
            val response = post(model, JSONObject().put("contents", contents).put("generationConfig", config))
            val inline = response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData")
            val providerBytes = Base64.decode(inline.getString("data"), Base64.DEFAULT)
            val mime = inline.optString("mimeType", "audio/L16;rate=24000")
            output.parentFile?.mkdirs()
            File(output.parentFile, "voiceover-provider.pcm").writeBytes(providerBytes)
            File(output.parentFile, "voiceover-mime.txt").writeText(mime)
            // L16 is signed 16-bit big-endian PCM; RIFF/WAVE PCM is little-endian.
            val bytes = if (mime.contains("L16", ignoreCase = true)) {
                ByteArray(providerBytes.size).also { dst ->
                    var i = 0
                    while (i + 1 < providerBytes.size) {
                        dst[i] = providerBytes[i + 1]; dst[i + 1] = providerBytes[i]; i += 2
                    }
                    if (i < providerBytes.size) dst[i] = providerBytes[i]
                }
            } else providerBytes
            val rate = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
            output.writeBytes(wavHeader(bytes.size, rate, 1, 16) + bytes)
            output
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

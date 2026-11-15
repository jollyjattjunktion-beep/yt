package com.preetsports.studio

import android.util.Base64
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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

/**
 * APInex adapter:
 * - Script generation uses the documented OpenAI-compatible Chat Completions API.
 * - Presenter audio uses APInex Voice TTS and is converted locally to 16-bit PCM WAV.
 */
class ApinexClient(
    private val scriptApiKey: String,
    private val voiceApiKey: String,
    private val scriptModel: String = "free/gemini-3.8-flash",
    private val ttsModel: String = "aura-2-thalia-en",
    private val ttsVoice: String = ""
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS)
        .build()

    private fun request(url: String, key: String, body: JSONObject): Pair<Int, Pair<String, String?>> {
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            return Pair(response.code, Pair(response.body?.string().orEmpty(), response.header("Retry-After")))
        }
    }

    private fun postJson(url: String, key: String, body: JSONObject): JSONObject {
        require(key.isNotBlank()) { "Enter your APInex API key in the relevant settings." }
        var last = "APInex request failed."
        for (attempt in 0..3) {
            val (status, result) = try {
                request(url, key, body)
            } catch (e: java.io.IOException) {
                last = "Network error contacting APInex: ${e.message ?: "connection failed"}"
                if (attempt == 3) error(last)
                Thread.sleep(1000L shl attempt)
                continue
            }
            val (raw, retryAfter) = result
            if (status in 200..299) return JSONObject(raw)
            last = when (status) {
                401, 403 -> "APInex rejected the API key ($status). Check the key in APInex dashboard."
                429 -> "APInex rate limit reached (429). Wait and retry; no video rendering was started."
                else -> "APInex request failed ($status): ${raw.take(450)}"
            }
            if (status !in setOf(429, 500, 502, 503, 504) || attempt == 3) error(last)
            val delay = retryAfter?.toLongOrNull()?.coerceIn(1, 30)?.times(1000)
                ?: (1000L shl attempt)
            Thread.sleep(delay)
        }
        error(last)
    }

    suspend fun createScript(match: String, format: String, minutes: Int, researchContext: String = ""): String =
        withContext(Dispatchers.IO) {
            require(scriptApiKey.isNotBlank()) { "Add your APInex Script API key in Settings first." }
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
            val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            val response = postJson(
                "https://api.apinex.bond/v1/chat/completions",
                scriptApiKey,
                JSONObject().put("model", scriptModel).put("messages", messages)
                    .put("temperature", 0.65).put("max_tokens", 9000)
            )
            response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                .getString("content").trim().also { require(it.isNotBlank()) { "APInex returned an empty script." } }
        }

    private fun postAudio(url: String, key: String, payload: JSONObject): ByteArray {
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = raw.take(450)
                error(when (response.code) {
                    401, 403 -> "APInex rejected the voice API key (${response.code})."
                    429 -> "APInex voice rate limit reached (429). Wait and retry."
                    else -> "APInex voice request failed (${response.code}): $detail"
                })
            }
            val audio = JSONObject(raw).optString("audio_base64")
            require(audio.isNotBlank()) { "APInex TTS response did not contain audio_base64." }
            val bytes = Base64.decode(audio, Base64.DEFAULT)
            require(bytes.isNotEmpty()) { "APInex returned an empty decoded audio segment." }
            return bytes
        }
    }

    suspend fun createVoice(script: String, outputWav: File, onProgress: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            require(voiceApiKey.isNotBlank()) { "Add your APInex Presenter Voice API key in the relevant APInex setting." }
            require(ttsModel.isNotBlank()) { "Enter an APInex TTS model ID in Voice settings." }
            require(script.isNotBlank()) { "Script is empty; generate the script before voice." }
            val chunks = splitForTts(script, 700)
            outputWav.parentFile?.mkdirs()
            val combinedMp3 = File(outputWav.parentFile, "apinex-voice-combined.mp3")
            combinedMp3.outputStream().use { stream ->
                chunks.forEachIndexed { index, chunk ->
                    onProgress("APInex presenter voice: segment ${index + 1}/${chunks.size} · model $ttsModel…")
                    val payload = JSONObject()
                        .put("text", "Read this Roman Hinglish cricket presenter script verbatim. Warm, energetic sports presenter delivery. Do not translate or paraphrase. $chunk")
                        .put("model", ttsModel)
                        .put("encoding", "mp3")
                    val bytes = postAudio("https://api.apinex.bond/v1/tools/voice/tts", voiceApiKey, payload)
                    require(bytes.size > 100) { "APInex returned an unexpectedly small audio segment ${index + 1}." }
                    stream.write(bytes)
                    stream.flush()
                }
            }
            require(combinedMp3.length() > 1000) { "APInex voice output is unexpectedly small." }
            outputWav.delete()
            val command = listOf(
                "-y", "-hide_banner", "-loglevel", "error",
                "-i", quote(combinedMp3.absolutePath),
                "-vn", "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le",
                quote(outputWav.absolutePath)
            ).joinToString(" ")
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode) || !outputWav.isFile || outputWav.length() <= 44) {
                error("Could not convert APInex MP3 voice to WAV: ${session.allLogsAsString.takeLast(900)}")
            }
            outputWav
        }

    private fun splitForTts(text: String, limit: Int): List<String> {
        val sentences = text.replace("\r\n", "\n").split(Regex("(?<=[.!?।])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            var remaining = sentence.trim()
            while (remaining.length > limit) {
                val cut = remaining.lastIndexOf(' ', limit).takeIf { it > limit / 2 } ?: limit
                if (current.isNotEmpty()) { chunks += current.toString(); current.setLength(0) }
                chunks += remaining.substring(0, cut).trim()
                remaining = remaining.substring(cut).trim()
            }
            if (remaining.isNotBlank()) {
                if (current.isNotEmpty() && current.length + remaining.length + 1 > limit) {
                    chunks += current.toString()
                    current.setLength(0)
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(remaining)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun quote(path: String) = "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

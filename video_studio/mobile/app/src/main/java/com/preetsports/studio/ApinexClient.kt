package com.preetsports.studio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.Base64Variants
import java.io.RandomAccessFile
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

    private fun postAudioToFile(url: String, key: String, payload: JSONObject, target: File): Long {
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(450)
                error(when (response.code) {
                    401, 403 -> "APInex rejected the voice API key (${response.code})."
                    429 -> "APInex voice rate limit reached (429). Wait and retry."
                    else -> "APInex voice request failed (${response.code}): $detail"
                })
            }
            val body = response.body ?: error("APInex returned an empty voice response.")
            var decoded = 0L
            body.byteStream().use { input ->
                JsonFactory().createParser(input).use { parser ->
                    while (parser.nextToken() != null) {
                        if (parser.currentToken == JsonToken.FIELD_NAME && parser.currentName == "audio_base64") {
                            require(parser.nextToken() == JsonToken.VALUE_STRING) {
                                "APInex audio_base64 field was not a string."
                            }
                            target.outputStream().use { output ->
                                decoded = parser.readBinaryValue(Base64Variants.getDefaultVariant(), output).toLong()
                            }
                            break
                        }
                    }
                }
            }
            require(decoded > 100) { "APInex TTS response did not contain usable audio_base64 data." }
            return decoded
        }
    }

    suspend fun createVoice(script: String, outputWav: File, onProgress: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            require(voiceApiKey.isNotBlank()) { "Add your APInex Presenter Voice API key in the relevant APInex setting." }
            require(ttsModel.isNotBlank()) { "Enter an APInex TTS model ID in Voice settings." }
            require(script.isNotBlank()) { "Script is empty; generate the script before voice." }
            val chunks = splitForTts(script, 320)
            val parent = outputWav.parentFile ?: error("Voice output folder is unavailable.")
            parent.mkdirs()
            val segmentDir = File(parent, "apinex-voice-segments").apply { mkdirs() }
            val diagnostic = File(parent, "voice-generation-diagnostics.txt")
            val tempMp3 = File(parent, "voice-segment-current.mp3")
            val tempPcm = File(parent, "voice-segment-current.pcm")
            // Resume only if the saved manifest matches this exact script and selected model.
            val signature = (ttsModel + "\n" + script).hashCode().toString()
            val manifest = File(segmentDir, "manifest.txt")
            if (!manifest.isFile || manifest.readText() != signature) {
                segmentDir.listFiles()?.forEach { it.delete() }
                manifest.writeText(signature)
            }
            diagnostic.writeText("Starting voice generation. Model=$ttsModel; chunks=${chunks.size}; scriptChars=${script.length}\n")
            var totalPcmBytes = 0L
            chunks.forEachIndexed { index, chunk ->
                val mp3 = File(segmentDir, "segment-%03d.mp3".format(index + 1))
                val pcm = File(segmentDir, "segment-%03d.pcm".format(index + 1))
                diagnostic.appendText("Segment ${index + 1}/${chunks.size}: begin; existingPcm=${pcm.length()}\n")
                if (!pcm.isFile || pcm.length() <= 100) {
                    if (!mp3.isFile || mp3.length() <= 100) {
                        onProgress("APInex voice request ${index + 1}/${chunks.size}…")
                        val payload = JSONObject()
                            .put("text", "Read this Roman Hinglish cricket presenter script verbatim. Warm, energetic sports presenter delivery. Do not translate or paraphrase. $chunk")
                            .put("model", ttsModel)
                            .put("encoding", "mp3")
                        val downloadedBytes = postAudioToFile("https://api.apinex.bond/v1/tools/voice/tts", voiceApiKey, payload, mp3)
                    diagnostic.appendText("Segment ${index + 1}: downloaded $downloadedBytes bytes\n")
                    } else {
                        onProgress("Reusing downloaded voice segment ${index + 1}/${chunks.size}…")
                    }
                    onProgress("Converting voice segment ${index + 1}/${chunks.size}…")
                    tempPcm.delete()
                    val command = listOf(
                        "-y", "-hide_banner", "-loglevel", "error",
                        "-i", quote(mp3.absolutePath),
                        "-vn", "-ac", "1", "-ar", "24000", "-f", "s16le",
                        quote(tempPcm.absolutePath)
                    ).joinToString(" ")
                    val session = FFmpegKit.execute(command)
                    if (!ReturnCode.isSuccess(session.returnCode) || !tempPcm.isFile || tempPcm.length() <= 100) {
                        error("Voice segment ${index + 1} conversion failed: ${session.allLogsAsString.takeLast(500)}")
                    }
                    tempPcm.copyTo(pcm, overwrite = true)
                    diagnostic.appendText("Segment ${index + 1}: converted PCM ${pcm.length()} bytes\n")
                    tempMp3.delete()
                    tempPcm.delete()
                }
                totalPcmBytes += pcm.length()
                diagnostic.appendText("Segment ${index + 1}: complete; cumulativePcm=$totalPcmBytes\n")
                onProgress("Voice segment ${index + 1}/${chunks.size} complete.")
            }
            // Build the final WAV sequentially from small on-disk PCM segments.
            require(totalPcmBytes > 1000) { "APInex voice output is unexpectedly small." }
            RandomAccessFile(outputWav, "rw").use { wav ->
                wav.setLength(0)
                wav.write(byteArrayOf(
                    0x52,0x49,0x46,0x46, 0,0,0,0, 0x57,0x41,0x56,0x45,
                    0x66,0x6d,0x74,0x20, 16,0,0,0, 1,0, 1,0,
                    0xC0.toByte(),0x5D,0,0, 0x80.toByte(),0xBB.toByte(),0,0,
                    2,0, 16,0, 0x64,0x61,0x74,0x61, 0,0,0,0
                ))
                chunks.indices.forEach { index ->
                    val pcm = File(segmentDir, "segment-%03d.pcm".format(index + 1))
                    pcm.inputStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            wav.write(buffer, 0, count)
                        }
                    }
                }
                val dataSize = wav.length() - 44
                wav.seek(4); writeLeInt(wav, (wav.length() - 8).toInt())
                wav.seek(40); writeLeInt(wav, dataSize.toInt())
            }
            require(outputWav.isFile && outputWav.length() > 44) { "Final presenter WAV could not be created." }
            diagnostic.appendText("WAV complete: ${outputWav.length()} bytes\n")
            outputWav
        }

    private fun writeLeInt(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value ushr 8) and 0xff)
        file.write((value ushr 16) and 0xff)
        file.write((value ushr 24) and 0xff)
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

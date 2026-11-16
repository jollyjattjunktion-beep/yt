package com.preetsports.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

    suspend fun createScript(match: String, format: String, minutes: Int, researchContext: String = "", model: String = "gemini-3.8-flash"): String = withContext(Dispatchers.IO) {
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
        val response = post(model, JSONObject().put("contents", contents))
        response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }

}

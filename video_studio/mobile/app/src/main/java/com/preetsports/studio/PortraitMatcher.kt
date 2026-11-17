package com.preetsports.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PortraitMatch(val playerName: String, val altText: String, val sourceUrl: String, val file: File)

class PortraitMatcher {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val allowed = setOf("icc-cricket.com", "cricbuzz.com", "espncricinfo.com", "howstat.com")

    suspend fun download(project: File, playerNames: List<String>, candidates: Map<String, String>): List<PortraitMatch> =
        withContext(Dispatchers.IO) {
            val dir = File(project, "assets").apply { mkdirs() }
            val results = mutableListOf<PortraitMatch>()
            playerNames.take(11).forEachIndexed { index, name ->
                val wanted = normalize(name)
                if (wanted.isBlank()) return@forEachIndexed
                val candidate = candidates.entries.firstOrNull { (label, url) ->
                    val host = runCatching { java.net.URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
                    val trusted = allowed.any { host == it || host.endsWith(".$it") }
                    val alt = normalize(label.substringAfter(":").substringBefore("http"))
                    trusted && alt == wanted
                } ?: return@forEachIndexed
                val url = candidate.value
                val request = Request.Builder().url(url).header("User-Agent", "PreetSportsResearch/1.0").get().build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        require(response.isSuccessful) { "Portrait source returned HTTP ${response.code}" }
                        val type = response.header("Content-Type").orEmpty().lowercase()
                        require(type.startsWith("image/")) { "Portrait URL did not return an image." }
                        val body = response.body ?: error("Portrait response was empty.")
                        require(body.contentLength() in 1..5_000_000) { "Portrait is too large or empty." }
                        val file = File(dir, "player-%02d".format(index + 1))
                        body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                        results += PortraitMatch(name, candidate.key, url, file)
                    }
                }
            }
            results
        }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

package com.preetsports.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

data class MatchSourcePage(
    val source: String,
    val url: String,
    val title: String,
    val description: String,
    val extractedText: String,
    val relatedLinks: Map<String, String>,
    val imageLinks: Map<String, String>
)

data class MatchResearch(
    val pages: List<MatchSourcePage>,
    val matchTitle: String,
    val venue: String?,
    val dateTime: String?,
    val squadUrl: String?,
    val scorecardUrl: String?,
    val warnings: List<String>
) {
    fun toPromptContext(): String = buildString {
        appendLine("MATCH RESEARCH — cite each source when presenting a fact.")
        pages.forEach { page ->
            appendLine("SOURCE: ${page.source} — ${page.url}")
            appendLine("PAGE TITLE: ${page.title}")
            if (page.description.isNotBlank()) appendLine("DESCRIPTION: ${page.description}")
            appendLine("EXTRACTED PAGE TEXT: ${page.extractedText.take(9000)}")
            page.relatedLinks.forEach { (label, url) -> appendLine("RELATED $label: $url") }
            page.imageLinks.forEach { (label, url) -> appendLine("IMAGE $label: $url") }
        }
        appendLine("VENUE: ${venue ?: "Not verified from supplied pages"}")
        appendLine("DATE/TIME: ${dateTime ?: "Not verified from supplied pages"}")
        appendLine("SQUAD URL: ${squadUrl ?: "Not found"}")
        appendLine("SCORECARD URL: ${scorecardUrl ?: "Not found"}")
        appendLine("Do not infer missing pitch reports, head-to-head figures, squads or player identities.")
    }
}

class MatchUrlImporter {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun import(iccUrl: String, cricbuzzUrl: String): MatchResearch = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MatchSourcePage>()
        val warnings = mutableListOf<String>()
        if (iccUrl.isNotBlank()) {
            try { pages += fetch(iccUrl, "ICC") }
            catch (e: Exception) { warnings += "ICC source could not be read: ${e.message}" }
        }
        if (cricbuzzUrl.isNotBlank()) {
            try { pages += fetch(cricbuzzUrl, "Cricbuzz") }
            catch (e: Exception) { warnings += "Cricbuzz source could not be read: ${e.message}" }
        }
        require(pages.isNotEmpty()) { "Neither match URL could be imported. Check the links or try again later." }

        val allText = pages.joinToString("\n") { it.extractedText }
        val venue = Regex("Venue:\\s*([^•]+)", RegexOption.IGNORE_CASE)
            .find(allText)?.groupValues?.getOrNull(1)?.trim()
        val date = Regex("Date & Time:\\s*([^•]+)", RegexOption.IGNORE_CASE)
            .find(allText)?.groupValues?.getOrNull(1)?.trim()
        val cricbuzz = pages.firstOrNull { it.source == "Cricbuzz" }
        val squad = cricbuzz?.relatedLinks?.entries?.firstOrNull { it.key.contains("squad", true) }?.value
        val scorecard = cricbuzz?.relatedLinks?.entries?.firstOrNull { it.key.contains("scorecard", true) }?.value
        val info = cricbuzz?.relatedLinks?.entries?.firstOrNull { it.key.equals("Info", true) }?.value
        listOf("Cricbuzz Squads" to squad, "Cricbuzz Info" to info, "Cricbuzz Scorecard" to scorecard)
            .forEach { (label, url) ->
                if (url != null) try { pages += fetch(url, label) }
                catch (e: Exception) { warnings += "$label page could not be read: ${e.message}" }
            }
        val title = cricbuzz?.title?.takeIf { it.isNotBlank() }
            ?: pages.first().title
        if (venue == null) warnings += "Venue was not extracted; confirm it before using it in the video."
        if (squad == null) warnings += "A squad link was not found on the supplied match page."
        warnings += "Pitch report and head-to-head are not assumed to exist on the fixture page; they need separate verified sources."
        MatchResearch(pages, title, venue, date, squad, scorecard, warnings)
    }

    private fun fetch(rawUrl: String, source: String): MatchSourcePage {
        val uri = URI(rawUrl.trim())
        val host = uri.host?.lowercase().orEmpty()
        require(uri.scheme == "https" && (host == "icc-cricket.com" || host.endsWith(".icc-cricket.com") ||
            host == "cricbuzz.com" || host.endsWith(".cricbuzz.com"))) {
            "Only official ICC and Cricbuzz HTTPS URLs are accepted."
        }
        val request = Request.Builder().url(uri.toASCIIString())
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$source returned HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            require(html.isNotBlank()) { "$source returned an empty page." }
            val doc = Jsoup.parse(html, uri.toString())
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: doc.title()
            val description = doc.selectFirst("meta[name=description]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
            doc.select("script, style, nav, footer, header, noscript").remove()
            val text = doc.body()?.text().orEmpty().replace(Regex("\\s+"), " ").trim()
            val links = linkedMapOf<String, String>()
            doc.select("a[href]").forEach { a ->
                val label = a.text().trim()
                val href = a.absUrl("href")
                if (label.isNotBlank() && href.isNotBlank() &&
                    (label.contains("squad", true) || label.contains("scorecard", true) ||
                     label.equals("Info", true) || label.contains("pitch", true) ||
                     label.contains("head to head", true))) {
                    links[label.take(60)] = href
                }
            }
            val images = linkedMapOf<String, String>()
            doc.select("img").forEach { img ->
                val label = img.attr("alt").ifBlank { img.attr("title") }.trim()
                val imageUrl = img.absUrl("src").ifBlank { img.absUrl("data-src") }
                if (label.isNotBlank() && imageUrl.startsWith("https://") && images.size < 40) {
                    images[label.take(80)] = imageUrl
                }
            }
            MatchSourcePage(source, uri.toString(), title, description, text.take(18000), links, images)
        }
    }
}

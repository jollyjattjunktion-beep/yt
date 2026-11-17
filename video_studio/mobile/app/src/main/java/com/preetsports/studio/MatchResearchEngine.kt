package com.preetsports.studio

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class EvidenceFact(
    val field: String,
    val value: String,
    val source: String,
    val evidence: String,
    val confidence: String
)

data class MatchResearchReport(
    val matchTitle: String,
    val facts: List<EvidenceFact>,
    val sourceUrls: List<String>,
    val imageCandidates: Map<String, String>,
    val conflicts: List<String>,
    val missing: List<String>
) {
    fun toPromptContext(): String = buildString {
        appendLine("SOURCE-CHECKED MATCH RESEARCH REPORT")
        facts.forEach { f ->
            appendLine("- ${f.field}: ${f.value} [${f.confidence}]")
            appendLine("  Source: ${f.source}")
            appendLine("  Evidence: ${f.evidence}")
        }
        conflicts.forEach { appendLine("CONFLICT: $it") }
        missing.forEach { appendLine("NOT VERIFIED: $it") }
        appendLine("SOURCE URLS:")
        sourceUrls.forEach { appendLine("- $it") }
        appendLine("IMAGE CANDIDATES (alt text -> source URL; not yet identity-verified):")
        imageCandidates.forEach { (alt, url) -> appendLine("- $alt -> $url") }
        appendLine("Use only facts marked HIGH or MEDIUM as factual claims. State low-confidence or missing facts as unverified.")
    }

    fun save(file: File) {
        val root = JSONObject()
            .put("matchTitle", matchTitle)
            .put("facts", JSONArray().apply { facts.forEach { put(JSONObject()
                .put("field",it.field).put("value",it.value).put("source",it.source)
                .put("evidence",it.evidence).put("confidence",it.confidence)) } })
            .put("sources", JSONArray(sourceUrls))
            .put("imageCandidates", JSONObject(imageCandidates))
            .put("conflicts", JSONArray(conflicts))
            .put("missing", JSONArray(missing))
        file.writeText(root.toString(2))
    }
}

class MatchResearchEngine(private val importer: MatchUrlImporter = MatchUrlImporter()) {
    suspend fun research(iccUrl: String, cricbuzzUrl: String, pitchSourceUrl: String = "", h2hSourceUrl: String = ""): MatchResearchReport {
        val base = importer.import(iccUrl, cricbuzzUrl)
        val supplements = mutableListOf<MatchSourcePage>()
        if (pitchSourceUrl.isNotBlank()) supplements += importer.fetchTrustedSupplementary(pitchSourceUrl, "Pitch source")
        if (h2hSourceUrl.isNotBlank()) supplements += importer.fetchTrustedSupplementary(h2hSourceUrl, "H2H source")
        val raw = base.copy(pages = base.pages + supplements)
        val facts = mutableListOf<EvidenceFact>()
        val conflicts = mutableListOf<String>()
        val missing = mutableListOf<String>()

        fun collect(field: String, pattern: Regex) {
            val noise = Regex("(?i)\\b(featured videos?|videos?|rankings?|teams videos?|more matches|live:|need \\d+ off|watch now|highlights)\\b")
            val found = raw.pages.mapNotNull { page ->
                pattern.find(page.extractedText)?.let { match ->
                    val rawValue = match.groupValues.getOrNull(1).orEmpty()
                    val stop = Regex("(?i)\\b(FEATURED VIDEOS?|VIDEOS?|RANKINGS?|MORE MATCHES|LIVE:)\\b").find(rawValue)
                    val value = (if (stop != null) rawValue.substring(0, stop.range.first) else rawValue)
                        .trim(' ', '|', '•', '-', ':')
                        .take(120)
                    if (value.length < 3 || noise.containsMatchIn(value)) null
                    else Triple(page, value, match.value.take(180))
                }
            }
            if (found.isEmpty()) { missing += field; return }
            val distinct = found.map { it.second.lowercase() }.distinct()
            if (distinct.size > 1) conflicts += "$field differs between supplied pages: " + found.joinToString(" / ") { "\${it.first.source}: \${it.second}" }
            found.forEach { (page, value, evidence) ->
                val same = distinct.size == 1 && found.size >= 2
                facts += EvidenceFact(field, value, page.url, evidence, if (same) "HIGH" else "MEDIUM")
            }
        }
        collect("Venue", Regex("(?i)\\bVenue\\s*:?\\s*([^•|]{3,120})"))
        collect("Match date/time", Regex("(?i)(?:Date\\s*&\\s*Time|Start\\s*Time|Match\\s*Date)\\s*:?\\s*([^•|]{3,140})"))

        val titleFacts = raw.pages.map { it to it.title }.filter { it.second.isNotBlank() }
        if (titleFacts.isNotEmpty()) {
            val titles = titleFacts.map { it.second }.distinct()
            if (titles.size > 1 && titles.none { it.equals(titles.first(), true) }) {
                // Different page titles are expected for Info/Squads; only source match pages are compared.
                val primary = titleFacts.filter { it.first.source == "ICC" || it.first.source == "Cricbuzz" }
                if (primary.map { it.second.lowercase() }.distinct().size > 1) conflicts += "ICC and Cricbuzz page titles differ; confirm they refer to the same fixture."
            }
        }
        val imageMap = linkedMapOf<String, String>()
        raw.pages.forEach { page -> page.imageLinks.forEach { (alt, url) ->
            if (imageMap.size < 80) imageMap["${page.source}: $alt"] = url
        } }
        val pitchPages = raw.pages.filter { it.source == "Pitch source" || it.url.contains("pitch", true) }
        val pitchEvidence = pitchPages.flatMap { page ->
            Regex("(?i)(?:pitch|surface|toss|weather|dew|conditions)[^.!?]{0,220}[.!?]?")
                .findAll(page.extractedText).map { it.value.trim() }.toList().take(12)
                .map { EvidenceFact("Pitch and conditions", it, page.url, it, "MEDIUM") }
        }
        facts += pitchEvidence
        if (pitchEvidence.isEmpty()) missing += "Pitch report: no venue-specific pitch evidence was extracted; supply an official ground/board or trusted match preview URL."

        val h2hPages = raw.pages.filter { it.source == "H2H source" || it.url.contains("head-to-head", true) || it.url.contains("h2h", true) }
        val h2hEvidence = h2hPages.flatMap { page ->
            Regex("(?i)(?:head.to.head|overall|played|won|wins|drawn|tied|last meeting)[^.!?]{0,180}[.!?]?")
                .findAll(page.extractedText).map { it.value.trim() }.toList().take(12)
                .map { EvidenceFact("Head to head", it, page.url, it, "MEDIUM") }
        }
        facts += h2hEvidence
        if (h2hEvidence.isEmpty()) missing += "Head-to-head: no historical result evidence was extracted; supply a trusted series/results or stats URL."
        missing += "Player names and portrait identity matching (image URLs are candidates only until matched to official squad names)"
        return MatchResearchReport(raw.matchTitle, facts.distinctBy { "${it.field}|${it.value}|${it.source}" },
            raw.pages.map { it.url }.distinct(), imageMap, conflicts.distinct(), missing.distinct())
    }
}

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
                    val stop = Regex("(?i)\\b(FEATURED VIDEOS?|VIDEOS?|RANKINGS?|MORE MATCHES|LIVE:|MATCHES PLAYED|SA VS ALL TEAMS|BAN VS ALL TEAMS|DATE|START TIME|TOSS|UMPIRES|SERIES)\\b").find(rawValue)
                    val value = (if (stop != null) rawValue.substring(0, stop.range.first) else rawValue)
                        .trim(' ', '|', '•', '-', ':', ',')
                        .take(100)
                    val invalidVenue = field.equals("Venue", true) &&
                        (Regex("(?i)\\b(vs all teams|matches played|win|loss|avg score|highest score|lowest score)\\b").containsMatchIn(value) ||
                         value.length > 80 || value.count { it.isDigit() } > 8)
                    if (value.length < 3 || noise.containsMatchIn(value) || invalidVenue) null
                    else Triple(page, value, match.value.take(180))
                }
            }
            if (found.isEmpty()) { missing += field; return }
            val distinct = found.map { it.second.lowercase() }.distinct()
            if (distinct.size > 1) conflicts += "$field differs between supplied pages: " + found.joinToString(" / ") { "${it.first.source}: ${it.second}" }
            found.forEach { (page, value, evidence) ->
                val same = distinct.size == 1 && found.size >= 2
                facts += EvidenceFact(field, value, page.url, evidence, if (same) "HIGH" else "MEDIUM")
            }
        }
        collect("Venue", Regex("(?i)\\bVenue\\s*:?\\s*([^•|.!?]{3,100})"))
        collect("Match date/time", Regex("(?i)(?:Date\\s*&\\s*Time|Start\\s*Time|Match\\s*Date)\\s*:?\\s*([^•|]{3,140})"))

        // CREX places fixture facts in adjacent text blocks, not always labelled fields.
        val crexPage = raw.pages.firstOrNull { it.source == "CREX" }
        val crexText = crexPage?.extractedText.orEmpty()
        fun addCrexFact(field: String, value: String, evidence: String = value) {
            if (value.isNotBlank() && facts.none { it.field == field && it.value.equals(value, true) }) {
                facts += EvidenceFact(field, value, crexPage?.url ?: "", evidence.take(220), "MEDIUM")
            }
        }
        if (crexText.isNotBlank()) {
            val fixtureDate = Regex("(?i)\\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\\s+\\d{1,2}\\s+[A-Za-z]+,?\\s+\\d{1,2}:\\d{2}\\s*(?:AM|PM)\\b").find(crexText)?.value
            if (fixtureDate != null) addCrexFact("Match date/time", fixtureDate)
            val ground = Regex("(?i)\\b((?:AM\\s+)?[A-Za-z][A-Za-z .'-]{2,45}\\b(?:Oval|Stadium|Ground|Park|Cricket Ground),\\s*[A-Za-z .'-]+,\\s*[A-Za-z .'-]+)\\b").find(crexText)?.groupValues?.getOrNull(1)?.trim()?.removePrefix("AM ")
            if (ground != null) addCrexFact("Venue", ground)
            val h2h = Regex("(?is)(?:AUS\\s+vs\\s+ENG|ENG\\s+vs\\s+AUS)\\s+Head to Head\\s+\\(Last 10 matches\\).*?\\b(\\d{1,2})\\s*-\\s*(\\d{1,2})\\b").find(crexText)
            if (h2h != null) addCrexFact("Head-to-head (last 10)", "AUS ${h2h.groupValues[1]} wins – ENG ${h2h.groupValues[2]} wins", h2h.value)
            val venueStats = Regex("(?is)Venue Stats.{0,260}?Avg 1st Inns\\s+(\\d+).{0,50}?Avg 2nd Inns\\s+(\\d+)").find(crexText)
            if (venueStats != null) addCrexFact("Venue scoring averages", "1st innings ${venueStats.groupValues[1]}, 2nd innings ${venueStats.groupValues[2]}", venueStats.value)
            val paceSpin = Regex("(?is)Pace vs Spin.{0,180}?Pace\\s+(\\d+)\\s+Wkt\\s+(\\d+)%\\s+(\\d+)%\\s+Spin\\s+(\\d+)\\s+Wkt").find(crexText)
            if (paceSpin != null) addCrexFact("Venue pace vs spin wickets", "Pace ${paceSpin.groupValues[1]} wickets (${paceSpin.groupValues[2]}%); spin ${paceSpin.groupValues[4]} wickets (${paceSpin.groupValues[3]}%)", paceSpin.value)
            val weather = Regex("(?i)Weather in [^.]{0,220}(?:chance|rain)[^.]{0,80}").find(crexText)?.value
            if (weather != null) addCrexFact("Match weather", weather)
            val formSection = Regex("(?is)(?:AUS & ENG|AUS vs ENG) Team Form.{0,360}?(?:Head to Head|Team Comparison)").find(crexText)?.value
            if (formSection != null) addCrexFact("Recent team form (CREX last five)", formSection, formSection)
        }

        // Clear generic-parser warnings when CREX adjacent-block parsing found the field.
        if (facts.any { it.field == "Venue" }) missing.removeAll { it == "Venue" || it.startsWith("Venue: no clean ground name") }
        if (facts.any { it.field == "Match date/time" }) missing.removeAll { it == "Match date/time" }
        if (facts.none { it.field == "Venue" }) missing += "Venue: no clean ground name was extracted; the page contained unrelated venue statistics or no venue field."
        val titleFacts = raw.pages.map { it to it.title }.filter { it.second.isNotBlank() }
        if (titleFacts.isNotEmpty()) {
            val titles = titleFacts.map { it.second }.distinct()
            if (titles.size > 1 && titles.none { it.equals(titles.first(), true) }) {
                // Different page titles are expected for Info/Squads; only source match pages are compared.
                val primary = titleFacts.filter { it.first.source == "ICC" || it.first.source == "Cricbuzz" || it.first.source == "CREX" }
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
        if (pitchEvidence.isEmpty() && facts.none { it.field == "Match weather" }) missing += "Pitch report: no venue-specific pitch evidence was extracted; supply an official ground/board or trusted match preview URL."
        else if (pitchEvidence.isEmpty()) missing += "Pitch assessment: weather was found, but no actual surface/pitch behaviour report was present."

        val h2hPages = raw.pages.filter { it.source == "H2H source" || it.url.contains("head-to-head", true) || it.url.contains("h2h", true) }
        val h2hEvidence = h2hPages.flatMap { page ->
            Regex("(?i)(?:head.to.head|overall|played|won|wins|drawn|tied|last meeting)[^.!?]{0,180}[.!?]?")
                .findAll(page.extractedText).map { it.value.trim() }.toList().take(12)
                .map { EvidenceFact("Head to head", it, page.url, it, "MEDIUM") }
        }
        facts += h2hEvidence
        if (h2hEvidence.isEmpty() && facts.none { it.field.startsWith("Head-to-head") }) missing += "Head-to-head: no historical result evidence was extracted; supply a trusted series/results or stats URL."
        missing += "Player names and portrait identity matching (image URLs are candidates only until matched to official squad names)"
        return MatchResearchReport(raw.matchTitle, facts.distinctBy { "${it.field}|${it.value}|${it.source}" },
            raw.pages.map { it.url }.distinct(), imageMap, conflicts.distinct(), missing.distinct())
    }
}

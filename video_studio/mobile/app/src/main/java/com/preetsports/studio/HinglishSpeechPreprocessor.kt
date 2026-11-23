package com.preetsports.studio

/**
 * Converts only the spoken Hindi/Hinglish layer for the experimental Hindi VITS voice.
 * The source script is never modified. Cricket terms and numeric facts are protected
 * by validation; if conversion changes them, synthesis must be stopped and Gemini used.
 */
object HinglishSpeechPreprocessor {
    private val protectedCricketTerms = listOf(
        "T20", "T10", "ODI", "Test", "IPL", "BPL", "PSL", "WPL", "ICC", "BCCI",
        "Powerplay", "Power Play", "Death Overs", "Yorker", "Bouncer", "No-ball",
        "Wide", "DRS", "DLS", "LBW", "Wicket", "Over", "Overs", "Run", "Runs",
        "Boundary", "Six", "Four", "Strike Rate", "Economy", "All-rounder",
        "Captain", "Vice-Captain", "Super Over", "Playing XI"
    )

    fun protectedTokens(source: String): List<String> {
        val tokens = linkedSetOf<String>()
        Regex("\\b\\d+(?:[.,:/-]\\d+)*(?:%|st|nd|rd|th)?\\b", RegexOption.IGNORE_CASE)
            .findAll(source).forEach { tokens += it.value }
        protectedCricketTerms.sortedByDescending { it.length }.forEach { term ->
            Regex("(?i)(?<![\\p{L}])" + Regex.escape(term) + "(?![\\p{L}])")
                .findAll(source).forEach { tokens += it.value }
        }
        // Preserve title-cased names/acronyms such as Virat Kohli, ZIM-W and SA-W.
        Regex("\\b(?:[A-Z][A-Za-z.'-]*|[A-Z]{2,}(?:-[A-Z]+)?)(?:\\s+(?:[A-Z][A-Za-z.'-]*|[A-Z]{2,}))?")
            .findAll(source).forEach { tokens += it.value }
        return tokens.toList()
    }

    fun validate(source: String, converted: String): String {
        require(source.isNotBlank()) { "Source script is empty." }
        require(converted.isNotBlank()) { "Hindi speech conversion returned empty text." }
        val missing = protectedTokens(source).filter { token ->
            !converted.contains(token, ignoreCase = true)
        }
        require(missing.isEmpty()) {
            "Hindi conversion changed protected cricket facts or names: ${missing.take(8).joinToString()}"
        }
        val sourceDigits = Regex("\\d+").findAll(source).map { it.value }.toList()
        val outputDigits = Regex("\\d+").findAll(converted).map { it.value }.toList()
        require(sourceDigits == outputDigits) {
            "Hindi conversion changed score/number sequence. Use Gemini voice instead."
        }
        return converted.trim()
    }

    fun buildConversionPrompt(source: String): String {
        val protected = protectedTokens(source)
        return """
            Convert this Roman Hinglish narration into natural spoken Hindi written in Devanagari.
            This is for a Hindi-only offline speech model.
            STRICT RULES:
            - Keep every player name, team name, venue name, tournament name and acronym exactly in Latin script.
            - Keep cricket terms in English/Latin script where commonly spoken in Indian cricket commentary.
            - Keep all numbers, overs, score strings, dates, percentages and statistics exactly unchanged.
            - Do not add, remove, summarize, translate facts, or change meaning.
            - Preserve paragraph and sentence order.
            - Convert Hindi words and Hindi grammatical phrases to Devanagari.
            - Return only the converted script, with no explanation or headings.
            Protected tokens identified from source: ${protected.joinToString(" | ")}
            
            SOURCE SCRIPT:
            $source
        """.trimIndent()
    }
}

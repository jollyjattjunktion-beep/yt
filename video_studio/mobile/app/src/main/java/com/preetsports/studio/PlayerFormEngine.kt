package com.preetsports.studio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class MatchSquad(val team: String, val players: List<String>, val sourceUrl: String)

data class PlayerMatchRecord(
    val player: String,
    val matchId: String,
    val matchLabel: String,
    val date: String,
    val sourceUrl: String,
    val runs: Int? = null,
    val balls: Int? = null,
    val fours: Int? = null,
    val sixes: Int? = null,
    val wickets: Int? = null,
    val overs: String? = null,
    val runsConceded: Int? = null,
    val maidens: Int? = null,
    val catches: Int? = null,
    val runOuts: Int? = null
)

data class PlayerFormSummary(
    val player: String,
    val matches: Int,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val runsConceded: Int,
    val battingAverage: Double?,
    val strikeRate: Double?,
    val bowlingEconomy: Double?,
    val recentRecords: List<PlayerMatchRecord>
)

class PlayerFormStore(context: Context) {
    private val file = File(context.getExternalFilesDir(null), "PreetSports/player-last-five.json").apply {
        parentFile?.mkdirs()
    }

    @Synchronized
    fun upsert(records: List<PlayerMatchRecord>) {
        val all = readAll().associateBy { key(it.player, it.matchId) }.toMutableMap()
        records.forEach { all[key(it.player, it.matchId)] = it }
        val json = JSONArray()
        all.values.forEach { r ->
            json.put(JSONObject()
                .put("player", r.player).put("matchId", r.matchId).put("matchLabel", r.matchLabel)
                .put("date", r.date).put("sourceUrl", r.sourceUrl)
                .put("runs", r.runs ?: JSONObject.NULL).put("balls", r.balls ?: JSONObject.NULL)
                .put("fours", r.fours ?: JSONObject.NULL).put("sixes", r.sixes ?: JSONObject.NULL)
                .put("wickets", r.wickets ?: JSONObject.NULL).put("overs", r.overs ?: JSONObject.NULL)
                .put("runsConceded", r.runsConceded ?: JSONObject.NULL).put("maidens", r.maidens ?: JSONObject.NULL)
                .put("catches", r.catches ?: JSONObject.NULL).put("runOuts", r.runOuts ?: JSONObject.NULL))
        }
        file.writeText(json.toString(2))
    }

    @Synchronized
    fun readAll(): List<PlayerMatchRecord> {
        if (!file.exists()) return emptyList()
        val json = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return (0 until json.length()).mapNotNull { i ->
            runCatching {
                val o = json.getJSONObject(i)
                fun int(name: String) = if (o.isNull(name)) null else o.optInt(name)
                fun str(name: String) = if (o.isNull(name)) null else o.optString(name)
                PlayerMatchRecord(o.optString("player"), o.optString("matchId"), o.optString("matchLabel"),
                    o.optString("date"), o.optString("sourceUrl"), int("runs"), int("balls"), int("fours"),
                    int("sixes"), int("wickets"), str("overs"), int("runsConceded"), int("maidens"),
                    int("catches"), int("runOuts"))
            }.getOrNull()
        }
    }

    fun lastFive(player: String): PlayerFormSummary? {
        val rows = readAll().filter { it.player.equals(player, true) }
            .distinctBy { it.matchId }.takeLast(5).reversed()
        if (rows.isEmpty()) return null
        val runs = rows.sumOf { it.runs ?: 0 }
        val balls = rows.sumOf { it.balls ?: 0 }
        val outs = rows.count { it.runs != null }
        val wickets = rows.sumOf { it.wickets ?: 0 }
        val conceded = rows.sumOf { it.runsConceded ?: 0 }
        val oversBalls = rows.sumOf { oversToBalls(it.overs) }
        return PlayerFormSummary(player, rows.size, runs, balls,
            rows.sumOf { it.fours ?: 0 }, rows.sumOf { it.sixes ?: 0 }, wickets, conceded,
            if (outs > 0) runs.toDouble() / outs else null,
            if (balls > 0) runs * 100.0 / balls else null,
            if (oversBalls > 0) conceded * 6.0 / oversBalls else null, rows)
    }

    fun allPlayers(): List<String> = readAll().map { it.player }.distinct().sorted()

    private fun key(player: String, matchId: String) = "${player.trim().lowercase(Locale.ROOT)}|${matchId.trim()}"
    private fun oversToBalls(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        val parts = value.split(".")
        return (parts.firstOrNull()?.toIntOrNull() ?: 0) * 6 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}

object PlayerFormParser {
    fun extractSquads(page: MatchSourcePage): List<MatchSquad> {
        val text = page.extractedText
        val result = mutableListOf<MatchSquad>()
        val pattern = Regex("(?is)([A-Za-z][A-Za-z .'-]{1,35})\\s+Squad\\s*:\\s*(.*?)(?=\\s+[A-Za-z][A-Za-z .'-]{1,35}\\s+Squad\\s*:|\\s+Bench\\s*:|\\s+Support Staff|$)")
        pattern.findAll(text).forEach { m ->
            val team = m.groupValues[1].trim()
            val names = m.groupValues[2].split(Regex(",|;")).map { cleanPlayerName(it) }
                .filter { it.length in 2..45 && !it.contains("support staff", true) }
                .distinctBy { it.lowercase(Locale.ROOT) }
            if (names.isNotEmpty()) result += MatchSquad(team, names, page.url)
        }
        return result
    }

    fun extractScorecard(page: MatchSourcePage, matchId: String, matchLabel: String, date: String = ""): List<PlayerMatchRecord> {
        val text = page.extractedText.replace(Regex("\\s+"), " ")
        val out = mutableListOf<PlayerMatchRecord>()
        // Cricbuzz/CREX batter rows: name, dismissal, runs, balls, 4s, 6s, strike rate.
        val batter = Regex("([A-Z][A-Za-z.' -]{1,38}(?:\\s*\\((?:c|wk|c & wk)\\))?)\\s+(?:c\\s+.{1,90}?\\s+b\\s+[A-Z][A-Za-z.' -]{1,35}|lbw\\s+b\\s+[A-Z][A-Za-z.' -]{1,35}|b\\s+[A-Z][A-Za-z.' -]{1,35}|run out(?:\\s+.{1,50})?|not out|retired hurt)\\s+(\\d{1,3})\\s+(\\d{1,3})\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{1,3}(?:\\.\\d+)?)")
        batter.findAll(text).forEach { m ->
            val name = cleanPlayerName(m.groupValues[1])
            if (name.isNotBlank()) out += PlayerMatchRecord(name, matchId, matchLabel, date, page.url,
                m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull(),
                m.groupValues[4].toIntOrNull(), m.groupValues[5].toIntOrNull())
        }
        // Bowler rows: name, overs, maidens, runs, wickets, economy.
        val bowler = Regex("([A-Z][A-Za-z.' -]{1,38})\\s+(\\d{1,2}(?:\\.\\d)?)\\s+(\\d{1,2})\\s+(\\d{1,3})\\s+(\\d{1,2})\\s+(\\d{1,2}(?:\\.\\d{1,2})?)")
        bowler.findAll(text).forEach { m ->
            val name = cleanPlayerName(m.groupValues[1])
            if (name.isNotBlank()) {
                val idx = out.indexOfFirst { it.player.equals(name, true) && it.matchId == matchId }
                if (idx >= 0) out[idx] = out[idx].copy(overs = m.groupValues[2],
                    maidens = m.groupValues[3].toIntOrNull(), runsConceded = m.groupValues[4].toIntOrNull(),
                    wickets = m.groupValues[5].toIntOrNull())
                else out += PlayerMatchRecord(name, matchId, matchLabel, date, page.url,
                    wickets = m.groupValues[5].toIntOrNull(), overs = m.groupValues[2],
                    maidens = m.groupValues[3].toIntOrNull(), runsConceded = m.groupValues[4].toIntOrNull())
            }
        }
        return out.distinctBy { it.player.lowercase(Locale.ROOT) }
    }

    private fun cleanPlayerName(raw: String): String = raw.trim()
        .replace(Regex("\\s*\\((?:c|wk|c & wk)\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ").trim(' ', ',', ':', '•', '-')
}

data class PlayerFantasyForm(
    val player: String,
    val matches: Int,
    val averageFantasyFormPoints: Double,
    val recentPoints: List<Double>,
    val explanation: String
)

object FantasyFormEngine {
    // Transparent internal baseline only; not a claim to reproduce any fantasy platform's live rules.
    fun evaluate(summary: PlayerFormSummary): PlayerFantasyForm {
        val points = summary.recentRecords.map { row ->
            (row.runs ?: 0) * 1.0 +
                (row.fours ?: 0) * 1.0 +
                (row.sixes ?: 0) * 2.0 +
                (row.wickets ?: 0) * 25.0 +
                (row.maidens ?: 0) * 8.0 +
                (row.catches ?: 0) * 8.0 +
                (row.runOuts ?: 0) * 8.0
        }
        val average = if (points.isEmpty()) 0.0 else points.average()
        return PlayerFantasyForm(summary.player, summary.matches, average, points,
            "Runs + boundaries + wickets + maidens + fielding where scorecard evidence exists; platform bonus rules are not applied.")
    }
}

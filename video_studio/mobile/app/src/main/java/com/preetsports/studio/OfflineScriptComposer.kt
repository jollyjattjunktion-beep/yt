package com.preetsports.studio

/**
 * Deterministic offline Roman-Hinglish script composer.
 * Uses only locally available research and scorecard data; it is not a neural LLM.
 */
object OfflineScriptComposer {
    fun generate(match: String, format: String, minutes: Int, report: MatchResearchReport?,
                 squads: List<MatchSquad>, playerForms: List<PlayerFormSummary>): String {
        val teams = match.split(Regex("(?i)\\s+vs\\.?\\s+|\\s+v\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        val a = teams.getOrNull(0) ?: "Pehli team"
        val b = teams.getOrNull(1) ?: "Doosri team"
        val facts = report?.facts.orEmpty().filter {
            it.confidence.equals("HIGH", true) || it.confidence.equals("MEDIUM", true)
        }.distinctBy { it.field.lowercase() + "|" + it.value.lowercase() }
        fun fact(field: String) = facts.firstOrNull { it.field.contains(field, true) }?.value
        val verifiedPitch = facts.filter { it.field.contains("pitch", true) || it.field.contains("pace", true) || it.field.contains("spin", true) }
        val players = playerForms.take(10).joinToString("\n") { p ->
            val sr = p.strikeRate?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "unavailable"
            val econ = p.bowlingEconomy?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "unavailable"
            "- " + p.player + ": " + p.matches + " imported matches, " + p.runs + " runs, " + p.wickets + " wickets, SR " + sr + ", economy " + econ
        }
        val squadNames = squads.flatMap { s -> s.players.map { s.team + ": " + it } }.distinct().take(20)
        val sections = mutableListOf<String>()
        sections += "NAMASKAR DOSTO! Swagat hai aapka Preet Sports mein. Aaj hum analysis karenge " + match + " ka. Is " + format + " match mein hum team combinations, available player form, pitch evidence aur possible match situations ko samjhenge. Hamara aim hype nahi, balki facts aur cricketing logic ke saath balanced preview dena hai."
        sections += "MATCH CONTEXT: " + (fact("date")?.let { "Available source mein match timing " + it + " di gayi hai." } ?: "Match date aur timing abhi verify nahi hui hai.") + " " + (fact("Venue")?.let { "Venue " + it + " hai." } ?: "Venue ka clean verified naam available nahi hai.") + " Final playing eleven aur toss ko official team sheet se confirm karna zaroori hai."
        sections += "TEAM ANALYSIS: " + a + " ko powerplay mein wickets bachate hue scoring ka balance banana hoga. Strong opening partnership middle order ko innings build karne ka mauka de sakti hai. Agar early wickets girti hain, toh batting side ko risk aur strike rotation ka balance banana padega. " + b + " ke liye disciplined powerplay bowling, middle overs mein run control aur death overs mein execution important ho sakta hai. Ye tactical possibilities hain, confirmed team plans nahi."
        sections += fact("head-to-head")?.let { "HEAD TO HEAD: Available source ke mutabik " + it + ". Purane results future match ki guarantee nahi hote, isliye ise sirf supporting context samjhein." } ?: "HEAD TO HEAD: Verified head-to-head record available nahi hai. Hum koi win-loss number guess nahi karenge."
        sections += if (verifiedPitch.isNotEmpty()) {
            "PITCH AND CONDITIONS: " + verifiedPitch.joinToString(" ") { it.field + ": " + it.value + "." } + " In facts ko source-backed evidence ke roop mein hi dekhein. Toss ke baad actual surface ko dobara assess karein."
        } else {
            "PITCH AND CONDITIONS: Venue-specific pitch evidence abhi available nahi hai. Isliye pitch ko batting-friendly ya bowling-friendly declare nahi karenge. Grass, hardness, bounce aur dew ko ground report se verify karein."
        }
        sections += if (players.isNotBlank()) {
            "RECENT PLAYER FORM (LOCAL DATABASE):\n" + players + "\nYe sirf imported scorecard records hain. Paanch alag completed scorecards import hue hon tabhi ise last-five form samjhein."
        } else {
            "RECENT PLAYER FORM: Local database mein abhi verified player scorecard records nahi mile. Runs, strike rate ya wickets invent nahi kiye jayenge. Historical scorecards import karne ke baad actual form yahan add hoga."
        }
        sections += if (squadNames.isNotEmpty()) {
            "SQUAD CHECK: Imported squad names: " + squadNames.joinToString("; ") + ". Squad list confirmed playing eleven nahi hoti. Final XI ko toss ke aas-paas official team sheet se verify karein."
        } else {
            "SQUAD CHECK: Squad aur confirmed playing eleven abhi verify karna baaki hai. Kisi player ka naam assume nahi kiya jayega."
        }
        sections += "FANTASY AND TACTICS: Selection se pehle confirmed role, batting position, expected bowling overs aur recent scorecard evidence check karein. All-rounders tabhi useful hote hain jab unka batting ya bowling role clear ho. Death-over bowlers ke wicket chances ho sakte hain, lekin unka selection expected overs aur opposition matchup dekhkar hi karein. Sirf reputation ke basis par selection risky ho sakta hai."
        sections += "MATCH SCENARIOS: Agar " + a + " powerplay mein wickets lose kiye bina start karta hai, toh middle overs mein pace badha sakta hai. Agar " + b + " early breakthroughs leta hai, toh pressure badh sakta hai. Doosri taraf, partnership banne par bowling side ko wicket-taking options aur field placements ka smart use karna hoga. Ye conditional scenarios hain, fixed prediction nahi."
        sections += "FINAL VIEW: Match ka edge toss, confirmed playing eleven, actual pitch aur player roles par depend karega. Jo facts verify nahi hue hain unke basis par strong winner prediction dena sahi nahi hoga. Dosto, aapke hisaab se key player kaun ho sakta hai? Comments mein batayein. Preet Sports ko follow karein. Milte hain next analysis mein. Jai Hind!"
        var script = sections.joinToString("\n\n")
        val targetWords = (minutes.coerceIn(3, 10) * 120).coerceAtLeast(360)
        val extra = listOf(
            "Role clarity bahut important hai. Player ka batting order, expected overs aur fielding position uski opportunity ko affect karte hain. Team sheet aane ke baad in roles ko dobara check karein.",
            "Scorecard numbers compare karte waqt opposition quality, match situation aur format ko bhi dhyan mein rakhein. Har innings ka context alag hota hai.",
            "Cricket mein momentum jaldi change hota hai. Toss ke baad final conditions aur playing eleven ke basis par analysis update karna sensible rahega."
        )
        var i = 0
        while (script.split(Regex("\\s+")).size < targetWords && i < 10) {
            script += "\n\n" + extra[i % extra.size]
            i++
        }
        return script
    }
}

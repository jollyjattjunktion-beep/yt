package com.preetsports.studio

import android.content.Context
import org.json.JSONObject
import java.io.File

class LocalProjectStore(context: Context) {
    private val root = File(context.getExternalFilesDir(null), "PreetSports").apply { mkdirs() }

    fun createProject(match: String): File {
        val safe = match.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "match" }
        return File(root, "$safe-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    fun saveScript(project: File, match: String, format: String, script: String): File {
        val file = File(project, "script.txt")
        file.writeText(script)
        File(project, "project.json").writeText(JSONObject()
            .put("brand", "Preet Sports").put("match", match).put("format", format)
            .put("language", "Roman Hinglish").put("status", "script_ready").toString(2))
        return file
    }
}

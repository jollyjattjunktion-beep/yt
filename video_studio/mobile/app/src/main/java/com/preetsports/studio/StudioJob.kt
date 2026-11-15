package com.preetsports.studio

import android.content.Context
import java.io.File
import org.json.JSONObject

data class StudioJob(
    val id: String,
    val match: String,
    val format: String,
    val durationMinutes: Int,
    val status: String,
    val progress: Int,
    val message: String,
    val projectPath: String
)

class StudioJobStore(context: Context) {
    private val file = File(context.filesDir, "studio-jobs.json")

    @Synchronized fun save(job: StudioJob) {
        val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        root.put(job.id, JSONObject()
            .put("id",job.id).put("match",job.match).put("format",job.format)
            .put("durationMinutes",job.durationMinutes).put("status",job.status)
            .put("progress",job.progress).put("message",job.message).put("projectPath",job.projectPath))
        file.writeText(root.toString(2))
    }

    fun all(): List<StudioJob> {
        if (!file.exists()) return emptyList()
        val root=JSONObject(file.readText()); val result=mutableListOf<StudioJob>()
        root.keys().forEach { key ->
            val j=root.getJSONObject(key)
            result += StudioJob(j.getString("id"),j.getString("match"),j.getString("format"),
                j.getInt("durationMinutes"),j.getString("status"),j.getInt("progress"),
                j.getString("message"),j.getString("projectPath"))
        }
        return result.sortedByDescending { it.id }
    }
}

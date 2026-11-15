package com.preetsports.studio

import android.graphics.Bitmap
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class YouTubePackage(
    val thumbnailLandscape: File,
    val thumbnailPortrait: File,
    val metadataFile: File,
    val packageZip: File
)

class YouTubePackageBuilder {
    fun build(project: File, match: String, format: String, script: String, scenes: List<StudioScene>): YouTubePackage {
        require(scenes.isNotEmpty()) { "No scenes available for thumbnail generation." }
        val renderer = CricketSceneRenderer()
        val assets = ScenePhotoAssets.load(project)
        val landscape = File(project, "youtube-thumbnail-16x9.jpg")
        val portrait = File(project, "youtube-thumbnail-9x16.jpg")
        try {
            renderer.savePreview(scenes.first(), landscape, assets, 1280, 720)
            renderer.savePreview(scenes.first(), portrait, assets, 720, 1280)
        } finally { assets.recycle() }

        val cleanMatch = match.replace(Regex("\\s+"), " ").trim()
        val title = "$cleanMatch | Match Preview, Squad, Pitch Report & Prediction | Preet Sports".take(100)
        val description = buildString {
            appendLine("$cleanMatch — Preet Sports match preview.")
            appendLine()
            appendLine("In this video:")
            appendLine("• Match details and confirmed squad information")
            appendLine("• Pitch report and head-to-head only where verified")
            appendLine("• Players to watch and tactical matchups")
            appendLine("• Match outlook and analysis")
            appendLine()
            appendLine("Language: Roman Hinglish")
            appendLine("Format: $format")
            appendLine()
            appendLine("Research and source references are included in match-research.txt where available.")
            appendLine("Please verify late team changes, toss and match-day conditions before publishing.")
            appendLine()
            appendLine("Preet Sports — Cricket ko samjho. Game ko enjoy karo.")
        }
        val tags = listOf("cricket", "match preview", "cricket analysis", "playing xi", "pitch report", "head to head", "Preet Sports")
        val metadata = File(project, "youtube-metadata.txt")
        metadata.writeText(buildString {
            appendLine("TITLE")
            appendLine(title)
            appendLine()
            appendLine("DESCRIPTION")
            appendLine(description)
            appendLine()
            appendLine("TAGS")
            appendLine(tags.joinToString(", "))
            appendLine()
            appendLine("SCRIPT")
            appendLine(script)
        })
        val zip = File(project, "preet-sports-youtube-package.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            listOf(landscape, portrait, metadata, File(project, "script.txt"),
                File(project, "match-research.txt"), File(project, "preet-sports-16x9.mp4"),
                File(project, "preet-sports-9x16.mp4")).filter { it.isFile }.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return YouTubePackage(landscape, portrait, metadata, zip)
    }
}

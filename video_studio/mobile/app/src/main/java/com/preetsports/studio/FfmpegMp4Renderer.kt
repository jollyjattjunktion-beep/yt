package com.preetsports.studio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Phone-local FFmpeg renderer: scene-card concat + narration -> H.264/AAC MP4. */
class FfmpegMp4Renderer {
    data class Result(val file: File, val width: Int, val height: Int)

    suspend fun render(project: File, wav: File, sceneFiles: List<File>, portrait: Boolean,
                       onProgress: (String) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        require(wav.isFile && wav.length() > 44) { "Narration WAV is missing." }
        val validScenes = sceneFiles.filter { it.isFile }
        require(validScenes.isNotEmpty()) { "No rendered scene cards found." }
        val width = if (portrait) 720 else 1280
        val height = if (portrait) 1280 else 720
        val concat = File(project, if (portrait) "scenes-portrait.ffconcat" else "scenes-landscape.ffconcat")
        val durationSeconds = wavDurationSeconds(wav).coerceAtLeast(1.0)
        val perScene = durationSeconds / validScenes.size
        concat.writeText(buildString {
            appendLine("ffconcat version 1.0")
            validScenes.forEach { file ->
                appendLine("file '${file.absolutePath.replace("'", "'\\''")}'")
                appendLine("duration ${"%.4f".format(java.util.Locale.US, perScene)}")
            }
            appendLine("file '${validScenes.last().absolutePath.replace("'", "'\\''")}'")
        })
        val output = File(project, if (portrait) "preet-sports-9x16.mp4" else "preet-sports-16x9.mp4")
        output.delete()
        val vf = "scale=$width:$height:force_original_aspect_ratio=decrease,pad=$width:$height:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=yuv420p"
        val command = listOf(
            "-y", "-hide_banner", "-loglevel", "warning",
            "-f", "concat", "-safe", "0", "-i", quote(concat.absolutePath),
            "-i", quote(wav.absolutePath),
            "-map", "0:v:0", "-map", "1:a:0",
            "-vf", quote(vf), "-c:v", "libx264", "-preset", "veryfast",
            "-crf", "21", "-r", "30", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "160k", "-ar", "48000",
            "-shortest", "-movflags", "+faststart", quote(output.absolutePath)
        ).joinToString(" ")
        onProgress("FFmpeg is rendering ${width}x$height video on this phone…")
        val session = FFmpegKit.execute(command)
        val rc = session.returnCode
        if (!ReturnCode.isSuccess(rc) || !output.isFile || output.length() < 1024) {
            val detail = session.failStackTrace ?: session.allLogsAsString
            error("FFmpeg render failed (return code $rc): ${detail.takeLast(1800)}")
        }
        output
        Result(output, width, height)
    }

    private fun quote(path: String): String = "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun wavDurationSeconds(file: File): Double {
        java.io.RandomAccessFile(file, "r").use { raf ->
            require(raf.length() >= 44) { "Invalid WAV file." }
            val header = ByteArray(44)
            raf.readFully(header)
            val b = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") { "Invalid WAV header." }
            val sampleRate = b.getInt(24)
            val channels = b.getShort(22).toInt() and 0xffff
            val bits = b.getShort(34).toInt() and 0xffff
            val dataSize = b.getInt(40).toLong() and 0xffffffffL
            require(sampleRate > 0 && channels > 0 && bits == 16) { "Only 16-bit PCM WAV is supported." }
            return dataSize.toDouble() / (sampleRate.toDouble() * channels * bits / 8.0)
        }
    }
}

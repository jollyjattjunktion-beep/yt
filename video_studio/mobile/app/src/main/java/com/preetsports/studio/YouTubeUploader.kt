package com.preetsports.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class YouTubeUploadResult(val videoId: String, val watchUrl: String, val privacyStatus: String)

/** Uploads the landscape MP4 through YouTube Data API v3 videos.insert. */
class YouTubeUploader {
    suspend fun uploadPrivate(video: File, title: String, description: String, tags: List<String>,
                              oauthAccessToken: String): YouTubeUploadResult = withContext(Dispatchers.IO) {
        require(video.isFile && video.length() > 0) { "Rendered video file is missing." }
        require(oauthAccessToken.isNotBlank()) { "A YouTube OAuth access token is required." }
        val metadata = JSONObject()
            .put("snippet", JSONObject().put("title", title.take(100))
                .put("description", description.take(5000))
                .put("tags", JSONArray(tags.take(500))).put("categoryId", " 스포츠".trim().ifBlank { "17" }))
            .put("status", JSONObject().put("privacyStatus", "private")
                .put("selfDeclaredMadeForKids", false))
        val init = URL("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .openConnection() as HttpURLConnection
        init.requestMethod = "POST"
        init.connectTimeout = 30000
        init.readTimeout = 30000
        init.doOutput = true
        init.setRequestProperty("Authorization", "Bearer $oauthAccessToken")
        init.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        init.setRequestProperty("X-Upload-Content-Type", "video/mp4")
        init.setRequestProperty("X-Upload-Content-Length", video.length().toString())
        init.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
        val status = init.responseCode
        if (status !in 200..299) {
            val error = (init.errorStream ?: init.inputStream).bufferedReader().use { it.readText() }
            init.disconnect()
            error("YouTube upload session failed ($status): ${error.take(900)}")
        }
        val uploadUrl = init.getHeaderField("Location") ?: run {
            init.disconnect()
            error("YouTube did not return a resumable upload URL.")
        }
        init.disconnect()

        val upload = URL(uploadUrl).openConnection() as HttpURLConnection
        upload.requestMethod = "PUT"
        upload.connectTimeout = 30000
        upload.readTimeout = 180000
        upload.doOutput = true
        upload.setFixedLengthStreamingMode(video.length())
        upload.setRequestProperty("Authorization", "Bearer $oauthAccessToken")
        upload.setRequestProperty("Content-Type", "video/mp4")
        video.inputStream().use { input -> upload.outputStream.use { output -> input.copyTo(output, 1024 * 1024) } }
        val code = upload.responseCode
        val body = (if (code in 200..299) upload.inputStream else upload.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        upload.disconnect()
        if (code !in 200..299) error("YouTube video upload failed ($code): ${body.take(1200)}")
        val response = JSONObject(body)
        val id = response.optString("id")
        require(id.isNotBlank()) { "YouTube upload response did not include a video ID." }
        YouTubeUploadResult(id, "https://www.youtube.com/watch?v=$id", "private")
    }
}

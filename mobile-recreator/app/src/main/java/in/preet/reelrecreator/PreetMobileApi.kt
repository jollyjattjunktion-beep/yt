package `in`.preet.reelrecreator

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PreetMobileApi {
    private const val BASE = BuildConfig.API_BASE_URL
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(360, TimeUnit.SECONDS)
        .build()

    suspend fun submit(context: Context, matchUrl: String, screenshots: List<Uri>): String =
        withContext(Dispatchers.IO) {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("match_url", matchUrl.trim())
                .addFormDataPart("format", "Test")
                .addFormDataPart("duration_minutes", "10")
                .addFormDataPart("language", "Roman Hinglish")
                .addFormDataPart("tts_provider", "both")
                .addFormDataPart("voice", "hi-IN-MadhurNeural")
            screenshots.take(10).forEachIndexed { index, uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read screenshot " + (index + 1))
                if (bytes.size > 12 * 1024 * 1024) throw IllegalArgumentException("Screenshot " + (index + 1) + " exceeds 12 MB.")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                form.addFormDataPart("screenshots", "match-" + (index + 1) + "." + ext,
                    bytes.toRequestBody(mime.toMediaTypeOrNull()))
            }
            val request = Request.Builder().url(BASE + "/api/jobs/upload").post(form.build()).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("Upload failed (" + response.code + "): " + body)
                JSONObject(body).getString("job_id")
            }
        }

    suspend fun waitForJob(jobId: String, onUpdate: (String) -> Unit) {
        repeat(900) {
            val json = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(BASE + "/api/jobs/" + jobId).get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("Job status failed (" + response.code + "): " + body)
                    JSONObject(body)
                }
            }
            val state = json.optString("status")
            onUpdate("Job " + jobId + " · " + state + " · " + json.optInt("progress", 0) + "%\\n" + json.optString("stage", "Working"))
            if (state == "completed") return
            if (state == "failed") throw IllegalStateException(json.optString("error", "Production failed."))
            delay(3000)
        }
        throw IllegalStateException("Production timed out. Job ID: " + jobId)
    }

    fun downloadLandscape(context: Context, jobId: String): Long {
        val request = DownloadManager.Request(Uri.parse(BASE + "/api/jobs/" + jobId + "/files/landscape"))
            .setTitle("Preet Sports cricket pre-analysis")
            .setDescription("Downloading 1080p match analysis video")
            .setMimeType("video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "PreetSports-" + jobId + "-1080p.mp4")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}

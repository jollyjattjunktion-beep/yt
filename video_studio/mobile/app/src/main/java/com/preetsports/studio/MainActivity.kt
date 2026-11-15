package com.preetsports.studio

import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private val Navy = Color(0xFF081522)
private val Panel = Color(0xFF12263A)
private val Gold = Color(0xFFE6B75F)

class MainActivity : ComponentActivity() {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            this, "preet_sports_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, background = Navy, surface = Panel,
                onPrimary = Navy, onBackground = Color.White, onSurface = Color.White
            )) {
                StudioHome(
                    savedKey = prefs.getString("gemini_api_key", "") ?: "",
                    onSaveKey = { prefs.edit().putString("gemini_api_key", it.trim()).apply() }
                )
            }
        }
    }
}

@Composable
private fun StudioHome(savedKey: String, onSaveKey: (String) -> Unit) {
    var match by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("T20") }
    var duration by remember { mutableStateOf("5 minutes") }
    var apiKey by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(savedKey.isNotBlank()) }
    var activeKey by remember { mutableStateOf(savedKey) }
    var notice by remember { mutableStateOf("Ready to configure your production studio.") }
    var busy by remember { mutableStateOf(false) }
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(14)
    ) { uris ->
        photoUris = uris.take(14)
        notice = if (uris.isEmpty()) "No match photos selected." else "${photoUris.size} match photos selected."
    }

    Column(
        Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("PREET SPORTS", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("AI Cricket Production Studio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Create Roman Hinglish cricket videos directly on your Android device.", color = Color.LightGray)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("01 · Match setup", color = Gold, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = match, onValueChange = { match = it }, label = { Text("Match name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Format", color = Color.LightGray)
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(format) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("T20", "ODI", "Test", "T10").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { format = it; expanded = false }) }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Duration", color = Color.LightGray)
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(duration) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("5 minutes", "6 minutes", "7 minutes").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { duration = it; expanded = false }) }
                            }
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("02 · Gemini settings", color = Gold, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Gemini API key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    if (apiKey.isNotBlank()) { onSaveKey(apiKey); activeKey = apiKey.trim(); apiKey = ""; keySaved = true; notice = "Gemini key saved securely on this device." }
                    else notice = if (keySaved) "A Gemini key is already saved on this device." else "Enter your Gemini API key first."
                }, modifier = Modifier.fillMaxWidth()) { Text("Save key securely") }
                Text(if (keySaved) "API key saved on device" else "API key not configured", color = if (keySaved) Gold else Color.LightGray)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("03 · Match photos", color = Gold, fontWeight = FontWeight.Bold)
                Text("Select up to 14 images in this order: venue, home-team, away-team, then player portraits.", color = Color.LightGray)
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose photos from gallery") }
                Text(if (photoUris.isEmpty()) "No photos selected — cinematic graphic backgrounds will be used." else "${photoUris.size} photos ready for video.", color = if (photoUris.isEmpty()) Color.LightGray else Gold)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("04 · Production modules", color = Gold, fontWeight = FontWeight.Bold)
                listOf("Roman Hinglish commentary", "Gemini presenter voice", "Stadium and team graphics", "Player cards and pitch report", "Head-to-head and match analysis", "9:16 and 16:9 video exports").forEach {
                    Text("✓  $it", color = Color.White)
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        when {
                            match.isBlank() -> notice = "Enter a match name to start."
                            !keySaved && apiKey.isBlank() -> notice = "Save your Gemini API key first."
                            else -> {
                                busy = true
                                notice = "Preparing your Roman Hinglish script…"
                                scope.launch {
                                    try {
                                        val key = if (apiKey.isNotBlank()) apiKey.trim() else activeKey
                                        val project = LocalProjectStore(context).createProject(match)
                                        if (photoUris.isNotEmpty()) {
                                            val assetDir = java.io.File(project, "assets").apply { mkdirs() }
                                            photoUris.take(14).forEachIndexed { index, uri ->
                                                val filename = when (index) {
                                                    0 -> "01-venue"
                                                    1 -> "02-home-team"
                                                    2 -> "03-away-team"
                                                    else -> "player-%02d".format(index - 2)
                                                }
                                                context.contentResolver.openInputStream(uri)?.use { input ->
                                                    java.io.File(assetDir, filename).outputStream().use { output -> input.copyTo(output) }
                                                }
                                            }
                                        }
                                        val minutes = duration.substringBefore(" ").toIntOrNull() ?: 5
                                        val gemini = GeminiClient(key)
                                        val script = gemini.createScript(match, format, minutes)
                                        LocalProjectStore(context).saveScript(project, match, format, script)
                                        notice = "Script ready. Generating presenter voice…"
                                        val audio = gemini.createVoice(script, java.io.File(project, "voiceover.wav"))
                                        val scenes = CricketSceneRenderer().defaultScenes(match, format)
                                        val photoAssets = ScenePhotoAssets.load(project)
                                        try {
                                            scenes.forEachIndexed { index, scene ->
                                                CricketSceneRenderer().savePreview(scene, java.io.File(project, "scene-${index + 1}.jpg"), photoAssets)
                                            }
                                        } finally {
                                            photoAssets.recycle()
                                        }
                                        notice = "Assets ready. Rendering landscape MP4 on this phone…"
                                        val renderer = AndroidMp4Renderer(context)
                                        val landscape = renderer.render(project, audio, scenes, false) { p ->
                                            notice = "Rendering landscape video: $p%"
                                        }
                                        renderer.publishToMovies(landscape.file)
                                        notice = "Landscape MP4 saved. Rendering vertical 9:16 version…"
                                        val vertical = renderer.render(project, audio, scenes, true) { p ->
                                            notice = "Rendering vertical video: $p%"
                                        }
                                        renderer.publishToMovies(vertical.file)
                                        val job = StudioJob(project.name, match, format, minutes, "completed", 100,
                                            "Both MP4 formats exported to Movies/Preet Sports", project.absolutePath)
                                        StudioJobStore(context).save(job)
                                        notice = "Production complete!\\n16:9: ${landscape.file.name}\\n9:16: ${vertical.file.name}\\nBoth videos saved in Movies/Preet Sports.\\nProject: ${project.absolutePath}"
                                    } catch (e: Exception) {
                                        notice = "Production step failed: ${e.message ?: "Unknown error"}"
                                    } finally { busy = false }
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Generating…" else "Generate script + voice") }
            }
        }
        Text(notice, color = Gold)
        Text("On-device Gemini script & voice · photo-led match graphics · animated 8 fps MP4 export", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
}

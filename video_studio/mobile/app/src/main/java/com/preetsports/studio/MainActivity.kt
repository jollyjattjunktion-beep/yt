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
                    scriptKey = prefs.getString("apinex_script_key", "") ?: "",
                    voiceKey = prefs.getString("apinex_voice_key", "") ?: "",
                    onSaveScriptKey = { prefs.edit().putString("apinex_script_key", it.trim()).apply() },
                    onSaveVoiceKey = { prefs.edit().putString("apinex_voice_key", it.trim()).apply() }
                )
            }
        }
    }
}

@Composable
private fun StudioHome(
    scriptKey: String, voiceKey: String,
    onSaveScriptKey: (String) -> Unit, onSaveVoiceKey: (String) -> Unit
) {
    var match by remember { mutableStateOf("") }
    var iccUrl by remember { mutableStateOf("https://www.icc-cricket.com/tournaments/icc-women-s-emerging-nations-trophy-2026/matches/275635/zimbabwe-vs-pakistan") }
    var cricbuzzUrl by remember { mutableStateOf("https://www.cricbuzz.com/live-cricket-scores/177481/zimw-vs-pakw-14th-match-group-b-icc-womens-emerging-nations-trophy-2026") }
    var pitchSourceUrl by remember { mutableStateOf("") }
    var h2hSourceUrl by remember { mutableStateOf("") }
    var playerNames by remember { mutableStateOf("") }
    var portraitStatus by remember { mutableStateOf("") }
    var youtubeAccessToken by remember { mutableStateOf("") }
    var completedProjectPath by remember { mutableStateOf("") }
    var importedResearch by remember { mutableStateOf<MatchResearch?>(null) }
    var researchReport by remember { mutableStateOf<MatchResearchReport?>(null) }
    var stageOneDone by remember { mutableStateOf(false) }
    var stageTwoDone by remember { mutableStateOf(false) }
    var stageThreeDone by remember { mutableStateOf(false) }
    var researchProjectPath by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("T20") }
    var duration by remember { mutableStateOf("5 minutes") }
    var scriptApiKey by remember { mutableStateOf("") }
    var voiceApiKey by remember { mutableStateOf("") }
    var activeScriptKey by remember { mutableStateOf(scriptKey) }
    var activeVoiceKey by remember { mutableStateOf(voiceKey) }
    var scriptKeySaved by remember { mutableStateOf(scriptKey.isNotBlank()) }
    var voiceKeySaved by remember { mutableStateOf(voiceKey.isNotBlank()) }
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
        Text("Research match facts, cross-check trusted sources, match portraits and create Roman Hinglish videos on your Android device.", color = Color.LightGray)
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
                Text("02 · Stage 1 — Match research", color = Gold, fontWeight = FontWeight.Bold)
                Text("Paste official ICC and Cricbuzz match links. The app fetches available page details and related squad/scorecard links.", color = Color.LightGray)
                OutlinedTextField(value = iccUrl, onValueChange = { iccUrl = it; importedResearch = null }, label = { Text("ICC match URL") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = cricbuzzUrl, onValueChange = { cricbuzzUrl = it; importedResearch = null; researchReport = null }, label = { Text("Cricbuzz match URL") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = pitchSourceUrl, onValueChange = { pitchSourceUrl = it; researchReport = null }, label = { Text("Trusted pitch report URL (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = h2hSourceUrl, onValueChange = { h2hSourceUrl = it; researchReport = null }, label = { Text("Trusted H2H / historical results URL (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = playerNames, onValueChange = { playerNames = it }, label = { Text("Confirmed player names (comma-separated)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedButton(
                    enabled = !busy && (iccUrl.isNotBlank() || cricbuzzUrl.isNotBlank()),
                    onClick = {
                        busy = true
                        notice = "Fetching match details from ICC and Cricbuzz…"
                        scope.launch {
                            try {
                                val research = MatchResearchEngine().research(iccUrl, cricbuzzUrl, pitchSourceUrl, h2hSourceUrl)
                                researchReport = research
                                val raw = MatchUrlImporter().import(iccUrl, cricbuzzUrl)
                                importedResearch = raw
                                if (match.isBlank()) match = research.matchTitle
                                val project = LocalProjectStore(context).createProject(match)
                                researchProjectPath = project.absolutePath
                                research.save(java.io.File(project, "research-report.json"))
                                java.io.File(project, "match-research.txt").writeText(research.toPromptContext())
                                java.io.File(project, "research-project-path.txt").writeText(project.absolutePath)
                                notice = "Stage 1 research completed. Review verified facts, conflicts and missing information before production."
                            } catch (e: Exception) {
                                notice = "Match import failed: ${e.message ?: "Unknown error"}"
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Fetching sources…" else "Fetch match data") }
                researchReport?.let { report ->
                    Text("MATCH: ${report.matchTitle}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("STAGE 1: ${report.facts.size} source-backed facts · ${report.sourceUrls.size} source pages · ${report.imageCandidates.size} image candidates", color = Gold)
                    report.facts.forEach { fact ->
                        Text("${fact.field}: ${fact.value} · ${fact.confidence}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                    report.conflicts.forEach { Text("CONFLICT: $it", color = Gold, style = MaterialTheme.typography.bodySmall) }
                    report.missing.forEach { Text("NOT VERIFIED: $it", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
                    Text("Player image candidates are not assigned to players until identity is verified. Source URLs are retained.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Text("Player portrait matching", color = Gold, fontWeight = FontWeight.Bold)
                    OutlinedButton(enabled = !busy && playerNames.isNotBlank(), onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val names = playerNames.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                val project = if (researchProjectPath.isNotBlank()) java.io.File(researchProjectPath)
                                    else LocalProjectStore(context).createProject(report.matchTitle)
                                val matches = PortraitMatcher().download(project, names, report.imageCandidates)
                                portraitStatus = "Downloaded ${matches.size} exact-name portrait(s). Unmatched names were left blank."
                                notice = portraitStatus
                            } catch (e: Exception) { notice = "Portrait matching failed: ${e.message}" }
                            finally { busy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Match and download official portraits") }
                    if (portraitStatus.isNotBlank()) Text(portraitStatus, color = Gold)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("03 · APInex settings", color = Gold, fontWeight = FontWeight.Bold)
                Text("Separate credentials for script generation and presenter voice. You may use the same APInex key in both fields; each is saved independently and encrypted on this device.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = scriptApiKey,
                    onValueChange = { entered ->
                        scriptApiKey = entered
                        if (entered.isNotBlank()) {
                            onSaveScriptKey(entered)
                            activeScriptKey = entered.trim()
                            scriptKeySaved = true
                            notice = "APInex script key securely saved on this device."
                        }
                    },
                    label = { Text("APInex Script API key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text(if (scriptKeySaved) "✓ Script key saved securely." else "Required for Roman Hinglish script generation.", color = if (scriptKeySaved) Gold else Color.LightGray)
                OutlinedTextField(
                    value = voiceApiKey,
                    onValueChange = { entered ->
                        voiceApiKey = entered
                        if (entered.isNotBlank()) {
                            onSaveVoiceKey(entered)
                            activeVoiceKey = entered.trim()
                            voiceKeySaved = true
                            notice = "APInex presenter voice key securely saved on this device."
                        }
                    },
                    label = { Text("APInex Presenter Voice API key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text(if (voiceKeySaved) "✓ Presenter voice key saved securely." else "Required for APInex text-to-speech.", color = if (voiceKeySaved) Gold else Color.LightGray)
                Text("Script model: free/gemini-3.8-flash · Voice model: aura-2-thalia-en. Voice is converted locally to WAV for video rendering.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("04 · Optional match photos", color = Gold, fontWeight = FontWeight.Bold)
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
                Text("03 · Stages 2 and 3 — Video & YouTube package", color = Gold, fontWeight = FontWeight.Bold)
                Text("Stage 1: research + script  ·  Stage 2: voice + 16:9/9:16 MP4  ·  Stage 3: thumbnails + metadata + ZIP package", color = Color.LightGray)
                Text("05 · Production modules", color = Gold, fontWeight = FontWeight.Bold)
                listOf("Roman Hinglish commentary", "APInex presenter voice", "Stadium and team graphics", "Player cards and pitch report", "Head-to-head and match analysis", "9:16 and 16:9 video exports").forEach {
                    Text("✓  $it", color = Color.White)
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        when {
                            match.isBlank() -> notice = "Enter a match name to start."
                            iccUrl.isBlank() && cricbuzzUrl.isBlank() -> notice = "Add at least one ICC or Cricbuzz URL before starting research."
                            !(scriptKeySaved || scriptApiKey.isNotBlank()) -> notice = "Enter your APInex Script API key first."
                            !(voiceKeySaved || voiceApiKey.isNotBlank()) -> notice = "Enter your APInex Presenter Voice API key first."
                            else -> {
                                busy = true
                                notice = "STAGE 1/3 — Researching both match sources…"
                                scope.launch {
                                    try {
                                        val textKey = if (scriptApiKey.isNotBlank()) scriptApiKey.trim() else activeScriptKey
                                        val speechKey = if (voiceApiKey.isNotBlank()) voiceApiKey.trim() else activeVoiceKey
                                        val project = if (researchProjectPath.isNotBlank() && java.io.File(researchProjectPath).isDirectory)
                                            java.io.File(researchProjectPath) else LocalProjectStore(context).createProject(match)
                                        val report = researchReport ?: MatchResearchEngine().research(iccUrl, cricbuzzUrl, pitchSourceUrl, h2hSourceUrl).also {
                                            researchReport = it
                                            it.save(java.io.File(project, "research-report.json"))
                                            java.io.File(project, "match-research.txt").writeText(it.toPromptContext())
                                        }
                                        stageOneDone = true
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
                                        val apinex = ApinexClient(textKey, speechKey)
                                        val script = apinex.createScript(match, format, minutes, report.toPromptContext())
                                        LocalProjectStore(context).saveScript(project, match, format, script)
                                        notice = "STAGE 1/3 COMPLETE — research and script saved. STAGE 2/3 — generating APInex presenter voice…"
                                        val audio = apinex.createVoice(script, java.io.File(project, "voiceover.wav")) { progress ->
                                            notice = progress
                                        }
                                        val scenes = CricketSceneRenderer().defaultScenes(match, format)
                                        val photoAssets = ScenePhotoAssets.load(project)
                                        try {
                                            scenes.forEachIndexed { index, scene ->
                                                CricketSceneRenderer().savePreview(scene, java.io.File(project, "scene-${index + 1}.jpg"), photoAssets)
                                            }
                                        } finally {
                                            photoAssets.recycle()
                                        }
                                        notice = "STAGE 2/3 — rendering landscape and portrait MP4 on this phone…"
                                        val sceneFiles = scenes.indices.map { java.io.File(project, "scene-${it + 1}.jpg") }
                                        val renderer = FfmpegMp4Renderer()
                                        val landscape = renderer.render(project, audio, sceneFiles, false) { message ->
                                            notice = message
                                        }
                                        AndroidMp4Renderer(context).publishToMovies(landscape.file)
                                        notice = "Landscape MP4 saved. Rendering vertical 9:16 version with FFmpeg…"
                                        val vertical = renderer.render(project, audio, sceneFiles, true) { message ->
                                            notice = message
                                        }
                                        AndroidMp4Renderer(context).publishToMovies(vertical.file)
                                        stageTwoDone = true
                                        notice = "STAGE 2/3 COMPLETE — both videos exported. STAGE 3/3 — generating thumbnails and YouTube metadata…"
                                        val packageFiles = YouTubePackageBuilder().build(project, match, format, script, scenes)
                                        completedProjectPath = project.absolutePath
                                        stageThreeDone = true
                                        val job = StudioJob(project.name, match, format, minutes, "completed", 100,
                                            "All three stages complete; YouTube package generated", project.absolutePath)
                                        StudioJobStore(context).save(job)
                                        notice = "ALL 3 STAGES COMPLETE!\\n16:9 video: ${landscape.file.name}\\n9:16 video: ${vertical.file.name}\\nThumbnail: ${packageFiles.thumbnailLandscape.name}\\nMetadata: ${packageFiles.metadataFile.name}\\nPackage: ${packageFiles.packageZip.name}\\nVideos saved in Movies/Preet Sports.\\nProject: ${project.absolutePath}"
                                    } catch (e: Exception) {
                                        notice = "Production step failed: ${e.message ?: "Unknown error"}"
                                    } finally { busy = false }
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Running production stages…" else "Run all 3 production stages") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YouTube upload — optional", color = Gold, fontWeight = FontWeight.Bold)
                Text("Connect using a short-lived OAuth access token with the youtube.upload scope. Uploads are PRIVATE by default; review in YouTube Studio before making public.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = youtubeAccessToken, onValueChange = { youtubeAccessToken = it }, label = { Text("YouTube OAuth access token") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(enabled = !busy && completedProjectPath.isNotBlank() && youtubeAccessToken.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val project = java.io.File(completedProjectPath)
                            val metadata = java.io.File(project, "youtube-metadata.txt").readText()
                            val title = metadata.substringAfter("TITLE\\n").substringBefore("\\n\\nDESCRIPTION").trim()
                            val description = metadata.substringAfter("DESCRIPTION\\n").substringBefore("\\n\\nTAGS").trim()
                            val tags = metadata.substringAfter("TAGS\\n").substringBefore("\\n\\nSCRIPT")
                                .split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val result = YouTubeUploader().uploadPrivate(
                                java.io.File(project, "preet-sports-16x9.mp4"), title, description, tags, youtubeAccessToken.trim()
                            )
                            notice = "Uploaded privately: ${result.watchUrl}. Open YouTube Studio to review and publish."
                            youtubeAccessToken = ""
                        } catch (e: Exception) { notice = "YouTube upload failed: ${e.message ?: "Unknown error"}" }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Upload 16:9 video as PRIVATE") }
            }
        }
        Text(notice, color = Gold)
        Text("Stage 1: URL research + APInex script · Stage 2: APInex voice + on-device MP4 · Stage 3: thumbnail + metadata + ZIP. YouTube upload remains manual; review every fact and image licence before publishing.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
}

package `in`.preet.reelrecreator

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0B0D12)
private val Panel = Color(0xFF171B25)
private val Accent = Color(0xFF5965F2)
private val Muted = Color(0xFFAEB4C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PreetApp() }
    }
}

@Composable
private fun PreetApp() {
    var module by remember { mutableStateOf("cricket") }
    var referenceUri by remember { mutableStateOf<Uri?>(null) }
    var replacementAudioUri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf("same_song") }
    var matchUrl by remember { mutableStateOf("") }
    var matchScreenshots by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status by remember { mutableStateOf("Paste a match URL to prepare a Preet Sports analysis video.") }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> referenceUri = uri; if (uri != null) status = "Reference Reel selected." }

    val screenshotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> matchScreenshots = uris; if (uris.isNotEmpty()) status = "${uris.size} match screenshots selected." }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> replacementAudioUri = uri; if (uri != null) status = "Replacement song selected." }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Ink,
            surface = Panel,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("PREET", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("Preet Sports AI", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Text("Upcoming-match cricket pre-analysis, created from your phone.", color = Muted, fontSize = 14.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModuleTab("Upcoming Match Analysis", module == "cricket", Modifier.weight(1f)) { module = "cricket"; status = "Paste a match URL to prepare a Preet Sports analysis video." }
                    ModuleTab("Reel Recreator", module == "reel", Modifier.weight(1f)) { module = "reel"; status = "Choose a reference Reel from your phone." }
                }

                if (module == "cricket") {
                    CricketAnalysisModule(
                        matchUrl = matchUrl,
                        onMatchUrlChange = { matchUrl = it },
                        screenshotCount = matchScreenshots.size,
                        onPickScreenshots = { screenshotPicker.launch(arrayOf("image/*")) },
                        status = status,
                        onPrepare = {
                            status = when {
                                matchUrl.isBlank() -> "Paste a match URL first."
                                !matchUrl.startsWith("https://") && !matchUrl.startsWith("http://") -> "Please enter a valid http(s) match URL."
                                screenshotCount == 0 -> "Match URL entered. Attach CREX page screenshots too if the page cannot be read automatically."
                                else -> "Match URL and $screenshotCount screenshots selected. Connect the research and image-rendering API to generate graphics."
                            }
                        }
                    )
                } else {
                    ReelRecreatorModule(
                        referenceUri = referenceUri,
                        replacementAudioUri = replacementAudioUri,
                        mode = mode,
                        status = status,
                        onModeChange = { mode = it },
                        onPickVideo = { videoPicker.launch(arrayOf("video/*")) },
                        onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                        onPrepare = {
                            status = when {
                                referenceUri == null -> "Please select a reference Reel first."
                                mode == "new_song" && replacementAudioUri == null -> "Please select the replacement Punjabi song."
                                else -> "Files selected. Connect the cloud processing API before rendering can start."
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleTab(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Panel,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CricketAnalysisModule(
    matchUrl: String,
    onMatchUrlChange: (String) -> Unit,
    screenshotCount: Int,
    onPickScreenshots: () -> Unit,
    status: String,
    onPrepare: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            Text("Upcoming Match · YouTube Pre-Analysis", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Choose an upcoming fixture by pasting its ICC, Cricbuzz or CREX match URL. The research engine will verify match details before preparing your original Preet Sports analysis.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            OutlinedTextField(
                value = matchUrl,
                onValueChange = onMatchUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Upcoming match URL") },
                placeholder = { Text("https://...") },
                singleLine = false,
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Muted,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Accent,
                    unfocusedLabelColor = Muted,
                    cursorColor = Accent
                )
            )
            HorizontalDivider(color = Color(0xFF343A4A))
            Text("MATCH VISUALS FROM CREX", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Paste the match-details URL and attach saved page screenshots. The visual engine will turn verified facts into clean, original 16:9 graphics—not reuse CREX screenshots as video frames.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedButton(onClick = onPickScreenshots, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (screenshotCount == 0) "Attach match-page screenshots" else "Screenshots attached: $screenshotCount")
            }
            Text("PLANNED GRAPHIC SEQUENCE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            VisualSlideLine("01", "Match introduction", "Teams · format · venue · date")
            VisualSlideLine("02", "Pitch & venue report", "Innings averages · batting / bowling first")
            VisualSlideLine("03", "Weather conditions", "Cloud cover · rain chance · humidity")
            VisualSlideLine("04", "Team comparison", "Win rate · average score · highest / lowest")
            VisualSlideLine("05", "Head-to-head", "Previous meetings and series results")
            VisualSlideLine("06", "Recent team form", "Last five results")
            VisualSlideLine("07", "Key players & matchups", "Squad-confirmed player analysis")
            VisualSlideLine("08", "Fantasy analysis", "Role balance · risk · captaincy options")
            Text("Visual style: Preet Sports broadcast graphics · cinematic cricket backgrounds · readable data overlays.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Text("VIDEO SETTINGS", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            SettingLine("Content", "Analysis + Fantasy")
            SettingLine("Script", "Roman Hinglish")
            SettingLine("Narration", "Mithi v2 Flash · Indian male voice")
            SettingLine("Format", "16:9 · 1920 × 1080")
            SettingLine("Rendering", "Cloud FFmpeg")
            HorizontalDivider(color = Color(0xFF343A4A))
            Text("Planned automatic workflow", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Match research → verified facts → Gemini script → voiceover → scene plan → visuals → captions → final MP4", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Button(
                onClick = onPrepare,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Prepare Match Pre-Analysis", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Text(status, color = Muted, fontSize = 12.sp)
            Text("Original analysis only. Check source accuracy and use footage/music you are licensed to publish.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun VisualSlideLine(number: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Color(0xFF252B42), shape = RoundedCornerShape(8.dp)) {
            Text(number, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReelRecreatorModule(
    referenceUri: Uri?,
    replacementAudioUri: Uri?,
    mode: String,
    status: String,
    onModeChange: (String) -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPrepare: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            Text("Upload reference Reel", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Choose an MP4 video saved on this phone.", color = Muted, fontSize = 13.sp)
            OutlinedButton(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (referenceUri == null) "Choose video from phone" else "Change reference video")
            }
            Text(if (referenceUri == null) "No video selected" else "Reference video ready", color = if (referenceUri == null) Muted else Color(0xFF77D6A4), fontSize = 12.sp)
        }
    }
    Text("Choose recreation mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    ModeCard("Same song · New visuals", "Keep the reference audio and lyrics; create fresh aesthetic scenes and gentle zoom.", mode == "same_song") { onModeChange("same_song") }
    ModeCard("Same style · Different song", "Use the reference's visual language with a Punjabi song you choose.", mode == "new_song") { onModeChange("new_song") }
    if (mode == "new_song") {
        OutlinedButton(onClick = onPickAudio, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (replacementAudioUri == null) "Choose Punjabi song" else "Change Punjabi song")
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, tint = Accent, contentDescription = null)
            Text("Automatic aesthetic recreation", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("AI scene matching • Punjabi / Roman lyrics • soft 100–108% zoom • slow pan • crossfade • 9:16 export", color = Muted, fontSize = 13.sp)
        }
    }
    Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
        Text("Prepare my Reel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    Text(status, color = Muted, fontSize = 12.sp)
    Text("Use videos and music you own or are licensed to reuse.", color = Muted, fontSize = 11.sp)
}

@Composable
private fun ModeCard(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF20264A) else Panel),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Accent) else null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                RadioButton(selected = selected, onClick = onClick)
            }
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

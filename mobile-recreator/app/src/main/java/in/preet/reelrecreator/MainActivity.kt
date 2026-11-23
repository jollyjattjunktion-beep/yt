package `in`.preet.reelrecreator

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VideoLibrary
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
    var referenceUri by remember { mutableStateOf<Uri?>(null) }
    var replacementAudioUri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf("same_song") }
    var status by remember { mutableStateOf("Choose a Reel from your phone to begin.") }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> referenceUri = uri; if (uri != null) status = "Reference Reel selected." }

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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("PREET", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("Reel Recreator", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Turn a reference Reel into your own aesthetic edit.", color = Muted, fontSize = 14.sp)

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
                        Text("1. Upload reference Reel", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Pick an MP4 video saved on this phone. Uploading from Gallery or Files is the primary workflow.", color = Muted, fontSize = 13.sp)
                        OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (referenceUri == null) "Choose video from phone" else "Change reference video")
                        }
                        Text(if (referenceUri == null) "No video selected" else "Reference video ready", color = if (referenceUri == null) Muted else Color(0xFF77D6A4), fontSize = 12.sp)
                    }
                }

                Text("2. Choose recreation mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                ModeCard(
                    title = "Same song · New visuals",
                    description = "Keep the reference audio and lyrics; create fresh aesthetic scenes, typography and gentle zoom.",
                    selected = mode == "same_song",
                    onClick = { mode = "same_song" }
                )
                ModeCard(
                    title = "Same style · Different song",
                    description = "Use the reference's visual language with a Punjabi song you choose.",
                    selected = mode == "new_song",
                    onClick = { mode = "new_song" }
                )
                if (mode == "new_song") {
                    OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
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

                Button(
                    onClick = {
                        status = when {
                            referenceUri == null -> "Please select a reference Reel first."
                            mode == "new_song" && replacementAudioUri == null -> "Please select the replacement Punjabi song."
                            else -> "Files selected. Cloud processing service must be connected before rendering can start."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Prepare my Reel", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                Text(status, color = Muted, fontSize = 12.sp)
                Text("Output target: 1080 × 1920 · MP4 · 30 FPS", color = Muted, fontSize = 11.sp)
                Text("Use videos and music you own or are licensed to reuse.", color = Muted, fontSize = 11.sp)
            }
        }
    }
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

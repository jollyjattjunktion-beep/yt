package com.preetsports.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/** Copies a user-selected WAV into app-private storage; never uploads the recording. */
@Composable
fun VoiceClonePromptCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("No private voice prompt imported yet.") }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        status = if (uri == null) {
            "Voice prompt selection cancelled."
        } else {
            try {
                val mime = context.contentResolver.getType(uri).orEmpty()
                require(mime == "audio/wav" || mime == "audio/x-wav" ||
                    uri.toString().lowercase().endsWith(".wav")) {
                    "Please select a WAV recording."
                }
                val folder = File(context.filesDir, "voice-clone").apply { mkdirs() }
                val target = File(folder, "preet_voice_prompt.wav")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open selected recording." }
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                "Voice prompt copied privately (" + (target.length() / 1024) + " KB)."
            } catch (e: Exception) {
                "Import failed: " + (e.message ?: "unknown error")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OFFLINE HINDI VOICE", style = MaterialTheme.typography.titleMedium)
            Text("Import your own clean WAV voice prompt. It stays in this app's private storage.")
            Row {
                Button(onClick = { picker.launch(arrayOf("audio/wav", "audio/x-wav")) }) {
                    Text("Import my WAV")
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text(
                "Voice generation will be enabled after the Android ARM64 runtime is integrated.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

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
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Copies a user-selected WAV into app-private storage; never uploads the recording. */
@Composable
fun VoiceClonePromptCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("No private voice prompt imported yet.") }
    var modelStatus by remember { mutableStateOf("Hindi model pack not imported.") }
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

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        modelStatus = if (uri == null) {
            "Model pack selection cancelled."
        } else {
            try {
                val folder = File(context.filesDir, "pockettts-models")
                val staging = File(context.filesDir, "pockettts-models-staging")
                staging.deleteRecursively()
                require(staging.mkdirs() || staging.isDirectory) { "Could not create model staging folder." }
                var entries = 0
                var extractedBytes = 0L
                context.contentResolver.openInputStream(uri).use { raw ->
                    requireNotNull(raw) { "Could not open model ZIP." }
                    ZipInputStream(raw).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val name = entry.name.replace('\\', '/')
                            require(!name.startsWith("/") && !name.split('/').contains("..")) {
                                "Unsafe path in model ZIP."
                            }
                            val relative = if (name.startsWith("onnx/")) name.removePrefix("onnx/") else name
                            if (relative.isNotBlank() && !entry.isDirectory) {
                                val target = File(staging, relative)
                                require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                                    "Unsafe model file path."
                                }
                                target.parentFile?.mkdirs()
                                target.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        extractedBytes += count
                                        require(extractedBytes <= 512L * 1024L * 1024L) {
                                            "Model ZIP expands beyond the 512 MB safety limit."
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                                entries++
                            }
                            zip.closeEntry()
                        }
                    }
                }
                val required = listOf(
                    "mimi_encoder.onnx", "text_conditioner.onnx",
                    "flow_lm_main_int8.onnx", "flow_lm_flow_int8.onnx",
                    "mimi_decoder_int8.onnx", "tokenizer.model", "bos_before_voice.npy"
                )
                val missing = required.filterNot { File(staging, it).isFile }
                require(missing.isEmpty()) { "Model ZIP is incomplete: " + missing.joinToString() }

                // Verify every file listed by the publisher's SHA-256 manifest before activation.
                val manifest = File(staging, "SHA256SUMS")
                require(manifest.isFile) { "Model ZIP has no SHA256SUMS manifest." }
                val checksums = manifest.readLines().filter { it.isNotBlank() }
                require(checksums.isNotEmpty()) { "Model checksum manifest is empty." }
                var verified = 0
                checksums.forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    require(parts.size == 2 && parts[0].matches(Regex("[a-fA-F0-9]{64}"))) {
                        "Invalid checksum manifest entry."
                    }
                    val relativePath = parts[1].trim().removePrefix("*")
                    val candidate = File(staging, relativePath)
                    require(candidate.canonicalPath.startsWith(staging.canonicalPath + File.separator) &&
                        candidate.isFile) { "Missing or unsafe checksummed file: $relativePath" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    candidate.inputStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    require(actual.equals(parts[0], ignoreCase = true)) {
                        "Checksum failed for $relativePath"
                    }
                    verified++
                }
                folder.deleteRecursively()
                require(staging.renameTo(folder)) { "Could not activate imported model pack." }
                "Hindi model pack imported: $entries files, $verified SHA-256 checks passed."
            } catch (e: Exception) {
                File(context.filesDir, "pockettts-models-staging").deleteRecursively()
                "Model import failed: " + (e.message ?: "unknown error")
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
            Button(onClick = { modelPicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                Text("Import Hindi model ZIP")
            }
            Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            Text(
                "Model files stay on this device. Voice generation remains disabled until the native Android inference bridge is integrated and tested.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

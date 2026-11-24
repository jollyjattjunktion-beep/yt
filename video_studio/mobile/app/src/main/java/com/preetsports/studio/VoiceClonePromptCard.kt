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
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Accepts the exact PCM format used by the Hindi PocketTTS voice encoder. */
private fun validatePocketTtsWav(file: File) {
    require(file.length() in 44L..10L * 1024L * 1024L) {
        "WAV must be between 44 bytes and 10 MB."
    }
    RandomAccessFile(file, "r").use { wav ->
        fun readFourCc(): String {
            val bytes = ByteArray(4)
            wav.readFully(bytes)
            return String(bytes, Charsets.US_ASCII)
        }
        require(readFourCc() == "RIFF") { "File is not a RIFF WAV." }
        wav.skipBytes(4)
        require(readFourCc() == "WAVE") { "File is not a WAV audio stream." }
        var format: Int? = null
        var channels: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var dataBytes: Long? = null
        while (wav.filePointer + 8 <= wav.length()) {
            val chunk = readFourCc()
            val size = Integer.toUnsignedLong(Integer.reverseBytes(wav.readInt()))
            val start = wav.filePointer
            when (chunk) {
                "fmt " -> if (size >= 16 && start + size <= wav.length()) {
                    format = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(wav.readShort()))
                    channels = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(wav.readShort()))
                    sampleRate = Integer.reverseBytes(wav.readInt())
                    wav.skipBytes(6)
                    bitsPerSample = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(wav.readShort()))
                }
                "data" -> dataBytes = size
            }
            val next = start + size + (size and 1L)
            if (next > wav.length()) break
            wav.seek(next)
        }
        require(format == 1 && channels == 1 && sampleRate == 24000 && bitsPerSample == 16) {
            "Use PCM 16-bit, mono, 24 kHz WAV audio."
        }
        val durationSeconds = (dataBytes ?: 0L) / (24000L * 2L)
        require(durationSeconds in 5L..30L) {
            "Voice prompt must contain 5–30 seconds of audio."
        }
    }
}

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
                val temporary = File(folder, "preet_voice_prompt.wav.tmp")
                temporary.delete()
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open selected recording." }
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                try {
                    validatePocketTtsWav(temporary)
                    require(temporary.renameTo(target)) { "Could not save validated voice prompt." }
                } catch (e: Exception) {
                    temporary.delete()
                    throw e
                }
                "Private voice prompt verified: PCM 16-bit, mono, 24 kHz."
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
                val manifestPaths = checksums.map { it.trim().split(Regex("\\s+"), limit = 2).getOrNull(1)?.trim()?.removePrefix("*") }
                require(required.all { it in manifestPaths }) {
                    "Checksum manifest does not cover every required model file."
                }
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

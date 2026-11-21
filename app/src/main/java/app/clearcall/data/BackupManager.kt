package app.clearcall.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreSummary(val restored: Int, val alreadyPresent: Int, val missingAudio: Int)

/**
 * Backups are a single ZIP the user saves wherever they choose through the system file picker
 * (manifest.json + audio/). ClearCall itself never uploads anything; the ZIP is NOT encrypted,
 * so it should be stored somewhere the user trusts.
 */
class BackupManager(
    private val context: Context,
    private val repository: RecordingRepository,
) {

    suspend fun export(destination: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val all = repository.getAll()
            val output = context.contentResolver.openOutputStream(destination) ?: error("Cannot open destination")
            output.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    zip.write(buildManifest(all).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    for (recording in all) {
                        val file = repository.fileFor(recording)
                        if (!file.exists()) continue
                        zip.putNextEntry(ZipEntry("$AUDIO_DIR${recording.fileName}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            all.size
        }
    }

    suspend fun restore(source: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(source) ?: error("Cannot open backup")
            var manifest: List<RecordingEntity> = emptyList()
            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name == MANIFEST) {
                            manifest = parseManifest(String(zip.readBytes(), Charsets.UTF_8))
                        } else if (name.startsWith(AUDIO_DIR) && !entry.isDirectory) {
                            // Only the base name is used, so a hostile archive cannot write elsewhere.
                            val safeName = File(name).name
                            if (isSafeFileName(safeName)) {
                                val target = File(repository.recordingsDir, safeName)
                                if (!target.exists()) {
                                    FileOutputStream(target).use { out -> zip.copyTo(out) }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            var restored = 0
            var present = 0
            var missing = 0
            for (item in manifest) {
                val file = File(repository.recordingsDir, item.fileName)
                when {
                    repository.get(item.id) != null -> present++
                    !file.exists() -> missing++
                    else -> {
                        repository.insert(item.copy(sizeBytes = file.length()))
                        restored++
                    }
                }
            }
            RestoreSummary(restored, present, missing)
        }
    }

    private fun buildManifest(items: List<RecordingEntity>): String {
        val array = JSONArray()
        for (r in items) {
            array.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("fileName", r.fileName)
                    put("startedAt", r.startedAt)
                    put("durationMs", r.durationMs)
                    put("sizeBytes", r.sizeBytes)
                    put("format", r.format)
                    put("sampleRate", r.sampleRate)
                    put("bitrate", r.bitrate)
                    put("direction", r.direction.name)
                    put("phoneNumber", r.phoneNumber ?: JSONObject.NULL)
                    put("contactName", r.contactName ?: JSONObject.NULL)
                    put("customTitle", r.customTitle ?: JSONObject.NULL)
                    put("captureMode", r.captureMode.name)
                    put("silent", r.silent)
                    put("starred", r.starred)
                },
            )
        }
        return JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("recordings", array)
        }.toString()
    }

    private fun parseManifest(json: String): List<RecordingEntity> {
        val array = JSONObject(json).optJSONArray("recordings") ?: return emptyList()
        val result = ArrayList<RecordingEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val fileName = o.optString("fileName")
            val id = o.optString("id")
            val format = o.optString("format")
            if (!isSafeFileName(fileName) || !isSafeFileName(id) || (format != "m4a" && format != "wav")) continue
            result += RecordingEntity(
                id = id,
                fileName = fileName,
                startedAt = o.optLong("startedAt"),
                durationMs = o.optLong("durationMs"),
                sizeBytes = o.optLong("sizeBytes"),
                format = format,
                sampleRate = o.optInt("sampleRate"),
                bitrate = o.optInt("bitrate"),
                direction = CallDirection.entries.firstOrNull { it.name == o.optString("direction") }
                    ?: CallDirection.UNKNOWN,
                phoneNumber = o.nullableString("phoneNumber"),
                contactName = o.nullableString("contactName"),
                customTitle = o.nullableString("customTitle"),
                captureMode = CaptureMode.entries.firstOrNull { it.name == o.optString("captureMode") }
                    ?: CaptureMode.UNKNOWN,
                silent = o.optBoolean("silent"),
                starred = o.optBoolean("starred"),
            )
        }
        return result
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val AUDIO_DIR = "audio/"
    }
}

private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

/** Allows only plain file names: no separators, no leading dots, no "..". */
internal fun isSafeFileName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 100 && SAFE_NAME.matches(name) && !name.contains("..")

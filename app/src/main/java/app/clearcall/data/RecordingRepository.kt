package app.clearcall.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.clearcall.recording.AudioMetadata
import app.clearcall.recording.WavHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for recordings. Audio lives in app-private internal storage
 * (`filesDir/recordings`): inaccessible to other apps, encrypted at rest by Android's
 * file-based encryption, and never uploaded anywhere.
 */
class RecordingRepository(
    private val context: Context,
    private val dao: RecordingDao,
) {
    val recordingsDir: File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()

    fun observe(id: String): Flow<RecordingEntity?> = dao.observe(id)

    suspend fun get(id: String): RecordingEntity? = dao.get(id)

    suspend fun getAll(): List<RecordingEntity> = dao.getAll()

    fun fileFor(recording: RecordingEntity): File = File(recordingsDir, recording.fileName)

    suspend fun insert(recording: RecordingEntity) = dao.upsert(recording)

    suspend fun rename(id: String, title: String?) =
        dao.rename(id, title?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun setStarred(id: String, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun updateCallInfo(id: String, number: String?, name: String?, direction: CallDirection) =
        dao.updateCallInfo(id, number, name, direction)

    suspend fun delete(recording: RecordingEntity) = withContext(Dispatchers.IO) {
        fileFor(recording).delete()
        dao.delete(recording.id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.getAll().forEach { fileFor(it).delete() }
        dao.deleteAll()
        recordingsDir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    /** Deletes unstarred recordings older than [days]. 0 disables retention. */
    suspend fun applyRetention(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        dao.getUnstarredOlderThan(cutoff).forEach { delete(it) }
    }

    /** Copies the audio to a location the user picked through the system file picker. */
    suspend fun exportTo(recording: RecordingEntity, destination: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = fileFor(recording)
                context.contentResolver.openOutputStream(destination)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Cannot open destination")
                true
            }.getOrDefault(false)
        }

    /** Read-only content:// URI for the system share sheet. Only granted per share action. */
    fun shareUri(recording: RecordingEntity): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileFor(recording))

    /**
     * If the process died mid-recording the audio file exists but has no database row.
     * Adopt playable orphans so nothing recorded is silently lost.
     */
    suspend fun recoverOrphans() = withContext(Dispatchers.IO) {
        val known = dao.getAll().map { it.fileName }.toSet()
        val now = System.currentTimeMillis()
        recordingsDir.listFiles()?.forEach { file ->
            if (!file.isFile || file.name in known) return@forEach
            val ext = file.extension.lowercase()
            if (ext != "m4a" && ext != "wav") return@forEach
            // A very recent file may belong to a recording that is still in progress.
            if (now - file.lastModified() < 60_000) return@forEach
            if (ext == "wav") WavHeader.repairInPlace(file)
            val duration = AudioMetadata.durationMs(file) ?: return@forEach
            if (duration < 1_000) return@forEach
            dao.upsert(
                RecordingEntity(
                    id = file.nameWithoutExtension,
                    fileName = file.name,
                    startedAt = file.lastModified() - duration,
                    durationMs = duration,
                    sizeBytes = file.length(),
                    format = ext,
                    sampleRate = 0,
                    bitrate = 0,
                    direction = CallDirection.UNKNOWN,
                    phoneNumber = null,
                    contactName = null,
                    customTitle = null,
                    captureMode = CaptureMode.UNKNOWN,
                    silent = false,
                ),
            )
        }
    }
}

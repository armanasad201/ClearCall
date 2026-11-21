package app.clearcall.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class CallDirection { INCOMING, OUTGOING, UNKNOWN }

/**
 * What the recording was actually captured from. Stored per recording so the UI never
 * overstates what a file contains.
 */
enum class CaptureMode {
    /** Telephony call stream: both sides. Only possible on devices that grant CAPTURE_AUDIO_OUTPUT. */
    CALL_AUDIO,

    /** Device microphone: your voice, plus the other party only if audible (e.g. speakerphone). */
    MICROPHONE,

    /** Recovered file whose origin is unknown. */
    UNKNOWN,
}

@Entity(tableName = "recordings", indices = [Index(value = ["startedAt"])])
data class RecordingEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val startedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    /** "m4a" or "wav". */
    val format: String,
    val sampleRate: Int,
    /** Bits per second; for WAV this is the PCM data rate. 0 if unknown. */
    val bitrate: Int,
    val direction: CallDirection,
    val phoneNumber: String?,
    val contactName: String?,
    val customTitle: String?,
    val captureMode: CaptureMode,
    /** True when no meaningful audio was detected (device most likely blocked capture). */
    val silent: Boolean,
    val starred: Boolean = false,
)

class Converters {
    @TypeConverter
    fun directionToString(value: CallDirection): String = value.name

    @TypeConverter
    fun stringToDirection(value: String): CallDirection =
        CallDirection.entries.firstOrNull { it.name == value } ?: CallDirection.UNKNOWN

    @TypeConverter
    fun captureModeToString(value: CaptureMode): String = value.name

    @TypeConverter
    fun stringToCaptureMode(value: String): CaptureMode =
        CaptureMode.entries.firstOrNull { it.name == value } ?: CaptureMode.UNKNOWN
}

val RecordingEntity.displayTitle: String
    get() = customTitle?.takeIf { it.isNotBlank() }
        ?: contactName?.takeIf { it.isNotBlank() }
        ?: phoneNumber?.takeIf { it.isNotBlank() }
        ?: "Unknown caller"

val RecordingEntity.mimeType: String
    get() = if (format == "wav") "audio/wav" else "audio/mp4"

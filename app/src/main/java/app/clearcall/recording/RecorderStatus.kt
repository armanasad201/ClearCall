package app.clearcall.recording

import app.clearcall.data.CallDirection
import app.clearcall.data.CaptureMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live recorder state, surfaced on the dashboard so the user always knows what is happening. */
sealed interface RecorderStatus {
    data object Idle : RecorderStatus

    data class Recording(
        val startedAt: Long,
        val mode: CaptureMode,
        val direction: CallDirection,
    ) : RecorderStatus

    data class Failed(val reason: String, val at: Long) : RecorderStatus
}

class RecordingStateHolder {
    private val _status = MutableStateFlow<RecorderStatus>(RecorderStatus.Idle)
    val status: StateFlow<RecorderStatus> = _status.asStateFlow()

    fun set(value: RecorderStatus) {
        _status.value = value
    }
}

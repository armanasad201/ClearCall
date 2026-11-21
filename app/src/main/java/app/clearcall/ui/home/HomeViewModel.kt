package app.clearcall.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clearcall.AppContainer
import app.clearcall.core.AppPermissions
import app.clearcall.data.AppSettings
import app.clearcall.data.CaptureMode
import app.clearcall.data.RecordMode
import app.clearcall.data.RecordingEntity
import app.clearcall.recording.RecorderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val loaded: Boolean = false,
    val recent: List<RecordingEntity> = emptyList(),
    val totalCount: Int = 0,
    val totalDurationMs: Long = 0,
    val totalBytes: Long = 0,
    val settings: AppSettings = AppSettings(),
    val status: RecorderStatus = RecorderStatus.Idle,
    val missingCore: List<String> = emptyList(),
    val notificationsBlocked: Boolean = false,
    /** What a recording would be captured from right now, or null if nothing can be. */
    val captureMode: CaptureMode? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Bumped on resume so permission changes made in system settings are picked up. */
    private val refreshTick = MutableStateFlow(0)

    val state: StateFlow<HomeState> = combine(
        container.recordings.observeAll(),
        container.settings.settings,
        container.recordingState.status,
        refreshTick,
    ) { recordings, settings, status, _ ->
        val caps = container.capabilities.evaluate()
        HomeState(
            loaded = true,
            recent = recordings.take(5),
            totalCount = recordings.size,
            totalDurationMs = recordings.sumOf { it.durationMs },
            totalBytes = recordings.sumOf { it.sizeBytes },
            settings = settings,
            status = status,
            missingCore = AppPermissions.missingCore(container.context),
            notificationsBlocked = !AppPermissions.notificationsEnabled(container.context),
            captureMode = when {
                caps.callAudioCapture -> CaptureMode.CALL_AUDIO
                settings.allowMicFallback -> CaptureMode.MICROPHONE
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refresh() {
        refreshTick.update { it + 1 }
    }

    fun setRecordMode(mode: RecordMode) {
        viewModelScope.launch { container.settings.setRecordMode(mode) }
    }
}

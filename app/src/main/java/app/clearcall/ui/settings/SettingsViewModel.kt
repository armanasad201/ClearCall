package app.clearcall.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clearcall.AppContainer
import app.clearcall.data.AppSettings
import app.clearcall.data.RecordMode
import app.clearcall.data.RecordingQuality
import app.clearcall.data.ThemeMode
import app.clearcall.recording.DeviceCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /** Null until the first read from disk, so the screen never flashes default values. */
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn<AppSettings?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recordingCount: StateFlow<Int> = container.recordings.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun capabilities(): DeviceCapabilities = container.capabilities.evaluate()

    fun setRecordMode(value: RecordMode) = run { container.settings.setRecordMode(value) }
    fun setRecordIncoming(value: Boolean) = run { container.settings.setRecordIncoming(value) }
    fun setRecordOutgoing(value: Boolean) = run { container.settings.setRecordOutgoing(value) }
    fun setQuality(value: RecordingQuality) = run { container.settings.setQuality(value) }
    fun setAllowMicFallback(value: Boolean) = run { container.settings.setAllowMicFallback(value) }
    fun setThemeMode(value: ThemeMode) = run { container.settings.setThemeMode(value) }
    fun setRetentionDays(value: Int) = run { container.settings.setRetentionDays(value) }

    /** The caller has already verified the user with biometrics/device credentials. */
    fun setAppLock(enabled: Boolean) {
        // Mark unlocked first so enabling the lock doesn't immediately lock the user out.
        if (enabled) container.appLock.markUnlocked()
        run { container.settings.setAppLock(enabled) }
    }

    // Long-running file operations use the app scope so leaving the screen can't cut a ZIP short.
    fun exportBackup(destination: Uri, onDone: (String) -> Unit) {
        container.appScope.launch {
            val result = container.backup.export(destination)
            val message = result.fold(
                onSuccess = { count -> if (count == 1) "Backed up 1 recording" else "Backed up $count recordings" },
                onFailure = { "Backup failed: ${it.message ?: "unknown error"}" },
            )
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun restoreBackup(source: Uri, onDone: (String) -> Unit) {
        container.appScope.launch {
            val result = container.backup.restore(source)
            val message = result.fold(
                onSuccess = { s ->
                    buildString {
                        append("Restored ${s.restored}")
                        if (s.alreadyPresent > 0) append(", ${s.alreadyPresent} already here")
                        if (s.missingAudio > 0) append(", ${s.missingAudio} missing audio")
                    }
                },
                onFailure = { "Restore failed: that doesn't look like a ClearCall backup" },
            )
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    fun deleteAllRecordings(onDone: () -> Unit) {
        container.appScope.launch {
            container.recordings.deleteAll()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

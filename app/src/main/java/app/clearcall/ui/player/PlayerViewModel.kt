package app.clearcall.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clearcall.AppContainer
import app.clearcall.data.RecordingEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface PlayerUi {
    data object Loading : PlayerUi
    data object Missing : PlayerUi
    data class Ready(val recording: RecordingEntity) : PlayerUi
}

class PlayerViewModel(
    private val container: AppContainer,
    private val id: String,
) : ViewModel() {

    private val repository get() = container.recordings

    val ui: StateFlow<PlayerUi> = repository.observe(id)
        .map { entity -> if (entity == null) PlayerUi.Missing else PlayerUi.Ready(entity) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUi.Loading)

    fun file(recording: RecordingEntity): File = repository.fileFor(recording)

    fun shareUri(recording: RecordingEntity): Uri = repository.shareUri(recording)

    fun rename(recording: RecordingEntity, title: String) {
        viewModelScope.launch { repository.rename(recording.id, title) }
    }

    fun toggleStar(recording: RecordingEntity) {
        viewModelScope.launch { repository.setStarred(recording.id, !recording.starred) }
    }

    fun delete(recording: RecordingEntity) {
        // Run on the app scope so deletion finishes even if the screen is popped immediately.
        container.appScope.launch { repository.delete(recording) }
    }

    fun export(recording: RecordingEntity, destination: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.exportTo(recording, destination)) }
    }
}

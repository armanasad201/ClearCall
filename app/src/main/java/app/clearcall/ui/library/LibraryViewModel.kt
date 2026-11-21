package app.clearcall.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.clearcall.AppContainer
import app.clearcall.core.Format
import app.clearcall.data.CallDirection
import app.clearcall.data.RecordingEntity
import app.clearcall.data.displayTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class LibraryFilter(val label: String) {
    ALL("All"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    STARRED("Starred"),
}

enum class LibrarySort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    LONGEST("Longest first"),
}

data class LibraryGroup(val title: String, val items: List<RecordingEntity>)

data class LibraryState(
    val loaded: Boolean = false,
    val totalCount: Int = 0,
    val groups: List<LibraryGroup> = emptyList(),
    val resultCount: Int = 0,
)

class LibraryViewModel(container: AppContainer) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LibraryFilter.ALL)
    val sort = MutableStateFlow(LibrarySort.NEWEST)

    val state: StateFlow<LibraryState> = combine(
        container.recordings.observeAll(),
        query,
        filter,
        sort,
    ) { all, q, f, s ->
        val needle = q.trim().lowercase()
        val digits = needle.filter { it.isDigit() }

        val matching = all.filter { r ->
            val passesFilter = when (f) {
                LibraryFilter.ALL -> true
                LibraryFilter.INCOMING -> r.direction == CallDirection.INCOMING
                LibraryFilter.OUTGOING -> r.direction == CallDirection.OUTGOING
                LibraryFilter.STARRED -> r.starred
            }
            val passesSearch = needle.isEmpty() ||
                r.displayTitle.lowercase().contains(needle) ||
                (r.contactName?.lowercase()?.contains(needle) == true) ||
                (r.phoneNumber?.lowercase()?.contains(needle) == true) ||
                (digits.isNotEmpty() && r.phoneNumber?.filter { it.isDigit() }?.contains(digits) == true)
            passesFilter && passesSearch
        }

        val groups = when (s) {
            LibrarySort.NEWEST -> groupByDay(matching.sortedByDescending { it.startedAt })
            LibrarySort.OLDEST -> groupByDay(matching.sortedBy { it.startedAt })
            LibrarySort.LONGEST -> listOf(
                LibraryGroup("Longest first", matching.sortedByDescending { it.durationMs }),
            ).filter { it.items.isNotEmpty() }
        }

        LibraryState(
            loaded = true,
            totalCount = all.size,
            groups = groups,
            resultCount = matching.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    private fun groupByDay(items: List<RecordingEntity>): List<LibraryGroup> =
        items.groupBy { Format.dayLabel(it.startedAt) }
            .map { (label, list) -> LibraryGroup(label, list) }
}

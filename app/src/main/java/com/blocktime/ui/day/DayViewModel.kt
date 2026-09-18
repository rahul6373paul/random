package com.blocktime.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blocktime.auth.AuthRepository
import com.blocktime.auth.AuthState
import com.blocktime.data.repository.CalendarRepository
import com.blocktime.data.repository.SettingsStore
import com.blocktime.data.repository.UserSettings
import com.blocktime.domain.BlockDraft
import com.blocktime.domain.CalendarEvent
import com.blocktime.domain.CalendarInfo
import com.blocktime.domain.FreeSlot
import com.blocktime.domain.Scheduling
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class DayUiState(
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val events: List<CalendarEvent> = emptyList(),
    val freeSlots: List<FreeSlot> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val bookedDuration: Duration = Duration.ZERO,
    val freeDuration: Duration = Duration.ZERO,
    val error: String? = null,
    val message: String? = null,
) {
    val timedEvents: List<CalendarEvent> get() = events.filterNot { it.allDay }
    val allDayEvents: List<CalendarEvent> get() = events.filter { it.allDay }
    val writableCalendars: List<CalendarInfo> get() = calendars.filter { it.canWrite }
    val isToday: Boolean get() = date == LocalDate.now()
}

/**
 * Drives the day screen: loads the selected day from every visible calendar, derives free
 * slots from the user's working hours, and writes blocks back to Google.
 */
class DayViewModel(
    private val calendarRepository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(DayUiState())
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var calendarsLoaded = false

    init {
        viewModelScope.launch {
            settingsStore.settings.collectLatest { settings ->
                val previous = _state.value.settings
                _state.value = _state.value.copy(settings = settings)
                if (!calendarsLoaded) {
                    loadCalendars(settings)
                } else if (settings.visibleCalendarIds != previous.visibleCalendarIds ||
                    settings.dayStart != previous.dayStart ||
                    settings.dayEnd != previous.dayEnd
                ) {
                    reload()
                }
            }
        }
        viewModelScope.launch {
            // A reconnect (or a token recovered at startup) should populate the screen.
            authRepository.state.collectLatest { authState ->
                if (authState is AuthState.Authorized && !calendarsLoaded) {
                    loadCalendars(_state.value.settings)
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (date == _state.value.date) return
        _state.value = _state.value.copy(date = date)
        reload()
    }

    fun goToToday() = selectDate(LocalDate.now())

    fun shiftDays(days: Long) = selectDate(_state.value.date.plusDays(days))

    fun refresh() {
        calendarsLoaded = false
        viewModelScope.launch { loadCalendars(_state.value.settings) }
    }

    fun toggleCalendar(calendarId: String) {
        val current = _state.value.settings.visibleCalendarIds
        val updated = if (calendarId in current) current - calendarId else current + calendarId
        viewModelScope.launch { settingsStore.setVisibleCalendars(updated) }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    /** Existing events that would clash with a candidate block, for the live conflict warning. */
    fun conflictsFor(start: ZonedDateTime, end: ZonedDateTime, ignoreEventId: String?): List<CalendarEvent> =
        Scheduling.conflicts(_state.value.events, start, end, ignoreEventId)

    fun createBlock(draft: BlockDraft) {
        viewModelScope.launch {
            runCatching { calendarRepository.createBlock(draft) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "Blocked \"${draft.title}\"")
                    reload()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun updateBlock(eventId: String, draft: BlockDraft) {
        viewModelScope.launch {
            runCatching { calendarRepository.updateBlock(eventId, draft) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "Updated \"${draft.title}\"")
                    reload()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch {
            runCatching { calendarRepository.deleteEvent(event.calendarId, event.id) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        events = _state.value.events.filterNot { it.id == event.id },
                        message = "Deleted \"${event.title}\"",
                    )
                    reload()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    private suspend fun loadCalendars(settings: UserSettings) {
        if (authRepository.state.value !is AuthState.Authorized) return
        runCatching { calendarRepository.calendars() }
            .onSuccess { calendars ->
                calendarsLoaded = true
                _state.value = _state.value.copy(calendars = calendars, error = null)
                if (!settings.initialized && calendars.isNotEmpty()) {
                    // First run: show the user's own calendars and write to the primary one.
                    val own = calendars.filter { it.canWrite }.map { it.id }.toSet()
                    val primary = calendars.firstOrNull { it.primary }?.id ?: own.firstOrNull()
                    settingsStore.initializeWith(own, primary)
                } else {
                    reload()
                }
            }
            .onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val current = _state.value
            val settings = current.settings
            val calendarIds = settings.visibleCalendarIds.ifEmpty {
                current.calendars.filter { it.primary }.map { it.id }.toSet()
            }
            if (calendarIds.isEmpty()) {
                _state.value = current.copy(loading = false, events = emptyList(), freeSlots = emptyList())
                return@launch
            }

            _state.value = current.copy(loading = true)
            val dayStart = current.date.atStartOfDay(zone)
            val dayEnd = dayStart.plusDays(1)

            runCatching { calendarRepository.events(calendarIds, dayStart, dayEnd) }
                .onSuccess { events -> _state.value = buildLoadedState(events) }
                .onFailure { error ->
                    _state.value = _state.value.copy(loading = false, error = error.message)
                }
        }
    }

    private fun buildLoadedState(events: List<CalendarEvent>): DayUiState {
        val current = _state.value
        val anchor = current.date.atStartOfDay(zone)
        val (windowStart, windowEnd) = Scheduling.workingWindow(
            anchor,
            current.settings.dayStart,
            current.settings.dayEnd,
        )
        val freeSlots = Scheduling.freeSlots(events, windowStart, windowEnd, MIN_FREE_SLOT)
        val booked = Scheduling.bookedDuration(events, windowStart, windowEnd)
        return current.copy(
            loading = false,
            events = events,
            freeSlots = freeSlots,
            bookedDuration = booked,
            freeDuration = freeSlots.fold(Duration.ZERO) { acc, slot -> acc + slot.duration },
            error = null,
        )
    }

    companion object {
        private val MIN_FREE_SLOT: Duration = Duration.ofMinutes(15)

        fun factory(
            calendarRepository: CalendarRepository,
            settingsStore: SettingsStore,
            authRepository: AuthRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DayViewModel(calendarRepository, settingsStore, authRepository) as T
        }
    }
}

package com.blocktime.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocktime.domain.CalendarEvent
import com.blocktime.domain.Scheduling
import com.blocktime.ui.block.BlockSheet
import com.blocktime.ui.block.BlockSheetInput
import com.blocktime.ui.common.EmptyState
import com.blocktime.util.DateTimeFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    viewModel: DayViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val zone = remember { ZoneId.systemDefault() }

    var sheetInput by remember { mutableStateOf<BlockSheetInput?>(null) }
    var detailEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.dismissMessage()
        }
    }

    fun openSheet(start: ZonedDateTime, end: ZonedDateTime, editing: CalendarEvent? = null) {
        sheetInput = BlockSheetInput(
            date = state.date,
            start = start,
            end = end,
            calendars = state.calendars,
            defaultCalendarId = state.settings.defaultCalendarId,
            templates = state.settings.templates,
            defaultReminderMinutes = state.settings.defaultReminderMinutes,
            editing = editing,
        )
    }

    /** The FAB starts from the next sensible gap rather than from "now" mid-meeting. */
    fun suggestedStart(): ZonedDateTime {
        val defaultDuration = Duration.ofMinutes(state.settings.defaultDurationMinutes.toLong())
        val anchor = state.date.atStartOfDay(zone)
        val (windowStart, windowEnd) = Scheduling.workingWindow(
            anchor,
            state.settings.dayStart,
            state.settings.dayEnd,
        )
        val from = if (state.isToday) {
            maxOf(Scheduling.roundUpTo(ZonedDateTime.now(zone)), windowStart)
        } else {
            windowStart
        }
        return Scheduling.nextFreeSlot(state.events, from, defaultDuration, windowEnd)?.start ?: from
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            DateTimeFormat.relativeDayLabel(state.date, LocalDate.now())
                                ?: DateTimeFormat.weekdayName(state.date),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            DateTimeFormat.shortDate(state.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.shiftDays(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shiftDays(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val start = suggestedStart()
                    openSheet(start, start.plusMinutes(state.settings.defaultDurationMinutes.toLong()))
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Block time") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            DateStrip(
                selectedDate = state.date,
                onSelectDate = viewModel::selectDate,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (!state.isToday) {
                TextButton(
                    onClick = viewModel::goToToday,
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text("Jump to today") }
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.calendars.size > 1) {
                CalendarFilterRow(
                    state = state,
                    onToggle = viewModel::toggleCalendar,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            val items = remember(state.events, state.freeSlots) {
                buildDayItems(state.events, state.freeSlots)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    DaySummaryBar(
                        booked = state.bookedDuration,
                        free = state.freeDuration,
                        eventCount = state.timedEvents.size,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (state.allDayEvents.isNotEmpty()) {
                    item { AllDayRow(state.allDayEvents, Modifier.padding(bottom = 8.dp)) }
                }

                if (!state.loading && items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.EventAvailable,
                            title = "Nothing booked",
                            subtitle = "Your ${DateTimeFormat.time(state.date.atTime(state.settings.dayStart).atZone(zone))}–" +
                                "${DateTimeFormat.time(state.date.atTime(state.settings.dayEnd).atZone(zone))} " +
                                "window is completely free. Tap Block time to claim a slot.",
                        )
                    }
                }

                items(items, key = { item ->
                    when (item) {
                        is DayItem.Event -> "event-${item.event.calendarId}-${item.event.id}-${item.event.start}"
                        is DayItem.Free -> "free-${item.slot.start}"
                    }
                }) { item ->
                    when (item) {
                        is DayItem.Event -> EventRow(
                            event = item.event,
                            onClick = { detailEvent = item.event },
                        )
                        is DayItem.Free -> FreeSlotRow(
                            slot = item.slot,
                            onBlock = {
                                val duration = minOf(
                                    item.slot.duration,
                                    Duration.ofMinutes(state.settings.defaultDurationMinutes.toLong()),
                                )
                                openSheet(item.slot.start, item.slot.start.plus(duration))
                            },
                        )
                    }
                }
            }
        }
    }

    sheetInput?.let { input ->
        BlockSheet(
            input = input,
            conflictsFor = viewModel::conflictsFor,
            onDismiss = { sheetInput = null },
            onSave = { draft ->
                val editingId = input.editing?.id
                if (editingId == null) viewModel.createBlock(draft) else viewModel.updateBlock(editingId, draft)
                sheetInput = null
            },
        )
    }

    detailEvent?.let { event ->
        EventDetailSheet(
            event = event,
            calendars = state.calendars,
            onDismiss = { detailEvent = null },
            onEdit = {
                detailEvent = null
                openSheet(it.start, it.end, editing = it)
            },
            onDelete = {
                detailEvent = null
                viewModel.deleteEvent(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarFilterRow(
    state: DayUiState,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.calendars, key = { it.id }) { calendar ->
            FilterChip(
                selected = calendar.id in state.settings.visibleCalendarIds,
                onClick = { onToggle(calendar.id) },
                label = { Text(calendar.title, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

package com.blocktime.ui.block

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.blocktime.domain.BlockDraft
import com.blocktime.domain.CalendarEvent
import com.blocktime.domain.CalendarInfo
import com.blocktime.domain.GoogleEventColor
import com.blocktime.domain.Repeat
import com.blocktime.domain.TaskTemplate
import com.blocktime.ui.common.ColorSwatch
import com.blocktime.util.DateTimeFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Everything the sheet needs to open, whether for a new block or an edit. */
data class BlockSheetInput(
    val date: LocalDate,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val calendars: List<CalendarInfo>,
    val defaultCalendarId: String?,
    val templates: List<TaskTemplate>,
    val defaultReminderMinutes: Int,
    /** Non-null when editing an existing block rather than creating one. */
    val editing: CalendarEvent? = null,
)

private val DURATION_CHOICES = listOf(15, 30, 45, 60, 90, 120)

/**
 * The bottom sheet used to block time. It keeps its own draft state and reports conflicts
 * live via [conflictsFor] so the user sees a clash before saving, not after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSheet(
    input: BlockSheetInput,
    conflictsFor: (ZonedDateTime, ZonedDateTime, String?) -> List<CalendarEvent>,
    onDismiss: () -> Unit,
    onSave: (BlockDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editing = input.editing

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var startTime by remember { mutableStateOf(input.start.toLocalTime().withSecond(0).withNano(0)) }
    var durationMinutes by remember {
        mutableStateOf(Duration.between(input.start, input.end).toMinutes().toInt().coerceAtLeast(5))
    }
    var calendarId by remember {
        mutableStateOf(
            editing?.calendarId
                ?: input.defaultCalendarId
                ?: input.calendars.firstOrNull { it.canWrite }?.id
                ?: "primary",
        )
    }
    var color by remember { mutableStateOf(GoogleEventColor.fromId(editing?.colorId)) }
    var busy by remember { mutableStateOf(editing?.busy ?: true) }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var repeatUntil by remember { mutableStateOf<LocalDate?>(null) }
    var reminderMinutes by remember { mutableStateOf<Int?>(input.defaultReminderMinutes) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var calendarMenuOpen by remember { mutableStateOf(false) }
    var repeatMenuOpen by remember { mutableStateOf(false) }
    var reminderMenuOpen by remember { mutableStateOf(false) }

    val zone = input.start.zone
    val start = input.date.atTime(startTime).atZone(zone)
    val end = start.plusMinutes(durationMinutes.toLong())
    val conflicts = conflictsFor(start, end, editing?.id)
    val selectedCalendar = input.calendars.firstOrNull { it.id == calendarId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (editing == null) "Block time" else "Edit block",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = DateTimeFormat.dayHeader(input.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What are you blocking?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )

            if (editing == null && input.templates.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    input.templates.forEach { template ->
                        AssistChip(
                            onClick = {
                                title = template.title
                                durationMinutes = template.minutes
                                color = GoogleEventColor.fromId(template.colorId)
                            },
                            label = { Text("${template.title} · ${template.minutes}m") },
                        )
                    }
                }
            }

            // --- when ---
            SectionLabel("When")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showStartPicker = true },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, Modifier.size(18.dp)) },
                    label = { Text(DateTimeFormat.time(start)) },
                )
                Text("→", style = MaterialTheme.typography.bodyMedium)
                Text(DateTimeFormat.time(end), style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DURATION_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = durationMinutes == minutes,
                        onClick = { durationMinutes = minutes },
                        label = { Text(DateTimeFormat.duration(Duration.ofMinutes(minutes.toLong()))) },
                    )
                }
            }

            if (conflicts.isNotEmpty()) {
                ConflictWarning(conflicts)
            }

            // --- where it lands ---
            SectionLabel("Calendar")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { calendarMenuOpen = true },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null, Modifier.size(18.dp)) },
                    label = { Text(selectedCalendar?.title ?: "Primary calendar") },
                )
                DropdownMenu(expanded = calendarMenuOpen, onDismissRequest = { calendarMenuOpen = false }) {
                    input.calendars.filter { it.canWrite }.forEach { calendar ->
                        DropdownMenuItem(
                            text = { Text(calendar.title) },
                            trailingIcon = {
                                if (calendar.id == calendarId) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            onClick = {
                                calendarId = calendar.id
                                calendarMenuOpen = false
                            },
                        )
                    }
                }
            }

            SectionLabel("Colour")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GoogleEventColor.entries.forEach { option ->
                    ColorSwatch(
                        color = option,
                        selected = option == color,
                        onClick = { color = option },
                    )
                }
            }

            // --- options ---
            SectionLabel("Options")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { repeatMenuOpen = true },
                    leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, Modifier.size(18.dp)) },
                    label = { Text(repeat.label) },
                )
                DropdownMenu(expanded = repeatMenuOpen, onDismissRequest = { repeatMenuOpen = false }) {
                    Repeat.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                repeat = option
                                if (option == Repeat.NONE) repeatUntil = null
                                repeatMenuOpen = false
                            },
                        )
                    }
                }
                if (repeat != Repeat.NONE) {
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = { showUntilPicker = true }) {
                        Text(repeatUntil?.let { "Until ${DateTimeFormat.shortDate(it)}" } ?: "Set end date")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { reminderMenuOpen = true },
                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp)) },
                    label = {
                        Text(reminderMinutes?.let { "Remind $it min before" } ?: "Calendar default reminder")
                    },
                )
                DropdownMenu(expanded = reminderMenuOpen, onDismissRequest = { reminderMenuOpen = false }) {
                    listOf(null, 0, 5, 10, 15, 30, 60).forEach { minutes ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (minutes) {
                                        null -> "Calendar default"
                                        0 -> "At start time"
                                        else -> "$minutes minutes before"
                                    },
                                )
                            },
                            onClick = {
                                reminderMinutes = minutes
                                reminderMenuOpen = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show as busy", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Others see you as unavailable during this block",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = busy, onCheckedChange = { busy = it })
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            HorizontalDivider()

            Button(
                onClick = {
                    onSave(
                        BlockDraft(
                            title = title.trim().ifEmpty { "Blocked" },
                            calendarId = calendarId,
                            start = start,
                            end = end,
                            description = description.trim().ifEmpty { null },
                            busy = busy,
                            colorId = color.id.takeIf { it.isNotBlank() },
                            repeat = repeat,
                            repeatUntil = repeatUntil,
                            reminderMinutes = reminderMinutes,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing == null) "Block ${DateTimeFormat.duration(Duration.ofMinutes(durationMinutes.toLong()))}" else "Save changes")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            title = "Start time",
            initial = startTime,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                startTime = it
                showStartPicker = false
            },
        )
    }

    if (showUntilPicker) {
        DatePickerDialogM3(
            initial = repeatUntil ?: input.date.plusMonths(1),
            onDismiss = { showUntilPicker = false },
            onConfirm = {
                repeatUntil = it
                showUntilPicker = false
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConflictWarning(conflicts: List<CalendarEvent>) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    "Overlaps ${conflicts.size} event${if (conflicts.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                conflicts.take(3).forEach { conflict ->
                    Text(
                        "${conflict.title} · ${DateTimeFormat.timeRange(conflict.start, conflict.end)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

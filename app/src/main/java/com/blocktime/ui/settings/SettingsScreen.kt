package com.blocktime.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.blocktime.auth.GoogleAuthorizer
import com.blocktime.domain.CalendarInfo
import com.blocktime.ui.block.TimePickerDialog
import com.blocktime.ui.day.DayUiState
import com.blocktime.util.DateTimeFormat
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: DayUiState,
    onBack: () -> Unit,
    onToggleCalendar: (String) -> Unit,
    onSetDefaultCalendar: (String) -> Unit,
    onSetDefaultDuration: (Int) -> Unit,
    onSetWorkingHours: (LocalTime, LocalTime) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editingDayStart by remember { mutableStateOf(false) }
    var editingDayEnd by remember { mutableStateOf(false) }
    val settings = state.settings

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            item { SectionHeader("Calendars shown") }
            items(state.calendars, key = { "show-${it.id}" }) { calendar ->
                CalendarToggleRow(
                    calendar = calendar,
                    checked = calendar.id in settings.visibleCalendarIds,
                    onToggle = { onToggleCalendar(calendar.id) },
                )
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("New blocks go to")
            }
            items(state.writableCalendars, key = { "default-${it.id}" }) { calendar ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetDefaultCalendar(calendar.id) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.defaultCalendarId == calendar.id,
                        onClick = { onSetDefaultCalendar(calendar.id) },
                    )
                    Text(calendar.title, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Planning window")
                Text(
                    "Free slots and suggested start times are found inside these hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { editingDayStart = true }) {
                        Text("Day starts ${settings.dayStart}")
                    }
                    TextButton(onClick = { editingDayEnd = true }) {
                        Text("Day ends ${settings.dayEnd}")
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Default block length")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(30, 45, 60, 90).forEach { minutes ->
                        TextButton(onClick = { onSetDefaultDuration(minutes) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (settings.defaultDurationMinutes == minutes) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                                Text(DateTimeFormat.duration(java.time.Duration.ofMinutes(minutes.toLong())))
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Google account")
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "BlockTime holds a short-lived access token only. Disconnect removes it from this " +
                            "device; to revoke access entirely, use your Google account settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, GoogleAuthorizer.REVOKE_ACCESS_URL.toUri()),
                                )
                            },
                        ) { Text("Manage access") }
                    }
                }
            }
        }
    }

    if (editingDayStart) {
        TimePickerDialog(
            title = "Day starts at",
            initial = settings.dayStart,
            onDismiss = { editingDayStart = false },
            onConfirm = {
                onSetWorkingHours(it, settings.dayEnd)
                editingDayStart = false
            },
        )
    }

    if (editingDayEnd) {
        TimePickerDialog(
            title = "Day ends at",
            initial = settings.dayEnd,
            onDismiss = { editingDayEnd = false },
            onConfirm = {
                onSetWorkingHours(settings.dayStart, it)
                editingDayEnd = false
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun CalendarToggleRow(
    calendar: CalendarInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column {
            Text(calendar.title, style = MaterialTheme.typography.bodyLarge)
            if (!calendar.canWrite) {
                Text(
                    "Read-only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

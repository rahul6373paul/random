package com.blocktime.ui.day

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.blocktime.domain.CalendarEvent
import com.blocktime.domain.CalendarInfo
import com.blocktime.ui.common.DetailRow
import com.blocktime.util.DateTimeFormat

/** Read-only detail for any event, with edit/delete for blocks this app can change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: CalendarEvent,
    calendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (CalendarEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val calendar = calendars.firstOrNull { it.id == event.calendarId }
    val writable = calendar?.canWrite ?: false

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.headlineSmall)

            DetailRow(
                icon = Icons.Default.Schedule,
                text = if (event.allDay) {
                    "All day · ${DateTimeFormat.dayHeader(event.start.toLocalDate())}"
                } else {
                    "${DateTimeFormat.dayHeader(event.start.toLocalDate())}\n" +
                        "${DateTimeFormat.timeRange(event.start, event.end)} · ${DateTimeFormat.duration(event.duration)}"
                },
            )

            DetailRow(
                icon = Icons.Default.CalendarToday,
                text = buildString {
                    append(calendar?.title ?: event.calendarId)
                    append(if (event.busy) " · Busy" else " · Free")
                },
            )

            if (event.recurring) {
                DetailRow(icon = Icons.Default.Repeat, text = "Part of a repeating series")
            }

            event.location?.takeIf { it.isNotBlank() }?.let {
                DetailRow(icon = Icons.Default.Place, text = it)
            }

            event.description?.takeIf { it.isNotBlank() }?.let {
                DetailRow(icon = Icons.AutoMirrored.Filled.Notes, text = it)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (writable) {
                    Button(onClick = { onEdit(event) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit")
                    }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                } else {
                    Text(
                        "This calendar is read-only for your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            event.htmlLink?.let { link ->
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Google Calendar")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this block?") },
            text = {
                Text(
                    if (event.recurring) {
                        "\"${event.title}\" is part of a repeating series. Deleting removes this occurrence from your Google Calendar."
                    } else {
                        "\"${event.title}\" will be removed from your Google Calendar."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(event)
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

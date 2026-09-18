package com.blocktime.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blocktime.domain.CalendarEvent
import com.blocktime.domain.FreeSlot
import com.blocktime.domain.GoogleEventColor
import com.blocktime.util.DateTimeFormat
import java.time.Duration

/** One row of the day: either something booked, or a gap you can block. */
sealed interface DayItem {
    val sortKey: java.time.ZonedDateTime

    data class Event(val event: CalendarEvent) : DayItem {
        override val sortKey get() = event.start
    }

    data class Free(val slot: FreeSlot) : DayItem {
        override val sortKey get() = slot.start
    }
}

/** Interleaves timed events and free gaps into a single chronological list. */
fun buildDayItems(events: List<CalendarEvent>, freeSlots: List<FreeSlot>): List<DayItem> =
    (events.filterNot { it.allDay }.map(DayItem::Event) + freeSlots.map(DayItem::Free))
        .sortedWith(compareBy({ it.sortKey }, { it is DayItem.Free }))

@Composable
fun DaySummaryBar(
    booked: Duration,
    free: Duration,
    eventCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric("Booked", DateTimeFormat.duration(booked))
            SummaryMetric("Free", DateTimeFormat.duration(free))
            SummaryMetric("Events", eventCount.toString())
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AllDayRow(events: List<CalendarEvent>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        events.forEach { event ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "All day  ·  ${event.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun EventRow(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(GoogleEventColor.fromId(event.colorId).rgb)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TimeGutter(start = DateTimeFormat.time(event.start), end = DateTimeFormat.time(event.end))
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(end = 12.dp)) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(if (event.duration > Duration.ofMinutes(45)) 72.dp else 56.dp)
                        .background(accent),
                )
                Column(
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            DateTimeFormat.duration(event.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!event.busy) {
                            Text(
                                "· Free",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (event.recurring) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = "Repeating event",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    event.location?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** A gap in the day, rendered as a dashed-looking outlined row that invites a tap. */
@Composable
fun FreeSlotRow(
    slot: FreeSlot,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onBlock)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TimeGutter(start = DateTimeFormat.time(slot.start), end = DateTimeFormat.time(slot.end))
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "${DateTimeFormat.duration(slot.duration)} free — block it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TimeGutter(start: String, end: String) {
    Column(
        modifier = Modifier.width(68.dp).padding(top = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(start, style = MaterialTheme.typography.labelMedium)
        Text(
            end,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

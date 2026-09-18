package com.blocktime.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blocktime.util.DateTimeFormat
import java.time.LocalDate

/**
 * A scrollable two-week date strip anchored on today. Tapping a day loads it; the selected
 * day is filled, today is outlined.
 */
@Composable
fun DateStrip(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    daysBefore: Long = 7,
    daysAfter: Long = 60,
) {
    val today = remember { LocalDate.now() }
    val dates = remember(today) {
        val first = today.minusDays(daysBefore)
        (0..(daysBefore + daysAfter)).map { first.plusDays(it) }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedDate) {
        val index = dates.indexOf(selectedDate)
        if (index >= 0) listState.animateScrollToItem(maxOf(0, index - 2))
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates, key = { it.toString() }) { date ->
            DateChip(
                date = date,
                selected = date == selectedDate,
                isToday = date == today,
                onClick = { onSelectDate(date) },
            )
        }
    }
}

@Composable
private fun DateChip(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.4f)
    val content = if (selected) colors.onPrimary else colors.onSurface
    val border = if (isToday && !selected) colors.primary else Color.Transparent

    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = DateTimeFormat.dayHeader(date) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = DateTimeFormat.weekdayInitial(date),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.7f),
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}

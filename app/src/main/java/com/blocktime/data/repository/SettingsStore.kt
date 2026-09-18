package com.blocktime.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blocktime.domain.TaskTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blocktime_settings")

/** User preferences: which calendars are shown, where new blocks go, and the planning window. */
data class UserSettings(
    val visibleCalendarIds: Set<String> = emptySet(),
    val defaultCalendarId: String? = null,
    val defaultDurationMinutes: Int = 60,
    val defaultReminderMinutes: Int = 10,
    val dayStart: LocalTime = LocalTime.of(9, 0),
    val dayEnd: LocalTime = LocalTime.of(18, 0),
    val templates: List<TaskTemplate> = TaskTemplate.DEFAULTS,
    /** False until the first successful calendar load, so we can preselect sensible defaults. */
    val initialized: Boolean = false,
)

class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<UserSettings> = store.data.map { prefs ->
        UserSettings(
            visibleCalendarIds = prefs[KEY_VISIBLE_CALENDARS] ?: emptySet(),
            defaultCalendarId = prefs[KEY_DEFAULT_CALENDAR],
            defaultDurationMinutes = prefs[KEY_DEFAULT_DURATION] ?: 60,
            defaultReminderMinutes = prefs[KEY_DEFAULT_REMINDER] ?: 10,
            dayStart = prefs[KEY_DAY_START]?.let(LocalTime::parse) ?: LocalTime.of(9, 0),
            dayEnd = prefs[KEY_DAY_END]?.let(LocalTime::parse) ?: LocalTime.of(18, 0),
            templates = prefs[KEY_TEMPLATES]
                ?.mapNotNull(TaskTemplate::parse)
                ?.takeIf { it.isNotEmpty() }
                ?.sortedBy { it.minutes }
                ?: TaskTemplate.DEFAULTS,
            initialized = prefs[KEY_INITIALIZED] == "true",
        )
    }

    suspend fun setVisibleCalendars(ids: Set<String>) = store.edit { it[KEY_VISIBLE_CALENDARS] = ids }

    suspend fun setDefaultCalendar(id: String) = store.edit { it[KEY_DEFAULT_CALENDAR] = id }

    suspend fun setDefaultDuration(minutes: Int) = store.edit { it[KEY_DEFAULT_DURATION] = minutes }

    suspend fun setDefaultReminder(minutes: Int) = store.edit { it[KEY_DEFAULT_REMINDER] = minutes }

    suspend fun setWorkingHours(start: LocalTime, end: LocalTime) = store.edit {
        it[KEY_DAY_START] = start.toString()
        it[KEY_DAY_END] = end.toString()
    }

    suspend fun setTemplates(templates: List<TaskTemplate>) = store.edit {
        it[KEY_TEMPLATES] = templates.map(TaskTemplate::serialize).toSet()
    }

    /** Called once after the first calendar load, so the user starts with their own calendars on. */
    suspend fun initializeWith(visibleCalendarIds: Set<String>, defaultCalendarId: String?) =
        store.edit { prefs ->
            prefs[KEY_VISIBLE_CALENDARS] = visibleCalendarIds
            defaultCalendarId?.let { prefs[KEY_DEFAULT_CALENDAR] = it }
            prefs[KEY_INITIALIZED] = "true"
        }

    private companion object {
        val KEY_VISIBLE_CALENDARS = stringSetPreferencesKey("visible_calendars")
        val KEY_DEFAULT_CALENDAR = stringPreferencesKey("default_calendar")
        val KEY_DEFAULT_DURATION = intPreferencesKey("default_duration")
        val KEY_DEFAULT_REMINDER = intPreferencesKey("default_reminder")
        val KEY_DAY_START = stringPreferencesKey("day_start")
        val KEY_DAY_END = stringPreferencesKey("day_end")
        val KEY_TEMPLATES = stringSetPreferencesKey("templates")
        val KEY_INITIALIZED = stringPreferencesKey("initialized")
    }
}

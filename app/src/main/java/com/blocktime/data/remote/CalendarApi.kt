package com.blocktime.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CalendarApi {

    @GET("users/me/calendarList")
    suspend fun calendarList(
        @Query("minAccessRole") minAccessRole: String? = null,
        @Query("showHidden") showHidden: Boolean = false,
        @Query("pageToken") pageToken: String? = null,
    ): CalendarListResponseDto

    /**
     * Single events in a window. `singleEvents=true` expands recurring events into the
     * individual occurrences the day view needs.
     */
    @GET("calendars/{calendarId}/events")
    suspend fun events(
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("maxResults") maxResults: Int = 250,
        @Query("showDeleted") showDeleted: Boolean = false,
        @Query("pageToken") pageToken: String? = null,
    ): EventsResponseDto

    @POST("calendars/{calendarId}/events")
    suspend fun createEvent(
        @Path("calendarId") calendarId: String,
        @Body event: EventDto,
        @Query("sendUpdates") sendUpdates: String = "none",
    ): EventDto

    @PATCH("calendars/{calendarId}/events/{eventId}")
    suspend fun patchEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body event: EventDto,
        @Query("sendUpdates") sendUpdates: String = "none",
    ): EventDto

    @DELETE("calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Query("sendUpdates") sendUpdates: String = "none",
    )

    companion object {
        const val BASE_URL = "https://www.googleapis.com/calendar/v3/"
    }
}

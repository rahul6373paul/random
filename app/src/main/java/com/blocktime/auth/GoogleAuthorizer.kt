package com.blocktime.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper over Play Services' AuthorizationClient.
 *
 * The app only ever needs an OAuth *access token* for the Calendar API, so we ask for the
 * calendar scopes directly instead of running a separate sign-in flow. Once the user has
 * granted the scopes, [authorize] returns a fresh token without any UI; before that, it
 * returns a [PendingIntent] the caller launches to show Google's consent screen.
 */
class GoogleAuthorizer(private val context: Context) {

    private val client = Identity.getAuthorizationClient(context)

    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(SCOPES.map { Scope(it) })
        .build()

    sealed interface Result {
        /** Scopes are granted; [accessToken] is valid for roughly an hour. */
        data class Authorized(val accessToken: String) : Result

        /** The user has to approve first — launch [pendingIntent] and feed the result back. */
        data class ConsentRequired(val pendingIntent: PendingIntent) : Result

        data class Failed(val error: Throwable) : Result
    }

    suspend fun authorize(): Result = try {
        toResult(client.authorize(request).await())
    } catch (e: Exception) {
        Result.Failed(e)
    }

    /** Parses the Intent returned by the consent screen launched from [Result.ConsentRequired]. */
    fun resultFromIntent(data: Intent?): Result = try {
        toResult(client.getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        Result.Failed(e)
    }

    private fun toResult(result: AuthorizationResult): Result {
        val pendingIntent = result.pendingIntent
        return when {
            pendingIntent != null && result.hasResolution() -> Result.ConsentRequired(pendingIntent)
            result.accessToken != null -> Result.Authorized(result.accessToken!!)
            else -> Result.Failed(IllegalStateException("Google returned no access token"))
        }
    }

    companion object {
        /** Read the user's calendar list and events, and create/update/delete events. */
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/calendar.events",
        )

        /** Where a user goes to revoke the app's access to their Google account. */
        const val REVOKE_ACCESS_URL = "https://myaccount.google.com/permissions"
    }
}

package com.blocktime.auth

import android.app.PendingIntent
import android.content.Intent
import com.blocktime.data.remote.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface AuthState {
    /** Startup: we haven't asked Play Services yet. */
    data object Unknown : AuthState

    /** No grant yet (or it was cleared) — show the connect screen. */
    data object SignedOut : AuthState

    data object Connecting : AuthState

    data object Authorized : AuthState

    data class Error(val message: String) : AuthState
}

/**
 * Owns the access token and the app's authorization state.
 *
 * Tokens are cached in memory only: they are short-lived and re-obtaining one after the first
 * consent is silent, so there is nothing worth persisting — and nothing sensitive left on disk.
 */
class AuthRepository(
    private val authorizer: GoogleAuthorizer,
) : TokenProvider {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _consentRequest = MutableStateFlow<PendingIntent?>(null)
    val consentRequest: StateFlow<PendingIntent?> = _consentRequest.asStateFlow()

    private val tokenLock = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenObtainedAt: Long = 0L

    /** Tries a silent authorization; only surfaces consent when [interactive] is true. */
    suspend fun refreshAuthorization(interactive: Boolean) {
        if (_state.value != AuthState.Authorized) _state.value = AuthState.Connecting
        when (val result = authorizer.authorize()) {
            is GoogleAuthorizer.Result.Authorized -> storeToken(result.accessToken)
            is GoogleAuthorizer.Result.ConsentRequired -> {
                if (interactive) {
                    _consentRequest.value = result.pendingIntent
                    _state.value = AuthState.Connecting
                } else {
                    _state.value = AuthState.SignedOut
                }
            }
            is GoogleAuthorizer.Result.Failed -> {
                _state.value = AuthState.Error(result.error.message ?: "Could not reach Google")
            }
        }
    }

    fun consentLaunched() {
        _consentRequest.value = null
    }

    fun onConsentResult(data: Intent?) {
        when (val result = authorizer.resultFromIntent(data)) {
            is GoogleAuthorizer.Result.Authorized -> storeToken(result.accessToken)
            is GoogleAuthorizer.Result.ConsentRequired -> _state.value = AuthState.SignedOut
            is GoogleAuthorizer.Result.Failed -> {
                // The user dismissing the consent screen lands here too; treat it as "not connected".
                _state.value = AuthState.SignedOut
            }
        }
    }

    /** Forgets the local token. Full revocation happens in the user's Google account settings. */
    fun disconnect() {
        cachedToken = null
        tokenObtainedAt = 0L
        _state.value = AuthState.SignedOut
    }

    private fun storeToken(token: String) {
        cachedToken = token
        tokenObtainedAt = System.currentTimeMillis()
        _state.value = AuthState.Authorized
    }

    // --- TokenProvider: called from the OkHttp interceptor, off the main thread. ---

    override fun token(): String? {
        val token = cachedToken
        val fresh = System.currentTimeMillis() - tokenObtainedAt < TOKEN_TTL_MS
        return if (token != null && fresh) token else refreshBlocking()
    }

    override fun invalidate(token: String) {
        if (cachedToken == token) {
            cachedToken = null
            tokenObtainedAt = 0L
        }
    }

    private fun refreshBlocking(): String? = runBlocking {
        tokenLock.withLock {
            // Another request may have refreshed while we waited for the lock.
            cachedToken?.takeIf { System.currentTimeMillis() - tokenObtainedAt < TOKEN_TTL_MS }
                ?: withContext(Dispatchers.IO) {
                    when (val result = authorizer.authorize()) {
                        is GoogleAuthorizer.Result.Authorized -> {
                            storeToken(result.accessToken)
                            result.accessToken
                        }
                        is GoogleAuthorizer.Result.ConsentRequired -> {
                            // The grant was revoked outside the app; StateFlow is safe to set here.
                            _state.value = AuthState.SignedOut
                            null
                        }
                        is GoogleAuthorizer.Result.Failed -> null
                    }
                }
        }
    }

    private companion object {
        /** Google access tokens last ~1h; refresh early so long-running screens don't 401. */
        const val TOKEN_TTL_MS = 45 * 60 * 1000L
    }
}

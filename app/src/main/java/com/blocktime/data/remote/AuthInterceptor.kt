package com.blocktime.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Supplies OAuth access tokens to the HTTP layer without pulling Play Services into it. */
interface TokenProvider {
    /** A usable token, refreshing if the cached one is stale. Null when not authorized. */
    fun token(): String?

    /** Marks [token] as rejected so the next call fetches a new one. */
    fun invalidate(token: String)
}

/**
 * Adds the bearer token to every request and retries once on 401, which happens when a token
 * expires early or is revoked while the app is open.
 */
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.token()
            ?: throw IOException("Not connected to Google Calendar")

        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != 401) return response

        tokenProvider.invalidate(token)
        val freshToken = tokenProvider.token()
        if (freshToken == null || freshToken == token) return response

        response.close()
        return chain.proceed(chain.request().withToken(freshToken))
    }

    private fun okhttp3.Request.withToken(token: String) = newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
}

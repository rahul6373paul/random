package com.blocktime

import android.app.Application
import com.blocktime.auth.AuthRepository
import com.blocktime.auth.GoogleAuthorizer
import com.blocktime.data.remote.AuthInterceptor
import com.blocktime.data.remote.CalendarApi
import com.blocktime.data.repository.CalendarRepository
import com.blocktime.data.repository.SettingsStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container. The graph is small enough that a DI framework would add more
 * build complexity than it removes.
 */
class BlockTimeApp : Application() {

    val authRepository: AuthRepository by lazy { AuthRepository(GoogleAuthorizer(this)) }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val calendarRepository: CalendarRepository by lazy { CalendarRepository(calendarApi) }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(authRepository))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val calendarApi: CalendarApi by lazy {
        Retrofit.Builder()
            .baseUrl(CalendarApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CalendarApi::class.java)
    }
}

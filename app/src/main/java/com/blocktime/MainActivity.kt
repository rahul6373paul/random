package com.blocktime

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blocktime.auth.AuthRepository
import com.blocktime.auth.AuthState
import com.blocktime.data.repository.SettingsStore
import com.blocktime.ui.day.DayScreen
import com.blocktime.ui.day.DayViewModel
import com.blocktime.ui.settings.SettingsScreen
import com.blocktime.ui.signin.ConnectScreen
import com.blocktime.ui.theme.BlockTimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BlockTimeApp
        val authRepository = app.authRepository

        // Google's consent screen comes back through this launcher.
        val consentLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                authRepository.onConsentResult(result.data)
            } else {
                authRepository.disconnect()
            }
        }

        setContent {
            BlockTimeTheme {
                val authState by authRepository.state.collectAsStateWithLifecycle()
                val consentRequest by authRepository.consentRequest.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    // Silent attempt on every cold start; shows the connect screen if not granted.
                    authRepository.refreshAuthorization(interactive = false)
                }

                LaunchedEffect(consentRequest) {
                    consentRequest?.let { pendingIntent ->
                        authRepository.consentLaunched()
                        consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (authState is AuthState.Authorized) {
                        BlockTimeNavHost(app, authRepository)
                    } else {
                        val scope = rememberCoroutineScope()
                        ConnectScreen(
                            authState = authState,
                            onConnect = {
                                scope.launch { authRepository.refreshAuthorization(interactive = true) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockTimeNavHost(app: BlockTimeApp, authRepository: AuthRepository) {
    val navController = rememberNavController()
    val settingsStore: SettingsStore = app.settingsStore
    val dayViewModel: DayViewModel = viewModel(
        factory = DayViewModel.factory(app.calendarRepository, settingsStore, authRepository),
    )
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "day") {
        composable("day") {
            DayScreen(
                viewModel = dayViewModel,
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            val state by dayViewModel.state.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onToggleCalendar = dayViewModel::toggleCalendar,
                onSetDefaultCalendar = { id -> scope.launch { settingsStore.setDefaultCalendar(id) } },
                onSetDefaultDuration = { minutes -> scope.launch { settingsStore.setDefaultDuration(minutes) } },
                onSetWorkingHours = { start, end -> scope.launch { settingsStore.setWorkingHours(start, end) } },
                onDisconnect = {
                    authRepository.disconnect()
                    navController.popBackStack()
                },
            )
        }
    }
}

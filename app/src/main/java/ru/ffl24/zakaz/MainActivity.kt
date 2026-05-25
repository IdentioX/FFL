package ru.ffl24.zakaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import ru.ffl24.zakaz.api.RetrofitProvider
import ru.ffl24.zakaz.data.auth.AuthSession
import ru.ffl24.zakaz.data.auth.AuthSessionManager
import ru.ffl24.zakaz.repository.AuthRepository
import ru.ffl24.zakaz.repository.DriverOrdersRepository
import ru.ffl24.zakaz.repository.UnauthorizedException
import ru.ffl24.zakaz.settings.AppSettingsStore
import ru.ffl24.zakaz.ui.screens.DriverOrdersScreen
import ru.ffl24.zakaz.ui.screens.SettingsScreen
import ru.ffl24.zakaz.ui.screens.SetupAuthScreen
import ru.ffl24.zakaz.ui.theme.FFl24Theme
import ru.ffl24.zakaz.viewmodel.DriverOrdersViewModel
import ru.ffl24.zakaz.viewmodel.SetupAuthViewModel

class MainActivity : ComponentActivity() {
    private val authSessionManager by lazy {
        AuthSessionManager(applicationContext)
    }

    private val setupAuthViewModel by viewModels<SetupAuthViewModel> {
        SetupAuthViewModelFactory(applicationContext, authSessionManager)
    }

    private val driverOrdersViewModel by viewModels<DriverOrdersViewModel> {
        DriverOrdersViewModelFactory(applicationContext, authSessionManager)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FFl24Theme {
                val sessionState by remember {
                    authSessionManager.getSession()
                        .map<AuthSession?, SessionUiState> { session ->
                            if (session == null) {
                                SessionUiState.Unauthenticated
                            } else {
                                SessionUiState.Authenticated(session)
                            }
                        }
                        .onStart { emit(SessionUiState.Loading) }
                }.collectAsStateWithLifecycle(initialValue = SessionUiState.Loading)
                val appContext = remember { applicationContext }
                var showSettings by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(sessionState) {
                    sessionState as? SessionUiState.Authenticated ?: return@LaunchedEffect
                    val apiService = RetrofitProvider.createDriverApiService(
                        baseUrl = AppSettingsStore.getApiBaseUrl(appContext),
                        authSessionManager = authSessionManager
                    )
                    val result = AuthRepository(apiService).me()
                    if (result.exceptionOrNull() is UnauthorizedException) {
                        authSessionManager.clearSession()
                        showSettings = false
                    }
                }

                when (val state = sessionState) {
                    SessionUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    SessionUiState.Unauthenticated -> {
                        SetupAuthScreen(
                            viewModel = setupAuthViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is SessionUiState.Authenticated -> {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(if (showSettings) "Settings" else state.session.driverName)
                                    },
                                    actions = {
                                        TextButton(
                                            onClick = { showSettings = !showSettings }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Open settings"
                                            )
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                if (showSettings) {
                                    SettingsScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        onSettingsSaved = driverOrdersViewModel::refresh
                                    )
                                } else {
                                    DriverOrdersScreen(
                                        viewModel = driverOrdersViewModel,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private class DriverOrdersViewModelFactory(
        private val appContext: android.content.Context,
        private val authSessionManager: AuthSessionManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DriverOrdersViewModel(
                repositoryProvider = {
                    val apiService = RetrofitProvider.createDriverApiService(
                        baseUrl = AppSettingsStore.getApiBaseUrl(appContext),
                        authSessionManager = authSessionManager
                    )
                    DriverOrdersRepository(apiService)
                },
                authSessionManager = authSessionManager
            ) as T
        }
    }

    private class SetupAuthViewModelFactory(
        private val appContext: android.content.Context,
        private val authSessionManager: AuthSessionManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SetupAuthViewModel(
                authRepositoryProvider = {
                    val apiService = RetrofitProvider.createDriverApiService(
                        baseUrl = AppSettingsStore.getApiBaseUrl(appContext),
                        authSessionManager = authSessionManager
                    )
                    AuthRepository(apiService)
                },
                authSessionManager = authSessionManager
            ) as T
        }
    }
}

private sealed interface SessionUiState {
    data object Loading : SessionUiState
    data object Unauthenticated : SessionUiState
    data class Authenticated(val session: AuthSession) : SessionUiState
}

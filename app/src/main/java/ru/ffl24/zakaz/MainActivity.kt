package ru.ffl24.zakaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import ru.ffl24.zakaz.ui.theme.AppOnPrimary
import ru.ffl24.zakaz.ui.screens.DriverOrdersScreen
import ru.ffl24.zakaz.ui.screens.OrderDetailsScreen
import ru.ffl24.zakaz.ui.screens.SettingsScreen
import ru.ffl24.zakaz.ui.screens.SetupAuthScreen
import ru.ffl24.zakaz.ui.theme.FFl24Theme
import ru.ffl24.zakaz.viewmodel.DriverOrdersViewModel
import ru.ffl24.zakaz.viewmodel.OrderDetailsViewModel
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

    private val orderDetailsViewModel by viewModels<OrderDetailsViewModel> {
        OrderDetailsViewModelFactory(applicationContext, authSessionManager)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkThemeEnabled by rememberSaveable {
                mutableStateOf(AppSettingsStore.isDarkThemeEnabled(applicationContext))
            }

            FFl24Theme(darkTheme = darkThemeEnabled) {
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
                var currentRoute by rememberSaveable { mutableStateOf(AppRoutes.Orders) }

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
                        val detailsOrderId = AppRoutes.orderDetailsId(currentRoute)
                        fun returnToOrders() {
                            currentRoute = AppRoutes.Orders
                            driverOrdersViewModel.refresh()
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                TopAppBar(
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor = AppOnPrimary,
                                        actionIconContentColor = AppOnPrimary
                                    ),
                                    navigationIcon = {
                                        if (detailsOrderId != null) {
                                            IconButton(onClick = ::returnToOrders) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Назад"
                                                )
                                            }
                                        }
                                    },
                                    title = {
                                        if (detailsOrderId != null) {
                                            Text(
                                                text = "Детали заказа",
                                                style = MaterialTheme.typography.titleLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (showSettings) {
                                            Text(
                                                text = "Настройки",
                                                style = MaterialTheme.typography.titleLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = state.session.driverName,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.titleLarge,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    actions = {
                                        if (detailsOrderId == null) {
                                            TextButton(
                                                onClick = { showSettings = !showSettings },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = AppOnPrimary
                                                )
                                            ) {
                                                Text(
                                                    text = if (showSettings) "Заказы" else "Настройки",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                if (detailsOrderId != null) {
                                    OrderDetailsScreen(
                                        orderId = detailsOrderId,
                                        viewModel = orderDetailsViewModel,
                                        onBack = ::returnToOrders,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (showSettings) {
                                    SettingsScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        darkThemeEnabled = darkThemeEnabled,
                                        onDarkThemeChanged = { enabled ->
                                            AppSettingsStore.saveDarkThemeEnabled(applicationContext, enabled)
                                            darkThemeEnabled = enabled
                                        },
                                        onSettingsSaved = driverOrdersViewModel::refresh
                                    )
                                } else {
                                    DriverOrdersScreen(
                                        viewModel = driverOrdersViewModel,
                                        onOpenDetails = { order ->
                                            showSettings = false
                                            currentRoute = AppRoutes.orderDetails(order.orderId.toInt())
                                        },
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
                appContext = appContext,
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

    private class OrderDetailsViewModelFactory(
        private val appContext: android.content.Context,
        private val authSessionManager: AuthSessionManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return OrderDetailsViewModel(
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
}

private sealed interface SessionUiState {
    data object Loading : SessionUiState
    data object Unauthenticated : SessionUiState
    data class Authenticated(val session: AuthSession) : SessionUiState
}

private object AppRoutes {
    const val Orders = "orders"
    const val OrderDetailsPattern = "orderDetails/{orderId}"
    private const val OrderDetailsPrefix = "orderDetails/"

    fun orderDetails(orderId: Int): String = "$OrderDetailsPrefix$orderId"

    fun orderDetailsId(route: String): Int? {
        if (!route.startsWith(OrderDetailsPrefix)) return null
        return route.removePrefix(OrderDetailsPrefix).toIntOrNull()
    }
}

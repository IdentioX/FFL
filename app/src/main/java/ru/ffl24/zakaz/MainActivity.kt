package ru.ffl24.zakaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.ffl24.zakaz.api.RetrofitProvider
import ru.ffl24.zakaz.repository.DriverOrdersRepository
import ru.ffl24.zakaz.settings.AppSettingsStore
import ru.ffl24.zakaz.ui.screens.DriverOrdersScreen
import ru.ffl24.zakaz.ui.screens.SettingsScreen
import ru.ffl24.zakaz.ui.theme.FFl24Theme
import ru.ffl24.zakaz.viewmodel.DriverOrdersViewModel

class MainActivity : ComponentActivity() {
    private val driverOrdersViewModel by viewModels<DriverOrdersViewModel> {
        DriverOrdersViewModelFactory(applicationContext)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FFl24Theme {
                var showSettings by rememberSaveable { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(if (showSettings) "Settings" else "Driver Orders")
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

    private class DriverOrdersViewModelFactory(
        private val appContext: android.content.Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DriverOrdersViewModel(
                repositoryProvider = {
                    val apiService = RetrofitProvider.createDriverApiService(
                        AppSettingsStore.getApiBaseUrl(appContext)
                    )
                    DriverOrdersRepository(apiService)
                },
                driverIdProvider = {
                    AppSettingsStore.getDriverId(appContext)
                }
            ) as T
        }
    }
}

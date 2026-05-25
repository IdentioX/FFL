package ru.ffl24.zakaz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ffl24.zakaz.api.RetrofitProvider
import ru.ffl24.zakaz.settings.AppSettingsStore

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSettingsSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var apiBaseUrl by rememberSaveable {
        mutableStateOf(AppSettingsStore.getApiBaseUrl(context))
    }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isErrorResult by rememberSaveable { mutableStateOf(false) }
    var isTestingConnection by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Backend settings",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = apiBaseUrl,
            onValueChange = {
                apiBaseUrl = it
                resultMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API base URL") },
            singleLine = true
        )

        Button(
            onClick = {
                when {
                    apiBaseUrl.isBlank() -> {
                        isErrorResult = true
                        resultMessage = "API base URL is required."
                    }

                    else -> {
                        AppSettingsStore.saveSettings(
                            context = context,
                            apiBaseUrl = apiBaseUrl,
                            driverId = AppSettingsStore.getDriverId(context)
                        )
                        isErrorResult = false
                        resultMessage = "Settings saved."
                        onSettingsSaved()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save settings")
        }

        Button(
            onClick = {
                if (apiBaseUrl.isBlank()) {
                    isErrorResult = true
                    resultMessage = "API base URL is required."
                } else {
                    testConnection(
                        coroutineScope = coroutineScope,
                        baseUrl = apiBaseUrl,
                        onStart = {
                            isTestingConnection = true
                            resultMessage = null
                        },
                        onResult = { success, message ->
                            isTestingConnection = false
                            isErrorResult = !success
                            resultMessage = message
                        }
                    )
                }
            },
            enabled = !isTestingConnection,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isTestingConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            }
            Text("Test connection")
        }

        resultMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isErrorResult) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

private fun testConnection(
    coroutineScope: CoroutineScope,
    baseUrl: String,
    onStart: () -> Unit,
    onResult: (Boolean, String) -> Unit
) {
    coroutineScope.launch {
        onStart()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val response = RetrofitProvider
                    .createDriverApiService(AppSettingsStore.normalizeBaseUrl(baseUrl))
                    .getHealth()

                if (response.isSuccessful) {
                    true to "Health check succeeded."
                } else {
                    false to "Health check failed with code ${response.code()}."
                }
            }.getOrElse { error ->
                false to (error.message ?: "Connection test failed.")
            }
        }
        onResult(result.first, result.second)
    }
}

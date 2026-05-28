package ru.ffl24.zakaz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ffl24.zakaz.api.RetrofitProvider
import ru.ffl24.zakaz.settings.AppSettingsStore
import ru.ffl24.zakaz.ui.theme.AppOnPrimary
import ru.ffl24.zakaz.ui.theme.AppPrimary

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    darkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки сервера",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = apiBaseUrl,
            onValueChange = {
                apiBaseUrl = it
                resultMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Адрес сервера") },
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Темная тема",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Удобнее в темное время суток",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = darkThemeEnabled,
                onCheckedChange = onDarkThemeChanged
            )
        }

        Button(
            onClick = {
                when {
                    apiBaseUrl.isBlank() -> {
                        isErrorResult = true
                        resultMessage = "Укажите адрес сервера."
                    }

                    else -> {
                        AppSettingsStore.saveSettings(
                            context = context,
                            apiBaseUrl = apiBaseUrl,
                            driverId = AppSettingsStore.getDriverId(context),
                            darkThemeEnabled = darkThemeEnabled
                        )
                        isErrorResult = false
                        resultMessage = "Настройки сохранены."
                        onSettingsSaved()
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPrimary,
                contentColor = AppOnPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить")
        }

        Button(
            onClick = {
                if (apiBaseUrl.isBlank()) {
                    isErrorResult = true
                    resultMessage = "Укажите адрес сервера."
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
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPrimary,
                contentColor = AppOnPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isTestingConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = AppOnPrimary,
                    strokeWidth = 2.dp
                )
            }
            Text("Проверить связь")
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
                    true to "Связь с сервером есть."
                } else {
                    false to "Сервер ответил с кодом ${response.code()}."
                }
            }.getOrElse { error ->
                false to (error.message ?: "Не удалось проверить связь.")
            }
        }
        onResult(result.first, result.second)
    }
}

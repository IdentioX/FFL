package ru.ffl24.zakaz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ffl24.zakaz.model.DriverOptionDto
import ru.ffl24.zakaz.viewmodel.SetupAuthViewModel

@Composable
fun SetupAuthScreen(
    viewModel: SetupAuthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Выберите водителя",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск водителя") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium
        )

        when {
            state.isLoadingDrivers -> LoadingDrivers()
            state.drivers.isEmpty() && state.errorMessage != null -> DriverLoadError(
                message = state.errorMessage ?: "Не удалось загрузить водителей.",
                onRetry = viewModel::loadDrivers
            )
            else -> DriverList(
                drivers = state.filteredDrivers,
                selectedDriver = state.selectedDriver,
                onSelectDriver = viewModel::selectDriver,
                modifier = Modifier.weight(1f)
            )
        }

        if (state.errorMessage == "Неверный пароль") {
            Text(
                text = "Неверный пароль",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = { showPasswordDialog = true },
            enabled = state.selectedDriver != null && !state.isLoadingDrivers,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Вход", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isLoggingIn) {
                    showPasswordDialog = false
                    viewModel.updatePassword("")
                }
            },
            title = {
                Text(state.selectedDriver?.driverName.orEmpty())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::updatePassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                    state.errorMessage?.let { message ->
                        Text(
                            text = if (message == "Неверный пароль") "Неверный пароль" else message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::login,
                    enabled = !state.isLoggingIn
                ) {
                    if (state.isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text("Войти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        viewModel.updatePassword("")
                    },
                    enabled = !state.isLoggingIn
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DriverList(
    drivers: List<DriverOptionDto>,
    selectedDriver: DriverOptionDto?,
    onSelectDriver: (DriverOptionDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = drivers,
            key = { it.driverId }
        ) { driver ->
            DriverRow(
                driver = driver,
                isSelected = selectedDriver?.driverId == driver.driverId,
                onClick = { onSelectDriver(driver) }
            )
        }
    }
}

@Composable
private fun DriverRow(
    driver: DriverOptionDto,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = driver.driverName,
            color = textColor,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun LoadingDrivers() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DriverLoadError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

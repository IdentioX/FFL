package ru.ffl24.zakaz.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.ffl24.zakaz.data.auth.AuthSession
import ru.ffl24.zakaz.data.auth.AuthSessionManager
import ru.ffl24.zakaz.model.DriverOptionDto
import ru.ffl24.zakaz.repository.AuthRepository
import ru.ffl24.zakaz.repository.UnauthorizedException

data class SetupAuthUiState(
    val isLoadingDrivers: Boolean = false,
    val drivers: List<DriverOptionDto> = emptyList(),
    val searchQuery: String = "",
    val selectedDriver: DriverOptionDto? = null,
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false
) {
    val filteredDrivers: List<DriverOptionDto>
        get() {
            val query = searchQuery.trim()
            return if (query.isBlank()) {
                drivers
            } else {
                drivers.filter { driver ->
                    driver.driverName.contains(query, ignoreCase = true)
                }
            }
        }
}

class SetupAuthViewModel(
    private val authRepositoryProvider: () -> AuthRepository,
    private val authSessionManager: AuthSessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetupAuthUiState())
    val uiState: StateFlow<SetupAuthUiState> = _uiState.asStateFlow()

    init {
        loadDrivers()
    }

    fun loadDrivers() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingDrivers = true, errorMessage = null, loginSuccess = false)
            }

            authRepositoryProvider().getDrivers()
                .onSuccess { drivers ->
                    _uiState.update {
                        it.copy(isLoadingDrivers = false, drivers = drivers, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingDrivers = false,
                            errorMessage = error.message ?: "Не удалось загрузить водителей."
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun selectDriver(driver: DriverOptionDto) {
        _uiState.update { it.copy(selectedDriver = driver, password = "", errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        val selectedDriver = state.selectedDriver

        when {
            selectedDriver == null -> {
                _uiState.update { it.copy(errorMessage = "Выберите водителя.") }
                return
            }

            state.password.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Введите пароль.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, errorMessage = null) }

            authRepositoryProvider()
                .login(driverId = selectedDriver.driverId, password = state.password)
                .onSuccess { response ->
                    authSessionManager.saveSession(
                        AuthSession(
                            accessToken = response.accessToken,
                            driverId = response.driverId,
                            driverName = response.driverName
                        )
                    )
                    _uiState.update {
                        it.copy(
                            password = "",
                            isLoggingIn = false,
                            errorMessage = null,
                            loginSuccess = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            password = "",
                            isLoggingIn = false,
                            errorMessage = if (error is UnauthorizedException) {
                                "Неверный пароль"
                            } else {
                                error.message ?: "Не удалось войти."
                            }
                        )
                    }
                }
        }
    }
}

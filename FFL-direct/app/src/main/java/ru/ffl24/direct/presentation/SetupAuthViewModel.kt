package ru.ffl24.direct.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ffl24.direct.domain.AuthError
import ru.ffl24.direct.domain.AuthRepository
import ru.ffl24.direct.domain.AuthResult
import ru.ffl24.direct.domain.Driver
import ru.ffl24.direct.domain.DriverSession
import ru.ffl24.direct.domain.RuntimeReadiness

sealed interface AuthUiState {
    data object ConfigurationMissing : AuthUiState
    data object ConfigurationInvalid : AuthUiState
    data object Loading : AuthUiState
    data class Choosing(
        val drivers: List<Driver>, val selectedId: Long? = null, val passwordRequested: Boolean = false,
        val busy: Boolean = false, val invalidCredentials: Boolean = false
    ) : AuthUiState
    data class Authenticated(val session: DriverSession) : AuthUiState
    data object Disabled : AuthUiState
    data class Failed(val error: AuthError) : AuthUiState
}

/** No password in StateFlow, SavedStateHandle, navigation or persisted session. */
class SetupAuthViewModel(
    private val readiness: RuntimeReadiness,
    private val repository: AuthRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<AuthUiState>(when (readiness) {
        RuntimeReadiness.ConfigurationMissing -> AuthUiState.ConfigurationMissing
        RuntimeReadiness.ConfigurationInvalid -> AuthUiState.ConfigurationInvalid
        RuntimeReadiness.ConnectionNotChecked -> AuthUiState.Loading
    })
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var secret: CharArray? = null

    fun onForeground() {
        if (readiness == RuntimeReadiness.ConnectionNotChecked) retry()
    }

    fun onBackground() {
        operation?.cancel()
        secret?.fill('\u0000')
        secret = null
        mutableState.value = when (val current = state.value) {
            is AuthUiState.Choosing -> current.copy(passwordRequested = false, busy = false)
            is AuthUiState.Authenticated -> AuthUiState.Loading // hide protected identity until fresh access
            else -> current
        }
    }

    fun retry() = perform {
        publish(AuthUiState.Loading)
        when (val result = repository.restore()) {
            is AuthResult.Success -> if (result.value == null) loadDrivers() else publish(AuthUiState.Authenticated(result.value))
            AuthResult.DriverMissing, AuthResult.InvalidCredentials -> loadDrivers()
            AuthResult.DriverDisabled -> publish(AuthUiState.Disabled)
            is AuthResult.Failed -> publish(AuthUiState.Failed(result.error))
        }
    }

    fun select(driverId: Long) {
        val current = state.value as? AuthUiState.Choosing ?: return
        if (!current.busy && current.drivers.any { it.id == driverId }) {
            mutableState.value = current.copy(selectedId = driverId, passwordRequested = false, invalidCredentials = false)
        }
    }

    fun requestPassword() {
        val current = state.value as? AuthUiState.Choosing ?: return
        if (!current.busy && current.selectedId != null) {
            mutableState.value = current.copy(passwordRequested = true, invalidCredentials = false)
        }
    }

    fun cancelLogin() {
        operation?.cancel()
        secret?.fill('\u0000')
        secret = null
        val current = state.value as? AuthUiState.Choosing ?: return
        mutableState.value = current.copy(passwordRequested = false, busy = false)
    }

    fun login(password: CharArray) {
        val current = state.value as? AuthUiState.Choosing
        val driverId = current?.selectedId
        if (current == null || current.busy || driverId == null || !current.passwordRequested) {
            password.fill('\u0000')
            return
        }
        operation?.cancel()
        secret?.fill('\u0000')
        secret = password
        mutableState.value = current.copy(passwordRequested = true, busy = true, invalidCredentials = false)
        operation = viewModelScope.launch {
            try {
                when (val result = repository.login(driverId, password)) {
                    is AuthResult.Success -> publish(AuthUiState.Authenticated(result.value))
                    AuthResult.InvalidCredentials, AuthResult.DriverMissing ->
                        publish(current.copy(passwordRequested = true, busy = false, invalidCredentials = true))
                    AuthResult.DriverDisabled -> publish(AuthUiState.Disabled)
                    is AuthResult.Failed -> publish(AuthUiState.Failed(result.error))
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                publish(AuthUiState.Failed(AuthError.DATA_UNAVAILABLE))
            } finally {
                password.fill('\u0000')
                if (secret === password) secret = null
            }
        }.also { job -> job.invokeOnCompletion { password.fill('\u0000') } }
    }

    fun recheckAccess() = perform {
        publish(AuthUiState.Loading)
        when (val result = repository.checkAccess()) {
            is AuthResult.Success -> if (result.value == null) loadDrivers() else publish(AuthUiState.Authenticated(result.value))
            AuthResult.DriverDisabled -> publish(AuthUiState.Disabled)
            AuthResult.DriverMissing, AuthResult.InvalidCredentials -> loadDrivers()
            is AuthResult.Failed -> publish(AuthUiState.Failed(result.error))
        }
    }

    fun logout() = perform {
        publish(AuthUiState.Loading)
        when (val result = repository.logout()) {
            is AuthResult.Success -> loadDrivers()
            is AuthResult.Failed -> publish(AuthUiState.Failed(result.error))
            else -> publish(AuthUiState.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE))
        }
    }

    private suspend fun loadDrivers() {
        when (val result = repository.getDrivers()) {
            is AuthResult.Success -> publish(AuthUiState.Choosing(result.value))
            is AuthResult.Failed -> publish(AuthUiState.Failed(result.error))
            else -> publish(AuthUiState.Failed(AuthError.DATA_UNAVAILABLE))
        }
    }

    private fun perform(block: suspend () -> Unit) {
        operation?.cancel()
        secret?.fill('\u0000')
        secret = null
        operation = viewModelScope.launch {
            try { block() } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                publish(AuthUiState.Failed(AuthError.DATA_UNAVAILABLE))
            }
        }
    }

    private suspend fun publish(next: AuthUiState) {
        currentCoroutineContext().ensureActive()
        mutableState.value = next
    }

    override fun onCleared() { secret?.fill('\u0000'); secret = null }

    class Factory(private val readiness: RuntimeReadiness, private val repository: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SetupAuthViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SetupAuthViewModel(readiness, repository) as T
        }
    }
}

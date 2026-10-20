package ru.ffl24.direct.domain

enum class AccessState { ACTIVE, DISABLED, MISSING }

enum class AuthError {
    CONFIGURATION_MISSING, CONFIGURATION_INVALID, CONNECTION_UNAVAILABLE, TLS_REJECTED,
    TIMEOUT, DATABASE_ACCOUNT_REJECTED, DATA_UNAVAILABLE, LOCAL_STORAGE_UNAVAILABLE
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data object InvalidCredentials : AuthResult<Nothing>
    data object DriverDisabled : AuthResult<Nothing>
    data object DriverMissing : AuthResult<Nothing>
    data class Failed(val error: AuthError) : AuthResult<Nothing>
}

interface AuthRepository {
    suspend fun getDrivers(): AuthResult<List<Driver>>
    suspend fun login(driverId: Long, password: CharArray): AuthResult<DriverSession>
    suspend fun restore(): AuthResult<DriverSession?>
    /** Uses the repository's current session, never a driver override supplied by protected UI. */
    suspend fun checkAccess(): AuthResult<DriverSession?>
    suspend fun logout(): AuthResult<Unit>
}

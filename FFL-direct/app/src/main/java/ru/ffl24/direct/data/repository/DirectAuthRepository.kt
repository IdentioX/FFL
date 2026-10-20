package ru.ffl24.direct.data.repository

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.ffl24.direct.data.mysql.DbResult
import ru.ffl24.direct.data.mysql.DirectDbError
import ru.ffl24.direct.data.mysql.DirectMySqlDriverDataSource
import ru.ffl24.direct.data.mysql.LoginLookup
import ru.ffl24.direct.domain.AccessState
import ru.ffl24.direct.domain.AuthError
import ru.ffl24.direct.domain.AuthRepository
import ru.ffl24.direct.domain.AuthResult
import ru.ffl24.direct.domain.Driver
import ru.ffl24.direct.domain.DriverSession
import ru.ffl24.direct.session.AuthSessionManager
import ru.ffl24.direct.session.SessionSnapshot

internal class DirectAuthRepository(
    private val data: DirectMySqlDriverDataSource,
    private val sessions: AuthSessionManager
) : AuthRepository {
    override suspend fun getDrivers(): AuthResult<List<Driver>> = when (val result = data.getDrivers()) {
        is DbResult.Success -> AuthResult.Success(result.value)
        is DbResult.Failed -> AuthResult.Failed(result.error.toAuthError())
    }

    override suspend fun login(driverId: Long, password: CharArray): AuthResult<DriverSession> {
        try {
            val revision = sessions.invalidate()
            sessions.clear(revision)
            if (driverId <= 0 || password.isEmpty()) return AuthResult.InvalidCredentials
            return when (val result = data.login(driverId, password)) {
                is DbResult.Failed -> AuthResult.Failed(result.error.toAuthError())
                is DbResult.Success -> when (val lookup = result.value) {
                    LoginLookup.InvalidCredentials -> AuthResult.InvalidCredentials
                    LoginLookup.Disabled -> AuthResult.DriverDisabled
                    is LoginLookup.Accepted -> {
                        val session = DriverSession(lookup.driver.id, lookup.driver.displayName)
                        currentCoroutineContext().ensureActive()
                        if (sessions.publish(session, revision, persist = true)) AuthResult.Success(session)
                        else AuthResult.Failed(AuthError.DATA_UNAVAILABLE)
                    }
                }
            }
        } catch (failure: IOException) {
            return AuthResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE)
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun restore(): AuthResult<DriverSession?> {
        val before=sessions.current()
        return try {
            revalidate(sessions.saved())
        } catch (failure: IOException) {
            sessions.invalidateIfCurrent(before.revision,preserveSavedIdentity=true)
            AuthResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE)
        }
    }

    override suspend fun checkAccess(): AuthResult<DriverSession?> = revalidate(sessions.current())

    private suspend fun revalidate(snapshot: SessionSnapshot): AuthResult<DriverSession?> {
        val session = snapshot.session ?: return AuthResult.Success(null)
        return try {
            when (val result = data.checkAccess(session.driverId)) {
                is DbResult.Failed -> {
                    // Keep disk hint for explicit retry, but it confers no protected access offline.
                    sessions.invalidateIfCurrent(snapshot.revision,preserveSavedIdentity=true)
                    AuthResult.Failed(result.error.toAuthError())
                }
                is DbResult.Success -> when (result.value) {
                    AccessState.ACTIVE -> {
                        if (sessions.publish(session, snapshot.revision, persist = false)) AuthResult.Success(session)
                        else AuthResult.Failed(AuthError.DATA_UNAVAILABLE)
                    }
                    AccessState.DISABLED -> {
                        sessions.invalidateIfCurrent(snapshot.revision)?.let { sessions.clear(it) }
                        AuthResult.DriverDisabled
                    }
                    AccessState.MISSING -> {
                        sessions.invalidateIfCurrent(snapshot.revision)?.let { sessions.clear(it) }
                        AuthResult.DriverMissing
                    }
                }
            }
        } catch (failure: IOException) {
            sessions.invalidateIfCurrent(snapshot.revision)
            AuthResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE)
        }
    }

    override suspend fun logout(): AuthResult<Unit> {
        val revision = sessions.invalidate()
        return try {
            sessions.clear(revision)
            AuthResult.Success(Unit)
        } catch (failure: IOException) {
            AuthResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE)
        }
    }
}

private fun DirectDbError.toAuthError(): AuthError = when (this) {
    DirectDbError.CONFIGURATION_MISSING -> AuthError.CONFIGURATION_MISSING
    DirectDbError.CONFIGURATION_INVALID -> AuthError.CONFIGURATION_INVALID
    DirectDbError.TLS_VERIFICATION -> AuthError.TLS_REJECTED
    DirectDbError.TIMEOUT -> AuthError.TIMEOUT
    DirectDbError.UNAVAILABLE -> AuthError.CONNECTION_UNAVAILABLE
    DirectDbError.ACCOUNT_REJECTED -> AuthError.DATABASE_ACCOUNT_REJECTED
    DirectDbError.DATABASE_NOT_FOUND, DirectDbError.DRIVER_UNAVAILABLE, DirectDbError.UNSUPPORTED, DirectDbError.QUERY_FAILED -> AuthError.DATA_UNAVAILABLE
}

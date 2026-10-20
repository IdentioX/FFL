package ru.ffl24.direct.data.repository

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.session.AuthSessionManager

internal class DirectOrderDetailsRepository(
    private val data: OrderDetailsDataSource,
    private val auth: AuthRepository,
    private val sessions: AuthSessionManager
) : OrderDetailsRepository {
    override suspend fun getDetails(id: OrderId): DetailsResult<OrderDetails> = protected { data.load(id, it) }
    override suspend fun validateRouteOpened(id: OrderId): DetailsResult<Unit> = protected { data.validateOwner(id, it) }

    private suspend fun <T> protected(read: suspend (Long) -> DbResult<DetailsLookup<T>>): DetailsResult<T> {
        val before = sessions.current()
        val session = before.session ?: return DetailsResult.SessionRequired
        when (val access = auth.checkAccess()) {
            AuthResult.DriverDisabled -> return DetailsResult.DriverDisabled
            is AuthResult.Failed -> return DetailsResult.Failed(access.error)
            is AuthResult.Success -> if (access.value == null || access.value.driverId != session.driverId) return DetailsResult.SessionRequired
            else -> return DetailsResult.SessionRequired
        }
        if (sessions.current() != before) return DetailsResult.SessionRequired
        val result = read(session.driverId)
        currentCoroutineContext().ensureActive()
        if (sessions.current() != before) return DetailsResult.SessionRequired
        return when (result) {
            is DbResult.Failed -> DetailsResult.Failed(result.error.asAuthError())
            is DbResult.Success -> when (val lookup = result.value) {
                is DetailsLookup.Found -> DetailsResult.Success(lookup.value)
                DetailsLookup.NotFound -> DetailsResult.NotFound
                DetailsLookup.Disabled, DetailsLookup.DriverMissing -> {
                    try {
                        sessions.invalidateIfCurrent(before.revision)?.let { sessions.clear(it) }
                    } catch (_: IOException) { return DetailsResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE) }
                    if (lookup == DetailsLookup.Disabled) DetailsResult.DriverDisabled else DetailsResult.SessionRequired
                }
            }
        }
    }
}

private fun DirectDbError.asAuthError() = when (this) {
    DirectDbError.CONFIGURATION_MISSING -> AuthError.CONFIGURATION_MISSING
    DirectDbError.CONFIGURATION_INVALID -> AuthError.CONFIGURATION_INVALID
    DirectDbError.TLS_VERIFICATION -> AuthError.TLS_REJECTED
    DirectDbError.TIMEOUT -> AuthError.TIMEOUT
    DirectDbError.UNAVAILABLE -> AuthError.CONNECTION_UNAVAILABLE
    DirectDbError.ACCOUNT_REJECTED -> AuthError.DATABASE_ACCOUNT_REJECTED
    else -> AuthError.DATA_UNAVAILABLE
}

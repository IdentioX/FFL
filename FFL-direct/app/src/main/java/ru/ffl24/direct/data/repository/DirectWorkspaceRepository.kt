package ru.ffl24.direct.data.repository

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.session.AuthSessionManager

internal class DirectWorkspaceRepository(
    private val data: DirectWorkspaceDataSource,
    private val delivery: DirectDeliveryDataSource,
    private val auth: AuthRepository,
    private val sessions: AuthSessionManager
) : DriverWorkspaceRepository {
    private val writes = Mutex()
    override suspend fun orders(date: String): DetailsResult<List<DriverOrder>> =
        if (!validDate(date)) DetailsResult.Failed(AuthError.DATA_UNAVAILABLE) else protected { id, _ -> data.orders(date, id) }
    override suspend fun directory() = protected { id, _ -> data.directory(id) }
    override suspend fun summary(date: String): DetailsResult<NotificationSummary> =
        if (!validDate(date)) DetailsResult.Failed(AuthError.DATA_UNAVAILABLE) else protected { id, _ -> data.summary(date, id) }
    override suspend fun deliver(id: OrderId) = writes.withLock { protected { driver, current -> delivery.deliver(id, driver, current) } }

    private suspend fun <T> protected(action: suspend (Long, () -> Boolean) -> DbResult<DetailsLookup<T>>): DetailsResult<T> {
        val before = sessions.current()
        val session = before.session ?: return DetailsResult.SessionRequired
        when (val access = auth.checkAccess()) {
            AuthResult.DriverDisabled -> return DetailsResult.DriverDisabled
            is AuthResult.Failed -> return DetailsResult.Failed(access.error)
            is AuthResult.Success -> if (access.value?.driverId != session.driverId) return DetailsResult.SessionRequired
            else -> return DetailsResult.SessionRequired
        }
        if (sessions.current() != before) return DetailsResult.SessionRequired
        val result = action(session.driverId) { sessions.current() == before }
        currentCoroutineContext().ensureActive()
        if (sessions.current() != before) return DetailsResult.SessionRequired
        return when (result) {
            is DbResult.Failed -> DetailsResult.Failed(when (result.error) {
                DirectDbError.TLS_VERIFICATION -> AuthError.TLS_REJECTED
                DirectDbError.TIMEOUT -> AuthError.TIMEOUT
                DirectDbError.UNAVAILABLE -> AuthError.CONNECTION_UNAVAILABLE
                DirectDbError.CONFIGURATION_MISSING -> AuthError.CONFIGURATION_MISSING
                DirectDbError.CONFIGURATION_INVALID -> AuthError.CONFIGURATION_INVALID
                DirectDbError.ACCOUNT_REJECTED -> AuthError.DATABASE_ACCOUNT_REJECTED
                else -> AuthError.DATA_UNAVAILABLE
            })
            is DbResult.Success -> when (val v = result.value) {
                is DetailsLookup.Found -> DetailsResult.Success(v.value)
                DetailsLookup.NotFound -> DetailsResult.NotFound
                DetailsLookup.DriverMissing, DetailsLookup.Disabled -> {
                    try { sessions.invalidateIfCurrent(before.revision)?.let { sessions.clear(it) } }
                    catch (_: IOException) { return DetailsResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE) }
                    if (v == DetailsLookup.Disabled) DetailsResult.DriverDisabled else DetailsResult.SessionRequired
                }
            }
        }
    }
    private fun validDate(date: String) = runCatching { java.time.LocalDate.parse(date).toString() == date }.getOrDefault(false)
}

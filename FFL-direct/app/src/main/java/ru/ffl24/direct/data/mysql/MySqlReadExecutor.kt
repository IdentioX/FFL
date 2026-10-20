package ru.ffl24.direct.data.mysql

import java.sql.Connection
import java.util.ServiceConfigurationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

internal object ConnectionSlots { val permits = Semaphore(2) }

internal sealed interface DbResult<out T> {
    data class Success<T>(val value: T) : DbResult<T>
    data class Failed(val error: DirectDbError) : DbResult<Nothing>
}

/** Shared, scoped read-only IO boundary for probe and auth; connections never leave this scope. */
internal class MySqlReadExecutor(
    private val configuration: MySqlConfiguration,
    private val connector: JdbcConnector = MySqlConnectionFactory(),
    private val policy: ConnectionPolicy = ConnectionPolicy(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val permits: Semaphore = ConnectionSlots.permits
) {
    val queryTimeoutSeconds get() = policy.queryTimeoutSeconds

    suspend fun <T> read(block: (Connection) -> T): DbResult<T> {
        currentCoroutineContext().ensureActive()
        val config = when (val state = configuration) {
            MySqlConfiguration.Missing -> return DbResult.Failed(DirectDbError.CONFIGURATION_MISSING)
            is MySqlConfiguration.Invalid -> return DbResult.Failed(DirectDbError.CONFIGURATION_INVALID)
            is MySqlConfiguration.Ready -> state.config
        }
        return try {
            withTimeout(policy.operationTimeoutMs) {
                try {
                    permits.withPermit {
                        val context = currentCoroutineContext()
                        runInterruptible(dispatcher) {
                            context.ensureActive()
                            connector.open(config, policy).use { connection ->
                                context.ensureActive()
                                // Android has no generic Connection.setNetworkTimeout: driver socketTimeout applies.
                                connection.isReadOnly = true
                                val value = block(connection)
                                context.ensureActive()
                                DbResult.Success(value)
                            }
                        }
                    }
                } finally {
                    currentCoroutineContext().ensureActive()
                }
            }
        } catch (failure: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            DbResult.Failed(DirectDbError.TIMEOUT)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            DbResult.Failed(DirectDbError.from(failure))
        } catch (failure: LinkageError) {
            currentCoroutineContext().ensureActive()
            DbResult.Failed(DirectDbError.DRIVER_UNAVAILABLE)
        } catch (failure: ServiceConfigurationError) {
            currentCoroutineContext().ensureActive()
            DbResult.Failed(DirectDbError.DRIVER_UNAVAILABLE)
        }
    }
}

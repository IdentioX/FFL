package ru.ffl24.direct.data.mysql

import java.sql.SQLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore

/** Technical probe only; shares the bounded read-only IO boundary with P3. */
class MySqlConnectionProbe internal constructor(
    configuration: MySqlConfiguration,
    connector: JdbcConnector = MySqlConnectionFactory(),
    policy: ConnectionPolicy = ConnectionPolicy(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    permits: Semaphore = ConnectionSlots.permits
) {
    private val expectedDatabase = (configuration as? MySqlConfiguration.Ready)?.config?.database
    private val reader = MySqlReadExecutor(configuration, connector, policy, dispatcher, permits)

    suspend fun check(): ConnectionProbeResult {
        return when (val result = reader.read { connection ->
            if (connection.catalog != expectedDatabase) throw SQLException("Invalid database", "42000",1049)
            connection.prepareStatement("SELECT 1").use { statement ->
                statement.queryTimeout = reader.queryTimeoutSeconds
                statement.maxRows = 1
                statement.executeQuery().use { rows ->
                    if (!rows.next() || rows.getInt(1) != 1) throw SQLException("Probe failed")
                }
            }
        }) {
            is DbResult.Success -> ConnectionProbeResult.Reachable
            is DbResult.Failed -> ConnectionProbeResult.Failed(result.error)
        }
    }
}

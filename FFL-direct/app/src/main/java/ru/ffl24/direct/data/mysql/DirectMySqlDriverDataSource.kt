package ru.ffl24.direct.data.mysql

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import ru.ffl24.direct.domain.AccessState
import ru.ffl24.direct.domain.Driver

internal sealed interface LoginLookup {
    data class Accepted(val driver: Driver) : LoginLookup
    data object InvalidCredentials : LoginLookup
    data object Disabled : LoginLookup
}

/** __hr_typepos only. No writes, parol projection, caller-selected table or API fallback. */
internal class DirectMySqlDriverDataSource(private val reader: MySqlReadExecutor) {
    suspend fun getDrivers(): DbResult<List<Driver>> = reader.read { connection ->
        connection.prepareStatement(DRIVERS_SQL).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(driver(rows, withContacts = true))
                    }
                }
            }
        }
    }

    suspend fun checkAccess(driverId: Long): DbResult<AccessState> =
        reader.read { connection -> access(connection, driverId) }

    suspend fun login(driverId: Long, password: CharArray): DbResult<LoginLookup> = reader.read { connection ->
        when (access(connection, driverId)) {
            AccessState.MISSING -> LoginLookup.InvalidCredentials
            AccessState.DISABLED -> LoginLookup.Disabled
            AccessState.ACTIVE -> {
                val matched = connection.prepareStatement(LOGIN_SQL).use { statement ->
                    statement.queryTimeout = reader.queryTimeoutSeconds
                    statement.maxRows = 1
                    statement.setLong(1, driverId)
                    // JDBC requires an immutable String; it is never stored in session, state or logs.
                    statement.setString(2, password.concatToString())
                    statement.executeQuery().use { rows -> if (rows.next()) driver(rows) else null }
                }
                // Re-read after password lookup, including a disable/remove that raced this attempt.
                when (access(connection, driverId)) {
                    AccessState.DISABLED -> LoginLookup.Disabled
                    AccessState.MISSING -> LoginLookup.InvalidCredentials
                    AccessState.ACTIVE -> if (matched == null) LoginLookup.InvalidCredentials else LoginLookup.Accepted(matched)
                }
            }
        }
    }

    private fun access(connection: Connection, driverId: Long): AccessState {
        if (driverId <= 0) return AccessState.MISSING
        return connection.prepareStatement(ACCESS_SQL).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.maxRows = 1
            statement.setLong(1, driverId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) AccessState.MISSING else {
                    // No getInt coercion: non-numeric/null/fractional access cannot become active.
                    val raw = rows.getString("access")?.trim()?.toBigDecimalOrNull()
                    if (raw != null && raw.compareTo(java.math.BigDecimal.ONE) == 0) AccessState.ACTIVE
                    else AccessState.DISABLED
                }
            }
        }
    }

    private fun driver(rows: ResultSet, withContacts: Boolean = false): Driver {
        val id = rows.getLong("driverId")
        if (rows.wasNull() || id <= 0) throw SQLException("Invalid driver identity")
        val name = rows.getString("driverName") ?: throw SQLException("Invalid driver identity")
        if (name.isEmpty()) throw SQLException("Invalid driver identity")
        // Preserve SQL contract: <> '' is not strengthened to TRIM(name) <> ''.
        return Driver(id, name,
            if (withContacts) rows.getString("fio")?.takeUnless { it.isBlank() } else null,
            if (withContacts) rows.getString("mobile")?.takeUnless { it.isBlank() } else null,
            if (withContacts) rows.getString("whatsapp")?.takeUnless { it.isBlank() } else null)
    }

    companion object {
        private const val DRIVERS_SQL = """
            SELECT posTypeId AS driverId, posTypeName AS driverName, fio, mobile, whatsapp
            FROM `__hr_typepos`
            WHERE posTypeId IS NOT NULL AND posTypeName IS NOT NULL
              AND posTypeName <> '' AND access = 1
            ORDER BY driverName
        """
        private const val LOGIN_SQL = """
            SELECT posTypeId AS driverId, posTypeName AS driverName
            FROM `__hr_typepos`
            WHERE posTypeId = ? AND BINARY parol = BINARY ? AND access = 1
              AND posTypeName IS NOT NULL AND posTypeName <> ''
            LIMIT 1
        """
        private const val ACCESS_SQL = """
            SELECT posTypeId AS driverId, access FROM `__hr_typepos`
            WHERE posTypeId = ? LIMIT 1
        """
    }
}

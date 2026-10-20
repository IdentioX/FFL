package ru.ffl24.direct.data.mysql

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import ru.ffl24.direct.domain.*

internal sealed interface DetailsLookup<out T> {
    data class Found<T>(val value: T) : DetailsLookup<T>
    data object NotFound : DetailsLookup<Nothing>
    data object Disabled : DetailsLookup<Nothing>
    data object DriverMissing : DetailsLookup<Nothing>
}

internal interface OrderDetailsDataSource {
    suspend fun load(id: OrderId, driverId: Long): DbResult<DetailsLookup<OrderDetails>>
    suspend fun validateOwner(id: OrderId, driverId: Long): DbResult<DetailsLookup<Unit>>
}

/** All table interpolation is from a closed enum. Every value is a PreparedStatement parameter. */
internal class DirectMySqlOrderDetailsDataSource(private val reader: MySqlReadExecutor) : OrderDetailsDataSource {
    override suspend fun load(id: OrderId, driverId: Long): DbResult<DetailsLookup<OrderDetails>> = reader.read { connection ->
        accessFailure(connection, driverId)?.let { return@read it }
        val row = connection.prepareStatement(detailsSql(id.source)).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.maxRows = 1
            statement.setLong(1, id.legacyId)
            statement.setLong(2, driverId)
            statement.executeQuery().use { rows ->
                if (rows.next()) readRow(rows, id, driverId) else null
            }
        } ?: return@read DetailsLookup.NotFound

        // Exact original name/date, not the normalized display text, are used for history.
        val history = connection.prepareStatement(historySql(id.source)).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.maxRows = 5
            statement.setString(1, row.client)
            statement.setLong(2, id.legacyId)
            statement.setString(3, row.date)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next() && size < 5) {
                        val previousDriverId = rows.getLong("driver_id")
                        if (rows.wasNull()) throw SQLException("Invalid history driver identity")
                        add(PreviousDriver(previousDriverId, rows.getString("driver_name"),
                            normalizeSpaces(rows.getString("driver_phone")), normalizeSpaces(rows.getString("driver_whatsapp")),
                            rows.getString("delivery_date") ?: throw SQLException("Invalid history data"),
                            OrderId(id.source, positiveId(rows, "order_id")), orderNumber(rows.getString("order_number"))))
                    }
                }
            }
        }
        val number = normalizeSpaces(row.number)
        val candidates = if (number == null) emptyList() else connection.prepareStatement(CONTACTS_SQL).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.setString(1, number)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(AddressCandidate(rows.getLong("id"), rows.getString("ooo"), rows.getString("adress"),
                        rows.getString("link"), rows.getString("tell_klient"), rows.getString("tell_gruzchik"),
                        rows.getString("manager_name"), rows.getString("manager_phone"), rows.getString("manager_short_phone")))
                }
            }
        }
        // No resolved-choice cache: two rows with the same number may require different candidates.
        val contacts = OrderContactResolver.resolve(candidates, row.client)
        val parsed = OrderTextParsing.parse(row.client, row.note)
        accessFailure(connection, driverId)?.let { return@read it }
        if (!owns(connection, id, driverId)) return@read DetailsLookup.NotFound
        DetailsLookup.Found(OrderDetails(id, row.sort, orderNumber(row.number), parsed.title,
            normalizeSpaces(row.client).orEmpty(), row.client, row.note, normalizeSpaces(row.note), parsed.deliveryTime,
            parsed.comment, row.invoices, row.status, row.quantity, row.delivered == 1L, row.deliveredAt, contacts, history))
    }

    override suspend fun validateOwner(id: OrderId, driverId: Long): DbResult<DetailsLookup<Unit>> = reader.read { connection ->
        accessFailure(connection, driverId)?.let { return@read it }
        if (owns(connection, id, driverId)) DetailsLookup.Found(Unit) else DetailsLookup.NotFound
    }

    private fun accessFailure(connection: Connection, driverId: Long): DetailsLookup<Nothing>? =
        connection.prepareStatement(ACCESS_SQL).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.maxRows = 1
            statement.setLong(1, driverId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) DetailsLookup.DriverMissing
                else if (rows.getString("access")?.trim()?.toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ONE) == 0) null
                else DetailsLookup.Disabled
            }
        }

    private fun owns(connection: Connection, id: OrderId, driverId: Long): Boolean =
        connection.prepareStatement(ownerSql(id.source)).use { statement ->
            statement.queryTimeout = reader.queryTimeoutSeconds
            statement.maxRows = 1
            statement.setLong(1, id.legacyId)
            statement.setLong(2, driverId)
            statement.executeQuery().use { it.next() }
        }

    private data class Row(val number: String?, val client: String?, val note: String?, val date: String,
        val sort: Long, val invoices: Long, val status: Long, val quantity: Long, val delivered: Long, val deliveredAt: String?)

    private fun readRow(rows: ResultSet, id: OrderId, driverId: Long): Row {
        if (positiveId(rows, "id") != id.legacyId || positiveId(rows, "id_v") != driverId) throw SQLException("Invalid order identity")
        return Row(rows.getString("nomer_z"), rows.getString("name_c"), rows.getString("vremya"),
            rows.getString("date") ?: throw SQLException("Invalid order date"),
            rows.getLong("sort_r"), rows.getLong("col"), rows.getLong("status"), rows.getLong("col_s"),
            rows.getLong("st_vv"), rows.getString("date_att_log"))
    }

    private fun positiveId(rows: ResultSet, column: String): Long {
        val value = rows.getLong(column)
        if (rows.wasNull() || value <= 0) throw SQLException("Invalid order identity")
        return value
    }

    companion object {
        internal fun detailsSql(source: OrderSource) = """
            SELECT r.id, r.nomer_z, r.id_v, r.name_c, r.col, r.vremya, r.date,
                   r.status, r.col_s, r.sort_r, r.st_vv, r.date_att_log
            FROM ${source.wire} r WHERE r.id = ? AND r.id_v = ?
        """.trimIndent()
        internal fun historySql(source: OrderSource) = """
            SELECT r.id_v AS driver_id, name_v(r.id_v) AS driver_name,
                   d.mobile AS driver_phone, d.whatsapp AS driver_whatsapp,
                   r.date AS delivery_date, r.id AS order_id, r.nomer_z AS order_number
            FROM ${source.wire} r LEFT JOIN `__hr_typepos` d ON d.PosTypeId = r.id_v
            WHERE r.name_c = ? AND r.id <> ? AND r.date < ?
            ORDER BY r.date DESC, r.id DESC LIMIT 5
        """.trimIndent()
        internal fun ownerSql(source: OrderSource) = "SELECT id FROM ${source.wire} WHERE id = ? AND id_v = ? LIMIT 1"
        private const val ACCESS_SQL = "SELECT access FROM `__hr_typepos` WHERE posTypeId = ? LIMIT 1"
        internal const val CONTACTS_SQL = """
            SELECT c.id, c.nomer_z, c.link, c.ooo, c.adress, c.tell_klient, c.tell_gruzchik,
                   c.id_m, m.meneger AS manager_name, m.tel AS manager_phone, m.short_tel AS manager_short_phone
            FROM meneger_contr_ffl c LEFT JOIN meneger_ffl m ON m.id = c.id_m
            WHERE TRIM(c.nomer_z) = TRIM(?) ORDER BY c.id ASC
        """
    }
}

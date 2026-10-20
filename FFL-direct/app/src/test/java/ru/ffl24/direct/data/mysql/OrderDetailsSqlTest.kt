package ru.ffl24.direct.data.mysql

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*

class OrderDetailsSqlTest {
    @Test fun bothTablesBindOwnerRawHistoryAndTrimmedNumberCloseEveryResource() = runBlocking {
        for (source in OrderSource.entries) {
            val jdbc = ScriptedDetailsJdbc(source)
            val result = source(jdbc).load(OrderId(source, 3_000_000_000L), 97)
            assertTrue(result is DbResult.Success)
            val value = ((result as DbResult.Success).value as DetailsLookup.Found).value
            assertEquals(OrderId(source, 3_000_000_000L), value.id)
            assertEquals(5, value.previousDrivers.size)
            assertTrue(value.previousDrivers.all { it.orderId.source == source })
            assertEquals("Клиент", value.title)
            assertEquals("Комментарий +70000000000", value.comment)
            assertEquals("с 11:30 Комментарий +70000000000", value.plannedTime)
            assertEquals("  Клиент   Москва Тверская 1 ", value.rawClientText)
            assertEquals(4L, value.invoiceCount)
            assertEquals("Частично готово", value.quantityLabel)
            assertTrue(value.isReady)
            assertFalse(value.isDelivered)
            assertEquals("Проверочный адрес", value.contacts.address)
            assertEquals("12345", value.contacts.managerPhone)
            val detail = jdbc.queries.first { it.kind == "details" }
            assertEquals(mapOf(1 to 3_000_000_000L, 2 to 97L), detail.params)
            assertFalse(detail.sql.contains("date ="))
            val history = jdbc.queries.single { it.kind == "history" }
            assertEquals(mapOf(1 to "  Клиент   Москва Тверская 1 ", 2 to 3_000_000_000L, 3 to "2026-05-18"), history.params)
            assertTrue(history.sql.contains("ORDER BY r.date DESC, r.id DESC LIMIT 5"))
            assertFalse(history.sql.contains("access")) // inactive historical drivers remain valid history
            assertEquals("Z-1", jdbc.queries.single { it.kind == "contacts" }.params[1])
            assertTrue(jdbc.queries.all { it.timeout == 3 })
            assertTrue(jdbc.readOnly)
            assertTrue(jdbc.closed)
            assertEquals(jdbc.queries.size, jdbc.statementsClosed)
            assertEquals(jdbc.queries.size, jdbc.resultsClosed)
            assertTrue(jdbc.queries.all { q -> q.sql.trimStart().startsWith("SELECT") })
        }
    }

    @Test fun missingOrForeignOrderNeverQueriesContactsOrHistory() = runBlocking {
        for (s in OrderSource.entries) {
            val jdbc = ScriptedDetailsJdbc(s, missing = true)
            assertEquals(DbResult.Success(DetailsLookup.NotFound), source(jdbc).load(OrderId(s, 3_000_000_000L), 97))
            assertEquals(listOf("access", "details"), jdbc.queries.map { it.kind })
            assertTrue(jdbc.closed)
        }
    }

    @Test fun disabledOrMissingDriverDoesNotReadOrders() = runBlocking {
        for (access in listOf("0", null, "bad", "1.1")) {
            val jdbc = ScriptedDetailsJdbc(OrderSource.REYS, access = access)
            assertEquals(DbResult.Success(DetailsLookup.Disabled), source(jdbc).load(OrderId(OrderSource.REYS, 3_000_000_000L), 97))
            assertEquals(listOf("access"), jdbc.queries.map { it.kind })
        }
        val missing = ScriptedDetailsJdbc(OrderSource.REYS, driverMissing = true)
        assertEquals(DbResult.Success(DetailsLookup.DriverMissing), source(missing).validateOwner(OrderId(OrderSource.REYS, 1), 97))
    }

    @Test fun blankNumberSkipsContactsUnknownAndNullStatusesAreIndependent() = runBlocking {
        val jdbc = ScriptedDetailsJdbc(OrderSource.REYS, number = " \t", status = 9L, quantity = null, delivered = 1L)
        val found = (source(jdbc).load(OrderId(OrderSource.REYS, 3_000_000_000L), 97) as DbResult.Success).value as DetailsLookup.Found
        assertEquals("Без номера", found.value.orderNumber)
        assertEquals("Неизвестно", found.value.readinessLabel)
        assertEquals("Накладные не готовы", found.value.quantityLabel)
        assertTrue(found.value.isDelivered)
        assertFalse(found.value.canMarkDelivered)
        assertEquals(AddressSource.NOT_FOUND, found.value.contacts.addressSource)
        assertFalse(jdbc.queries.any { it.kind == "contacts" })
    }

    @Test fun ambiguousContactsDoNotUseParsedClientAddress() = runBlocking {
        for (s in OrderSource.entries) {
            val jdbc = ScriptedDetailsJdbc(s, ambiguous = true)
            val found = (source(jdbc).load(OrderId(s, 3_000_000_000L), 97) as DbResult.Success).value as DetailsLookup.Found
            assertEquals(AddressSource.AMBIGUOUS, found.value.contacts.addressSource)
            assertNull(found.value.contacts.address)
            assertNull(found.value.contacts.clientPhone)
            assertNull(found.value.contacts.routeUrl)
        }
    }

    @Test fun routeOpenedOnlyChecksAccessAndOwnershipAndPerformsNoWrite() = runBlocking {
        for (s in OrderSource.entries) {
            val jdbc = ScriptedDetailsJdbc(s)
            assertEquals(DbResult.Success(DetailsLookup.Found(Unit)), source(jdbc).validateOwner(OrderId(s, 1), 97))
            assertEquals(listOf("access", "owner"), jdbc.queries.map { it.kind })
            assertEquals(mapOf(1 to 1L, 2 to 97L), jdbc.queries.last().params)
        }
    }

    @Test fun ownershipChangeOrAccessRevocationDuringReadDoesNotReturnDetails() = runBlocking {
        val lost = ScriptedDetailsJdbc(OrderSource.REYS, ownerLost = true)
        assertEquals(DbResult.Success(DetailsLookup.NotFound), source(lost).load(OrderId(OrderSource.REYS, 3_000_000_000L), 97))
        val revoked = ScriptedDetailsJdbc(OrderSource.REYS, revoked = true)
        assertEquals(DbResult.Success(DetailsLookup.Disabled), source(revoked).load(OrderId(OrderSource.REYS, 3_000_000_000L), 97))
    }

    @Test fun sqlFailureDoesNotExposeRawExceptionAndClosesResources() = runBlocking {
        val jdbc = ScriptedDetailsJdbc(OrderSource.REYS, failContacts = true)
        assertEquals(DbResult.Failed(DirectDbError.QUERY_FAILED), source(jdbc).load(OrderId(OrderSource.REYS, 3_000_000_000L), 97))
        assertTrue(jdbc.closed)
        assertEquals(jdbc.queries.size, jdbc.statementsClosed)
        assertEquals(jdbc.queries.size - 1, jdbc.resultsClosed)
    }

    @Test fun nullHistoryIdentityIsADataErrorRatherThanAnInventedDriverZero() = runBlocking {
        val jdbc = ScriptedDetailsJdbc(OrderSource.REYS_DEN, historyDriverId = null)
        assertEquals(DbResult.Failed(DirectDbError.QUERY_FAILED), source(jdbc).load(OrderId(OrderSource.REYS_DEN, 3_000_000_000L), 97))
        assertTrue(jdbc.closed)
        assertEquals(jdbc.queries.size, jdbc.statementsClosed)
        assertEquals(jdbc.queries.size, jdbc.resultsClosed)
    }

    private fun source(jdbc: ScriptedDetailsJdbc) = DirectMySqlOrderDetailsDataSource(
        MySqlReadExecutor(fixtureReady(), JdbcConnector { _, _ -> jdbc.connection }))
}

private data class SqlCall(val sql: String, val kind: String, val params: MutableMap<Int, Any?> = mutableMapOf(), var timeout: Int = 0)

/** Scripted JDBC boundary; deliberately no network or MySQL emulation. */
private class ScriptedDetailsJdbc(
    private val expectedSource: OrderSource, private val missing: Boolean = false, private val access: String? = "1",
    private val driverMissing: Boolean = false, private val number: String? = " Z-1 ",
    private val status: Long? = 2, private val quantity: Long? = 1, private val delivered: Long? = 0,
    private val ambiguous: Boolean = false, private val ownerLost: Boolean = false, private val revoked: Boolean = false,
    private val failContacts: Boolean = false, private val historyDriverId: Long? = 98L
) {
    val queries = mutableListOf<SqlCall>()
    var closed = false
    var readOnly = false
    var statementsClosed = 0
    var resultsClosed = 0
    val connection: Connection = jdbcProxy(Connection::class.java) { name, args -> when (name) {
        "setReadOnly" -> { readOnly = args!![0] as Boolean; null }
        "close" -> { closed = true; null }
        "prepareStatement" -> statement(args!![0] as String)
        else -> error("Unexpected Connection.$name")
    } }
    private fun statement(sql: String): PreparedStatement {
        val kind = when {
            "SELECT access" in sql -> "access"
            "meneger_contr_ffl" in sql -> "contacts"
            "r.name_c = ?" in sql -> "history"
            "SELECT id FROM" in sql -> "owner"
            else -> "details"
        }
        if (kind in listOf("details", "history", "owner")) {
            assertTrue(sql, sql.contains("FROM ${expectedSource.wire}"))
            val other = if (expectedSource == OrderSource.REYS) "reys_den" else "FROM reys "
            assertFalse(sql, sql.contains(other))
        }
        val call = SqlCall(sql, kind)
        queries.add(call)
        return jdbcProxy(PreparedStatement::class.java) { name, args -> when (name) {
            "setQueryTimeout" -> { call.timeout = args!![0] as Int; null }
            "setMaxRows" -> null
            "setLong", "setString" -> { call.params[args!![0] as Int] = args[1]; null }
            "close" -> { statementsClosed++; null }
            "executeQuery" -> {
                if (kind == "contacts" && failContacts) throw SQLException("PRIVATE_ADDRESS_PASSWORD", "HY000")
                result(when (kind) {
                    "access" -> if (driverMissing) emptyList() else listOf(mapOf("access" to if (revoked && queries.count { it.kind == "access" } > 1) "0" else access))
                    "owner" -> if (ownerLost) emptyList() else listOf(mapOf("id" to 1L))
                    "details" -> if (missing) emptyList() else listOf(mapOf("id" to 3_000_000_000L, "id_v" to 97L,
                        "nomer_z" to number, "name_c" to "  Клиент   Москва Тверская 1 ", "vremya" to "с 11:30 Комментарий +70000000000",
                        "date" to "2026-05-18", "sort_r" to null, "col" to 4L, "status" to status, "col_s" to quantity,
                        "st_vv" to delivered, "date_att_log" to null))
                    "history" -> List(7) { i -> mapOf("driver_id" to historyDriverId, "driver_name" to "Тестовый водитель",
                        "driver_phone" to "+70000000000", "driver_whatsapp" to "+70000000001", "delivery_date" to "2026-05-17",
                        "order_id" to (100L + i), "order_number" to " Старый номер ") }
                    "contacts" -> List(if (ambiguous) 2 else 1) { i -> mapOf("id" to (i+1L), "ooo" to "Тест",
                        "adress" to "Проверочный адрес", "link" to "https://example.invalid/route", "tell_klient" to "+70000000000",
                        "tell_gruzchik" to null, "manager_name" to "Тест", "manager_phone" to " ", "manager_short_phone" to "12345") }
                    else -> error("Unexpected query")
                })
            }
            else -> error("Unexpected Statement.$name")
        } }
    }
    private fun result(data: List<Map<String, Any?>>): ResultSet {
        var index = -1
        var wasNull = false
        return jdbcProxy(ResultSet::class.java) { name, args -> when (name) {
            "next" -> { index++; index < data.size }
            "close" -> { resultsClosed++; null }
            "wasNull" -> wasNull
            "getLong", "getString" -> {
                val value = data[index][args!![0] as String]
                wasNull = value == null
                if (name == "getString") value?.toString() else (value as? Number)?.toLong() ?: 0L
            }
            else -> error("Unexpected ResultSet.$name")
        } }
    }
}

private fun <T : Any> jdbcProxy(type: Class<T>, call: (String, Array<out Any?>?) -> Any?): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> call(method.name, args) }
)

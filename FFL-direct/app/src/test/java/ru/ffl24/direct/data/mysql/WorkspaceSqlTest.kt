package ru.ffl24.direct.data.mysql

import java.lang.reflect.Proxy
import java.sql.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*

class WorkspaceSqlTest {
    @Test fun orderNumberPreservesInternalWhitespaceLikeDetailsAndBackend() = runBlocking {
        val jdbc=ScriptJdbc(query={sql,_->when {
            "SELECT access" in sql -> listOf(mapOf("access" to "1"))
            "SELECT orders.*" in sql -> listOf(order("reys",0)+("nomer_z" to "  FFL  42  "))
            else -> emptyList()
        }})
        val rows=((source(jdbc).orders("2026-05-18",97) as DbResult.Success).value as DetailsLookup.Found).value
        assertEquals("FFL  42",rows.single().orderNumber)
    }

    @Test fun contactsAreBatchedWithoutCachingTheResolvedChoiceForDifferentClients() = runBlocking {
        val jdbc=ScriptJdbc(query={sql,_->when {
            "SELECT access" in sql -> listOf(mapOf("access" to "1"))
            "SELECT orders.*" in sql -> listOf(
                order("reys",0)+mapOf("nomer_z" to "same","name_c" to "Первая улица"),
                order("reys_den",0)+mapOf("nomer_z" to "same","name_c" to "Вторая улица"),
                order("reys_den",1)+mapOf("nomer_z" to "different","name_c" to "Без совпадения"))
            "FROM meneger_contr_ffl" in sql || "JOIN meneger_contr_ffl" in sql -> listOf(
                mapOf("requested_number" to "same","id" to 1L,"adress" to "Первая улица"),
                mapOf("requested_number" to "same","id" to 2L,"adress" to "Вторая улица"))
            else -> error("Unexpected SQL")
        }})
        val rows=((source(jdbc).orders("2026-05-18",97) as DbResult.Success).value as DetailsLookup.Found).value
        assertEquals(listOf("Первая улица","Вторая улица",null),rows.map { it.contacts.address })
        val contacts=jdbc.calls.filter { "JOIN meneger_contr_ffl" in it.sql || "FROM meneger_contr_ffl" in it.sql }
        assertEquals(1,contacts.size)
        assertEquals(mapOf(1 to "same",2 to "different"),contacts.single().params)
        assertEquals(jdbc.calls.size,jdbc.closedStatements)
        assertEquals(jdbc.calls.size,jdbc.closedResults)
    }
    @Test fun listBindsBothTablesRetainsSourcesAndDeliveredSection() = runBlocking {
        val jdbc=ScriptJdbc(query = { sql,_ -> when {
            "SELECT access" in sql -> listOf(mapOf("access" to "1"))
            "SELECT orders.*" in sql -> listOf(order("reys",0),order("reys_den",0),order("reys_den",1))
            else -> error(sql)
        } })
        val result=source(jdbc).orders("2026-05-18",97) as DbResult.Success
        val rows=(result.value as DetailsLookup.Found).value
        assertEquals(3,rows.size)
        assertEquals(listOf(OrderSection.FIRST,OrderSection.SECOND,OrderSection.DELIVERED),rows.map { it.section })
        assertEquals(3,rows.map { it.id }.distinct().size) // same physical id in different tables stays distinct
        assertEquals(3_000_000_000L,rows.first().id.legacyId)
        assertEquals(4L,rows.first().invoiceCount)
        assertEquals(1L,rows.first().quantityReadiness)
        assertEquals(mapOf(1 to "2026-05-18",2 to 97L,3 to "2026-05-18",4 to 97L),jdbc.calls[1].params)
        assertTrue(jdbc.calls[1].sql.contains("UNION ALL"));assertTrue(jdbc.calls[1].sql.contains("is_delivered ASC"))
        assertTrue(jdbc.closed);assertEquals(jdbc.calls.size,jdbc.closedStatements);assertEquals(jdbc.calls.size,jdbc.closedResults)
        assertTrue(jdbc.calls.all { it.sql.trim().startsWith("SELECT") && it.timeout==3 })
    }
    @Test fun directoryPreservesRawPhonesMissingCardAndUsesMobileFallback() = runBlocking {
        val jdbc=ScriptJdbc(query = { sql,_ -> when {
            "SELECT access" in sql -> listOf(mapOf("access" to "1"))
            "FROM nastroiki" in sql -> listOf(mapOf("cardNumber" to null,"cardHolder" to null))
            "FROM phone" in sql -> listOf(mapOf("name" to "Desk","phone" to " +70000000000 "))
            "AS driverId" in sql -> listOf(mapOf("driverId" to 97L,"name" to "Driver","mobile" to " ","whatsapp" to " +70000000001 "))
            else -> error(sql)
        } })
        val directory=((source(jdbc).directory(97) as DbResult.Success).value as DetailsLookup.Found).value
        assertNull(directory.cardNumber);assertEquals(" +70000000000 ",directory.phones.single().phone)
        assertEquals(" +70000000001 ",directory.drivers.single().phone)
        assertTrue(jdbc.calls.single { "AS driverId" in it.sql }.sql.contains("access = 1"))
        assertNull(directoryNumber(null," "));assertEquals("raw",directoryNumber("raw","fallback"))
        assertEquals(5,jdbc.calls.size)
    }
    @Test fun disabledMissingAndRevokedAccessNeverPublishProtectedData() = runBlocking {
        for (value in listOf("0",null,"wrong","1.2")) {
            val jdbc=ScriptJdbc(query = { _,_ -> listOf(mapOf("access" to value)) })
            assertEquals(DbResult.Success(DetailsLookup.Disabled),source(jdbc).directory(97))
            assertEquals(1,jdbc.calls.size)
        }
        val missing=ScriptJdbc(query = { _,_ -> emptyList() })
        assertEquals(DbResult.Success(DetailsLookup.DriverMissing),source(missing).orders("2026-05-18",97))
        var checks=0
        val revoked=ScriptJdbc(query = { sql,_ -> if("SELECT access" in sql) listOf(mapOf("access" to if(++checks==1) "1" else "0")) else emptyList() })
        assertEquals(DbResult.Success(DetailsLookup.Disabled),source(revoked).orders("2026-05-18",97))
    }
    @Test fun summaryCountsAndLongSecondRouteIdsUseDriverAndDate() = runBlocking {
        val jdbc=ScriptJdbc(query = { sql,_ -> when {
            "SELECT access" in sql -> listOf(mapOf("access" to "1"))
            "AS first_count" in sql -> listOf(mapOf("first_count" to 4L,"second_count" to 2L,"delivered_count" to 3L))
            "SELECT id FROM reys_den" in sql -> listOf(mapOf("id" to 3_000_000_000L),mapOf("id" to 2L))
            else -> error(sql)
        } })
        val v=((source(jdbc).summary("2026-05-18",97) as DbResult.Success).value as DetailsLookup.Found).value
        assertEquals(6L,v.active);assertEquals(3L,v.delivered)
        assertEquals(listOf("reys_den:3000000000","reys_den:2"),v.secondIds.map { it.wire })
        assertEquals(mapOf(1 to "2026-05-18",2 to 97L),jdbc.calls[2].params)
        assertTrue(jdbc.calls[2].sql.contains("COALESCE(st_vv,0)<>1"))
    }
    @Test fun queryErrorsAreSafeAndResourcesClose() = runBlocking {
        val jdbc=ScriptJdbc(query = { sql,_ -> if("SELECT access" in sql) listOf(mapOf("access" to "1")) else throw SQLException("PRIVATE_VALUE") })
        assertEquals(DbResult.Failed(DirectDbError.QUERY_FAILED),source(jdbc).orders("2026-05-18",97))
        assertTrue(jdbc.closed);assertEquals(2,jdbc.closedStatements)
    }
    private fun source(jdbc: ScriptJdbc)=DirectWorkspaceDataSource(MySqlReadExecutor(fixtureReady(),JdbcConnector { _,_->jdbc.connection }))
    private fun order(source: String,delivered: Long)=mapOf("source_table" to source,"id" to if(delivered==1L) 2L else 3_000_000_000L,"id_v" to 97L,
        "driver_name" to "Fixture","date" to "2026-05-18","nomer_z" to null,"name_c" to "Client","vremya" to null,
        "sort_r" to 0L,"col" to 4L,"status" to 2L,"col_s" to 1L,"st_vv" to delivered,"date_att_log" to null)
}

internal data class SqlRecorded(val sql: String,val params: MutableMap<Int,Any?> = mutableMapOf(),var timeout: Int=0)
internal class ScriptJdbc(private val query: (String,Map<Int,Any?>)->List<Map<String,Any?>>,
    private val update: (String,Map<Int,Any?>)->Int={_,_->1},private val commitFailure: Boolean=false) {
    val calls=mutableListOf<SqlRecorded>();var closed=false;var commits=0;var rollbacks=0
    var closedStatements=0;var closedResults=0
    val connection: Connection=proxy(Connection::class.java) { name,args -> when(name) {
        "prepareStatement" -> statement(args!![0] as String)
        "setReadOnly","setAutoCommit" -> null
        "close" -> { closed=true;null }
        "commit" -> { commits++;if(commitFailure) throw SQLException("lost ack");null }
        "rollback" -> { rollbacks++;null }
        else -> error("Connection.$name")
    } }
    private fun statement(sql: String): PreparedStatement {
        val call=SqlRecorded(sql);calls.add(call)
        return proxy(PreparedStatement::class.java) { name,args -> when(name) {
            "setQueryTimeout" -> { call.timeout=args!![0] as Int;null }
            "setMaxRows" -> null
            "setLong","setString" -> { call.params[args!![0] as Int]=args[1];null }
            "executeQuery" -> rows(query(sql,call.params))
            "executeUpdate" -> update(sql,call.params)
            "close" -> { closedStatements++;null }
            else -> error("Statement.$name")
        } }
    }
    private fun rows(data: List<Map<String,Any?>>): ResultSet {
        var index=-1;var nil=false
        return proxy(ResultSet::class.java) { name,args -> when(name) {
            "next" -> { index++;index<data.size }
            "wasNull" -> nil
            "getString","getLong" -> {
                val v=data[index][args!![0] as String];nil=v==null
                if(name=="getString") v?.toString() else (v as? Number)?.toLong() ?: 0L
            }
            "close" -> { closedResults++;null }
            else -> error("Rows.$name")
        } }
    }
}
private fun <T: Any> proxy(type: Class<T>,call: (String,Array<out Any?>?)->Any?): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,m,a -> call(m.name,a) }))

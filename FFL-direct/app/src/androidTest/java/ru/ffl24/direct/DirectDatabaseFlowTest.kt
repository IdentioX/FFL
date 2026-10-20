package ru.ffl24.direct

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.*

/** Actual MySQL 8.4 with disposable synthetic schema, never the user's legacy database. */
class DirectDatabaseFlowTest {
    @Test fun realTlsJdbcLoginReadAndIdempotentDeliveryBothTables() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=java.io.File(instrumentation.targetContext.filesDir,"completion-db.json")
        assumeTrue("Disposable MySQL fixture not configured",file.exists())
        val settings=JSONObject(file.readText())
        require(settings.getString("fixtureMarker")=="ffl-direct-owned-fixture-20261017")
        val ca=settings.optString("caPem").takeIf { it.isNotBlank() }
            ?: instrumentation.context.assets.open("fixture-ca.pem").bufferedReader().use { it.readText() }
        val dbSettings=DirectMySqlSettings(settings.getString("host"),settings.getString("port"),
            settings.getString("database"),settings.getString("user"),settings.getString("password"),"true",ca)
        val graph=AppGraph.create("DIRECT_MYSQL",dbSettings)
        assertEquals(RuntimeMode.DIRECT_MYSQL,graph.mode)
        assertEquals(ConnectionProbeResult.Reachable,graph.connectionProbe.check())
        val drivers=(graph.authRepository.getDrivers() as AuthResult.Success).value
        assertEquals(listOf(97L),drivers.map { it.id })
        assertEquals(AuthResult.InvalidCredentials,graph.authRepository.login(97,"wrong-fixture-password".toCharArray()))
        assertEquals(AuthResult.DriverDisabled,graph.authRepository.login(98,"not-used".toCharArray()))
        assertTrue(graph.authRepository.login(97,"driver-fixture-only".toCharArray()) is AuthResult.Success)
        val orders=(graph.workspaceRepository.orders("2026-05-18") as DetailsResult.Success).value
        assertEquals(3,orders.size)
        assertTrue(orders.any { it.section==OrderSection.FIRST });assertTrue(orders.any { it.section==OrderSection.SECOND })
        assertTrue(orders.any { it.section==OrderSection.DELIVERED })
        val first=OrderId(OrderSource.REYS,1)
        val details=(graph.orderDetailsRepository.getDetails(first) as DetailsResult.Success).value
        assertEquals("Synthetic test address",details.contacts.address)
        assertEquals("с 11:30",details.deliveryTime)
        assertEquals(DetailsResult.NotFound,graph.orderDetailsRepository.getDetails(OrderId(OrderSource.REYS,3)))
        val directory=(graph.workspaceRepository.directory() as DetailsResult.Success).value
        assertNull(directory.cardNumber);assertEquals(1,directory.drivers.size);assertEquals(1,directory.phones.size)
        val summary=(graph.workspaceRepository.summary("2026-05-18") as DetailsResult.Success).value
        assertEquals(2L,summary.active);assertEquals(listOf(OrderId(OrderSource.REYS_DEN,1)),summary.secondIds)
        // Inject a client failure AFTER a real UPDATE, proving rollback on the actual InnoDB server.
        val config=(DirectMySqlConfig.parse(dbSettings) as MySqlConfiguration.Ready).config
        MySqlConnectionFactory().open(config,ConnectionPolicy()).use { connection ->
            fun forward(target: Any,method: java.lang.reflect.Method,args: Array<out Any?>?): Any? =
                try { method.invoke(target,*(args ?: emptyArray())) }
                catch(e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
            var injectedAfterUpdate=false
            val proxy=java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader,arrayOf(java.sql.Connection::class.java)) { _,method,args ->
                val value=forward(connection,method,args)
                if(method.name=="prepareStatement" && (args?.get(0) as? String)?.startsWith("UPDATE")==true) {
                    java.lang.reflect.Proxy.newProxyInstance(java.sql.PreparedStatement::class.java.classLoader,arrayOf(java.sql.PreparedStatement::class.java)) { _,m,a ->
                        val result=forward(value!!,m,a)
                        if(m.name=="executeUpdate") { injectedAfterUpdate=true;throw java.sql.SQLException("Injected fixture failure after UPDATE") }
                        result
                    }
                } else value
            } as java.sql.Connection
            var failed=false
            try { deliveryTransaction(proxy,first,97,3) { true } } catch(_: java.sql.SQLException) { failed=true }
            assertTrue(failed);assertTrue(injectedAfterUpdate)
            connection.prepareStatement("SELECT st_vv FROM reys WHERE id=1 AND id_v=97").use { statement ->
                statement.executeQuery().use { rows -> assertTrue(rows.next());assertEquals(0,rows.getInt(1)) }
            }
            connection.rollback()
        }
        assertEquals(DetailsResult.NotFound,graph.workspaceRepository.deliver(OrderId(OrderSource.REYS,3)))
        for(source in OrderSource.entries) {
            val id=OrderId(source,1)
            val result=(graph.workspaceRepository.deliver(id) as DetailsResult.Success).value
            assertEquals(DeliveryStatus.DELIVERED,result.status);assertNotNull(result.deliveredAt)
            val repeated=(graph.workspaceRepository.deliver(id) as DetailsResult.Success).value
            assertEquals(DeliveryStatus.ALREADY_DELIVERED,repeated.status);assertEquals(result.deliveredAt,repeated.deliveredAt)
            val after=(graph.orderDetailsRepository.getDetails(id) as DetailsResult.Success).value
            assertTrue(after.isDelivered);assertEquals(2L,after.status)
            assertEquals(if(source==OrderSource.REYS) 3L else 2L,after.invoiceCount)
        }
        val after=(graph.workspaceRepository.summary("2026-05-18") as DetailsResult.Success).value
        assertEquals(0L,after.active);assertEquals(3L,after.delivered);assertTrue(after.secondIds.isEmpty())
        assertTrue(graph.authRepository.logout() is AuthResult.Success)
        assertEquals(DetailsResult.SessionRequired,graph.workspaceRepository.orders("2026-05-18"))
    }
}

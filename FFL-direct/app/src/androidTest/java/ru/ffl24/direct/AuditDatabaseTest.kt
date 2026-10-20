package ru.ffl24.direct

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.*

/** Tests only the reserved fictional rows inside the user's restored local schema. */
class AuditDatabaseTest {
    @Test fun operatorRevocationAfterLoginBlocksReadsAndDelivery() = runBlocking {
        val db=owned()
        val graph=(db.context.applicationContext as DirectApplication).graph
        assertTrue(graph.authRepository.login(db.driver,db.password.toCharArray()) is AuthResult.Success)
        val directory=java.io.File(db.context.filesDir,"restored-private").apply { mkdirs() }
        java.io.File(directory,"revoke-ready").writeText("READY")
        withTimeout(30_000) { while(!java.io.File(directory,"revoke-ack").exists()) delay(100) }
        assertTrue(graph.authRepository.checkAccess()==AuthResult.DriverDisabled)
        assertTrue(graph.workspaceRepository.orders(db.date)==DetailsResult.SessionRequired)
        assertTrue(graph.workspaceRepository.deliver(OrderId(OrderSource.REYS,db.order))==DetailsResult.SessionRequired)
        MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { c ->
            c.prepareStatement("SELECT st_vv,date_att_log FROM reys WHERE id=? AND id_v=?").use { s ->
                s.setLong(1,db.order);s.setLong(2,db.driver)
                s.executeQuery().use { r -> assertTrue(r.next() && r.getInt(1)==0 && r.getString(2)==null) }
            }
        }
        Unit
    }
    @Test fun simultaneousRestoresKeepThePersistentSessionAndLogoutWins() = runBlocking {
        val db=owned()
        val graph=(db.context.applicationContext as DirectApplication).graph
        assertTrue(graph.authRepository.login(db.driver,db.password.toCharArray()) is AuthResult.Success)
        val results=(1..4).map { async(Dispatchers.IO) { graph.authRepository.restore() } }.awaitAll()
        assertTrue(results.all { it is AuthResult.Success && it.value?.driverId==db.driver })
        assertTrue(graph.authRepository.logout() is AuthResult.Success)
        assertTrue(graph.authRepository.restore()==AuthResult.Success<DriverSession?>(null))
        assertTrue(graph.workspaceRepository.orders(db.date)==DetailsResult.SessionRequired)
        Unit
    }
    private fun owned(): OwnedTestDatabase = OwnedTestDatabase().also { assertTrue(it.restored && it.driver==2000000001L) }
    private fun business(c: Connection,id: OrderId,driver: Long): Map<String,String?> =
        c.prepareStatement("SELECT * FROM ${id.source.wire} WHERE id=? AND id_v=?").use { s ->
            s.setLong(1,id.legacyId);s.setLong(2,driver)
            s.executeQuery().use { r ->
                assertTrue(r.next())
                (1..r.metaData.columnCount).map { r.metaData.getColumnName(it) }
                    .filter { it !in setOf("st_vv","date_att_log") }.associateWith { r.getString(it) }
            }
        }
    private fun receipt(result: DbResult<DetailsLookup<DeliveryReceipt>>): DeliveryReceipt {
        assertTrue(result is DbResult.Success && result.value is DetailsLookup.Found)
        return ((result as DbResult.Success).value as DetailsLookup.Found).value
    }

    @Test fun concurrentIndependentConnectionsDeliverEachSourceOnlyOnce() = runBlocking {
        val db=owned()
        for(source in OrderSource.entries) {
            val id=OrderId(source,db.order)
            val before=MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { business(it,id,db.driver) }
            val barrier=CyclicBarrier(2)
            val connector=JdbcConnector { config, policy ->
                MySqlConnectionFactory().open(config,policy).also { c ->
                    try { barrier.await(8,TimeUnit.SECONDS) } catch(e: Exception) { c.close();throw e }
                }
            }
            val results=withTimeout(30_000) { (1..2).map {
                async(Dispatchers.IO) { receipt(DirectDeliveryDataSource(DirectMySqlConfig.parse(db.settings),connector)
                    .deliver(id,db.driver) { true }) }
            }.awaitAll() }
            assertTrue(results.map { it.status }.toSet()==setOf(DeliveryStatus.DELIVERED,DeliveryStatus.ALREADY_DELIVERED))
            assertTrue(results[0].deliveredAt!=null && results[0].deliveredAt==results[1].deliveredAt)
            MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { c ->
                assertTrue(before==business(c,id,db.driver))
                c.prepareStatement("SELECT st_vv,date_att_log FROM ${source.wire} WHERE id=? AND id_v=?").use { s ->
                    s.setLong(1,db.order);s.setLong(2,db.driver);s.executeQuery().use { r ->
                        assertTrue(r.next() && r.getInt(1)==1 && r.getString(2)==results[0].deliveredAt)
                    }
                }
            }
        }
        Unit
    }

    @Test fun accessRowIsLockedUntilTheDeliveryTransactionCommits() = runBlocking {
        val db=owned();val id=OrderId(OrderSource.REYS,db.order)
        MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { delivery ->
            MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { contender ->
                contender.autoCommit=false
                contender.createStatement().use { it.execute("SET SESSION innodb_lock_wait_timeout=1") }
                var checks=0;var blocked=false
                fun lockAccess() = contender.prepareStatement("SELECT access FROM __hr_typepos WHERE posTypeId=? FOR UPDATE").use { s ->
                    s.queryTimeout=3;s.setLong(1,db.driver);s.executeQuery().use { r -> assertTrue(r.next() && r.getInt(1)==1) }
                }
                val result=deliveryTransaction(delivery,id,db.driver,3) {
                    if(++checks==2) {
                        try { lockAccess() } catch(e: SQLException) { assertTrue(e.errorCode==1205);blocked=true }
                        finally { contender.rollback() }
                    }
                    true
                }
                assertTrue(blocked && result is DetailsLookup.Found && result.value.status==DeliveryStatus.DELIVERED)
                lockAccess();contender.rollback() // lock released by the real commit
            }
        }
        Unit
    }

    @Test fun connectionLossAfterRealUpdateIsUnknownAndServerRollsBack() = runBlocking {
        val db=owned();val id=OrderId(OrderSource.REYS,db.order)
        val before=MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { business(it,id,db.driver) }
        var disconnected=false
        fun forward(target: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? = try {
            method.invoke(target,*(args ?: emptyArray()))
        } catch(e: InvocationTargetException) { throw e.targetException }
        val connector=JdbcConnector { config,policy ->
            val real=MySqlConnectionFactory().open(config,policy)
            Proxy.newProxyInstance(Connection::class.java.classLoader,arrayOf(Connection::class.java)) { _,method,args ->
                val value=forward(real,method,args)
                if(method.name=="prepareStatement" && (args?.get(0) as? String)?.startsWith("UPDATE")==true)
                    Proxy.newProxyInstance(PreparedStatement::class.java.classLoader,arrayOf(PreparedStatement::class.java)) { _,m,a ->
                        val result=forward(value!!,m,a)
                        if(m.name=="executeUpdate") { real.close();disconnected=true;throw SQLException("Owned connection loss","08006") }
                        result
                    } else value
            } as Connection
        }
        val result=receipt(DirectDeliveryDataSource(DirectMySqlConfig.parse(db.settings),connector).deliver(id,db.driver) { true })
        assertTrue(disconnected && result.status==DeliveryStatus.UNKNOWN)
        MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { c ->
            assertTrue(before==business(c,id,db.driver))
            c.prepareStatement("SELECT st_vv,date_att_log FROM reys WHERE id=? AND id_v=?").use { s ->
                s.setLong(1,db.order);s.setLong(2,db.driver);s.executeQuery().use { r -> assertTrue(r.next() && r.getInt(1)==0 && r.getString(2)==null) }
            }
        }
        Unit
    }
}

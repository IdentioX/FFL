package ru.ffl24.direct

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.*

/** Actual uploaded schema/data. Personal values remain in memory, never assertions/logs. */
class RestoredDatabaseFlowTest {
    @Test fun pairedLegacyTriggerRollsBackAndIdempotentRepeatDoesNotDuplicate() = runBlocking {
        val db=OwnedTestDatabase();assumeTrue(db.restored)
        val graph=AppGraph.create(BuildConfig.DATA_SOURCE_MODE, db.settings)
        assertTrue(graph.authRepository.login(db.driver,db.password.toCharArray()) is AuthResult.Success)
        val id=OrderId(OrderSource.REYS,db.paired)
        fun count(): Int = MySqlConnectionFactory().open(db.verificationJdbc,ConnectionPolicy()).use { c ->
            c.prepareStatement("SELECT COUNT(*) FROM reys_bf WHERE nomer_z LIKE ?").use { s ->
                s.setString(1,"FFL-E2E-PAIRED%");s.executeQuery().use { r -> assertTrue(r.next());r.getInt(1) }
            }
        }
        MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { c ->
            assertTrue(count()==0)
            var injected=false
            fun forward(target: Any,method: java.lang.reflect.Method,args: Array<out Any?>?): Any? = try {
                method.invoke(target,*(args ?: emptyArray()))
            } catch(e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
            val proxy=java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader,arrayOf(java.sql.Connection::class.java)) { _,m,a ->
                val result=forward(c,m,a)
                if(m.name=="prepareStatement" && (a?.get(0) as? String)?.startsWith("UPDATE")==true)
                    java.lang.reflect.Proxy.newProxyInstance(java.sql.PreparedStatement::class.java.classLoader,arrayOf(java.sql.PreparedStatement::class.java)) { _,method,args ->
                        val value=forward(result!!,method,args)
                        if(method.name=="executeUpdate") { injected=true;throw java.sql.SQLException("Owned failure after trigger execution") }
                        value
                    } else result
            } as java.sql.Connection
            var failed=false
            try { deliveryTransaction(proxy,id,db.driver,3) { true } } catch(_: java.sql.SQLException) { failed=true }
            assertTrue(injected && failed && count()==0)
            c.prepareStatement("SELECT st_vv FROM reys WHERE id=? AND id_v=?").use { s ->
                s.setLong(1,db.paired);s.setLong(2,db.driver);s.executeQuery().use { r -> assertTrue(r.next());assertTrue(r.getInt(1)==0) }
            }
            c.rollback()
        }
        val delivered=(graph.workspaceRepository.deliver(id) as DetailsResult.Success).value
        assertTrue(delivered.status==DeliveryStatus.DELIVERED)
        val repeated=(graph.workspaceRepository.deliver(id) as DetailsResult.Success).value
        assertTrue(repeated.status==DeliveryStatus.ALREADY_DELIVERED && repeated.deliveredAt==delivered.deliveredAt)
        MySqlConnectionFactory().open(db.verificationJdbc,ConnectionPolicy()).use { c ->
            assertTrue(count()==1)
            c.prepareStatement("SELECT st_vv,date_att_log FROM reys_bf WHERE nomer_z LIKE ?").use { s ->
                s.setString(1,"FFL-E2E-PAIRED%");s.executeQuery().use { r ->
                    assertTrue(r.next());assertTrue(r.getInt(1)==1 && r.getString(2)==delivered.deliveredAt)
                }
            }
        }
        graph.authRepository.logout()
        Unit
    }

    @Test fun legacyRecordsMatchSqlDtoOrderAndSummaryWithoutPublishingPersonalData() = runBlocking {
        val db = OwnedTestDatabase(); assumeTrue(db.restored)
        val graph = AppGraph.create(BuildConfig.DATA_SOURCE_MODE, db.settings)
        assertTrue(graph.connectionProbe.check() == ConnectionProbeResult.Reachable)
        var driver = 0L
        var date = ""
        var password = charArrayOf()
        val expected = mutableListOf<OrderId>()
        var first = 0L; var second = 0L; var delivered = 0L
        try {
            MySqlConnectionFactory().open(db.jdbc, ConnectionPolicy()).use { c ->
                c.prepareStatement("""SELECT r.id_v,r.date FROM (
                    SELECT id,id_v,date,'reys' AS src FROM reys WHERE id<2000000000
                    UNION ALL SELECT id,id_v,date,'reys_den' AS src FROM reys_den WHERE id<2000000000
                    ) r JOIN __hr_typepos h ON h.posTypeId=r.id_v
                    WHERE h.posTypeId<2000000000 AND h.access=1 AND h.posTypeName IS NOT NULL
                    AND h.posTypeName<>'' AND h.parol IS NOT NULL AND h.parol<>'' AND r.date>='2000-01-01'
                    GROUP BY r.id_v,r.date ORDER BY COUNT(DISTINCT r.src) DESC,r.date DESC LIMIT 1""").use { s ->
                    s.queryTimeout=3
                    s.executeQuery().use { r -> assertTrue(r.next()); driver=r.getLong(1); date=r.getString(2) }
                }
                c.prepareStatement("SELECT parol FROM __hr_typepos WHERE posTypeId=?").use { s ->
                    s.setLong(1,driver); s.executeQuery().use { r -> assertTrue(r.next()); password=r.getString(1).toCharArray() }
                }
                c.prepareStatement("""SELECT src,id,COALESCE(st_vv,0) AS delivered FROM (
                    SELECT 'reys' AS src,1 AS section,id,st_vv,sort_r FROM reys WHERE id_v=? AND date=?
                    UNION ALL SELECT 'reys_den',2,id,st_vv,sort_r FROM reys_den WHERE id_v=? AND date=?
                    ) q ORDER BY COALESCE(st_vv,0),section,CASE WHEN sort_r IS NULL OR sort_r=0 THEN 1 ELSE 0 END,sort_r,id""").use { s ->
                    s.setLong(1,driver);s.setString(2,date);s.setLong(3,driver);s.setString(4,date)
                    s.executeQuery().use { r -> while(r.next()) {
                        val source=r.getString("src"); expected+=OrderId.parse("$source:${r.getLong("id")}")!!
                        if(r.getLong("delivered")==1L) delivered++ else if(source=="reys") first++ else second++
                    } }
                }
            }
            assertTrue(expected.isNotEmpty())
            assertTrue(graph.authRepository.login(driver,password) is AuthResult.Success)
            val orders=graph.workspaceRepository.orders(date);assertTrue(orders is DetailsResult.Success)
            assertTrue((orders as DetailsResult.Success).value.map { it.id }==expected)
            val summary=graph.workspaceRepository.summary(date);assertTrue(summary is DetailsResult.Success)
            val counts=(summary as DetailsResult.Success).value
            assertTrue(counts.first==first && counts.second==second && counts.delivered==delivered)
            val details=graph.orderDetailsRepository.getDetails(expected.first());assertTrue(details is DetailsResult.Success)
            assertTrue((details as DetailsResult.Success).value.previousDrivers.size<=5)
            assertTrue(graph.workspaceRepository.directory() is DetailsResult.Success)
            val directory=java.io.File(db.context.filesDir,"restored-private").apply { mkdirs() }
            java.io.File(directory,"legacy-read-proof.json").writeText(JSONObject(mapOf(
                "restored_original_records_read" to true,"orders_checked" to expected.size,
                "dto_sql_id_order_parity" to true,"summary_parity" to true,"details_and_directory" to true,
                "personal_values_published" to false,"writes_to_original_records" to false)).toString())
        } finally { password.fill('\u0000'); graph.authRepository.logout() }
    }

    @Test fun controlledRecordsOnRestoredSchemaValidateContactsHistoryRollbackAndBothDeliveries() = runBlocking {
        val db=OwnedTestDatabase();assumeTrue(db.restored)
        val graph=AppGraph.create(BuildConfig.DATA_SOURCE_MODE, db.settings)
        val drivers=graph.authRepository.getDrivers();assertTrue(drivers is AuthResult.Success)
        assertTrue((drivers as AuthResult.Success).value.any { it.id==db.driver })
        assertTrue(drivers.value.none { it.id==db.disabled })
        assertTrue(graph.authRepository.login(db.driver,"wrong-owned-password".toCharArray())==AuthResult.InvalidCredentials)
        for (wrong in listOf("",db.password.uppercase(java.util.Locale.ROOT),db.password+" ","' OR 1=1 --")) {
            val secret=wrong.toCharArray()
            assertTrue(graph.authRepository.login(db.driver,secret)==AuthResult.InvalidCredentials)
            assertTrue(secret.all { it=='\u0000' })
        }
        assertTrue(graph.authRepository.login(Long.MAX_VALUE,db.password.toCharArray())==AuthResult.InvalidCredentials)
        assertTrue(graph.authRepository.login(db.disabled,db.password.toCharArray())==AuthResult.DriverDisabled)
        assertTrue(graph.authRepository.login(db.driver,db.password.toCharArray()) is AuthResult.Success)
        val orders=(graph.workspaceRepository.orders(db.date) as DetailsResult.Success).value
        assertTrue(orders.size==4 && OrderSection.entries.all { section -> orders.any { it.section==section } })
        val first=OrderId(OrderSource.REYS,db.order)
        val detail=(graph.orderDetailsRepository.getDetails(first) as DetailsResult.Success).value
        assertTrue(detail.contacts.address==db.address && detail.previousDrivers.size==5)
        val ambiguous=(graph.orderDetailsRepository.getDetails(OrderId(OrderSource.REYS_DEN,db.order)) as DetailsResult.Success).value
        assertTrue(ambiguous.contacts.addressSource==AddressSource.AMBIGUOUS && ambiguous.contacts.address==null)
        assertTrue(graph.orderDetailsRepository.getDetails(OrderId(OrderSource.REYS,db.foreign))==DetailsResult.NotFound)
        assertTrue(graph.workspaceRepository.deliver(OrderId(OrderSource.REYS,db.foreign))==DetailsResult.NotFound)
        val directory=(graph.workspaceRepository.directory() as DetailsResult.Success).value
        assertTrue(directory.phones.any { it.name=="FFL E2E Dispatcher" })
        val summary=(graph.workspaceRepository.summary(db.date) as DetailsResult.Success).value
        assertTrue(summary.active==2L && summary.secondIds==listOf(OrderId(OrderSource.REYS_DEN,db.order)))
        MySqlConnectionFactory().open(db.jdbc,ConnectionPolicy()).use { c ->
            var injected=false
            fun forward(target: Any,method: java.lang.reflect.Method,args: Array<out Any?>?): Any? = try {
                method.invoke(target,*(args ?: emptyArray()))
            } catch(e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
            val proxy=java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader,arrayOf(java.sql.Connection::class.java)) { _,m,a ->
                val result=forward(c,m,a)
                if(m.name=="prepareStatement" && (a?.get(0) as? String)?.startsWith("UPDATE")==true)
                    java.lang.reflect.Proxy.newProxyInstance(java.sql.PreparedStatement::class.java.classLoader,arrayOf(java.sql.PreparedStatement::class.java)) { _,method,args ->
                        val value=forward(result!!,method,args)
                        if(method.name=="executeUpdate") { injected=true;throw java.sql.SQLException("Owned failure after UPDATE") }
                        value
                    } else result
            } as java.sql.Connection
            var failed=false
            try { deliveryTransaction(proxy,first,db.driver,3) { true } } catch(_: java.sql.SQLException) { failed=true }
            assertTrue(injected && failed)
            c.prepareStatement("SELECT st_vv FROM reys WHERE id=? AND id_v=?").use { s ->
                s.setLong(1,db.order);s.setLong(2,db.driver);s.executeQuery().use { r -> assertTrue(r.next());assertTrue(r.getInt(1)==0) }
            }
            c.rollback()
        }
        for(source in OrderSource.entries) {
            val id=OrderId(source,db.order)
            val result=(graph.workspaceRepository.deliver(id) as DetailsResult.Success).value
            assertTrue(result.status==DeliveryStatus.DELIVERED && result.deliveredAt!=null)
            val repeated=(graph.workspaceRepository.deliver(id) as DetailsResult.Success).value
            assertTrue(repeated.status==DeliveryStatus.ALREADY_DELIVERED && result.deliveredAt==repeated.deliveredAt)
            val after=(graph.orderDetailsRepository.getDetails(id) as DetailsResult.Success).value
            assertTrue(after.status==2L && after.invoiceCount==(if(source==OrderSource.REYS)3L else 2L))
        }
        assertTrue(graph.authRepository.logout() is AuthResult.Success)
        assertTrue(graph.workspaceRepository.orders(db.date)==DetailsResult.SessionRequired)
    }
}

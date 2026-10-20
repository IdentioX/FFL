package ru.ffl24.direct.data.mysql

import java.sql.SQLException
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*

class DeliveryTransactionTest {
    @Test fun deliveryLocksTheAccessRowBeforeLockingAnOrder() {
        val jdbc=ScriptJdbc(query={sql,_-> if("SELECT access" in sql) listOf(mapOf("access" to "1"))
            else listOf(mapOf("id" to 1L,"st_vv" to 1L,"date_att_log" to "original")) })
        deliveryTransaction(jdbc.connection,OrderId(OrderSource.REYS,1),97,3) { true }
        assertTrue("Access must be a current locking read, not a repeatable-read snapshot",jdbc.calls.first().sql.endsWith("LOCK IN SHARE MODE"))
        assertTrue(jdbc.calls[1].sql.endsWith("FOR UPDATE"))
    }
    @Test fun bothTablesCommitOnlyTwoColumnsWithOwnerAndPostconditions() {
        for(s in OrderSource.entries) {
            var updated=false
            val jdbc=ScriptJdbc({ sql,_ -> if("SELECT access" in sql) listOf(mapOf("access" to "1"))
                else listOf(mapOf("id" to 3_000_000_000L,"st_vv" to if(updated) 1L else 0L,"date_att_log" to if(updated) "fixture-time" else null)) },
                { sql,params ->
                    assertEquals("UPDATE ${s.wire} SET st_vv=1,date_att_log=NOW() WHERE id=? AND id_v=? AND COALESCE(st_vv,0)<>1",sql)
                    assertEquals(mapOf(1 to 3_000_000_000L,2 to 97L),params);updated=true;1
                })
            val result=deliveryTransaction(jdbc.connection,OrderId(s,3_000_000_000L),97,3) { true }
            assertEquals(DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.DELIVERED,"fixture-time")),result)
            assertEquals(1,jdbc.commits);assertEquals(0,jdbc.rollbacks)
            assertTrue(jdbc.calls.any { "FOR UPDATE" in it.sql });assertEquals(1,jdbc.calls.count { it.sql.startsWith("UPDATE") })
            assertEquals(jdbc.calls.size,jdbc.closedStatements);assertEquals(jdbc.calls.size-1,jdbc.closedResults)
        }
    }
    @Test fun alreadyDeliveredDoesNotUpdateOrChangeTimestamp() {
        val jdbc=ScriptJdbc(query = { sql,_ -> if("SELECT access" in sql) listOf(mapOf("access" to "1")) else listOf(mapOf("id" to 1L,"st_vv" to 1L,"date_att_log" to "original")) })
        assertEquals(DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.ALREADY_DELIVERED,"original")),deliveryTransaction(jdbc.connection,OrderId(OrderSource.REYS,1),97,3) { true })
        assertFalse(jdbc.calls.any { it.sql.startsWith("UPDATE") });assertEquals(1,jdbc.commits)
    }
    @Test fun disabledOrMissingOwnerRollsBackWithoutUpdate() {
        for(disabled in listOf(true,false)) {
            val jdbc=ScriptJdbc(query = { sql,_ -> if("SELECT access" in sql) listOf(mapOf("access" to if(disabled) "0" else "1")) else emptyList() })
            val result=deliveryTransaction(jdbc.connection,OrderId(OrderSource.REYS_DEN,1),97,3) { true }
            assertEquals(if(disabled) DetailsLookup.Disabled else DetailsLookup.NotFound,result)
            assertEquals(1,jdbc.rollbacks);assertEquals(0,jdbc.commits);assertFalse(jdbc.calls.any { it.sql.startsWith("UPDATE") })
        }
    }
    @Test fun zeroAffectedRowsAndBadPostconditionRollback() {
        for(affected in listOf(0,1)) {
            val jdbc=ScriptJdbc({sql,_-> if("SELECT access" in sql) listOf(mapOf("access" to "1")) else listOf(mapOf("id" to 1L,"st_vv" to 0L)) },{_,_->affected})
            try { deliveryTransaction(jdbc.connection,OrderId(OrderSource.REYS,1),97,3) { true };fail("Must fail") }
            catch(_: SQLException) { }
            assertEquals(1,jdbc.rollbacks);assertEquals(0,jdbc.commits)
        }
    }
    @Test fun lostCommitResponseIsUnknownAndNeverRetries() {
        var changed=false
        val jdbc=ScriptJdbc({sql,_-> if("SELECT access" in sql) listOf(mapOf("access" to "1")) else listOf(mapOf("id" to 1L,"st_vv" to if(changed) 1L else 0L,"date_att_log" to "time")) },
            {_,_->changed=true;1},commitFailure=true)
        assertEquals(DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.UNKNOWN,null)),deliveryTransaction(jdbc.connection,OrderId(OrderSource.REYS,1),97,3) { true })
        assertEquals(1,jdbc.commits);assertEquals(1,jdbc.calls.count { it.sql.startsWith("UPDATE") })
    }
    @Test fun changedSessionNeverWrites() {
        val jdbc=ScriptJdbc(query = { _,_->error("No SQL allowed") })
        assertEquals(DetailsLookup.DriverMissing,deliveryTransaction(jdbc.connection,OrderId(OrderSource.REYS,1),97,3) { false })
        assertTrue(jdbc.calls.isEmpty());assertEquals(1,jdbc.rollbacks)
    }
}

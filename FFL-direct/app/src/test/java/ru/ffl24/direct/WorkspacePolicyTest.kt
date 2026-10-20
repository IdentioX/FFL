package ru.ffl24.direct

import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*

class WorkspacePolicyTest {
    @Test fun firstObservationNoAlertNewIdsOnlyAndNoRepeatAfterDelivery() {
        val a=OrderId(OrderSource.REYS_DEN,1);val b=OrderId(OrderSource.REYS_DEN,3_000_000_000L)
        assertTrue(PollingChanges.newIds(null,listOf(a)).isEmpty())
        assertEquals(listOf(b),PollingChanges.newIds(setOf(a.wire),listOf(a,b,b)))
        assertTrue(PollingChanges.newIds(setOf(a.wire,b.wire),listOf(a)).isEmpty())
        assertTrue(PollingChanges.newIds(setOf(a.wire,b.wire),listOf(b)).isEmpty())
    }
    @Test fun localOrderRetainsSourcesAppendsNewAndRemovesMissing() {
        fun row(source: OrderSource,id: Long,delivered: Boolean=false)=DriverOrder(OrderId(source,id),97,null,"2026-05-18","N","Client",null,0,1,2,0,delivered,null,OrderContacts())
        val a=row(OrderSource.REYS,1);val b=row(OrderSource.REYS_DEN,1);val c=row(OrderSource.REYS,2)
        assertEquals(listOf(b,a,c),mergeOrder(listOf(b.id.wire,"reys:99",a.id.wire),listOf(a,b,c)))
        assertEquals(OrderSection.DELIVERED,row(OrderSource.REYS_DEN,1,true).section)
        assertNotEquals(a.id,b.id)
    }
}

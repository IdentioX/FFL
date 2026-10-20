package ru.ffl24.direct.data.mysql

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class RetiringConnectorTest {
    @Test fun retirementClosesOutstandingSocketsAndRejectsFurtherOpenWithoutCallingDriver() {
        val opens=AtomicInteger();val closes=AtomicInteger()
        val connector=RetiringConnector(JdbcConnector { _,_->
            opens.incrementAndGet()
            Proxy.newProxyInstance(Connection::class.java.classLoader,arrayOf(Connection::class.java)){proxy,method,args->
                when(method.name){"close"->{closes.incrementAndGet();null};"hashCode"->System.identityHashCode(proxy);"equals"->proxy===args?.get(0);else->null}
            } as Connection
        })
        connector.open(fixtureReady().config,ConnectionPolicy())
        connector.open(fixtureReady().config,ConnectionPolicy())
        connector.retire();assertEquals(2,closes.get());assertEquals(2,opens.get())
        try {connector.open(fixtureReady().config,ConnectionPolicy());fail("Retired graph reopened a connection")}catch(e:SQLException){assertEquals("08003",e.sqlState)}
        assertEquals(2,opens.get());connector.retire();assertEquals(2,closes.get())
    }
    @Test fun safeDatabaseErrorsDoNotContainVendorText() {
        val text="PRIVATE_ACCOUNT_ENDPOINT_PASSWORD"
        assertEquals(DirectDbError.DATABASE_NOT_FOUND,DirectDbError.from(SQLException(text,"42000",1049)))
        assertEquals(DirectDbError.DATABASE_NOT_FOUND,DirectDbError.from(SQLException(text,"42000",1044)))
        assertFalse(DirectDbError.from(SQLException(text)).toString().contains(text))
    }
}

package ru.ffl24.direct.data.mysql

import java.net.ConnectException
import java.util.ServiceConfigurationError
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ffl24.direct.domain.*

class DeliveryFailureTest {
    @Test fun refusedConnectionIsDefiniteFailureRatherThanAnUnknownWrite() = checkFailure(ConnectException(),DirectDbError.UNAVAILABLE)
    @Test fun rejectedTlsIsDefiniteFailureRatherThanAnUnknownWrite() = checkFailure(SSLHandshakeException("PRIVATE"),DirectDbError.TLS_VERIFICATION)
    @Test fun brokenServiceLoaderBecomesASafeError() = checkFailure(ServiceConfigurationError("PRIVATE"),DirectDbError.DRIVER_UNAVAILABLE)
    private fun checkFailure(failure: Throwable, expected: DirectDbError) = runBlocking {
        val source=DirectDeliveryDataSource(fixtureReady(),JdbcConnector { _,_->throw failure })
        assertEquals(DbResult.Failed(expected),source.deliver(OrderId(OrderSource.REYS,1),97) { true })
    }
}

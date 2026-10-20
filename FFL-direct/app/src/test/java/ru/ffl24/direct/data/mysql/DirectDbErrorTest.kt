package ru.ffl24.direct.data.mysql

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class DirectDbErrorTest {
    @Test fun classifyByTypeAndStateWithoutUsingMessages() {
        val cases = listOf(
            SSLHandshakeException("PRIVATE_FIXTURE") to DirectDbError.TLS_VERIFICATION,
            SQLTimeoutException("PRIVATE_FIXTURE") to DirectDbError.TIMEOUT,
            SocketTimeoutException("PRIVATE_FIXTURE") to DirectDbError.TIMEOUT,
            SQLException("PRIVATE_FIXTURE", "28000") to DirectDbError.ACCOUNT_REJECTED,
            SQLException("PRIVATE_FIXTURE", "08000") to DirectDbError.UNAVAILABLE,
            ConnectException("PRIVATE_FIXTURE") to DirectDbError.UNAVAILABLE,
            SQLFeatureNotSupportedException("PRIVATE_FIXTURE") to DirectDbError.UNSUPPORTED,
            NoClassDefFoundError("PRIVATE_FIXTURE") to DirectDbError.DRIVER_UNAVAILABLE,
            SQLException("PRIVATE_FIXTURE", "42000") to DirectDbError.QUERY_FAILED
        )
        for ((failure, expected) in cases) {
            assertEquals(expected, DirectDbError.from(failure))
            assertFalse(ConnectionProbeResult.Failed(DirectDbError.from(failure)).toString().contains("PRIVATE_FIXTURE"))
        }
    }

    @Test fun nestedTlsFailureTakesPriorityOverGenericConnectionState() {
        assertEquals(DirectDbError.TLS_VERIFICATION,
            DirectDbError.from(SQLException("PRIVATE_FIXTURE", "08000", SSLHandshakeException("PRIVATE_FIXTURE"))))
    }

    @Test fun misleadingVendorTextDoesNotControlClassification() {
        assertEquals(DirectDbError.QUERY_FAILED, DirectDbError.from(SQLException("SSL timeout password host", "HY000")))
    }
}

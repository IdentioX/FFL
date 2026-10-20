package ru.ffl24.direct.data.mysql

import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** Real ART/JDBC/TLS on loopback wire fixtures, not a database or live SELECT proof. */
class ConnectionLayerTest {
    private fun settings(port: Int) = DirectMySqlSettings(
        "localhost", port.toString(), "fixture", "fixture_read", "fixture-only", "true",
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-ca.pem").bufferedReader().use { it.readText() }
    )

    private suspend fun probe(port: Int, policy: ConnectionPolicy = ConnectionPolicy()) =
        MySqlConnectionProbe(DirectMySqlConfig.parse(settings(port)), policy = policy).check()

    @Test fun missingAndPartialConfigRemainSafeOnArt() = runBlocking {
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.CONFIGURATION_MISSING),
            MySqlConnectionProbe(DirectMySqlConfig.parse(DirectMySqlSettings())).check())
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.CONFIGURATION_INVALID),
            MySqlConnectionProbe(DirectMySqlConfig.parse(DirectMySqlSettings(host = "localhost"))).check())
    }

    @Test fun driverLoadsAndRefusedConnectionIsSafe() = runBlocking {
        val unusedPort = ServerSocket(0).use { it.localPort }
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE), probe(unusedPort))
        assertNotNull(Class.forName("org.mariadb.jdbc.Driver"))
    }

    @Test fun untrustedCaIsRejectedByRealJdbcTls() = runBlocking {
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TLS_VERIFICATION), probe(19063))
    }

    @Test fun wrongHostnameIsRejectedByRealJdbcTls() = runBlocking {
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TLS_VERIFICATION), probe(19062))
    }

    @Test fun trustedTlsGetsPastHandshakeButFixtureIsNotADatabase() = runBlocking {
        // Server accepts TLS then closes BEFORE auth/query; no fake success is returned.
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE), probe(19061))
    }

    @Test fun stalledGreetingUsesSocketTimeout() = runBlocking {
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TIMEOUT),
            probe(19064, ConnectionPolicy(connectTimeoutMs = 500, socketTimeoutMs = 500,
                queryTimeoutSeconds = 1, operationTimeoutMs = 10_000)))
    }

    @Test fun callerOnMainNeverPerformsNetworkOnMain() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var previous: StrictMode.ThreadPolicy
        instrumentation.runOnMainSync {
            previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeath().build())
        }
        try {
            val unusedPort = ServerSocket(0).use { it.localPort }
            val result = withContext(Dispatchers.Main) { probe(unusedPort) }
            assertEquals(ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE), result)
        } finally {
            instrumentation.runOnMainSync { StrictMode.setThreadPolicy(previous) }
        }
    }
}

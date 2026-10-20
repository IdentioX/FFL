package ru.ffl24.direct.data.mysql

import org.junit.Assert.*
import org.junit.Test

internal fun fixtureSettings(
    host: String = "localhost", port: String = "3306", database: String = "fixture",
    user: String = "fixture_read", password: String = "fixture-only", ssl: String = "true",
    ca: String = fixtureCa()
) = DirectMySqlSettings(host, port, database, user, password, ssl, ca)

internal fun fixtureCa() = DirectMySqlSettings::class.java.getResource("/fixture-ca.pem")!!.readText()
internal fun fixtureReady() = DirectMySqlConfig.parse(fixtureSettings()) as MySqlConfiguration.Ready

class DirectMySqlConfigTest {
    @Test fun emptyIsMissingAndPartialIsInvalid() {
        assertEquals(MySqlConfiguration.Missing, DirectMySqlConfig.parse(DirectMySqlSettings()))
        assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INCOMPLETE),
            DirectMySqlConfig.parse(DirectMySqlSettings(host = "localhost")))
        assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INCOMPLETE),
            DirectMySqlConfig.parse(fixtureSettings(password = "")))
    }

    @Test fun requiresAllSixProperties() {
        val base = listOf("localhost", "3306", "fixture", "fixture_read", "fixture-only", "true")
        for (index in base.indices) {
            val v = base.toMutableList().also { it[index] = "" }
            assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INCOMPLETE), DirectMySqlConfig.parse(
                DirectMySqlSettings(v[0], v[1], v[2], v[3], v[4], v[5], fixtureCa())))
        }
    }

    @Test fun portsMustBeExplicitAndInRange() {
        for (bad in listOf("0", "65536", "-1", "1.0", "abc", " 3306", "999999999999")) {
            assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INVALID_PORT),
                DirectMySqlConfig.parse(fixtureSettings(port = bad)))
        }
        for (valid in listOf("1", "3306", "65535")) {
            assertTrue(DirectMySqlConfig.parse(fixtureSettings(port = valid)) is MySqlConfiguration.Ready)
        }
    }

    @Test fun endpointCannotInjectOptionsOrAdditionalServers() {
        for (bad in listOf("a,b", "localhost:3306", "https://localhost", "a?sslMode=disable", "a/b", "a@b", "a\nb", "-a", "a..b", "::1")) {
            assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INVALID_ENDPOINT),
                DirectMySqlConfig.parse(fixtureSettings(host = bad)))
        }
        assertTrue(DirectMySqlConfig.parse(fixtureSettings(host = "db.test.invalid")) is MySqlConfiguration.Ready)
        assertTrue(DirectMySqlConfig.parse(fixtureSettings(host = "127.0.0.1")) is MySqlConfiguration.Ready)
        assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INVALID_ENDPOINT),
            DirectMySqlConfig.parse(fixtureSettings(database = "fixture?sslMode=disable")))
    }

    @Test fun tlsAndExplicitCaAreMandatory() {
        for (bad in listOf("false", "TRUE", "1", "true ")) {
            assertEquals(MySqlConfiguration.Invalid(ConfigProblem.TLS_REQUIRED),
                DirectMySqlConfig.parse(fixtureSettings(ssl = bad)))
        }
        assertEquals(MySqlConfiguration.Invalid(ConfigProblem.CA_REQUIRED),
            DirectMySqlConfig.parse(fixtureSettings(ca = "")))
    }

    @Test fun rejectRootAndWhitespaceAccountButPreserveExactPassword() {
        for (bad in listOf("root", "ROOT", " fixture_read", "fixture\nread")) {
            assertEquals(MySqlConfiguration.Invalid(ConfigProblem.INVALID_ACCOUNT),
                DirectMySqlConfig.parse(fixtureSettings(user = bad)))
        }
        val ready = DirectMySqlConfig.parse(fixtureSettings(password = " fixture \"\\\n ")) as MySqlConfiguration.Ready
        assertEquals(" fixture \"\\\n ", ready.config.password)
    }

    @Test fun diagnosticStringsDoNotExposeSettings() {
        val raw = fixtureSettings(host = "private.fixture.invalid", password = "PRIVATE_FIXTURE_PASSWORD")
        val ready = DirectMySqlConfig.parse(raw) as MySqlConfiguration.Ready
        for (value in listOf(raw.toString(), ready.toString(), ready.config.toString())) {
            assertFalse(value.contains("PRIVATE_FIXTURE"))
            assertFalse(value.contains("private.fixture.invalid"))
            assertFalse(value.contains("fixture_read"))
        }
    }

    @Test fun driverPropertiesCannotDisableTlsOrEnableRedirectAndKeyRetrieval() {
        val p = MySqlConnectionFactory.properties(fixtureReady().config, ConnectionPolicy())
        assertEquals("verify-full", p.getProperty("sslMode"))
        assertEquals("ffl-android", p.getProperty("tlsSocketType"))
        assertEquals(fixtureCa(), p.getProperty("serverSslCert"))
        for (key in listOf("fallbackToSystemTrustStore", "fallbackToSystemKeyStore", "allowPublicKeyRetrieval", "permitRedirect", "useIpForKillQuery")) {
            assertEquals("false", p.getProperty(key))
        }
        assertTrue(p.getProperty("enabledSslProtocolSuites").split(',').all { it in listOf("TLSv1.2", "TLSv1.3") })
        assertEquals("mysql_native_password,caching_sha2_password", p.getProperty("restrictedAuth"))
        assertEquals("3000", p.getProperty("connectTimeout"))
        assertEquals("5000", p.getProperty("socketTimeout"))
    }

    @Test fun timeoutPolicyCannotDisableLimits() {
        assertThrows(IllegalArgumentException::class.java) { ConnectionPolicy(connectTimeoutMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { ConnectionPolicy(socketTimeoutMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { ConnectionPolicy(queryTimeoutSeconds = 0) }
        assertThrows(IllegalArgumentException::class.java) { ConnectionPolicy(operationTimeoutMs = 0) }
    }
}

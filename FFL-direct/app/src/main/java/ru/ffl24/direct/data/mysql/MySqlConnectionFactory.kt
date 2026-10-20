package ru.ffl24.direct.data.mysql

import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.sql.Connection
import java.sql.Driver
import java.sql.SQLException
import java.util.Properties
import javax.net.ssl.SSLContext

internal fun interface JdbcConnector {
    /** Blocking: caller must dispatch to IO and scope/close the returned connection. */
    fun open(config: DirectMySqlConfig, policy: ConnectionPolicy): Connection
}

internal class MySqlConnectionFactory : JdbcConnector {
    override fun open(config: DirectMySqlConfig, policy: ConnectionPolicy): Connection {
        // Explicit CA also prevents MariaDB's zero-configuration/deferred-identity TLS path.
        val certificates = CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(config.caPem.toByteArray(Charsets.UTF_8)))
        if (certificates.isEmpty()) throw CertificateException()
        certificates.forEach {
            val ca = it as? X509Certificate ?: throw CertificateException()
            if (ca.basicConstraints < 0) throw CertificateException()
            ca.checkValidity()
        }
        val properties = properties(config, policy)
        return driver.connect("jdbc:mariadb://${config.host}:${config.port}/${config.database}", properties)
            ?: throw SQLException("Unsupported driver URL", "08000")
    }

    companion object {
        private val driver: Driver by lazy {
            // Must happen before driver/logger classes initialize: vendor errors may contain PII.
            System.setProperty("mariadb.logging.disable", "true")
            Class.forName("org.mariadb.jdbc.Driver").getDeclaredConstructor().newInstance() as Driver
        }

        internal fun properties(config: DirectMySqlConfig, policy: ConnectionPolicy) = Properties().apply {
            setProperty("user", config.user)
            setProperty("password", config.password)
            setProperty("sslMode", "verify-full")
            setProperty("tlsSocketType", "ffl-android")
            setProperty("serverSslCert", config.caPem)
            setProperty("fallbackToSystemTrustStore", "false")
            setProperty("fallbackToSystemKeyStore", "false")
            val supported = SSLContext.getDefault().supportedSSLParameters.protocols.toSet()
            val protocols = listOf("TLSv1.3", "TLSv1.2").filter { it in supported }
            check(protocols.isNotEmpty())
            setProperty("enabledSslProtocolSuites", protocols.joinToString(","))
            setProperty("allowPublicKeyRetrieval", "false")
            setProperty("restrictedAuth", "mysql_native_password,caching_sha2_password")
            setProperty("permitRedirect", "false")
            // Query cancellation must retain DNS hostname for certificate verification.
            setProperty("useIpForKillQuery", "false")
            setProperty("connectTimeout", policy.connectTimeoutMs.toString())
            setProperty("socketTimeout", policy.socketTimeoutMs.toString())
            setProperty("tcpKeepAlive", "true")
        }
    }
}

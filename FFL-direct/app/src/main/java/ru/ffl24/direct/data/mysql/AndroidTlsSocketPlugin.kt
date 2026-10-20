package ru.ffl24.direct.data.mysql

import java.io.ByteArrayInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.sql.SQLException
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import org.mariadb.jdbc.Configuration
import org.mariadb.jdbc.HostAddress
import org.mariadb.jdbc.export.ExceptionFactory
import org.mariadb.jdbc.export.SslMode
import org.mariadb.jdbc.plugin.TlsSocketPlugin

/** Android JSSE performs CA and endpoint identity checks during startHandshake.
 * Avoids the driver's desktop CN fallback (javax.naming.ldap, absent on Android).
 * ServiceLoader creates a fresh plugin instance for every connection.
 */
class AndroidTlsSocketPlugin : TlsSocketPlugin {
    private var endpoint: String? = null

    override fun type() = "ffl-android"

    override fun getTrustManager(conf: Configuration, factory: ExceptionFactory, host: HostAddress): Array<TrustManager> {
        if (conf.sslMode() != SslMode.VERIFY_FULL || (host.sslMode != null && host.sslMode != SslMode.VERIFY_FULL)) {
            throw SQLException("TLS verification required")
        }
        endpoint = host.host
        val pem = conf.serverSslCert() ?: throw SQLException("CA required")
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val certificates = CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)))
        if (certificates.isEmpty()) throw SSLPeerUnverifiedException("CA required")
        certificates.forEachIndexed { index, certificate -> store.setCertificateEntry("ca-$index", certificate) }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }.trustManagers
    }

    override fun getKeyManager(conf: Configuration, factory: ExceptionFactory): Array<KeyManager>? = null

    override fun createSocket(socket: Socket, factory: SSLSocketFactory): SSLSocket {
        val host = endpoint ?: throw SSLPeerUnverifiedException("TLS endpoint missing")
        return (factory.createSocket(socket, host, socket.port, true) as SSLSocket).apply {
            sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        }
    }

    override fun verify(host: String, session: SSLSession, serverThreadId: Long) {
        // Identity has already been verified by JSSE with HTTPS endpoint identification.
        if (host != endpoint || session.peerHost != host) throw SSLPeerUnverifiedException("TLS endpoint mismatch")
    }
}

package ru.ffl24.direct.data.mysql

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLTimeoutException
import javax.net.ssl.SSLException

/** No vendor text, SQL, URL, exception cause or account details cross this boundary. */
enum class DirectDbError {
    CONFIGURATION_MISSING, CONFIGURATION_INVALID, TLS_VERIFICATION, TIMEOUT,
    UNAVAILABLE, ACCOUNT_REJECTED, DATABASE_NOT_FOUND, DRIVER_UNAVAILABLE, UNSUPPORTED, QUERY_FAILED;

    companion object {
        fun from(failure: Throwable): DirectDbError {
            val chain = generateSequence(failure) { it.cause }.take(12).toList()
            return when {
                chain.any { it is SSLException || it is CertificateException } -> TLS_VERIFICATION
                chain.any { it is SQLTimeoutException || it is SocketTimeoutException } -> TIMEOUT
                chain.any { it is LinkageError || it is ClassNotFoundException } -> DRIVER_UNAVAILABLE
                chain.any { it is SQLFeatureNotSupportedException } -> UNSUPPORTED
                chain.any { it is SQLException && it.errorCode in setOf(1049, 1044) } -> DATABASE_NOT_FOUND
                chain.any { it is SQLException && it.sqlState?.startsWith("28") == true } -> ACCOUNT_REJECTED
                chain.any { it is ConnectException || it is UnknownHostException || it is SocketException ||
                    (it is SQLException && it.sqlState?.startsWith("08") == true) } -> UNAVAILABLE
                else -> QUERY_FAILED
            }
        }
    }
}

sealed interface ConnectionProbeResult {
    data object Reachable : ConnectionProbeResult
    data class Failed(val error: DirectDbError) : ConnectionProbeResult
}

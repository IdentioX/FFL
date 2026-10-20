package ru.ffl24.direct.data.mysql

/** Values deliberately have no generated data-class toString/copy/component methods. */
class DirectMySqlSettings(
    internal val host: String = "",
    internal val port: String = "",
    internal val database: String = "",
    internal val user: String = "",
    internal val password: String = "",
    internal val useSsl: String = "",
    internal val caPem: String = ""
) {
    override fun toString() = "DirectMySqlSettings([redacted])"
}

enum class ConfigProblem { INCOMPLETE, INVALID_ENDPOINT, INVALID_PORT, INVALID_ACCOUNT, TLS_REQUIRED, CA_REQUIRED }

sealed interface MySqlConfiguration {
    data object Missing : MySqlConfiguration
    data class Invalid(val problem: ConfigProblem) : MySqlConfiguration
    class Ready internal constructor(internal val config: DirectMySqlConfig) : MySqlConfiguration
}

class DirectMySqlConfig private constructor(
    internal val host: String,
    internal val port: Int,
    internal val database: String,
    internal val user: String,
    internal val password: String,
    internal val caPem: String
) {
    override fun toString() = "DirectMySqlConfig([redacted])"

    companion object {
        fun parse(raw: DirectMySqlSettings): MySqlConfiguration {
            val fields = listOf(raw.host, raw.port, raw.database, raw.user, raw.password, raw.useSsl)
            if (fields.all { it.isEmpty() } && raw.caPem.isEmpty()) return MySqlConfiguration.Missing
            if (fields.any { it.isBlank() }) return MySqlConfiguration.Invalid(ConfigProblem.INCOMPLETE)
            // One DNS name or IPv4 address. No URL options, multi-host, socket paths or IPv6 in P2.
            val labels = raw.host.split('.')
            if (raw.host.length > 253 || labels.any {
                    !it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"))
                } || !raw.database.matches(Regex("[A-Za-z0-9_$-]{1,64}"))) {
                return MySqlConfiguration.Invalid(ConfigProblem.INVALID_ENDPOINT)
            }
            val port = raw.port.takeIf { it.matches(Regex("[0-9]{1,5}")) }?.toIntOrNull()
            if (port == null || port !in 1..65535) return MySqlConfiguration.Invalid(ConfigProblem.INVALID_PORT)
            if (raw.user.length > 80 || raw.password.length > 1024 || raw.user != raw.user.trim() || raw.user.any { it.isISOControl() } ||
                raw.user.lowercase() in setOf("root", "admin")) {
                return MySqlConfiguration.Invalid(ConfigProblem.INVALID_ACCOUNT)
            }
            if (raw.useSsl != "true") return MySqlConfiguration.Invalid(ConfigProblem.TLS_REQUIRED)
            if (raw.caPem.length > 64_000 || !raw.caPem.trim().startsWith("-----BEGIN CERTIFICATE-----")) {
                return MySqlConfiguration.Invalid(ConfigProblem.CA_REQUIRED)
            }
            return MySqlConfiguration.Ready(DirectMySqlConfig(
                raw.host, port, raw.database, raw.user, raw.password, raw.caPem
            ))
        }
    }
}

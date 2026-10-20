package ru.ffl24.direct.domain

/** Local identity hint only, revalidated against access before use. Never a password/token. */
data class DriverSession(val driverId: Long, val displayName: String, val version: Int = 1) {
    init { require(driverId > 0 && displayName.isNotEmpty() && version == 1) }
    override fun toString() = "DriverSession([redacted])"
}

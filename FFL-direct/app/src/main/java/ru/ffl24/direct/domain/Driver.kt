package ru.ffl24.direct.domain

data class Driver(
    val id: Long,
    val displayName: String,
    val fio: String? = null,
    val mobile: String? = null,
    val whatsapp: String? = null
) {
    override fun toString() = "Driver([redacted])"
}

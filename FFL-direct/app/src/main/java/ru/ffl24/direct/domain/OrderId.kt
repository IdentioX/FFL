package ru.ffl24.direct.domain

enum class OrderSource(val wire: String, val section: String, val sectionLabel: String) {
    REYS("reys", "FIRST_ROUTE", "Первый рейс"),
    REYS_DEN("reys_den", "SECOND_ROUTE", "Второй рейс")
}

/** Source is retained through repository, navigation and history. No narrowing to Int. */
data class OrderId(val source: OrderSource, val legacyId: Long) {
    init { require(legacyId > 0) { "Invalid order identity" } }
    val wire: String get() = "${source.wire}:$legacyId"

    companion object {
        fun parse(value: String): OrderId? {
            val source = OrderSource.entries.firstOrNull { it.wire == value.substringBefore(':') } ?: return null
            if (':' !in value) return null
            val number = value.substringAfter(':').trim()
            if (!number.matches(Regex("[+]?[0-9]+"))) return null
            val id = number.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return OrderId(source, id)
        }
    }
}

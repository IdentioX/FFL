package ru.ffl24.direct.domain

enum class OrderSection(val label: String) {
    FIRST("Первый рейс"), SECOND("Второй рейс"), DELIVERED("Сданные заказы")
}

data class DriverOrder(
    val id: OrderId, val driverId: Long, val driverName: String?, val date: String,
    val orderNumber: String, val clientName: String, val note: String?, val sortIndex: Long,
    val invoiceCount: Long, val status: Long, val quantityReadiness: Long,
    val isDelivered: Boolean, val deliveredAt: String?, val contacts: OrderContacts
) {
    val section get() = when {
        isDelivered -> OrderSection.DELIVERED
        id.source == OrderSource.REYS -> OrderSection.FIRST
        else -> OrderSection.SECOND
    }
    val isReady get() = status == 2L
    val readinessLabel get() = when (status) { 2L -> "Готово"; 1L -> "Не готово"; else -> "Неизвестно" }
    override fun toString() = "DriverOrder(redacted)"
}

data class DirectoryPhone(val name: String?, val phone: String) {
    override fun toString() = "DirectoryPhone(redacted)"
}
data class DriverPhone(val id: Long, val name: String, val phone: String) {
    override fun toString() = "DriverPhone(redacted)"
}
data class PhoneDirectory(val cardNumber: String?, val cardHolder: String?,
    val phones: List<DirectoryPhone>, val drivers: List<DriverPhone>) {
    override fun toString() = "PhoneDirectory(redacted)"
}
data class NotificationSummary(val first: Long, val second: Long, val delivered: Long, val secondIds: List<OrderId>) {
    val active get() = first + second
}
enum class DeliveryStatus { DELIVERED, ALREADY_DELIVERED, UNKNOWN }
data class DeliveryReceipt(val status: DeliveryStatus, val deliveredAt: String?)

interface DriverWorkspaceRepository {
    suspend fun orders(date: String): DetailsResult<List<DriverOrder>>
    suspend fun directory(): DetailsResult<PhoneDirectory>
    suspend fun summary(date: String): DetailsResult<NotificationSummary>
    suspend fun deliver(id: OrderId): DetailsResult<DeliveryReceipt>
}

/** Existing IDs keep local order; new IDs append in SQL order, removed/moved IDs disappear. */
fun mergeOrder(saved: List<String>, current: List<DriverOrder>): List<DriverOrder> {
    val byId = current.associateBy { it.id.wire }
    val ids = (saved + current.map { it.id.wire }).distinct()
    return ids.mapNotNull(byId::get)
}

object PollingChanges {
    /** First successful observation is a baseline. Removed IDs stay seen for this driver/date. */
    fun newIds(previous: Set<String>?, current: List<OrderId>): List<OrderId> =
        if (previous == null) emptyList() else current.filter { it.wire !in previous }.distinct()
}

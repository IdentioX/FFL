package ru.ffl24.direct.domain

enum class AddressSource { MENEGER_CONTR_FFL, NOT_FOUND, AMBIGUOUS }
enum class MatchConfidence { HIGH, MEDIUM, NONE }

data class OrderContacts(
    val address: String? = null,
    val routeText: String? = null,
    val routeUrl: String? = null,
    val addressSource: AddressSource = AddressSource.NOT_FOUND,
    val confidence: MatchConfidence = MatchConfidence.NONE,
    val clientPhone: String? = null,
    val loaderPhone: String? = null,
    val managerName: String? = null,
    val managerPhone: String? = null
) {
    val routeMode get() = if (routeUrl == null) "manual" else "link"
    val routeButtonLabel get() = if (routeUrl == null) "Ручной маршрут" else "Маршрут"
    override fun toString() = "OrderContacts(redacted)"
}

data class PreviousDriver(
    val driverId: Long, val driverName: String?, val driverPhone: String?, val driverWhatsapp: String?,
    val deliveryDate: String, val orderId: OrderId, val orderNumber: String
) {
    override fun toString() = "PreviousDriver(redacted)"
}

data class OrderDetails(
    val id: OrderId, val sortIndex: Long, val orderNumber: String,
    val title: String, val clientName: String,
    val rawClientText: String?, val rawDeliveryNote: String?,
    val plannedTime: String?, val deliveryTime: String?, val comment: String?,
    val invoiceCount: Long, val status: Long, val quantityReadiness: Long,
    val isDelivered: Boolean, val deliveredAt: String?,
    val contacts: OrderContacts, val previousDrivers: List<PreviousDriver>
) {
    val isReady get() = status == 2L
    val readinessLabel get() = when (status) { 2L -> "Готово"; 1L -> "Не готово"; else -> "Неизвестно" }
    val quantityLabel get() = when (quantityReadiness) {
        0L -> "Накладные не готовы"; 1L -> "Частично готово"; 2L -> "Готово"
        else -> "Неизвестный статус накладных"
    }
    // Parity field only. No delivery action/write is part of P5.
    val canMarkDelivered get() = !isDelivered
    override fun toString() = "OrderDetails(redacted)"
}

sealed interface DetailsResult<out T> {
    data class Success<T>(val value: T) : DetailsResult<T>
    data object NotFound : DetailsResult<Nothing>
    data object SessionRequired : DetailsResult<Nothing>
    data object DriverDisabled : DetailsResult<Nothing>
    data class Failed(val error: AuthError) : DetailsResult<Nothing>
}

interface OrderDetailsRepository {
    suspend fun getDetails(id: OrderId): DetailsResult<OrderDetails>
    /** Read-only access/ownership validation; never persists route telemetry. */
    suspend fun validateRouteOpened(id: OrderId): DetailsResult<Unit>
}

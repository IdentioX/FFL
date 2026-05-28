package ru.ffl24.zakaz.model

import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.JsonAdapter

@JsonAdapter(DriverOrderDtoDeserializer::class)
data class DriverOrderDto(
    /** Unique order identifier in backend storage. */
    val orderId: Long = 0L,
    /** Human-readable order number shown in UI and docs. */
    val orderNumber: String = "Без номера",
    /** Driver identifier assigned to this order. */
    val driverId: Long = 0L,
    /** Driver full name for display in order screens. */
    val driverName: String = "",
    /** Client summary text (name/contact/address) provided by backend. */
    val clientInfo: String = "",
    /** Optional dispatcher/warehouse note for delivery. */
    val deliveryNote: String? = null,
    /** Planned delivery date/time in backend string format. */
    val deliveryDate: String = "",
    /** Total invoices attached to this order. */
    val invoicesCount: Int = 0,
    /** Count of invoices that are still not ready. */
    val notReadyInvoices: Int = 0,
    /** True when all invoices in the order are ready. */
    val isReady: Boolean = false,
    /** True when only part of invoices are ready. */
    val isPartiallyReady: Boolean = false,
    /** Optional route description text for the driver. */
    val routeText: String? = null,
    /** Destination latitude used for Yandex route building when provided by backend. */
    @SerializedName(value = "lat", alternate = ["latitude", "routeLatitude", "route_latitude"])
    val lat: Double? = null,
    /** Destination longitude used for Yandex route building when provided by backend. */
    @SerializedName(value = "lon", alternate = ["longitude", "routeLongitude", "route_longitude"])
    val lon: Double? = null,
    /** True when routeText is provided by backend. */
    val hasRouteText: Boolean = false,
    /** Main title text to render this order card/list item. */
    val displayTitle: String = "",
    /** Backend-provided main readiness label based on reys.status. */
    val readinessLabel: String = "Неизвестно",
    /** Backend-provided quantitative invoice readiness code based on reys.col_s. */
    val quantityReadiness: String = "unknown",
    /** Backend-provided quantitative invoice readiness label based on reys.col_s. */
    val quantityLabel: String = "Неизвестный статус накладных",
    /** True when the driver has already marked this order as delivered. */
    val isDelivered: Boolean = false,
    /** Backend delivery confirmation timestamp when available. */
    val deliveredAt: String? = null,
    /** True when this order may still be marked delivered from the app. */
    val canMarkDelivered: Boolean = true
) {
    companion object {
        val Preview = DriverOrderDto(
            orderId = 101L,
            orderNumber = "A-2026-00101",
            driverId = 77L,
            driverName = "Ivan Petrov",
            clientInfo = "Acme LLC, +1 555 123 45 67, 5th Ave 10",
            deliveryNote = "Call before arrival",
            deliveryDate = "2026-05-21T14:30:00Z",
            invoicesCount = 8,
            notReadyInvoices = 2,
            isReady = false,
            isPartiallyReady = true,
            routeText = "Warehouse -> Downtown -> Uptown",
            lat = 55.751244,
            lon = 37.618423,
            hasRouteText = true,
            displayTitle = "Order A-2026-00101",
            readinessLabel = "Не готов",
            quantityReadiness = "partial",
            quantityLabel = "Часть накладных готова"
        )
    }
}

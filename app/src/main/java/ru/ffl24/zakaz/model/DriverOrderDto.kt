package ru.ffl24.zakaz.model

import com.google.gson.annotations.SerializedName

data class DriverOrderDto(
    /** Unique order identifier in backend storage. */
    val orderId: Long,
    /** Human-readable order number shown in UI and docs. */
    val orderNumber: String,
    /** Driver identifier assigned to this order. */
    val driverId: Long,
    /** Driver full name for display in order screens. */
    val driverName: String,
    /** Client summary text (name/contact/address) provided by backend. */
    val clientInfo: String,
    /** Optional dispatcher/warehouse note for delivery. */
    val deliveryNote: String?,
    /** Planned delivery date/time in backend string format. */
    val deliveryDate: String,
    /** Total invoices attached to this order. */
    val invoicesCount: Int,
    /** Count of invoices that are still not ready. */
    val notReadyInvoices: Int,
    /** True when all invoices in the order are ready. */
    val isReady: Boolean,
    /** True when only part of invoices are ready. */
    val isPartiallyReady: Boolean,
    /** Optional route description text for the driver. */
    val routeText: String?,
    /** Destination latitude used for Yandex route building when provided by backend. */
    @SerializedName(value = "lat", alternate = ["latitude", "routeLatitude", "route_latitude"])
    val lat: Double?,
    /** Destination longitude used for Yandex route building when provided by backend. */
    @SerializedName(value = "lon", alternate = ["longitude", "routeLongitude", "route_longitude"])
    val lon: Double?,
    /** True when routeText is provided by backend. */
    val hasRouteText: Boolean,
    /** Main title text to render this order card/list item. */
    val displayTitle: String,
    /** Backend-provided readiness label for badges/status chips. */
    val readinessLabel: String
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
            readinessLabel = "Partially ready"
        )
    }
}

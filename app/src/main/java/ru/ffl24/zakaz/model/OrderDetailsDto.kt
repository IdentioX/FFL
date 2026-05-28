package ru.ffl24.zakaz.model

import com.google.gson.annotations.JsonAdapter

@JsonAdapter(OrderDetailsDtoDeserializer::class)
data class OrderDetailsDto(
    val orderId: Int = 0,
    val orderNumber: String = "Без номера",
    val title: String = "",
    val address: String? = null,
    val deliveryTime: String? = null,
    val comment: String? = null,
    val routeText: String = "",
    val invoicesCount: Int = 0,
    val isReady: Boolean = false,
    val readinessLabel: String = "Неизвестно",
    val quantityReadiness: String = "unknown",
    val quantityLabel: String = "Неизвестный статус накладных",
    val isDelivered: Boolean = false,
    val deliveredAt: String? = null,
    val canMarkDelivered: Boolean = true,
    val previousDrivers: List<PreviousDriverDto> = emptyList()
)

data class PreviousDriverDto(
    val driverId: Int = 0,
    val driverName: String = "",
    val deliveryDate: String = "",
    val orderId: Int = 0
)

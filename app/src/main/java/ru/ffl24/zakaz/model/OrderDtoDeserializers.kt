package ru.ffl24.zakaz.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

class DriverOrderDtoDeserializer : JsonDeserializer<DriverOrderDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DriverOrderDto {
        val fields = json.asJsonObject.toOrderFields()
        return DriverOrderDto(
            orderId = fields.orderId,
            orderNumber = fields.orderNumber,
            driverId = fields.driverId,
            driverName = fields.driverName,
            clientInfo = fields.clientInfo,
            deliveryNote = fields.deliveryNote,
            deliveryDate = fields.deliveryDate,
            invoicesCount = fields.invoicesCount,
            notReadyInvoices = fields.notReadyInvoices,
            isReady = fields.isReady,
            isPartiallyReady = fields.isPartiallyReady,
            routeText = fields.routeText,
            lat = fields.lat,
            lon = fields.lon,
            hasRouteText = fields.hasRouteText,
            displayTitle = fields.displayTitle,
            readinessLabel = fields.readinessLabel,
            quantityReadiness = fields.quantityReadiness,
            quantityLabel = fields.quantityLabel,
            isDelivered = fields.isDelivered,
            deliveredAt = fields.deliveredAt,
            canMarkDelivered = fields.canMarkDelivered
        )
    }
}

class OrderDetailsDtoDeserializer : JsonDeserializer<OrderDetailsDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): OrderDetailsDto {
        val jsonObject = json.asJsonObject
        val orderNumber = jsonObject.normalizedOrderNumber()
        return OrderDetailsDto(
            orderId = jsonObject.int("orderId", "order_id", "id"),
            orderNumber = orderNumber,
            title = jsonObject.string("title", "displayTitle", "display_title")
                .ifBlank { orderNumber },
            address = jsonObject.nullableString("address", "clientInfo", "client_info", "name_c"),
            deliveryTime = jsonObject.nullableString(
                "deliveryTime",
                "delivery_time",
                "deliveryDate",
                "delivery_date",
                "date"
            ),
            comment = jsonObject.nullableString("comment", "deliveryNote", "delivery_note"),
            routeText = jsonObject.string("routeText", "route_text"),
            invoicesCount = jsonObject.int("invoicesCount", "invoices_count", "col"),
            isReady = jsonObject.boolean(default = false, "isReady", "is_ready"),
            readinessLabel = jsonObject.string("readinessLabel", "readiness_label")
                .ifBlank { "Неизвестно" },
            quantityReadiness = jsonObject.string("quantityReadiness", "quantity_readiness")
                .ifBlank { "unknown" },
            quantityLabel = jsonObject.string("quantityLabel", "quantity_label")
                .ifBlank { "Неизвестный статус накладных" },
            isDelivered = jsonObject.boolean(default = false, "isDelivered", "is_delivered", "st_vv"),
            deliveredAt = jsonObject.nullableString("deliveredAt", "delivered_at", "date_att_log"),
            canMarkDelivered = jsonObject.boolean(default = true, "canMarkDelivered", "can_mark_delivered"),
            previousDrivers = jsonObject.previousDrivers()
        )
    }
}

private data class OrderFields(
    val orderId: Long,
    val orderNumber: String,
    val driverId: Long,
    val driverName: String,
    val clientInfo: String,
    val deliveryNote: String?,
    val deliveryDate: String,
    val invoicesCount: Int,
    val notReadyInvoices: Int,
    val isReady: Boolean,
    val isPartiallyReady: Boolean,
    val routeText: String?,
    val lat: Double?,
    val lon: Double?,
    val hasRouteText: Boolean,
    val displayTitle: String,
    val readinessLabel: String,
    val quantityReadiness: String,
    val quantityLabel: String,
    val isDelivered: Boolean,
    val deliveredAt: String?,
    val canMarkDelivered: Boolean
)

private fun JsonObject.toOrderFields(): OrderFields {
    val orderNumber = normalizedOrderNumber()
    return OrderFields(
        orderId = long("orderId", "order_id", "id"),
        orderNumber = orderNumber,
        driverId = long("driverId", "driver_id", "id_v"),
        driverName = string("driverName", "driver_name"),
        clientInfo = string("clientInfo", "client_info", "name_c"),
        deliveryNote = nullableString("deliveryNote", "delivery_note"),
        deliveryDate = string("deliveryDate", "delivery_date", "date"),
        invoicesCount = int("invoicesCount", "invoices_count", "col"),
        notReadyInvoices = int("notReadyInvoices", "not_ready_invoices"),
        isReady = boolean(default = false, "isReady", "is_ready"),
        isPartiallyReady = boolean(default = false, "isPartiallyReady", "is_partially_ready"),
        routeText = nullableString("routeText", "route_text"),
        lat = doubleOrNull("lat", "latitude", "routeLatitude", "route_latitude"),
        lon = doubleOrNull("lon", "longitude", "routeLongitude", "route_longitude"),
        hasRouteText = boolean(default = false, "hasRouteText", "has_route_text"),
        displayTitle = string("displayTitle", "display_title").ifBlank { orderNumber },
        readinessLabel = string("readinessLabel", "readiness_label").ifBlank { "Неизвестно" },
        quantityReadiness = string("quantityReadiness", "quantity_readiness").ifBlank { "unknown" },
        quantityLabel = string("quantityLabel", "quantity_label").ifBlank { "Неизвестный статус накладных" },
        isDelivered = boolean(default = false, "isDelivered", "is_delivered", "st_vv"),
        deliveredAt = nullableString("deliveredAt", "delivered_at", "date_att_log"),
        canMarkDelivered = boolean(default = true, "canMarkDelivered", "can_mark_delivered")
    )
}

private fun JsonObject.normalizedOrderNumber(): String {
    return nullableString("orderNumber", "order_number", "nomer_z")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Без номера"
}

private fun JsonObject.string(vararg names: String): String {
    return nullableString(*names).orEmpty()
}

private fun JsonObject.nullableString(vararg names: String): String? {
    val element = firstElement(*names) ?: return null
    return if (element.isJsonNull) null else element.asString
}

private fun JsonObject.long(vararg names: String): Long {
    val element = firstElement(*names) ?: return 0L
    return if (element.isJsonNull) 0L else element.asLong
}

private fun JsonObject.int(vararg names: String): Int {
    val element = firstElement(*names) ?: return 0
    return if (element.isJsonNull) 0 else element.asInt
}

private fun JsonObject.doubleOrNull(vararg names: String): Double? {
    val element = firstElement(*names) ?: return null
    return if (element.isJsonNull) null else element.asDouble
}

private fun JsonObject.boolean(default: Boolean, vararg names: String): Boolean {
    val element = firstElement(*names) ?: return default
    if (element.isJsonNull) return default
    return when {
        element.asJsonPrimitive.isBoolean -> element.asBoolean
        element.asJsonPrimitive.isNumber -> element.asInt != 0
        else -> element.asString.equals("true", ignoreCase = true) || element.asString == "1"
    }
}

private fun JsonObject.previousDrivers(): List<PreviousDriverDto> {
    val element = firstElement("previousDrivers", "previous_drivers") ?: return emptyList()
    if (!element.isJsonArray) return emptyList()

    return element.asJsonArray
        .filter { it.isJsonObject }
        .map { previousDriver ->
            val jsonObject = previousDriver.asJsonObject
            PreviousDriverDto(
                driverId = jsonObject.int("driverId", "driver_id", "id_v"),
                driverName = jsonObject.string("driverName", "driver_name"),
                deliveryDate = jsonObject.string("deliveryDate", "delivery_date", "date"),
                orderId = jsonObject.int("orderId", "order_id", "id")
            )
        }
}

private fun JsonObject.firstElement(vararg names: String): JsonElement? {
    return names.firstNotNullOfOrNull { name ->
        get(name)?.takeUnless { it.isJsonNull }
    }
}

package ru.ffl24.direct.navigation

import android.net.Uri
import androidx.navigation.NavController
import ru.ffl24.direct.domain.OrderId

/** P4 list can call open; no invented orders or public external deep link. */
object OrderDetailsDestination {
    const val pattern = "orders/{orderId}"
    fun open(controller: NavController, id: OrderId) {
        controller.navigate("orders/${Uri.encode(id.wire)}") { launchSingleTop = true }
    }
}

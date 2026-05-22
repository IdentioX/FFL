package ru.ffl24.zakaz.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import ru.ffl24.zakaz.model.DriverOrderDto

object YandexRouteNavigator {
    private const val YANDEX_MAPS_PACKAGE = "ru.yandex.yandexmaps"
    private const val NO_ROUTE_DATA_MESSAGE = "Нет данных для маршрута"
    private const val YANDEX_MAPS_NOT_FOUND_MESSAGE = "Яндекс Карты не найдены"
    private const val TAG = "YandexRouteNavigator"

    fun openRoute(context: Context, order: DriverOrderDto): Boolean {
        val routeIntents = buildRouteIntents(context, order)
        if (routeIntents.isEmpty()) {
            Toast.makeText(context, NO_ROUTE_DATA_MESSAGE, Toast.LENGTH_SHORT).show()
            return false
        }

        routeIntents.forEach { intent ->
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Yandex Maps did not handle intent: ${intent.data}", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Security error while opening Yandex Maps: ${intent.data}", e)
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error while opening Yandex Maps: ${intent.data}", e)
            }
        }

        Toast.makeText(context, YANDEX_MAPS_NOT_FOUND_MESSAGE, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun buildRouteIntents(context: Context, order: DriverOrderDto): List<Intent> {
        val routeData = buildRouteData(order) ?: return emptyList()
        val uris = listOf(
            Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$routeData&rtt=auto"),
            Uri.parse("https://maps.yandex.ru/?rtext=~$routeData&rtt=auto")
        )

        return uris.map { uri ->
            Intent(Intent.ACTION_VIEW, uri).apply {
                `package` = YANDEX_MAPS_PACKAGE
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }

    private fun buildRouteData(order: DriverOrderDto): String? {
        return when {
            hasCoordinates(order) -> "${order.lat},${order.lon}"
            !order.routeText.isNullOrBlank() -> Uri.encode(order.routeText.trim())
            else -> null
        }
    }

    private fun hasCoordinates(order: DriverOrderDto): Boolean {
        return order.lat != null && order.lon != null
    }
}

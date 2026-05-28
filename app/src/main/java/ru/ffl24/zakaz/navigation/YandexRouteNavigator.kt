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
    private const val NO_ROUTE_DATA_MESSAGE = "Нет адреса для маршрута"
    private const val YANDEX_MAPS_NOT_FOUND_MESSAGE = "Яндекс Карты не найдены"
    private const val TAG = "YandexRouteNavigator"

    fun openRoute(context: Context, order: DriverOrderDto): Boolean {
        return openRoute(
            context = context,
            routeText = order.routeText,
            orderId = order.orderId
        )
    }

    fun openRoute(context: Context, routeText: String?, orderId: Number? = null): Boolean {
        val trimmedRouteText = routeText?.trim().orEmpty()
        if (trimmedRouteText.isBlank()) {
            Log.d(TAG, "routeText is blank for orderId=$orderId")
            Toast.makeText(context, NO_ROUTE_DATA_MESSAGE, Toast.LENGTH_SHORT).show()
            return false
        }

        val finalUri = buildRouteUri(trimmedRouteText)
        Log.d(TAG, "routeText=$trimmedRouteText")
        Log.d(TAG, "final Uri=$finalUri")

        val intent = Intent(Intent.ACTION_VIEW, finalUri).apply {
            `package` = YANDEX_MAPS_PACKAGE
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Yandex Maps did not handle intent: $finalUri", e)
            Toast.makeText(context, YANDEX_MAPS_NOT_FOUND_MESSAGE, Toast.LENGTH_SHORT).show()
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Security error while opening Yandex Maps: $finalUri", e)
            Toast.makeText(context, YANDEX_MAPS_NOT_FOUND_MESSAGE, Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error while opening Yandex Maps: $finalUri", e)
            Toast.makeText(context, YANDEX_MAPS_NOT_FOUND_MESSAGE, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun buildRouteUri(routeText: String): Uri {
        // Для локальной проверки можно временно подставить:
        // val debugRouteText = "Москва, Красная площадь, 1"
        val encodedRouteText = Uri.encode(routeText.trim())
        return Uri.parse("https://yandex.ru/maps/?rtext=~$encodedRouteText&rtt=auto")
    }
}

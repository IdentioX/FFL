package ru.ffl24.zakaz.repository

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import retrofit2.HttpException
import ru.ffl24.zakaz.api.DriverApiService
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.model.MarkDeliveredResponse
import ru.ffl24.zakaz.model.OrderDetailsDto
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DriverOrdersRepository(
    private val driverApiService: DriverApiService
) {

    suspend fun logRouteOpened(
        orderId: Long,
        driverId: Long,
        source: String = "android"
    ) {
        try {
            val response = driverApiService.logRouteOpened(
                orderId = orderId,
                orderIdQuery = orderId,
                driverId = driverId,
                source = source
            )

            if (!response.isSuccessful) {
                Log.w(
                    TAG,
                    "Route-opened logging failed with code ${response.code()} for orderId=$orderId"
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error logging route-opened for orderId=$orderId", e)
        } catch (e: HttpException) {
            Log.w(TAG, "HTTP error logging route-opened for orderId=$orderId", e)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error logging route-opened for orderId=$orderId", e)
        }
    }

    suspend fun getDriverOrders(
        date: String,
        ready: String? = null,
        sort: String? = null
    ): Result<List<DriverOrderDto>> {
        return try {
            val response = driverApiService.getDriverOrders(
                date = date,
                ready = ready,
                sort = sort
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(IllegalStateException("Сервер вернул пустой список заказов."))
                }
            } else {
                Result.failure(toHttpError(response.code()))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("Сервер не ответил вовремя. Попробуйте еще раз.", e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("Нет подключения или сервер недоступен.", e))
        } catch (e: ConnectException) {
            Result.failure(IllegalStateException("Не удалось подключиться к серверу.", e))
        } catch (e: NoRouteToHostException) {
            Result.failure(IllegalStateException("Сеть недоступна. Проверьте подключение.", e))
        } catch (e: MalformedJsonException) {
            Result.failure(IllegalStateException("Сервер вернул нечитаемые данные.", e))
        } catch (e: JsonSyntaxException) {
            Result.failure(IllegalStateException("Сервер вернул данные в неожиданном формате.", e))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Ошибка сети. Проверьте подключение.", e))
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(toHttpErrorMessage(e.code()), e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось загрузить заказы. Попробуйте еще раз.", e))
        }
    }

    suspend fun getOrderDetails(orderId: Int): Result<OrderDetailsDto> {
        return try {
            Result.success(driverApiService.getOrderDetails(orderId = orderId))
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("Сервер не ответил вовремя. Попробуйте еще раз.", e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("Нет подключения или сервер недоступен.", e))
        } catch (e: ConnectException) {
            Result.failure(IllegalStateException("Не удалось подключиться к серверу.", e))
        } catch (e: NoRouteToHostException) {
            Result.failure(IllegalStateException("Сеть недоступна. Проверьте подключение.", e))
        } catch (e: MalformedJsonException) {
            Result.failure(IllegalStateException("Сервер вернул нечитаемые данные.", e))
        } catch (e: JsonSyntaxException) {
            Result.failure(IllegalStateException("Сервер вернул данные в неожиданном формате.", e))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Ошибка сети. Проверьте подключение.", e))
        } catch (e: HttpException) {
            Result.failure(toHttpError(e.code()))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось загрузить заказ. Попробуйте еще раз.", e))
        }
    }

    suspend fun markDelivered(orderId: Long): Result<MarkDeliveredResponse> {
        return try {
            val response = driverApiService.markDelivered(orderId = orderId)

            if (response.isSuccessful) {
                Result.success(response.body() ?: MarkDeliveredResponse())
            } else {
                Result.failure(toHttpError(response.code()))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("Сервер не ответил вовремя. Попробуйте еще раз.", e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("Нет подключения или сервер недоступен.", e))
        } catch (e: ConnectException) {
            Result.failure(IllegalStateException("Не удалось подключиться к серверу.", e))
        } catch (e: NoRouteToHostException) {
            Result.failure(IllegalStateException("Сеть недоступна. Проверьте подключение.", e))
        } catch (e: MalformedJsonException) {
            Result.failure(IllegalStateException("Сервер вернул нечитаемые данные.", e))
        } catch (e: JsonSyntaxException) {
            Result.failure(IllegalStateException("Сервер вернул данные в неожиданном формате.", e))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Ошибка сети. Проверьте подключение.", e))
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(toHttpErrorMessage(e.code()), e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось отметить заказ сданным. Попробуйте еще раз.", e))
        }
    }

    private fun toHttpErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Неверный запрос."
            401 -> "Нужно войти заново."
            403 -> "Доступ запрещен."
            404 -> "Заказы не найдены."
            in 500..599 -> "Ошибка сервера. Попробуйте позже."
            else -> "Запрос не выполнен. Код $code."
        }
    }

    private fun toHttpError(code: Int): Throwable {
        return if (code == 401) {
            UnauthorizedException("Нужно войти заново.")
        } else {
            IllegalStateException(toHttpErrorMessage(code))
        }
    }

    private companion object {
        const val TAG = "DriverOrdersRepository"
    }
}

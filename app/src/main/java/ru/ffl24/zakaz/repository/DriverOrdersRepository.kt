package ru.ffl24.zakaz.repository

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import retrofit2.HttpException
import ru.ffl24.zakaz.api.DriverApiService
import ru.ffl24.zakaz.model.DriverOrderDto
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
                    Result.failure(IllegalStateException("The server returned an empty orders response."))
                }
            } else {
                Result.failure(toHttpError(response.code()))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("The server took too long to respond. Please try again.", e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("No internet connection or the server address could not be reached.", e))
        } catch (e: ConnectException) {
            Result.failure(IllegalStateException("Could not connect to the server. Check the API address and try again.", e))
        } catch (e: NoRouteToHostException) {
            Result.failure(IllegalStateException("Network route to the server is unavailable. Check your connection and try again.", e))
        } catch (e: MalformedJsonException) {
            Result.failure(IllegalStateException("The server returned unreadable data. Please try again later.", e))
        } catch (e: JsonSyntaxException) {
            Result.failure(IllegalStateException("The server returned data in an unexpected format.", e))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("Network error. Please check your connection and try again.", e))
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(toHttpErrorMessage(e.code()), e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Something went wrong while loading orders. Please try again.", e))
        }
    }

    private fun toHttpErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Invalid request. Please check request parameters."
            401 -> "Authorization required. Please sign in again."
            403 -> "Access denied."
            404 -> "Driver orders were not found."
            in 500..599 -> "Server error. Please try again later."
            else -> "Request failed with code $code."
        }
    }

    private fun toHttpError(code: Int): Throwable {
        return if (code == 401) {
            UnauthorizedException("Authorization required. Please sign in again.")
        } else {
            IllegalStateException(toHttpErrorMessage(code))
        }
    }

    private companion object {
        const val TAG = "DriverOrdersRepository"
    }
}

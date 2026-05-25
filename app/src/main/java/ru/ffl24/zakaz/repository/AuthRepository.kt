package ru.ffl24.zakaz.repository

import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import retrofit2.HttpException
import retrofit2.Response
import ru.ffl24.zakaz.api.DriverApiService
import ru.ffl24.zakaz.model.AuthMeResponse
import ru.ffl24.zakaz.model.DriverOptionDto
import ru.ffl24.zakaz.model.LoginRequest
import ru.ffl24.zakaz.model.LoginResponse
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AuthRepository(
    private val driverApiService: DriverApiService
) {
    suspend fun getDrivers(): Result<List<DriverOptionDto>> {
        return safeCall(
            emptyBodyMessage = "Сервер вернул пустой список водителей."
        ) {
            driverApiService.getDrivers()
        }
    }

    suspend fun login(driverId: Int, password: String): Result<LoginResponse> {
        return safeCall(
            emptyBodyMessage = "Сервер вернул пустой ответ авторизации.",
            unauthorizedMessage = "Неверный пароль"
        ) {
            driverApiService.login(LoginRequest(driverId = driverId, password = password))
        }
    }

    suspend fun me(): Result<AuthMeResponse> {
        return safeCall(
            emptyBodyMessage = "Сервер вернул пустой ответ профиля."
        ) {
            driverApiService.me()
        }
    }

    private suspend fun <T> safeCall(
        emptyBodyMessage: String,
        unauthorizedMessage: String = "Требуется повторный вход.",
        call: suspend () -> Response<T>
    ): Result<T> {
        return try {
            val response = call()
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(IllegalStateException(emptyBodyMessage))
                    }
                }

                response.code() == 401 -> Result.failure(UnauthorizedException(unauthorizedMessage))
                else -> Result.failure(IllegalStateException(toHttpErrorMessage(response.code())))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IllegalStateException("Сервер не ответил вовремя. Попробуйте еще раз.", e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("Не удалось найти сервер. Проверьте подключение.", e))
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
            if (e.code() == 401) {
                Result.failure(UnauthorizedException(unauthorizedMessage))
            } else {
                Result.failure(IllegalStateException(toHttpErrorMessage(e.code()), e))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось выполнить запрос. Попробуйте еще раз.", e))
        }
    }

    private fun toHttpErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Неверный запрос."
            403 -> "Доступ запрещен."
            404 -> "Данные не найдены."
            in 500..599 -> "Ошибка сервера. Попробуйте позже."
            else -> "Запрос не выполнен. Код $code."
        }
    }
}

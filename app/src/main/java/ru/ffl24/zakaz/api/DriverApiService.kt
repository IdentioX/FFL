package ru.ffl24.zakaz.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.ffl24.zakaz.model.AuthMeResponse
import ru.ffl24.zakaz.model.DriverOptionDto
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.model.LoginRequest
import ru.ffl24.zakaz.model.LoginResponse

interface DriverApiService {
    @GET("health")
    suspend fun getHealth(): Response<Unit>

    @GET("api/auth/drivers")
    suspend fun getDrivers(): Response<List<DriverOptionDto>>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun me(): Response<AuthMeResponse>

    @GET("api/driver/orders")
    suspend fun getDriverOrders(
        @Query("date") date: String,
        @Query("ready") ready: String? = null,
        @Query("sort") sort: String? = null
    ): Response<List<DriverOrderDto>>

    @POST("api/driver/orders/{order_id}/route-opened")
    suspend fun logRouteOpened(
        @Path("order_id") orderId: Long,
        @Query("order_id") orderIdQuery: Long,
        @Query("driver_id") driverId: Long,
        @Query("source") source: String = "android"
    ): Response<Unit>
}

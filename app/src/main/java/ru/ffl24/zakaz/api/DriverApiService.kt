package ru.ffl24.zakaz.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.ffl24.zakaz.model.DriverOrderDto

interface DriverApiService {
    @GET("health")
    suspend fun getHealth(): Response<Unit>

    @GET("api/driver/orders")
    suspend fun getDriverOrders(
        @Query("date") date: String,
        @Query("driver_id") driverId: Int,
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

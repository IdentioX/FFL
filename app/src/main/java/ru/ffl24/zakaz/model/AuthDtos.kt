package ru.ffl24.zakaz.model

data class DriverOptionDto(
    val driverKey: String? = null,
    val driverId: Int,
    val driverName: String,
    val source: String? = null
)

data class LoginRequest(
    val driverId: Int,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val driverId: Int,
    val driverName: String
)

data class AuthMeResponse(
    val driverId: Int,
    val driverName: String
)

package ru.ffl24.zakaz.data.auth

data class AuthSession(
    val accessToken: String,
    val driverId: Int,
    val driverName: String
)

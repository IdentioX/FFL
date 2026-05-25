package ru.ffl24.zakaz.repository

class UnauthorizedException(
    message: String = "Authorization required."
) : IllegalStateException(message)

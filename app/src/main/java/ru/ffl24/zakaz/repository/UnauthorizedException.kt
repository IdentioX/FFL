package ru.ffl24.zakaz.repository

class UnauthorizedException(
    message: String = "Нужно войти заново."
) : IllegalStateException(message)

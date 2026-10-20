package ru.ffl24.direct.domain

enum class RuntimeMode {
    DIRECT_MYSQL;

    companion object {
        fun fromBuildValue(value: String): RuntimeMode = valueOf(value)
    }
}

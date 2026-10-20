package ru.ffl24.direct.domain

sealed interface RuntimeReadiness {
    data object ConfigurationMissing : RuntimeReadiness
    data object ConfigurationInvalid : RuntimeReadiness
    data object ConnectionNotChecked : RuntimeReadiness
}

interface StartupRepository {
    fun readiness(): RuntimeReadiness
}

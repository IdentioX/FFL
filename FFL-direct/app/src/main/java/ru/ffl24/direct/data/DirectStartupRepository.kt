package ru.ffl24.direct.data

import ru.ffl24.direct.data.mysql.MySqlConfiguration
import ru.ffl24.direct.domain.RuntimeReadiness
import ru.ffl24.direct.domain.StartupRepository

class DirectStartupRepository(private val configuration: MySqlConfiguration) : StartupRepository {
    override fun readiness(): RuntimeReadiness = when (configuration) {
        MySqlConfiguration.Missing -> RuntimeReadiness.ConfigurationMissing
        is MySqlConfiguration.Invalid -> RuntimeReadiness.ConfigurationInvalid
        is MySqlConfiguration.Ready -> RuntimeReadiness.ConnectionNotChecked
    }
}

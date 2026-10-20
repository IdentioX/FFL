package ru.ffl24.direct

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ffl24.direct.domain.RuntimeMode
import ru.ffl24.direct.domain.RuntimeReadiness
import ru.ffl24.direct.data.mysql.DirectMySqlSettings

class AppGraphTest {
    @Test
    fun productionGraphStartsWithoutConnectionOrCredentials() {
        val graph = AppGraph.create(BuildConfig.DATA_SOURCE_MODE, DirectMySqlSettings())
        assertEquals(RuntimeMode.DIRECT_MYSQL, graph.mode)
        assertEquals(RuntimeReadiness.ConfigurationMissing, graph.startupRepository.readiness())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedModeCannotSelectAnotherTransport() {
        AppGraph.create("UNSUPPORTED_TRANSPORT")
    }
}

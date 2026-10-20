package ru.ffl24.direct

import ru.ffl24.direct.data.DirectStartupRepository
import ru.ffl24.direct.data.mysql.DirectMySqlConfig
import ru.ffl24.direct.data.mysql.DirectMySqlSettings
import ru.ffl24.direct.data.mysql.MySqlConnectionProbe
import ru.ffl24.direct.data.mysql.MySqlReadExecutor
import ru.ffl24.direct.data.mysql.DirectMySqlDriverDataSource
import ru.ffl24.direct.data.repository.DirectAuthRepository
import ru.ffl24.direct.domain.AuthRepository
import ru.ffl24.direct.domain.RuntimeMode
import ru.ffl24.direct.domain.StartupRepository
import ru.ffl24.direct.presentation.SetupAuthViewModel
import ru.ffl24.direct.session.AuthSessionManager
import ru.ffl24.direct.session.DriverSessionStore
import ru.ffl24.direct.session.InMemoryDriverSessionStore
import ru.ffl24.direct.data.mysql.DirectMySqlOrderDetailsDataSource
import ru.ffl24.direct.data.repository.DirectOrderDetailsRepository
import ru.ffl24.direct.domain.OrderDetailsRepository

class AppGraph private constructor(
    val mode: RuntimeMode,
    override val startupRepository: StartupRepository,
    val connectionProbe: MySqlConnectionProbe,
    override val authRepository: AuthRepository,
    override val orderDetailsRepository: OrderDetailsRepository,
    override val workspaceRepository: ru.ffl24.direct.domain.DriverWorkspaceRepository,
    internal val sessions: AuthSessionManager,
    private val connector: ru.ffl24.direct.data.mysql.RetiringConnector
) : DirectRuntime {

    var profileScope: String = ""
    internal fun retire() = connector.retire()

    companion object {
        fun create(
            buildMode: String,
            settings: DirectMySqlSettings = buildSettings(),
            sessionStore: DriverSessionStore = InMemoryDriverSessionStore()
        ): AppGraph {
            val mode = RuntimeMode.fromBuildValue(buildMode)
            val configuration = DirectMySqlConfig.parse(settings)
            val connector = ru.ffl24.direct.data.mysql.RetiringConnector()
            val reader = MySqlReadExecutor(configuration,connector)
            val sessions = AuthSessionManager(sessionStore)
            val repository = DirectAuthRepository(DirectMySqlDriverDataSource(reader), sessions)
            val details = DirectOrderDetailsRepository(DirectMySqlOrderDetailsDataSource(reader), repository, sessions)
            val workspace = ru.ffl24.direct.data.repository.DirectWorkspaceRepository(
                ru.ffl24.direct.data.mysql.DirectWorkspaceDataSource(reader),
                ru.ffl24.direct.data.mysql.DirectDeliveryDataSource(configuration,connector), repository, sessions)
            return AppGraph(mode, DirectStartupRepository(configuration), MySqlConnectionProbe(configuration,connector), repository, details, workspace, sessions,connector)
        }

        internal fun buildSettings() = DirectMySqlSettings(
            BuildConfig.DIRECT_MYSQL_HOST, BuildConfig.DIRECT_MYSQL_PORT,
            BuildConfig.DIRECT_MYSQL_DATABASE, BuildConfig.DIRECT_MYSQL_USER,
            BuildConfig.DIRECT_MYSQL_PASSWORD, BuildConfig.DIRECT_MYSQL_USE_SSL,
            BuildConfig.DIRECT_MYSQL_CA_PEM
        )
    }
}

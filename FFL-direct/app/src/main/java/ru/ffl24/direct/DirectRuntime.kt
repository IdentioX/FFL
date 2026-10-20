package ru.ffl24.direct

import ru.ffl24.direct.domain.*
import ru.ffl24.direct.presentation.SetupAuthViewModel

/** UI depends on contracts; production composition is always the Direct MySQL AppGraph. */
interface DirectRuntime {
    val startupRepository: StartupRepository
    val authRepository: AuthRepository
    val orderDetailsRepository: OrderDetailsRepository
    val workspaceRepository: DriverWorkspaceRepository
    val authViewModelFactory get() = SetupAuthViewModel.Factory(startupRepository.readiness(),authRepository)
}

package ru.ffl24.zakaz.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.ffl24.zakaz.data.auth.AuthSessionManager
import ru.ffl24.zakaz.model.OrderDetailsDto
import ru.ffl24.zakaz.repository.DriverOrdersRepository
import ru.ffl24.zakaz.repository.UnauthorizedException

data class OrderDetailsUiState(
    val isLoading: Boolean = false,
    val details: OrderDetailsDto? = null,
    val errorMessage: String? = null,
    val isDelivering: Boolean = false,
    val message: String? = null
)

class OrderDetailsViewModel(
    private val repositoryProvider: () -> DriverOrdersRepository,
    private val authSessionManager: AuthSessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(OrderDetailsUiState())
    val uiState: StateFlow<OrderDetailsUiState> = _uiState.asStateFlow()

    private var currentOrderId: Int? = null

    fun loadDetails(orderId: Int) {
        currentOrderId = orderId
        val shouldClearDetails = _uiState.value.details?.orderId != orderId

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    details = if (shouldClearDetails) null else it.details,
                    errorMessage = null,
                    message = null
                )
            }

            repositoryProvider().getOrderDetails(orderId)
                .onSuccess { details ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = details,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is UnauthorizedException) {
                        authSessionManager.clearSession()
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = throwable.message ?: "Не удалось загрузить заказ",
                                details = null
                            )
                        }
                    }
                }
        }
    }

    fun retry() {
        currentOrderId?.let(::loadDetails)
    }

    fun markDelivered() {
        val details = _uiState.value.details ?: return
        if (_uiState.value.isDelivering || details.isDelivered || !details.isReady || !details.canMarkDelivered) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDelivering = true,
                    message = null
                )
            }

            repositoryProvider().markDelivered(orderId = details.orderId.toLong())
                .onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            isDelivering = false,
                            details = state.details?.copy(
                                isDelivered = true,
                                canMarkDelivered = false,
                                deliveredAt = response.deliveredAt
                            ),
                            message = "Заказ сдан"
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is UnauthorizedException) {
                        authSessionManager.clearSession()
                    } else {
                        _uiState.update {
                            it.copy(
                                isDelivering = false,
                                message = "Не удалось сдать заказ. Повторите."
                            )
                        }
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

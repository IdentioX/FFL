package ru.ffl24.zakaz.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.repository.DriverOrdersRepository
import ru.ffl24.zakaz.repository.UnauthorizedException
import ru.ffl24.zakaz.data.auth.AuthSessionManager
import java.net.ConnectException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DriverOrdersViewModel(
    private val repositoryProvider: () -> DriverOrdersRepository,
    private val authSessionManager: AuthSessionManager
) : ViewModel() {
    private val testOrdersDate = LocalDate.of(2026, 5, 18)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _orders = MutableStateFlow<List<DriverOrderDto>>(emptyList())
    val orders: StateFlow<List<DriverOrderDto>> = _orders.asStateFlow()

    private val _deliveringOrderIds = MutableStateFlow<Set<Long>>(emptySet())
    val deliveringOrderIds: StateFlow<Set<Long>> = _deliveringOrderIds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>()
    val toastMessages: SharedFlow<String> = _toastMessages

    init {
        loadTodayOrders()
    }

    fun refresh() {
        loadTodayOrders()
    }

    fun logRouteOpened(order: DriverOrderDto) {
        viewModelScope.launch {
            repositoryProvider().logRouteOpened(
                orderId = order.orderId,
                driverId = order.driverId
            )
        }
    }

    fun markDelivered(order: DriverOrderDto) {
        if (order.isDelivered || !order.isReady || !order.canMarkDelivered) {
            return
        }

        viewModelScope.launch {
            _deliveringOrderIds.value = _deliveringOrderIds.value + order.orderId

            try {
                repositoryProvider().markDelivered(orderId = order.orderId)
                    .onSuccess { response ->
                        _orders.value = sortOrders(
                            _orders.value.map { currentOrder ->
                                if (currentOrder.orderId == order.orderId) {
                                    currentOrder.copy(
                                        isDelivered = true,
                                        canMarkDelivered = false,
                                        deliveredAt = response.deliveredAt
                                    )
                                } else {
                                    currentOrder
                                }
                            }
                        )
                        _toastMessages.emit("Заказ сдан")
                    }
                    .onFailure { throwable ->
                        if (throwable is UnauthorizedException) {
                            authSessionManager.clearSession()
                        } else {
                            _toastMessages.emit("Не удалось сдать заказ. Повторите.")
                        }
                    }
            } catch (e: Exception) {
                _toastMessages.emit("Не удалось сдать заказ. Повторите.")
            } finally {
                _deliveringOrderIds.value = _deliveringOrderIds.value - order.orderId
            }
        }
    }

    private fun loadTodayOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = repositoryProvider().getDriverOrders(
                    date = testOrdersDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                )

                result
                    .onSuccess { loadedOrders ->
                        _orders.value = sortOrders(loadedOrders)
                    }
                    .onFailure { throwable ->
                        if (throwable is UnauthorizedException) {
                            authSessionManager.clearSession()
                        } else {
                            _errorMessage.value = throwable.message ?: "Не удалось загрузить заказы."
                        }
                    }
            } catch (_: IllegalArgumentException) {
                _errorMessage.value = "Неверный адрес сервера. Проверьте настройки."
            } catch (e: SocketTimeoutException) {
                _errorMessage.value = "Сервер не ответил вовремя. Попробуйте еще раз."
            } catch (e: UnknownHostException) {
                _errorMessage.value = "Нет подключения или сервер недоступен."
            } catch (e: ConnectException) {
                _errorMessage.value = "Не удалось подключиться к серверу."
            } catch (e: NoRouteToHostException) {
                _errorMessage.value = "Сеть недоступна. Проверьте подключение."
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Не удалось загрузить заказы."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun sortOrders(orders: List<DriverOrderDto>): List<DriverOrderDto> {
        return orders.sortedWith(
            compareBy<DriverOrderDto> { it.isDelivered }
                .thenBy { it.orderId }
        )
    }
}

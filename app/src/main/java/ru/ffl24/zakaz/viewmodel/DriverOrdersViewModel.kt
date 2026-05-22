package ru.ffl24.zakaz.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ffl24.zakaz.model.DriverOrderDto
import ru.ffl24.zakaz.repository.DriverOrdersRepository
import java.net.ConnectException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DriverOrdersViewModel(
    private val repositoryProvider: () -> DriverOrdersRepository,
    private val driverIdProvider: () -> Int
) : ViewModel() {
    private val testOrdersDate = LocalDate.of(2026, 5, 18)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _orders = MutableStateFlow<List<DriverOrderDto>>(emptyList())
    val orders: StateFlow<List<DriverOrderDto>> = _orders.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

    private fun loadTodayOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = repositoryProvider().getDriverOrders(
                    date = testOrdersDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    driverId = driverIdProvider()
                )

                result
                    .onSuccess { loadedOrders ->
                        _orders.value = loadedOrders
                    }
                    .onFailure { throwable ->
                        _errorMessage.value = throwable.message ?: "Unable to load orders. Please try again."
                    }
            } catch (e: IllegalArgumentException) {
                _errorMessage.value = "The API base URL is invalid. Check it in Settings."
            } catch (e: SocketTimeoutException) {
                _errorMessage.value = "The server took too long to respond. Please try again."
            } catch (e: UnknownHostException) {
                _errorMessage.value = "No internet connection or the server address could not be reached."
            } catch (e: ConnectException) {
                _errorMessage.value = "Could not connect to the server. Check the API address and try again."
            } catch (e: NoRouteToHostException) {
                _errorMessage.value = "Network route to the server is unavailable. Check your connection and try again."
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unable to load orders. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}

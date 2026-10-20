package ru.ffl24.direct.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ffl24.direct.domain.*

sealed interface OrderDetailsUiState {
    data object Loading : OrderDetailsUiState
    data class Content(val details: OrderDetails, val busy: Boolean = false, val refreshFailed: Boolean = false) : OrderDetailsUiState
    data object NotFound : OrderDetailsUiState
    data object SessionRequired : OrderDetailsUiState
    data object Disabled : OrderDetailsUiState
    data class Failed(val error: AuthError) : OrderDetailsUiState
}

class OrderDetailsViewModel(private val id: OrderId, private val repository: OrderDetailsRepository) : ViewModel() {
    private val mutable = MutableStateFlow<OrderDetailsUiState>(OrderDetailsUiState.Loading)
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L

    fun refresh() {
        if(operation?.isActive==true)return
        val previous=state.value as? OrderDetailsUiState.Content
        perform {
            mutable.value=previous?.copy(busy=true,refreshFailed=false)?:OrderDetailsUiState.Loading
            val result=try {repository.getDetails(id)} catch(e:CancellationException){throw e} catch(_:Exception){DetailsResult.Failed(AuthError.DATA_UNAVAILABLE)}
            currentCoroutineContext().ensureActive()
            if(previous!=null&&result is DetailsResult.Failed)mutable.value=previous.copy(busy=false,refreshFailed=true)
            else show(result)
        }
    }

    fun hide() {
        generation++
        operation?.cancel()
        mutable.value = OrderDetailsUiState.Loading
    }

    /** Re-read access, ownership and route instead of opening an old address after refresh/logout. */
    fun prepareRoute(open: (OrderContacts) -> Unit) {
        if ((state.value as? OrderDetailsUiState.Content)?.busy != false) return
        perform {
            val current = mutable.value as? OrderDetailsUiState.Content ?: return@perform
            mutable.value = current.copy(busy = true)
            val result = repository.getDetails(id)
            if (result !is DetailsResult.Success) { show(result); return@perform }
            when (val valid = repository.validateRouteOpened(id)) {
                is DetailsResult.Success -> {
                    currentCoroutineContext().ensureActive()
                    mutable.value = OrderDetailsUiState.Content(result.value)
                    open(result.value.contacts)
                }
                else -> showFailure(valid)
            }
        }
    }

    private fun perform(block: suspend () -> Unit) {
        operation?.cancel()
        val token = ++generation
        operation = viewModelScope.launch {
            try { block() } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (token == generation) mutable.value = OrderDetailsUiState.Failed(AuthError.DATA_UNAVAILABLE)
            }
        }
    }

    private suspend fun show(result: DetailsResult<OrderDetails>) {
        currentCoroutineContext().ensureActive()
        mutable.value = when (result) {
            is DetailsResult.Success -> OrderDetailsUiState.Content(result.value)
            else -> failure(result)
        }
    }
    private suspend fun showFailure(result: DetailsResult<*>) {
        currentCoroutineContext().ensureActive()
        mutable.value = failure(result)
    }
    private fun failure(result: DetailsResult<*>): OrderDetailsUiState = when (result) {
        DetailsResult.NotFound -> OrderDetailsUiState.NotFound
        DetailsResult.SessionRequired -> OrderDetailsUiState.SessionRequired
        DetailsResult.DriverDisabled -> OrderDetailsUiState.Disabled
        is DetailsResult.Failed -> OrderDetailsUiState.Failed(result.error)
        else -> OrderDetailsUiState.Failed(AuthError.DATA_UNAVAILABLE)
    }

    class Factory(private val id: OrderId, private val repository: OrderDetailsRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OrderDetailsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return OrderDetailsViewModel(id, repository) as T
        }
    }
}

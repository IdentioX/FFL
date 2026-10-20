package ru.ffl24.direct.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences

class WorkspaceViewModel(private val driver: Long, val date: String,
    private val repository: DriverWorkspaceRepository, private val local: WorkspacePreferences) : ViewModel() {
    private val mutable = MutableStateFlow<DetailsResult<List<DriverOrder>>?>(null)
    val state = mutable.asStateFlow()
    private val noticeState = MutableStateFlow<String?>(null)
    val notice = noticeState.asStateFlow()
    val scrollPositions = (-1..2).associateWith { local.scroll(driver,date,it) }.toMutableMap()
    fun saveScroll(section: Int,index: Int,offset: Int) {
        scrollPositions[section]=index to offset
        viewModelScope.launch { try { local.scroll(driver,date,section,index,offset) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { noticeState.value="Не удалось сохранить прокрутку" } }
    }
    private val localOrders = OrderSection.entries.associateWith { local.savedOrder(driver,date,it) }.toMutableMap()
    private var generation=0L
    private val refreshingState=MutableStateFlow(false)
    val refreshing=refreshingState.asStateFlow()
    private var read: Job? = null
    private var write: Job? = null
    val busy = MutableStateFlow(false)
    fun refresh() {
        if (read?.isActive == true) return
        val token=++generation
        refreshingState.value=true
        read = viewModelScope.launch {
            try {
                val result=repository.orders(date)
                currentCoroutineContext().ensureActive()
                if(token!=generation)return@launch
                if(result is DetailsResult.Success) {
                    var storageFailed=false
                    val ordered=OrderSection.entries.flatMap { section ->
                        val rows=mergeOrder(localOrders[section].orEmpty(),result.value.filter {it.section==section})
                        localOrders[section]=rows.map {it.id.wire}
                        try {local.order(driver,date,section,localOrders.getValue(section))}
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){storageFailed=true}
                        rows
                    }
                    currentCoroutineContext().ensureActive()
                    if(token==generation) {
                        // A drag may finish while a preference commit is suspended on IO.
                        // Reapply the latest in-memory order before publishing the SQL response.
                        val latest=OrderSection.entries.flatMap {section->mergeOrder(localOrders[section].orEmpty(),ordered.filter {it.section==section})}
                        mutable.value=DetailsResult.Success(latest)
                        if(storageFailed)noticeState.value="Не удалось сохранить порядок"
                        else if(noticeState.value=="Не удалось обновить. Показаны ранее загруженные данные.")noticeState.value=null
                    }
                } else if(result is DetailsResult.Failed && mutable.value is DetailsResult.Success) {
                    noticeState.value="Не удалось обновить. Показаны ранее загруженные данные."
                } else mutable.value=result // access/session failures always remove protected rows
            } catch(e:CancellationException){throw e}
            catch(_:Exception) {
                if(token==generation) {
                    if(mutable.value is DetailsResult.Success)noticeState.value="Не удалось обновить. Показаны ранее загруженные данные."
                    else mutable.value=DetailsResult.Failed(AuthError.DATA_UNAVAILABLE)
                }
            } finally {if(token==generation)refreshingState.value=false}
        }
    }
    fun hide() { generation++;read?.cancel();read=null;refreshingState.value=false;mutable.value=null }
    fun move(section: OrderSection, id: OrderId, delta: Int) {
        val current = (state.value as? DetailsResult.Success)?.value ?: return
        val rows = current.filter { it.section == section }.toMutableList()
        val from = rows.indexOfFirst { it.id == id }; val to = from + delta
        if (from < 0 || to !in rows.indices) return
        java.util.Collections.swap(rows, from, to)
        localOrders[section]=rows.map {it.id.wire}
        mutable.value = DetailsResult.Success(OrderSection.entries.flatMap { if (it == section) rows else current.filter { r -> r.section == it } })
        viewModelScope.launch {
            try { local.order(driver, date, section, rows.map { it.id.wire }) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { noticeState.value = "Не удалось сохранить порядок" }
        }
    }
    fun resetOrder() = viewModelScope.launch {
        try { OrderSection.entries.forEach { localOrders[it]=emptyList();local.order(driver, date, it, emptyList()) }; generation++;read?.cancel();read=null;refresh() }
        catch (e: CancellationException) { throw e }
            catch (_: Exception) { noticeState.value = "Не удалось сбросить порядок" }
    }
    fun deliver(id: OrderId, finished: () -> Unit = {}) {
        if (write?.isActive == true) return
        busy.value = true
        write = viewModelScope.launch {
            try {
                noticeState.value = deliveryMessage(repository.deliver(id))
                finished();generation++;read?.cancel();read=null;refresh()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { noticeState.value="Результат доставки неизвестен. Обновите заказ перед повторной попыткой." }
            finally { busy.value = false }
        }
    }
    class Factory(private val driver: Long, private val date: String, private val repo: DriverWorkspaceRepository,
        private val local: WorkspacePreferences) : ViewModelProvider.Factory {
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkspaceViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return WorkspaceViewModel(driver,date,repo,local) as T
        }
    }
}
fun deliveryMessage(result: DetailsResult<DeliveryReceipt>) = when (result) {
    is DetailsResult.Success -> when (result.value.status) {
        DeliveryStatus.DELIVERED -> "Заказ сдан"
        DeliveryStatus.ALREADY_DELIVERED -> "Заказ уже сдан"
        DeliveryStatus.UNKNOWN -> "Результат доставки неизвестен. Обновите заказ перед повторной попыткой."
    }
    DetailsResult.DriverDisabled -> "Недоступно. Доступ водителя отключён"
    DetailsResult.SessionRequired -> "Необходимо войти снова"
    DetailsResult.NotFound -> "Заказ не найден"
    is DetailsResult.Failed -> "Не удалось сдать заказ. Обновите данные перед повтором."
}

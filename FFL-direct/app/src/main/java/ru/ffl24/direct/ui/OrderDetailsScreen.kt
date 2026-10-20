package ru.ffl24.direct.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.navigation.*
import ru.ffl24.direct.presentation.*

@Composable fun OrderDetailsScreen(model:OrderDetailsViewModel,onDeliver:((OrderId)->Unit)?=null,deliveryBusy:Boolean=false,deliveryNotice:String?=null,refreshHandle:UiRefreshHandle?=null,onHeader:(OrderDetails?)->Unit={}) {
    val state by model.state.collectAsStateWithLifecycle();val lifecycle=LocalLifecycleOwner.current.lifecycle;val context=LocalContext.current
    val d=(state as? OrderDetailsUiState.Content)?.details
    SideEffect{onHeader(d);refreshHandle?.apply{available=true;loading=state==OrderDetailsUiState.Loading||(state as? OrderDetailsUiState.Content)?.busy==true||deliveryBusy;action=model::refresh}}
    DisposableEffect(model,lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_START)model.refresh()else if(event==Lifecycle.Event.ON_STOP)model.hide()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);model.hide();onHeader(null)}}
    var confirm by remember{mutableStateOf(false)};var route by remember{mutableStateOf(false)}
    val contentBusy=(state as? OrderDetailsUiState.Content)?.busy==true||deliveryBusy
    fun open(app:YandexApp){model.prepareRoute{contacts->if(!OrderExternalActions.openRoute(context,contacts.routeUrl,app))Toast.makeText(context,"Не удалось открыть выбранное приложение",Toast.LENGTH_SHORT).show()}}
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)){OrderDetailsContent(state,model::refresh,::open,showRouteAction=false)}
        deliveryNotice?.let{Text(it,Modifier.padding(horizontal=16.dp,vertical=6.dp).testTag("delivery_notice"),style=MaterialTheme.typography.bodyMedium)}
        if(d!=null)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FflSecondaryButton(if(RouteLinks.safeUrl(d.contacts.routeUrl)==null)"Ручной маршрут"else "Маршрут",{route=true},Modifier.weight(1f).testTag("route"),enabled=!contentBusy,icon=FflSymbol.Route)
            if(!d.isDelivered&&onDeliver!=null)FflPrimaryButton("Сдал",{confirm=true},Modifier.weight(1f).testTag("deliver"),enabled=d.isReady&&!contentBusy,busy=deliveryBusy,icon=FflSymbol.Check)
        }
    }
    if(confirm&&d!=null)DeliveryConfirmation(d.orderNumber,false,{confirm=false}){confirm=false;onDeliver?.invoke(d.id)}
    if(route&&d!=null)RouteChoiceDialog(d,{route=false}){app->route=false;open(app)}
}

/** Existing details/resolver/phone contracts, rendered with the approved FFL components. */
@Composable fun OrderDetailsContent(state:OrderDetailsUiState,onRefresh:()->Unit,onRoute:(YandexApp)->Unit,showRouteAction:Boolean=true) {
    val context=LocalContext.current
    when(state) {
        OrderDetailsUiState.Loading->FflStatePanel("Загрузка заказа…","Получаем актуальные данные",loading=true)
        OrderDetailsUiState.NotFound->ResultMessage(DetailsResult.NotFound,onRefresh)
        OrderDetailsUiState.SessionRequired->ResultMessage(DetailsResult.SessionRequired,onRefresh)
        OrderDetailsUiState.Disabled->ResultMessage(DetailsResult.DriverDisabled,onRefresh)
        is OrderDetailsUiState.Failed->ResultMessage(DetailsResult.Failed(state.error),onRefresh)
        is OrderDetailsUiState.Content->{
            val d=state.details;var route by remember(d.id){mutableStateOf(false)};var original by remember(d.id){mutableStateOf(false)};var commentPhones by remember(d.id){mutableStateOf(false)};var allHistory by remember(d.id){mutableStateOf(false)}
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp).testTag("order_details"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(state.refreshFailed)Text("Не удалось обновить. Показаны ранее загруженные данные.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("Клиент",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(d.title,style=MaterialTheme.typography.titleMedium);if(d.clientName!=d.title)Text(d.clientName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    FflCall(d.contacts.clientPhone,"Позвонить клиенту")
                }}
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {Row(verticalAlignment=Alignment.Top) {
                    DetailIcon(FflSymbol.Pin);Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                        Text("Адрес доставки",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(d.contacts.addressSource==AddressSource.AMBIGUOUS)Text("Найдено несколько адресов. Достоверный адрес не выбран.",Modifier.testTag("ambiguous_address"),style=MaterialTheme.typography.bodyMedium)
                        Text(d.contacts.address?:"Адрес не определён",style=MaterialTheme.typography.bodyMedium)
                    }
                    d.contacts.address?.let{address->IconButton({copyFfl(context,address)},Modifier.size(36.dp)){FflIcon(FflSymbol.Copy,Modifier.size(19.dp),description="Копировать адрес")}}
                }}
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    DetailIcon(FflSymbol.Clock);Column(Modifier.weight(1f).padding(start=10.dp,end=8.dp)) {Text("Время доставки",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(d.deliveryTime?:"Не указано",style=MaterialTheme.typography.bodyMedium)}
                    VerticalDivider(Modifier.height(36.dp));Spacer(Modifier.width(10.dp));DetailIcon(FflSymbol.Box)
                    Column(Modifier.padding(start=9.dp).widthIn(max=110.dp)){Text("Накладные",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(d.invoiceCount.toString(),style=MaterialTheme.typography.titleSmall)}
                }}
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {
                    Text("Контакты",style=MaterialTheme.typography.titleSmall)
                    DetailPhone("Клиент",d.contacts.clientPhone);DetailPhone("Грузчик",d.contacts.loaderPhone);DetailPhone("Менеджер",d.contacts.managerPhone,d.contacts.managerName)
                    if(listOf(d.contacts.clientPhone,d.contacts.loaderPhone,d.contacts.managerPhone).all{it==null})Text("Контакты не указаны",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {
                    val phones=ContactParsing.phones(d.rawDeliveryNote)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Комментарий",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        if(d.plannedTime!=null||d.rawDeliveryNote!=null)IconButton({original=true},Modifier.size(32.dp)){FflIcon(FflSymbol.Info,Modifier.size(18.dp),description="Исходное примечание")}
                        if(phones.isNotEmpty())Box {
                            IconButton({commentPhones=true},Modifier.size(32.dp)){FflIcon(FflSymbol.Phone,Modifier.size(18.dp),description="Номера из примечания")}
                            DropdownMenu(commentPhones,{commentPhones=false}) {
                                phones.forEach{phone->DropdownMenuItem(text={Column{Text(phone);Text("Проверьте номер перед звонком",style=MaterialTheme.typography.bodySmall)}},onClick={commentPhones=false;if(!OrderExternalActions.dial(context,phone))Toast.makeText(context,"Нет приложения для звонков",Toast.LENGTH_SHORT).show()})}
                                DropdownMenuItem(text={Text("Копировать номера")},onClick={copyFfl(context,phones.joinToString("\n"));commentPhones=false})
                            }
                        }
                    }
                    Text(d.comment?:"Нет комментария",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(original)Dialog({original=false}){FflCard(Modifier.fillMaxWidth()) {
                    Text("Исходное примечание",style=MaterialTheme.typography.titleMedium)
                    val text=d.plannedTime?:d.rawDeliveryNote.orEmpty()
                    Text(text,Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodyMedium)
                    FflSecondaryButton("Копировать",{copyFfl(context,text)},Modifier.fillMaxWidth(),icon=FflSymbol.Copy)
                    FflPrimaryButton("Закрыть",{original=false},Modifier.fillMaxWidth())
                }}
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Предыдущие водители",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        if(d.previousDrivers.size>2)TextButton({allHistory=!allHistory},Modifier.height(32.dp).testTag("history_expand"),contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Text(if(allHistory)"Свернуть"else "Все (${d.previousDrivers.take(5).size})",style=MaterialTheme.typography.bodySmall)}
                    }
                    if(d.previousDrivers.isEmpty())Text("Предыдущих водителей нет",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    d.previousDrivers.take(if(allHistory)5 else 2).forEachIndexed{i,driver->key(driver.orderId.wire){
                        var expanded by remember{mutableStateOf(false)}
                        Column {
                            Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("${i+1}.",Modifier.width(22.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(driver.driverName?:"Водитель",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                Text(runCatching{java.time.LocalDate.parse(driver.deliveryDate).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.getOrDefault(driver.deliveryDate),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                FflIcon(FflSymbol.Chevron,Modifier.size(14.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(expanded) {
                                Text(driver.orderNumber,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                DetailPhone("Телефон",driver.driverPhone);DetailPhone("WhatsApp",driver.driverWhatsapp)
                            }
                        }
                    }}


                }
                FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp){Text(d.id.source.sectionLabel,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("№${d.orderNumber} · ${d.readinessLabel}",style=MaterialTheme.typography.bodyMedium);Text(d.quantityLabel,style=MaterialTheme.typography.bodySmall);if(d.isDelivered)Text("Сдан${d.deliveredAt?.let{" · $it"}.orEmpty()}",style=MaterialTheme.typography.bodySmall)}
                if(showRouteAction)FflSecondaryButton(if(RouteLinks.safeUrl(d.contacts.routeUrl)==null)"Ручной маршрут"else "Маршрут",{route=true},Modifier.fillMaxWidth().testTag("route"),enabled=!state.busy,icon=FflSymbol.Route)
                Spacer(Modifier.height(6.dp))
            }
            if(route)RouteChoiceDialog(d,{route=false}){route=false;onRoute(it)}
        }
    }
}
@Composable private fun DetailIcon(icon:FflSymbol){Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center){FflIcon(icon,Modifier.size(21.dp))}}
@Composable private fun DetailPhone(label:String,phone:String?,name:String?=null) {
    if(phone==null)return
    val context=LocalContext.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            Column(Modifier.weight(1.25f).padding(horizontal=4.dp)) {
                Text(phone,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                name?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            IconButton({copyFfl(context,phone)},Modifier.size(32.dp)){FflIcon(FflSymbol.Copy,Modifier.size(16.dp),description="Копировать: $label")}
            FflCall(phone,"Позвонить: $label")
        }
    }
}
@Composable private fun RouteChoiceDialog(d:OrderDetails,dismiss:()->Unit,onRoute:(YandexApp)->Unit) {
    val context=LocalContext.current;val safe=RouteLinks.safeUrl(d.contacts.routeUrl)
    Dialog(dismiss){FflCard(Modifier.fillMaxWidth(),padding=12.dp,gap=6.dp) {
        Text(if(safe==null)"Ручной маршрут"else "Открыть маршрут",style=MaterialTheme.typography.titleMedium)
        if(safe==null){Text("Уточните адрес перед построением маршрута. Можно скопировать исходный текст заказа и найти адрес вручную.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FflSecondaryButton("Скопировать исходный текст",{copyFfl(context,listOfNotNull(d.rawClientText,d.rawDeliveryNote).joinToString("\n"))},Modifier.fillMaxWidth(),icon=FflSymbol.Copy)}
        YandexApp.entries.filter{safe==null||RouteLinks.supports(safe,it)}.forEach{app->FflPrimaryButton(if(app==YandexApp.MAPS)"Яндекс Карты"else "Яндекс Навигатор",{onRoute(app)},Modifier.fillMaxWidth(),icon=FflSymbol.Route)}
        FflSecondaryButton("Отмена",dismiss,Modifier.fillMaxWidth())
    }}
}

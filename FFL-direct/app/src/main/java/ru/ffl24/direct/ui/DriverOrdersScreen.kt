package ru.ffl24.direct.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.presentation.WorkspaceViewModel
import ru.ffl24.direct.ui.theme.*

@Composable fun DriverOrdersScreen(model:WorkspaceViewModel,onDetails:(OrderId)->Unit,onAccess:()->Unit,second:Boolean=false,sorting:Boolean=false,refreshHandle:UiRefreshHandle?=null) {
    val state by model.state.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle();val notice by model.notice.collectAsStateWithLifecycle()
    SideEffect{refreshHandle?.apply {available=true;loading=refreshing;action=model::refresh}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model,lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_START)model.refresh() else if(event==Lifecycle.Event.ON_STOP)model.hide()}
        lifecycle.addObserver(observer);model.refresh()
        onDispose{lifecycle.removeObserver(observer);model.hide()}
    }
    LaunchedEffect(state){if(state==DetailsResult.DriverDisabled||state==DetailsResult.SessionRequired)onAccess()}
    OrdersContent(state,model.date,busy,notice,model::refresh,model::resetOrder,model::move,onDetails,{model.deliver(it)},second,model.scrollPositions,model::saveScroll,sorting,refreshing)
}

@OptIn(FlowPreview::class,ExperimentalMaterial3Api::class)
@Composable fun OrdersContent(state:DetailsResult<List<DriverOrder>>?,date:String,busy:Boolean,notice:String?,refresh:()->Unit,reset:()->Unit,
    move:(OrderSection,OrderId,Int)->Unit,details:(OrderId)->Unit,deliver:(OrderId)->Unit,second:Boolean=false,positions:Map<Int,Pair<Int,Int>> = emptyMap(),
    saveScroll:(Int,Int,Int)->Unit={_,_,_->},sorting:Boolean=false,refreshing:Boolean=state==null) {
    val scroll=rememberLazyListState(positions[-1]?.first?:0,positions[-1]?.second?:0)
    val scope=rememberCoroutineScope();val haptics=LocalHapticFeedback.current
    var confirmation by remember {mutableStateOf<DriverOrder?>(null)}
    var dragged by remember {mutableStateOf<OrderId?>(null)};var offset by remember {mutableFloatStateOf(0f)}
    var secondPositioned by remember {mutableStateOf(false)}
    var hint by rememberSaveable {mutableStateOf(true)}
    val rows=(state as? DetailsResult.Success)?.value.orEmpty()
    val latestRows by rememberUpdatedState(rows);val latestMove by rememberUpdatedState(move)
    val headerIndices=buildMap {var index=if(notice!=null)1 else 0;OrderSection.entries.forEach {section->put(section,index);index+=1+rows.count {it.section==section}.coerceAtLeast(1)+if(section==OrderSection.FIRST&&hint)1 else 0;index++}}
    LaunchedEffect(second,rows.isNotEmpty()){if(second&&!secondPositioned&&rows.isNotEmpty()){scroll.scrollToItem(headerIndices[OrderSection.SECOND]?:0);secondPositioned=true}}
    LaunchedEffect(scroll){snapshotFlow{scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset}.distinctUntilChanged().debounce(250).collect{saveScroll(-1,it.first,it.second)}}
    DisposableEffect(scroll){onDispose{saveScroll(-1,scroll.firstVisibleItemIndex,scroll.firstVisibleItemScrollOffset)}}
    LaunchedEffect(sorting){if(!sorting){dragged=null;offset=0f}}
    PullToRefreshBox(isRefreshing=refreshing,onRefresh=refresh,modifier=Modifier.fillMaxSize().testTag("orders_refresh")) {
        if(state==null) {FflStatePanel("Обновление заказов…","Загружаем актуальные данные",loading=true,modifier=Modifier.testTag("orders_loading"));return@PullToRefreshBox}
        if(state !is DetailsResult.Success) {ResultMessage(state,refresh);return@PullToRefreshBox}
        Box(Modifier.fillMaxSize().testTag("orders").semantics{paneTitle=if(second)"Второй рейс"else "Заказы"}) {
        LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().testTag("order_list"),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
            if(notice!=null)item(key="notice") {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(bottom=10.dp)) {
                    Text(notice,Modifier.padding(12.dp).testTag("delivery_notice"),style=MaterialTheme.typography.bodyMedium)
                }
            }
            OrderSection.entries.forEachIndexed {sectionIndex,section->
                val sectionRows=rows.filter {it.section==section}
                item(key="header-${section.name}") {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(topStart=18.dp,topEnd=18.dp)).clickable {scope.launch {scroll.animateScrollToItem(headerIndices[section]?:0)}}.padding(horizontal=14.dp,vertical=10.dp).testTag("section_$sectionIndex"),verticalAlignment=Alignment.CenterVertically) {
                        Text(section.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(7.dp)) {Text(orderCount(sectionRows.size),Modifier.padding(horizontal=8.dp,vertical=4.dp),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)}
                    }
                }
                if(sectionRows.isEmpty())item(key="empty-${section.name}"){
                    val radius=if(section==OrderSection.FIRST&&hint)0.dp else 18.dp
                    Text("Заказов нет",Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(bottomStart=radius,bottomEnd=radius)).padding(horizontal=16.dp,vertical=14.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(sectionRows,key={_,row->row.id.wire}) {index,row->
                    val moving=dragged==row.id
                    val shape=if(index==sectionRows.lastIndex&&!(section==OrderSection.FIRST&&hint))RoundedCornerShape(bottomStart=18.dp,bottomEnd=18.dp)else RoundedCornerShape(0.dp)
                    val handle=Modifier.testTag("drag_${row.id.wire}").semantics {
                        contentDescription="Переместить заказ ${row.orderNumber}"
                        customActions=listOf(CustomAccessibilityAction("Поднять заказ"){move(section,row.id,-1);true},CustomAccessibilityAction("Опустить заказ"){move(section,row.id,1);true})
                    }.pointerInput(row.id,sorting) {
                        if(sorting)detectDragGesturesAfterLongPress(onDragStart={dragged=row.id;offset=0f;haptics.performHapticFeedback(HapticFeedbackType.LongPress)},onDragEnd={dragged=null;offset=0f},onDragCancel={dragged=null;offset=0f}) {change,amount->
                            change.consume();offset+=amount.y
                            val info=scroll.layoutInfo.visibleItemsInfo.firstOrNull{it.key==row.id.wire}
                            val height=(info?.size?:64).toFloat()
                            if(kotlin.math.abs(offset)>height*.55f) {
                                val delta=if(offset>0)1 else -1
                                val group=latestRows.filter{it.section==section};val from=group.indexOfFirst{it.id==row.id}
                                if(from>=0&&from+delta in group.indices){latestMove(section,row.id,delta);offset-=height*delta}
                                else offset=offset.coerceIn(-height*.4f,height*.4f)
                            }
                            val y=(info?.offset?:0)+offset+height/2
                            if(y<60)scope.launch{scroll.scrollBy(-12f)}else if(y>scroll.layoutInfo.viewportEndOffset-60)scope.launch{scroll.scrollBy(12f)}
                        }
                    }
                    CompactOrderRow(row,sorting,{details(row.id)},{if(row.isReady&&!row.isDelivered&&!busy)confirmation=row},handle,
                        Modifier.animateItem(placementSpec=spring(stiffness=Spring.StiffnessMediumLow)).zIndex(if(moving)2f else 0f).graphicsLayer{translationY=if(moving)offset else 0f;shadowElevation=if(moving)12.dp.toPx()else 0f;scaleX=if(moving)1.015f else 1f;scaleY=if(moving)1.015f else 1f}
                            .background(MaterialTheme.colorScheme.surface,shape).testTag("order_${row.id.wire}"))
                    if(index<sectionRows.lastIndex)HorizontalDivider(Modifier.padding(horizontal=14.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
                }
                if(section==OrderSection.FIRST&&hint)item(key="sorting-hint") {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(bottomStart=18.dp,bottomEnd=18.dp)).padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(10.dp)).padding(7.dp),verticalAlignment=Alignment.CenterVertically) {
                            FflIcon(FflSymbol.Sort,Modifier.size(20.dp));Spacer(Modifier.width(10.dp))
                            Text(if(sorting)"Удерживайте ручку справа, чтобы изменить порядок" else "Используйте Сортировку, чтобы изменить порядок заказов",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(sorting)TextButton(reset){Text("Сбросить",style=MaterialTheme.typography.labelSmall)}else IconButton({hint=false},Modifier.size(32.dp)){FflIcon(FflSymbol.Close,Modifier.size(15.dp),description="Скрыть подсказку")}
                        }
                    }
                }
                item(key="gap-${section.name}"){Spacer(Modifier.height(10.dp))}
            }
        }
    }
    }
    confirmation?.let {row->DeliveryConfirmation(row.orderNumber,true,{confirmation=null}){confirmation=null;deliver(row.id)}}
}

@Composable private fun CompactOrderRow(row:DriverOrder,sorting:Boolean,details:()->Unit,confirm:()->Unit,handle:Modifier,modifier:Modifier) {
    val color=readinessColor(row.isDelivered,row.status,row.quantityReadiness)
    Row(modifier.fillMaxWidth().heightIn(min=FflSpace.row).then(if(sorting)Modifier else Modifier.combinedClickable(onClick=details,onLongClickLabel="Сдать заказ",onLongClick=if(row.isReady&&!row.isDelivered)confirm else null)).padding(horizontal=13.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.width(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(9.dp).background(color,androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.size(10.dp,4.dp).background(color.copy(alpha=.28f),RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).alpha(if(row.isDelivered).62f else 1f)) {
            Text(displayOrderNumber(row.orderNumber),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(if(row.isDelivered)"Сдано · ${invoiceCount(row.invoiceCount)}" else "${invoiceCount(row.invoiceCount)} · ${compactStatus(false,row.status,row.quantityReadiness)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        if(sorting)Box(handle.size(44.dp),contentAlignment=Alignment.Center){FflIcon(FflSymbol.Grip,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        else {FflCall(row.contacts.clientPhone,"Позвонить клиенту заказа ${row.orderNumber}");Spacer(Modifier.width(8.dp));FflIcon(FflSymbol.Chevron,Modifier.size(16.dp),MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable fun DeliveryConfirmation(number:String,countdown:Boolean,dismiss:()->Unit,confirm:()->Unit) {
    var seconds by remember{mutableIntStateOf(if(countdown)5 else 0)};var submitted by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(seconds>0){delay(1000);seconds--}}
    Dialog(onDismissRequest={if(!submitted)dismiss()}) {
        FflCard(Modifier.fillMaxWidth()) {Column(Modifier.fillMaxWidth().padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center){FflIcon(FflSymbol.Box,Modifier.size(30.dp))}
            Text("Подтвердить сдачу?",style=MaterialTheme.typography.titleMedium)
            Text("Заказ ${displayOrderNumber(number)} будет отмечен как сданный.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            FflPrimaryButton(if(seconds>0)"Да, сдал ($seconds)" else "Да, сдал",{if(!submitted){submitted=true;confirm()}},Modifier.fillMaxWidth().testTag("confirm_delivery"),enabled=seconds==0&&!submitted,icon=FflSymbol.Check)
            FflSecondaryButton("Отмена",dismiss,Modifier.fillMaxWidth(),enabled=!submitted)
        }}
    }
}
@Composable fun ResultMessage(result:DetailsResult<*>?,retry:()->Unit) {
    when(result) {
        null->FflStatePanel("Загрузка…","Получаем актуальные данные",loading=true)
        DetailsResult.DriverDisabled->FflStatePanel("Нет доступа","Доступ водителя отключён",FflSymbol.Lock,error=true,action="Повторить",onAction=retry)
        DetailsResult.SessionRequired->FflStatePanel("Необходимо войти снова","Сессия требует повторной проверки",FflSymbol.Lock,action="Повторить",onAction=retry)
        DetailsResult.NotFound->FflStatePanel("Данные не найдены","Обновите список и повторите попытку",action="Повторить",onAction=retry)
        is DetailsResult.Failed->FflStatePanel(if(result.error==AuthError.TLS_REJECTED)"Безопасное соединение отклонено"else "Ошибка соединения",authErrorText(result.error),error=true,action="Повторить",onAction=retry)
        else->Unit
    }
}

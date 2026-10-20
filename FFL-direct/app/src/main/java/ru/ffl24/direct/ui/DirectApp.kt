package ru.ffl24.direct.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import kotlinx.coroutines.*
import ru.ffl24.direct.DirectRuntime
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences
import ru.ffl24.direct.navigation.OrderDetailsDestination
import ru.ffl24.direct.notifications.SecondRouteNotifications
import ru.ffl24.direct.presentation.*
import ru.ffl24.direct.ui.theme.*
import java.time.LocalDate

class UiRefreshHandle {var available by mutableStateOf(false);var loading by mutableStateOf(false);var orderDetails by mutableStateOf<OrderDetails?>(null);var action:()->Unit={}}

@Composable private fun destinationRefresh(id:String,handles:MutableMap<String,UiRefreshHandle>):UiRefreshHandle {
    val handle=remember(id){UiRefreshHandle()}
    SideEffect { handles[id]=handle }
    DisposableEffect(id,handle){onDispose{if(handles[id]===handle)handles.remove(id)}}
    return handle
}
private object Routes {const val Home="home";const val Directory="directory";const val Settings="settings";const val Second="second_route"}

@Composable fun DirectApp(graph:DirectRuntime,notificationDriver:Long?=null,consumeNotification:()->Unit={},profileScope:String="",openConnection:(()->Unit)?=null) {
    val context=LocalContext.current;val local=remember(profileScope){WorkspacePreferences(context,profileScope)};val scope=rememberCoroutineScope()
    var workingDate by rememberSaveable{mutableStateOf(local.date())};var notifications by remember{mutableStateOf(local.notifications())};var dark by rememberSaveable{mutableStateOf(local.dark())}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->notifications=granted;scope.launch{saveSetting(context){local.setNotifications(granted)}}}
    val graphKey=System.identityHashCode(graph)
    val auth:SetupAuthViewModel=viewModel(key="direct-auth-$graphKey",factory=graph.authViewModelFactory)
    val authState by auth.state.collectAsStateWithLifecycle();val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(auth,lifecycle) {
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->{workingDate=local.date();auth.onForeground()};Lifecycle.Event.ON_STOP->auth.onBackground();else->Unit}}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);auth.onBackground()}
    }
    val session=(authState as? AuthUiState.Authenticated)?.session
    val workspace:WorkspaceViewModel?=if(session==null)null else viewModel(key="workspace-$graphKey-${session.driverId}-$workingDate",factory=WorkspaceViewModel.Factory(session.driverId,workingDate,graph.workspaceRepository,local))
    var sorting by rememberSaveable(session?.driverId,workingDate){mutableStateOf(false)}
    LaunchedEffect(session?.driverId,notifications){if(session!=null&&notifications)SecondRouteNotifications.schedule(context,session.driverId)else if(!notifications||authState is AuthUiState.Choosing||authState==AuthUiState.Disabled)SecondRouteNotifications.cancel(context)}
    var navigationStarted by rememberSaveable{mutableStateOf(false)}
    if(session!=null)SideEffect{navigationStarted=true}
    DirectTheme(dark) {
        if(!navigationStarted && session==null) {
            // Render first-run/auth immediately; the protected NavHost is expensive on cold ART.
            Scaffold(modifier=Modifier.imePadding(),containerColor=MaterialTheme.colorScheme.background,
                contentWindowInsets=WindowInsets.safeDrawing){padding->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)){
                    when(authState){
                        AuthUiState.ConfigurationMissing->FflStatePanel("База данных не настроена","Настройте подключение для продолжения",FflSymbol.Lock,action=if(openConnection!=null)"Настроить подключение"else null,onAction={openConnection?.invoke()},modifier=Modifier.testTag("configuration_missing"))
                        AuthUiState.ConfigurationInvalid->FflStatePanel("Подключение требует проверки","Обратитесь к ответственному за настройку.",FflSymbol.Lock,action=if(openConnection!=null)"Настроить подключение"else null,onAction={openConnection?.invoke()},modifier=Modifier.testTag("configuration_invalid"))
                        else->if(openConnection==null)AuthScreen(authState,auth)else Column(Modifier.fillMaxSize()){Box(Modifier.weight(1f)){AuthScreen(authState,auth)};TextButton({openConnection.invoke()},Modifier.align(Alignment.CenterHorizontally).testTag("auth_admin_connection")){Text("Подключение · администратор")}}
                    }
                }
            }
        } else {
        val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route?:Routes.Home
        val imeVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
        val home=route==Routes.Home||route==Routes.Second;val settings=route==Routes.Settings;val directory=route==Routes.Directory
        // Incoming and outgoing destinations coexist during navigation animations.
        // Each entry owns its binding, so their loading/header effects cannot fight.
        val refreshHandles=remember{mutableStateMapOf<String,UiRefreshHandle>()}
        val emptyRefresh=remember{UiRefreshHandle()}
        val refresh=refreshHandles[entry?.id]?:emptyRefresh
        val detailsHeader=refresh.orderDetails
        var dateRequested by remember{mutableStateOf(false)}
        LaunchedEffect(notificationDriver,session?.driverId){if(notificationDriver!=null&&session!=null){if(notificationDriver==session.driverId)nav.navigate(Routes.Second){launchSingleTop=true};consumeNotification()}}
        LaunchedEffect(authState){if(authState is AuthUiState.Choosing||authState==AuthUiState.Disabled)nav.popBackStack(Routes.Home,false)}
        fun goHome(){if(!home)nav.popBackStack(Routes.Home,false)else if(route==Routes.Second)nav.popBackStack(Routes.Home,false)}
        fun open(destination:String){if(route!=destination)nav.navigate(destination){popUpTo(Routes.Home);launchSingleTop=true}}
        BackHandler(session!=null&&!home){nav.popBackStack()}
        Scaffold(modifier=Modifier.imePadding(),containerColor=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground,
            contentWindowInsets=WindowInsets.safeDrawing,
            topBar={if(session!=null) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=if(directory)96.dp else 68.dp).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(home){FflAvatar(session.displayName,Modifier.size(38.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)) {
                        Text(session.displayName,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text((if(workingDate==LocalDate.now().toString())"Сегодня, " else "")+displayDate(workingDate),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.testTag("work_date_display").semantics{contentDescription="Рабочая дата $workingDate"})
                    };IconButton({dateRequested=true;open(Routes.Settings)},Modifier.testTag("open_settings")){Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){FflIcon(FflSymbol.Calendar,Modifier.size(22.dp),description="Выбрать дату")}}}
                    else {
                        if(!directory)IconButton({nav.popBackStack()},Modifier.size(40.dp).testTag("back")){FflIcon(FflSymbol.Back,color=MaterialTheme.colorScheme.onSurface,description="Назад")}
                        Text(if(settings)"Настройки" else if(directory)"Справочник" else detailsHeader?.let{displayOrderNumber(it.orderNumber)}?:"Заказ",Modifier.weight(1f).padding(start=if(directory)0.dp else 6.dp),style=if(directory)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(!settings&&!directory)detailsHeader?.let{FflStatus(compactStatus(it.isDelivered,it.status,it.quantityReadiness),readinessColor(it.isDelivered,it.status,it.quantityReadiness))}
                    }
                }
            }},
            bottomBar={if(session!=null&&!imeVisible)FflBottomBar(if(home)0 else if(directory)1 else if(settings)4 else -1,sorting,home,refresh.loading,refresh.available,
                {goHome()},{open(Routes.Directory)},{refresh.action()},{sorting=!sorting},{dateRequested=false;open(Routes.Settings)})}
        ) {padding->
            NavHost(nav,Routes.Home,Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable(Routes.Home){homeEntry->val ownRefresh=destinationRefresh(homeEntry.id,refreshHandles);when(authState) {
                    AuthUiState.ConfigurationMissing->FflStatePanel("База данных не настроена","Настройте подключение для продолжения",FflSymbol.Lock,action=if(openConnection!=null)"Настроить подключение"else null,onAction={openConnection?.invoke()},modifier=Modifier.testTag("configuration_missing"))
                    AuthUiState.ConfigurationInvalid->FflStatePanel("Подключение требует проверки","Обратитесь к ответственному за настройку.",FflSymbol.Lock,action=if(openConnection!=null)"Настроить подключение"else null,onAction={openConnection?.invoke()},modifier=Modifier.testTag("configuration_invalid"))
                    is AuthUiState.Authenticated->key(session!!.driverId,workingDate){DriverOrdersScreen(workspace!!,{OrderDetailsDestination.open(nav,it)},auth::recheckAccess,sorting=sorting,refreshHandle=ownRefresh)}
                    else->if(openConnection==null)AuthScreen(authState,auth)else Column(Modifier.fillMaxSize()){Box(Modifier.weight(1f)){AuthScreen(authState,auth)};TextButton({openConnection.invoke()},Modifier.align(Alignment.CenterHorizontally).testTag("auth_admin_connection")){Text("Подключение · администратор")}}
                }}
                composable(Routes.Second){secondEntry->val ownRefresh=destinationRefresh(secondEntry.id,refreshHandles);if(session!=null){val secondModel:WorkspaceViewModel=viewModel(key="second-$graphKey-${session.driverId}-$workingDate",factory=WorkspaceViewModel.Factory(session.driverId,workingDate,graph.workspaceRepository,local));DriverOrdersScreen(secondModel,{OrderDetailsDestination.open(nav,it)},auth::recheckAccess,second=true,sorting=sorting,refreshHandle=ownRefresh)}else AuthScreen(authState,auth)}
                composable(Routes.Directory){directoryEntry->val ownRefresh=destinationRefresh(directoryEntry.id,refreshHandles);if(session==null)AuthScreen(authState,auth)else key(session.driverId){DirectoryDestination(graph.workspaceRepository,ownRefresh,auth::recheckAccess)}}
                composable(Routes.Settings){settingsEntry->destinationRefresh(settingsEntry.id,refreshHandles);WorkspaceSettings(dark,{dark=it;scope.launch{saveSetting(context){local.setDark(it)}}},workingDate,{date->scope.launch{saveSetting(context){local.setDate(date);workingDate=local.date()}}},notifications,{enabled->
                    if(enabled&&android.os.Build.VERSION.SDK_INT>=33&&!SecondRouteNotifications.allowed(context))permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else{notifications=enabled;scope.launch{saveSetting(context){local.setNotifications(enabled)}}}
                },{scope.launch{SecondRouteNotifications.cancel(context);auth.logout();saveSetting(context){local.clearSeen()}}},session!=null,dateRequested,openConnection)}
                composable(OrderDetailsDestination.pattern){detailsEntry->
                    val ownRefresh=destinationRefresh(detailsEntry.id,refreshHandles)
                    val id=OrderId.parse(detailsEntry.arguments?.getString("orderId").orEmpty())
                    if(session==null)AuthScreen(authState,auth)else if(id==null)ResultMessage(DetailsResult.NotFound,{goHome()})else key(session.driverId,id.wire){
                        val model:OrderDetailsViewModel=viewModel(key="details-$graphKey-${session.driverId}-${id.wire}",factory=OrderDetailsViewModel.Factory(id,graph.orderDetailsRepository))
                        val busy by workspace!!.busy.collectAsStateWithLifecycle();val notice by workspace.notice.collectAsStateWithLifecycle()
                        OrderDetailsScreen(model,{workspace.deliver(it){model.refresh()}},busy,notice,ownRefresh){ownRefresh.orderDetails=it}
                    }
                }
            }
        }
        }
    }
}

@Composable private fun DirectoryDestination(repository:DriverWorkspaceRepository,refresh:UiRefreshHandle,access:()->Unit) {
    val scope=rememberCoroutineScope();var read by remember{mutableStateOf<Job?>(null)};var result by remember{mutableStateOf<DetailsResult<PhoneDirectory>?>(null)};var error by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(false)}
    fun load(){if(read?.isActive==true)return;loading=true;read=scope.launch{try{val next=repository.directory();currentCoroutineContext().ensureActive();if(next is DetailsResult.Failed&&result is DetailsResult.Success)error=true else{result=next;error=false};if(next==DetailsResult.DriverDisabled||next==DetailsResult.SessionRequired)access()}catch(e:CancellationException){throw e}catch(_:Exception){if(result is DetailsResult.Success)error=true else result=DetailsResult.Failed(AuthError.DATA_UNAVAILABLE)}finally{loading=false}}}
    SideEffect{refresh.available=true;refresh.loading=loading;refresh.action={load()}}
    LaunchedEffect(Unit){load()}
    Column(Modifier.fillMaxSize()) {if(error)Text("Не удалось обновить. Показаны ранее загруженные данные.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error);DirectoryContent(result){load()}}
}

@Composable private fun FflBottomBar(active:Int,sorting:Boolean,canSort:Boolean,refreshing:Boolean,refreshAvailable:Boolean,home:()->Unit,directory:()->Unit,refresh:()->Unit,sort:()->Unit,settings:()->Unit) {
    val haptics=LocalHapticFeedback.current
    val density=LocalDensity.current
    val labelHeight=with(density){(if(fontScale>1.1f)28.sp else 14.sp).toDp()}
    val names=listOf("Домой","Справочник","Обновить","Сортировка","Настройки")
    val icons=listOf(FflSymbol.Home,FflSymbol.Book,FflSymbol.Refresh,FflSymbol.Sort,FflSymbol.Settings)
    val actions=listOf(home,directory,refresh,sort,settings)
    Box(Modifier.fillMaxWidth().testTag("bottom_navigation")) {
        Surface(Modifier.matchParentSize().padding(top=12.dp).shadow(8.dp,RoundedCornerShape(topStart=22.dp,topEnd=22.dp)).testTag("nav_surface"),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),color=MaterialTheme.colorScheme.surface) {}
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=4.dp,end=4.dp,top=12.dp).heightIn(min=68.dp),verticalAlignment=Alignment.CenterVertically) {
            names.forEachIndexed{i,name->
                val enabled=if(i==3)canSort else if(i==2)refreshAvailable&&!refreshing else true
                val selected=active==i||(i==3&&sorting&&canSort)
                val color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled)1f else.38f)
                Column(Modifier.weight(1f).heightIn(min=64.dp).then(if(i==2)Modifier else Modifier.clip(RoundedCornerShape(14.dp))).clickable(enabled=enabled,role=Role.Button){haptics.performHapticFeedback(HapticFeedbackType.LongPress);actions[i]()}.padding(vertical=5.dp)
                    .testTag(listOf("nav_home","nav_directory","nav_refresh","nav_sort","nav_settings")[i]).semantics{contentDescription=name;this.selected=selected;if(i==2)stateDescription=if(refreshing)"Обновление"else "Готово"},horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.height(42.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        if(i==2){val rotation=if(refreshing){val transition=rememberInfiniteTransition(label="refresh");val value by transition.animateFloat(0f,360f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="rotation");value}else 0f
                            Box(Modifier.offset(y=(-10).dp).requiredSize(56.dp).testTag("refresh_fab").shadow(5.dp,CircleShape).background(Brush.linearGradient(if(refreshAvailable)listOf(AppPrimaryLight,AppPrimary)else listOf(MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.colorScheme.surfaceVariant)),CircleShape),contentAlignment=Alignment.Center){FflIcon(FflSymbol.Refresh,Modifier.size(28.dp).graphicsLayer{rotationZ=rotation},if(refreshAvailable)Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f))}
                        }else FflIcon(icons[i],Modifier.size(24.dp),color)
                    }
                    Text(name,Modifier.heightIn(min=labelHeight),style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp),color=if(i==2)MaterialTheme.colorScheme.onSurfaceVariant else color,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
private suspend fun saveSetting(context:Context,save:suspend ()->Unit){try{save()}catch(e:CancellationException){throw e}catch(_:Exception){android.widget.Toast.makeText(context,"Не удалось сохранить настройку",android.widget.Toast.LENGTH_SHORT).show()}}

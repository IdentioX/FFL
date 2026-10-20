package ru.ffl24.direct.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.ffl24.direct.domain.AuthError
import ru.ffl24.direct.presentation.AuthUiState
import ru.ffl24.direct.presentation.SetupAuthViewModel
import ru.ffl24.direct.ui.theme.AppReady

@Composable fun AuthScreen(state:AuthUiState,model:SetupAuthViewModel) {
    when(state) {
        AuthUiState.Loading->FflStatePanel("Загрузка…","Получаем список доступных водителей",loading=true,modifier=Modifier.testTag("connection_checking"))
        is AuthUiState.Choosing->if(state.passwordRequested&&state.selectedId!=null) PasswordPage(state,model) else DriverChooser(state,model)
        is AuthUiState.Authenticated->FflStatePanel("Вход выполнен",state.session.displayName,icon=FflSymbol.User,action="Продолжить",onAction=model::recheckAccess,modifier=Modifier.testTag("authenticated"))
        AuthUiState.Disabled->Column(Modifier.fillMaxSize().testTag("driver_disabled")) {
            Box(Modifier.weight(1f)){FflStatePanel("Нет доступа","Доступ водителя отключён. Обратитесь к ответственному за доступ.",FflSymbol.Lock,error=true,action="Повторить",onAction=model::retry)}
            FflSecondaryButton("К выбору водителя",model::logout,Modifier.fillMaxWidth().padding(20.dp))
        }
        is AuthUiState.Failed->Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)){FflStatePanel(if(state.error==AuthError.TLS_REJECTED)"Безопасное соединение отклонено" else "Ошибка соединения",authErrorText(state.error),error=true,action="Повторить",onAction=model::retry,modifier=Modifier.testTag("connection_failed"))}
            TextButton(model::logout,Modifier.align(Alignment.CenterHorizontally).padding(bottom=12.dp)){Text("К выбору водителя")}
        }
        AuthUiState.ConfigurationMissing,AuthUiState.ConfigurationInvalid->Unit
    }
}
@Composable private fun DriverChooser(state:AuthUiState.Choosing,model:SetupAuthViewModel) {
    var query by remember {mutableStateOf("")}
    val filtered=remember(query,state.drivers){state.drivers.filter {it.displayName.contains(query,ignoreCase=true)}}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=20.dp).testTag("driver_list")) {
        Spacer(Modifier.height(30.dp))
        Text("FFL DRIVER",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
        Text("Добро пожаловать",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))
        Text("Выберите водителя для входа",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=18.dp))
        FflSearch(query,{query=it},"Поиск по имени",Modifier.testTag("driver_search"))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) {
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
            LazyColumn {
                items(filtered,key={it.id}) {driver->
                    val selected=driver.id==state.selectedId
                    Row(Modifier.fillMaxWidth().background(if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .selectable(selected=selected,enabled=!state.busy,onClick={model.select(driver.id)}).padding(horizontal=12.dp,vertical=11.dp).testTag("driver_${driver.id}"),verticalAlignment=Alignment.CenterVertically) {
                        FflAvatar(driver.displayName);Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(driver.displayName,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.padding(end=5.dp).size(7.dp).background(AppReady,androidx.compose.foundation.shape.CircleShape))
                                Text("Активен",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        FflIcon(if(selected)FflSymbol.Check else FflSymbol.Chevron,Modifier.size(18.dp))
                    }
                    HorizontalDivider(Modifier.padding(start=64.dp,end=12.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
                }
                if(filtered.isEmpty())item {Text(if(state.drivers.isEmpty())"Нет доступных водителей" else "Ничего не найдено",Modifier.padding(20.dp),style=MaterialTheme.typography.bodyMedium)}
            }
        }
        }
        if(state.invalidCredentials)Text("Неверный пароль. Вход не выполнен.",Modifier.padding(top=10.dp).testTag("invalid_credentials"),color=MaterialTheme.colorScheme.error)
        FflPrimaryButton("Продолжить",model::requestPassword,Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp).testTag("login"),enabled=state.selectedId!=null&&!state.busy,icon=FflSymbol.Chevron)
        TextButton(model::retry,Modifier.align(Alignment.CenterHorizontally)){Text("Обновить список",style=MaterialTheme.typography.bodySmall)}
    }
}
@Composable private fun PasswordPage(state:AuthUiState.Choosing,model:SetupAuthViewModel) {
    val driver=state.drivers.firstOrNull {it.id==state.selectedId}?:return
    var password by remember(driver.id){mutableStateOf("")}
    var visible by remember(driver.id){mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(driver.id,lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){password="";visible=false;model.cancelLogin()}}
        lifecycle.addObserver(observer)
        onDispose{password="";visible=false;lifecycle.removeObserver(observer)}
    }
    BackHandler {password="";model.cancelLogin()}
    fun submit(){if(password.isNotEmpty()&&!state.busy){val secret=password.toCharArray();password="";visible=false;focus.clearFocus();model.login(secret)}}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({password="";model.cancelLogin()},Modifier.testTag("password_back")){FflIcon(FflSymbol.Back,description="Назад")}
            Text("FFL DRIVER",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(48.dp))
        }
        FflAvatar(driver.displayName,large=true)
        Text(driver.displayName,Modifier.padding(top=14.dp),style=MaterialTheme.typography.headlineSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text("● Активен",Modifier.padding(top=5.dp,bottom=30.dp),style=MaterialTheme.typography.bodyMedium,color=AppReady)
        OutlinedTextField(password,{password=it},Modifier.fillMaxWidth().testTag("driver_password"),enabled=!state.busy,singleLine=true,
            placeholder={Text("Введите пароль",style=MaterialTheme.typography.bodyMedium)},visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}),
            trailingIcon={IconButton({visible=!visible},Modifier.testTag("password_visibility")){FflIcon(if(visible)FflSymbol.EyeOff else FflSymbol.Eye,description=if(visible)"Скрыть пароль" else "Показать пароль")}},
            shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=MaterialTheme.colorScheme.outline))
        if(state.invalidCredentials)Text("Неверный пароль. Попробуйте ещё раз.",Modifier.fillMaxWidth().padding(top=12.dp).testTag("invalid_credentials"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        FflPrimaryButton("Войти",{submit()},Modifier.fillMaxWidth().padding(top=18.dp).testTag("confirm_login"),enabled=password.isNotEmpty()&&!state.busy,busy=state.busy)
        Spacer(Modifier.height(24.dp))
    }
}
fun authErrorText(error:AuthError)=when(error) {
    AuthError.TLS_REJECTED->"Не удалось установить защищённое соединение. Обратитесь к ответственному за настройку."
    AuthError.TIMEOUT->"Время ожидания истекло. Повторите попытку."
    AuthError.CONFIGURATION_MISSING,AuthError.CONFIGURATION_INVALID,AuthError.DATABASE_ACCOUNT_REJECTED->"Подключение требует проверки настроек. Обратитесь к ответственному за настройку."
    AuthError.LOCAL_STORAGE_UNAVAILABLE->"Не удалось сохранить или очистить сессию. Действия заблокированы; повторите попытку."
    else->"Не удалось получить данные. Проверьте сеть и повторите попытку."
}

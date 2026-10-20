package ru.ffl24.direct.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import ru.ffl24.direct.config.safeMessage
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.ui.theme.DirectTheme

/** Draft is deliberately memory-only: passwords never enter saved instance state or ViewModel logs. */
@Composable fun ConnectionSettingsScreen(initial: DirectMySqlSettings, check: suspend (DirectMySqlSettings)->ConnectionProbeResult,
    save: suspend (DirectMySqlSettings)->String?, cancel:()->Unit) {
    var host by remember{mutableStateOf(initial.host)};var port by remember{mutableStateOf(initial.port.ifEmpty{"3306"})}
    var database by remember{mutableStateOf(initial.database)};var user by remember{mutableStateOf(initial.user)}
    var password by remember{mutableStateOf("")};var ca by remember{mutableStateOf(initial.caPem)}
    var visible by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
    var success by remember{mutableStateOf(false)};var checked by remember{mutableStateOf(false)};var job by remember{mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)}
    fun task(block:suspend ()->Unit){if(busy)return;busy=true;job=scope.launch{try{block()}finally{busy=false}}}
    fun edited(){checked=false;message=null;success=false}
    fun draft()=DirectMySqlSettings(host.trim(),port.trim(),database.trim(),user.trim(),password.ifEmpty{initial.password},"true",ca.trim())
    fun leave(){job?.cancel();password="";cancel()}
    BackHandler{leave()}
    DirectTheme(false) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.imePadding(),topBar={
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton({leave()}){FflIcon(FflSymbol.Back,description="Назад")}
                Column(Modifier.weight(1f)){Text("Подключение к БД",style=MaterialTheme.typography.titleMedium);Text("MySQL / MariaDB · DIRECT MYSQL",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                FflIcon(FflSymbol.Database)
            }
        }){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            FflCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically){FflIcon(FflSymbol.Server);Spacer(Modifier.width(10.dp));Text("Параметры подключения",style=MaterialTheme.typography.titleSmall)}
                ConnectionField("Адрес сервера",host,{host=it;edited()},"db_host",busy)
                ConnectionField("Порт",port,{port=it;edited()},"db_port",busy,KeyboardType.Number)
                ConnectionField("Имя базы данных",database,{database=it;edited()},"db_database",busy)
                ConnectionField("Пользователь",user,{user=it;edited()},"db_user",busy)
                Text("Пароль",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(password,{password=it;edited()},Modifier.fillMaxWidth().testTag("db_password"),enabled=!busy,singleLine=true,
                    placeholder={Text(if(initial.password.isEmpty())"Введите пароль" else "Сохранённый пароль")},
                    visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                    trailingIcon={IconButton({visible=!visible}){FflIcon(if(visible)FflSymbol.EyeOff else FflSymbol.Eye,description=if(visible)"Скрыть пароль"else "Показать пароль")}},shape=RoundedCornerShape(14.dp))
                if(initial.password.isNotEmpty())Text("Пустое поле сохраняет прежний пароль.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment=Alignment.CenterVertically){FflIcon(FflSymbol.Lock);Text("Защищённое соединение TLS",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.bodyMedium);Switch(true,null,enabled=false)}
                Text("TLS и проверка имени сервера обязательны. Вставьте CA-сертификат, полученный у администратора.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(ca,{ca=it;edited()},Modifier.fillMaxWidth().heightIn(min=100.dp).testTag("db_ca"),enabled=!busy,label={Text("CA-сертификат PEM")},minLines=2,maxLines=5,shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
            }
            message?.let { text->FflCard(Modifier.fillMaxWidth().testTag("db_result")){Row(verticalAlignment=Alignment.CenterVertically){FflIcon(if(success)FflSymbol.Check else FflSymbol.Info,color=if(success)androidx.compose.ui.graphics.Color(0xFF29A875) else MaterialTheme.colorScheme.error);Text(text,Modifier.padding(start=10.dp),style=MaterialTheme.typography.bodyMedium)}} }
            FflPrimaryButton(if(busy)"Проверяем подключение…"else "Проверить соединение",{
                task{checked=false;message=null;val result=check(draft());success=result==ConnectionProbeResult.Reachable;checked=success;message=if(success)"Соединение установлено. База данных проверена." else (result as ConnectionProbeResult.Failed).error.safeMessage()}
            },Modifier.fillMaxWidth().testTag("db_check"),enabled=!busy,busy=busy)
            FflSecondaryButton("Сохранить настройки",{if(checked)task{val error=save(draft());if(error==null){password="";cancel()}else{message=error;success=false;checked=false}}},Modifier.fillMaxWidth().testTag("db_save"),enabled=checked&&!busy)
            FflSecondaryButton("Отмена",{leave()},Modifier.fillMaxWidth().testTag("db_cancel"))
            Text("Только для администратора. После смены подключения потребуется повторный вход водителя.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
    }
}
@Composable private fun ConnectionField(label:String,value:String,change:(String)->Unit,tag:String,busy:Boolean,type:KeyboardType=KeyboardType.Ascii) {
    Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(value,change,Modifier.fillMaxWidth().testTag(tag),enabled=!busy,singleLine=true,shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=type,imeAction=ImeAction.Next))
}

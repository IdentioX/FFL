package ru.ffl24.direct.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.ffl24.direct.BuildConfig
import java.time.LocalDate

@Composable fun WorkspaceSettings(dark:Boolean,theme:(Boolean)->Unit,date:String,changeDate:(String?)->Unit,notifications:Boolean,notify:(Boolean)->Unit,logout:()->Unit,signedIn:Boolean,openDateInitially:Boolean=false,openConnection:(()->Unit)?=null) {
    var editDate by remember{mutableStateOf(openDateInitially)};var pollingInfo by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {Column {
            SettingsRow(FflSymbol.Calendar,"Выбранная дата",value=displayDate(date),onClick={editDate=true},tag="open_work_date")
            SettingsDivider()
            SettingsRow(FflSymbol.Refresh,"Фоновая проверка",value="Каждые 15 минут",onClick={pollingInfo=true})
            SettingsDivider()
            SettingsRow(FflSymbol.Bell,"Уведомления",toggle={Switch(notifications,notify,modifier=Modifier.testTag("notifications"))})
            SettingsDivider()
            SettingsRow(FflSymbol.Sun,"Тёмная тема",toggle={Switch(dark,theme,modifier=Modifier.testTag("dark_theme"))})
            SettingsDivider()
            if(openConnection!=null){SettingsRow(FflSymbol.Lock,"Подключение к БД",onClick=openConnection,tag="open_db_settings");SettingsDivider()}
            SettingsRow(FflSymbol.Info,"О приложении",value="Версия ${BuildConfig.VERSION_NAME}")
        }}
        Text("Проверка второго рейса работает при наличии сети. Android может задерживать её в режиме экономии энергии.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp))
        if(signedIn)FflSecondaryButton("Выйти из аккаунта",logout,Modifier.fillMaxWidth().testTag("logout"),icon=FflSymbol.Logout)
    }
    if(editDate)WorkDateDialog(date,{editDate=false}){value->changeDate(value);editDate=false}
    if(pollingInfo)AlertDialog(onDismissRequest={pollingInfo=false},title={Text("Проверка второго рейса")},text={Text("При включённых уведомлениях проверяем новые заказы примерно раз в 15 минут. Первый ответ запоминается без уведомления. Это не автоматическое обновление открытого списка: для него используйте центральную кнопку Обновить.")},confirmButton={TextButton({pollingInfo=false}){Text("Понятно")}})
}
@Composable private fun SettingsDivider(){HorizontalDivider(Modifier.padding(start=48.dp,end=14.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))}
@Composable private fun SettingsRow(icon:FflSymbol,title:String,value:String?=null,onClick:(()->Unit)?=null,tag:String?=null,toggle:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier).then(if(tag!=null)Modifier.testTag(tag)else Modifier).heightIn(min=60.dp).padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        FflIcon(icon,Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        if(toggle!=null)toggle() else {
            if(value!=null)Text(value,Modifier.widthIn(max=140.dp).padding(start=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(onClick!=null){Spacer(Modifier.width(5.dp));FflIcon(FflSymbol.Chevron,Modifier.size(14.dp),MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable private fun WorkDateDialog(date:String,dismiss:()->Unit,apply:(String?)->Unit) {
    var input by remember{mutableStateOf(date)}
    val valid=runCatching{LocalDate.parse(input).toString()==input}.getOrDefault(false)
    Dialog(dismiss) {FflCard(Modifier.fillMaxWidth()){Text("Рабочая дата",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(input,{input=it},Modifier.fillMaxWidth().testTag("work_date"),label={Text("ГГГГ-ММ-ДД")},singleLine=true,isError=!valid,shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
        FflPrimaryButton("Применить дату",{apply(input)},Modifier.fillMaxWidth(),enabled=valid)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({apply(null)}){Text("Сегодня")};TextButton(dismiss){Text("Отмена")}}
    }}
}

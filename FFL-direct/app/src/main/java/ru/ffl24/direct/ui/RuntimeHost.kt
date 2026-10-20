package ru.ffl24.direct.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.ffl24.direct.config.RuntimeProfiles
import ru.ffl24.direct.data.mysql.*

@Composable fun RuntimeHost(profiles: RuntimeProfiles,driver:Long?,consume:()->Unit) {
    val graph by profiles.graph.collectAsState()
    val context=LocalContext.current
    var editor by remember{mutableStateOf(false)}
    var accessMessage by remember{mutableStateOf<String?>(null)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){if(it.resultCode==Activity.RESULT_OK)editor=true}
    fun open() {
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val configured=profiles.hasSavedProfile() || DirectMySqlConfig.parse(profiles.currentSettings()) is MySqlConfiguration.Ready
        if(!configured)editor=true
        else if(!keyguard.isDeviceSecure)accessMessage="Для изменения подключения установите PIN или пароль блокировки Android."
        else {
            @Suppress("DEPRECATION") val intent=keyguard.createConfirmDeviceCredentialIntent("Настройка базы данных","Подтвердите доступ администратора устройства")
            if(intent!=null)launcher.launch(intent)else accessMessage="Системная проверка владельца устройства недоступна."
        }
    }
    key(graph){DirectApp(graph,driver,consume,graph.profileScope,{open()})}
    if(editor) {
        val activity=context as? Activity;val lifecycle=LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle){
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)editor=false}
            lifecycle.addObserver(observer)
            onDispose{lifecycle.removeObserver(observer);activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
        }
        androidx.compose.ui.window.Dialog(onDismissRequest={editor=false},properties=androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth=false,decorFitsSystemWindows=false,securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn)) {
            ConnectionSettingsScreen(profiles.currentSettings(),profiles::check,profiles::apply,{editor=false})
        }
    }
    accessMessage?.let{message->androidx.compose.material3.AlertDialog(onDismissRequest={accessMessage=null},title={androidx.compose.material3.Text("Доступ к настройкам")},text={androidx.compose.material3.Text(message)},confirmButton={androidx.compose.material3.TextButton({accessMessage=null}){androidx.compose.material3.Text("Понятно")}})}
}

package ru.ffl24.direct

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import ru.ffl24.direct.notifications.SecondRouteNotifications
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.ffl24.direct.ui.DirectApp

class MainActivity : ComponentActivity() {
    private val notificationDriver = mutableStateOf<Long?>(null)
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readTap(intent) }
    private fun readTap(intent: Intent?) {
        notificationDriver.value = intent?.getLongExtra(SecondRouteNotifications.TAP_DRIVER,-1)?.takeIf { it > 0 }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTap(intent)
        val profiles = (application as DirectApplication).profiles
        setContent { ru.ffl24.direct.ui.RuntimeHost(profiles, notificationDriver.value) { notificationDriver.value=null; intent?.removeExtra(SecondRouteNotifications.TAP_DRIVER) } }
    }
}

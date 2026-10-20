package ru.ffl24.direct

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.config.RuntimeProfiles
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.ui.ConnectionSettingsScreen
import java.io.File

class LiveConnectionSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun checkAndSaveButtonsApplyActualRestoredMariaDbWithoutRebuildingApk() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val json=JSONObject(File(context.filesDir,"runtime-test-2.json").readText())
        check(json.getString("host")=="localhost"&&json.getString("fixtureMarker")=="ffl-direct-private-restored-dump")
        val c=DirectMySqlSettings("localhost",json.getString("port"),json.getString("database"),json.getString("user"),json.getString("password"),"true",json.getString("caPem"))
        val manager=(context.applicationContext as DirectApplication).profiles
        val old=manager.graph.value;var saved=false
        val oldConfigured=DirectMySqlConfig.parse(manager.currentSettings()) is MySqlConfiguration.Ready
        compose.activityRule.scenario.onActivity{activity->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            activity.setContent{ConnectionSettingsScreen(c,manager::check,manager::apply,{saved=true})}
        }
        compose.onNodeWithTag("db_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("db_check").performScrollTo().performClick()
        compose.waitUntil(30000){runCatching{compose.onNodeWithTag("db_save").assertIsEnabled();true}.getOrDefault(false)}
        compose.onNodeWithTag("db_save").performScrollTo().performClick()
        compose.waitUntil(30000){saved}
        val next=manager.graph.value
        assertNotSame(old,next);assertEquals(RuntimeProfiles.scope(c),next.profileScope)
        runBlocking{
            assertEquals(ConnectionProbeResult.Reachable,next.connectionProbe.check())
            assertEquals(ConnectionProbeResult.Failed(if(oldConfigured)DirectDbError.UNAVAILABLE else DirectDbError.CONFIGURATION_MISSING),old.connectionProbe.check())
        }
        // A configured editor must require device credentials when no secure lock is configured.
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30000){runCatching{compose.onNodeWithTag("auth_admin_connection").assertIsDisplayed();true}.getOrDefault(false)}
        compose.onNodeWithTag("auth_admin_connection").performClick()
        compose.onNodeWithText("Для изменения подключения установите PIN или пароль блокировки Android.").assertIsDisplayed()
        compose.onNodeWithTag("db_host").assertDoesNotExist()
    }
}

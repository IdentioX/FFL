package ru.ffl24.direct

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.ui.ConnectionSettingsScreen
import java.io.File

class ConnectionSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun sample()=DirectMySqlSettings("db.example.invalid","3306","ffl_demo","driver_demo","fictional-only","true","")
    @Test fun editorValidatesChecksInvalidatesProofAndCancelsWithoutSaving() {
        var saves=0;var cancels=0;val gate=CompletableDeferred<Unit>();var checks=0
        compose.activityRule.scenario.onActivity{it.setContent{ConnectionSettingsScreen(sample(),{checks++;gate.await();ConnectionProbeResult.Reachable},{saves++;null},{cancels++})}}
        assertEquals("",compose.onNodeWithTag("db_password").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("db_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("db_check").performScrollTo().performClick()
        compose.onNodeWithTag("db_check").assertIsNotEnabled();assertEquals(1,checks)
        compose.runOnIdle{gate.complete(Unit)}
        compose.waitUntil(5000){runCatching{compose.onNodeWithTag("db_save").assertIsEnabled();true}.getOrDefault(false)}
        compose.onNodeWithTag("db_host").performScrollTo().performTextReplacement("different.example.invalid")
        compose.onNodeWithTag("db_save").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle{(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.waitForIdle()
        compose.onNodeWithTag("db_cancel").performScrollTo().performClick();compose.runOnIdle{assertEquals(1,cancels);assertEquals(0,saves)}
    }
    @Test fun firstLaunchOpensProtectedEditorAndCancelReturnsToUnconfiguredState() {
        compose.waitUntil(15000){runCatching{compose.onNodeWithTag("configuration_missing").assertIsDisplayed();true}.getOrDefault(false)}
        compose.onNodeWithText("Настроить подключение").performClick()
        compose.onNodeWithTag("db_host").assertIsDisplayed()
        compose.runOnIdle{assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
        compose.onNodeWithTag("db_cancel").performScrollTo().performClick()
        compose.onNodeWithTag("configuration_missing").assertIsDisplayed()
        compose.runOnIdle{assertEquals(0,compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)}
    }
    @Test fun syntheticEditorScreenshotsAreRealComposeNotLiveCredentials() {
        compose.activityRule.scenario.onActivity{it.setContent{ConnectionSettingsScreen(sample(),{ConnectionProbeResult.Reachable},{null},{})}}
        fun shot(name:String){compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync();try{i.uiAutomation.waitForIdle(250,3000)}catch(_:java.util.concurrent.TimeoutException){}
            try{compose.waitUntil(10000){i.uiAutomation.rootInActiveWindow?.packageName?.toString()=="ru.ffl24.direct.dev"}}
            catch(e:Exception){val raw=i.uiAutomation.takeScreenshot();if(raw!=null){File(i.targetContext.filesDir,"rejected-settings-private.png").outputStream().use{raw.compress(Bitmap.CompressFormat.PNG,100,it)};raw.recycle()};throw IllegalStateException("Native package="+i.uiAutomation.rootInActiveWindow?.packageName+"; activity window focus="+compose.activity.window.decorView.hasWindowFocus(),e)}
            check(i.uiAutomation.rootInActiveWindow?.packageName?.toString()=="ru.ffl24.direct.dev"){"Screenshot rejected: active package="+i.uiAutomation.rootInActiveWindow?.packageName};val b=requireNotNull(i.uiAutomation.takeScreenshot());val dir=File(i.targetContext.filesDir,"design-ui").apply{mkdirs()};File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
        shot("10-db-settings-fields")
        compose.onNodeWithTag("db_check").performScrollTo().performClick()
        compose.waitUntil(5000){runCatching{compose.onNodeWithTag("db_save").assertIsEnabled();true}.getOrDefault(false)}
        compose.onNodeWithTag("db_save").performScrollTo();shot("10b-db-settings-success-synthetic")
    }
}

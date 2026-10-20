package ru.ffl24.direct

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences
import ru.ffl24.direct.presentation.WorkspaceViewModel
import ru.ffl24.direct.ui.DirectApp
import java.io.File

class DesignImplementationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var fixture by mutableStateOf(DesignFixture())
    private fun show(signed:Boolean=true,mode:String="normal",small:Boolean=false) {
        context.getSharedPreferences("ffl_direct_workspace_v1",0).edit().clear().commit()
        runBlocking{WorkspacePreferences(context).setDate("2099-12-31")}
        fixture=DesignFixture(mode,if(signed)DriverSession(97,"Тестовый водитель")else null)
        compose.activityRule.scenario.onActivity{it.setContent{
            if(small){val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,1.6f)){Box(Modifier.width(320.dp)){key(fixture){DirectApp(fixture,openConnection={})}}}}
            else key(fixture){DirectApp(fixture,openConnection={})}
        }}
    }
    private fun waitTag(tag:String){compose.waitUntil(30000){runCatching{compose.onNodeWithTag(tag).assertIsDisplayed();true}.getOrDefault(false)}}
    private fun shot(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val ui=InstrumentationRegistry.getInstrumentation().uiAutomation
        try{ui.waitForIdle(250,3000)}catch(_:java.util.concurrent.TimeoutException){}
        val pkg=ui.rootInActiveWindow?.packageName?.toString().orEmpty()
        check(pkg=="ru.ffl24.direct.dev"||pkg.contains("inputmethod")){"A system overlay covers the app; screenshot rejected"}
        val dir=File(context.filesDir,"design-ui").apply{mkdirs()}
        val bitmap=requireNotNull(ui.takeScreenshot())
        File(dir,"$name.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun hideKeyboard(){compose.runOnIdle{(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun login(driver:Long=97){waitTag("driver_$driver");compose.onNodeWithTag("driver_$driver").performClick();compose.onNodeWithTag("login").performClick();compose.onNodeWithTag("driver_password").performTextInput("demo-only");hideKeyboard();compose.onNodeWithTag("confirm_login").performClick();waitTag("orders")}

    @Test fun visualWalkthroughUsesAllEightApprovedScreenTypes() {
        show(false);waitTag("driver_97");compose.onNodeWithTag("bottom_navigation").assertDoesNotExist();shot("01-driver-selection")
        compose.onNodeWithTag("driver_97").performClick();shot("01-driver-selection");compose.onNodeWithTag("login").performClick();waitTag("driver_password");
        compose.onNodeWithTag("driver_password").performClick().performTextInput("demo-only");shot("02a-keyboard");hideKeyboard();shot("02-password");compose.onNodeWithTag("confirm_login").performClick();waitTag("orders");shot("03-orders")
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys:1"));compose.onNodeWithTag("order_reys:1").performClick();waitTag("order_details");shot("04-details");compose.onNodeWithText("Предыдущие водители").performScrollTo();shot("04b-history");compose.onNodeWithTag("history_expand").performScrollTo().performClick();compose.onNodeWithText("Тестовый водитель №5").performScrollTo().assertIsDisplayed();shot("04c-history-five")
        compose.onNodeWithTag("deliver").performClick();shot("07-delivery-confirmation");compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("nav_directory").performClick();waitTag("phone_directory");shot("05-directory")
        compose.onNodeWithTag("nav_sort").assertIsNotEnabled();compose.onNodeWithTag("directory_category_1").performClick();hideKeyboard();waitTag("bottom_navigation");shot("05b-directory-drivers")
        compose.onNodeWithTag("nav_settings").performClick();waitTag("open_work_date");shot("06-settings")
        compose.onNodeWithTag("open_work_date").performClick();waitTag("work_date");shot("06b-date");compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("nav_refresh").assertIsNotEnabled();compose.onNodeWithTag("nav_sort").assertIsNotEnabled()
        compose.onNodeWithTag("nav_home").performClick();waitTag("orders");compose.onNodeWithTag("nav_sort").performClick();shot("03b-sorting")
        compose.onNodeWithTag("nav_settings").performClick();compose.onNodeWithTag("dark_theme").performClick();compose.onNodeWithTag("nav_home").performClick();waitTag("orders");shot("03c-dark")
        compose.onNodeWithTag("nav_settings").performClick();compose.onNodeWithTag("dark_theme").performClick();compose.onNodeWithTag("nav_home").performClick();waitTag("orders")
        compose.runOnIdle{fixture=DesignFixture("loading")};waitTag("connection_checking");shot("08-loading")
        compose.runOnIdle{fixture=DesignFixture("error")};waitTag("connection_failed");shot("08b-error")
        compose.runOnIdle{fixture=DesignFixture("disabled")};waitTag("driver_disabled");shot("08c-access-disabled")
        compose.runOnIdle{fixture=DesignFixture("empty",DriverSession(97,"Тестовый водитель"))};waitTag("orders");compose.onNodeWithTag("order_reys:1").assertDoesNotExist();compose.onAllNodesWithText("Заказов нет").assertCountEquals(3);shot("08d-empty")
    }

    @Test fun refreshDeduplicatesKeepsCachedRowsAndRemovesThemOnAccessRevocation() {
        show();waitTag("orders")
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys_den:1"))
        val anchor=compose.onNodeWithTag("order_reys_den:1").fetchSemanticsNode().boundsInRoot.top
        val before=fixture.reads.get();val gate=CompletableDeferred<Unit>()
        compose.runOnIdle{fixture.gate=gate;fixture.revision=10}
        compose.onNodeWithTag("nav_refresh").performClick();compose.onNodeWithTag("nav_refresh").assertIsNotEnabled()
        compose.onNodeWithTag("order_reys_den:1").assertIsDisplayed()
        compose.runOnIdle{assertEquals(before+1,fixture.reads.get());gate.complete(Unit)}
        compose.waitUntil(10000){runCatching{compose.onNodeWithTag("nav_refresh").assertIsEnabled();true}.getOrDefault(false)}
        assertTrue(compose.onAllNodesWithText("13 накладных",substring=true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(anchor,compose.onNodeWithTag("order_reys_den:1").fetchSemanticsNode().boundsInRoot.top,2f)
        compose.runOnIdle{fixture.gate=null;fixture.failure=DetailsResult.Failed(AuthError.CONNECTION_UNAVAILABLE)}
        compose.onNodeWithTag("nav_refresh").performClick();waitTag("delivery_notice");compose.onNodeWithTag("order_reys_den:1").assertIsDisplayed()
        assertEquals("2099-12-31",WorkspacePreferences(context).date())
        compose.runOnIdle{fixture.failure=DetailsResult.DriverDisabled;fixture.revoked=true}
        compose.onNodeWithTag("nav_refresh").performClick();waitTag("driver_disabled");compose.onNodeWithTag("orders").assertDoesNotExist()
    }

    @Test fun sortingDragPersistsSourceAwareSectionOrderAcrossNewGraph() {
        show();waitTag("order_reys:1");compose.onNodeWithTag("nav_sort").performClick();compose.onNodeWithTag("nav_sort").assertIsSelected()
        val handle=compose.onNodeWithTag("drag_reys:1")
        handle.performTouchInput{down(center);advanceEventTime(700);moveBy(Offset(0f,100f),900);up()}
        compose.waitUntil(10000){WorkspacePreferences(context).savedOrder(97,"2099-12-31",OrderSection.FIRST).firstOrNull()=="reys:2"}
        assertEquals(listOf("reys_den:1"),WorkspacePreferences(context).savedOrder(97,"2099-12-31",OrderSection.SECOND))
        compose.runOnIdle{fixture=DesignFixture(session=DriverSession(97,"Тестовый водитель"))};waitTag("order_reys:2")
        assertTrue(compose.onNodeWithTag("order_reys:2").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithTag("order_reys:1").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(listOf("reys:2","reys:1","reys:3"),WorkspacePreferences(context).savedOrder(97,"2099-12-31",OrderSection.FIRST))
        fun date(value:String){
            compose.onNodeWithTag("nav_settings").performClick();compose.onNodeWithTag("open_work_date").performClick()
            compose.onNodeWithTag("work_date").performTextReplacement(value);hideKeyboard();compose.onNodeWithText("Применить дату").performClick()
            compose.onNodeWithTag("nav_home").performClick();waitTag("order_reys:1")
        }
        date("2099-12-30")
        assertTrue(compose.onNodeWithTag("order_reys:1").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithTag("order_reys:2").fetchSemanticsNode().boundsInRoot.top)
        date("2099-12-31")
        assertTrue(compose.onNodeWithTag("order_reys:2").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithTag("order_reys:1").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("nav_settings").performClick();compose.onNodeWithTag("logout").performScrollTo().performClick();login(98)
        assertTrue(compose.onNodeWithTag("order_reys:1").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithTag("order_reys:2").fetchSemanticsNode().boundsInRoot.top)
        assertEquals("reys:2",WorkspacePreferences(context).savedOrder(97,"2099-12-31",OrderSection.FIRST).first())
    }

    @Test fun directorySearchAndPasswordVisibilityAreRealControls() {
        show(false);waitTag("driver_97");compose.onNodeWithTag("driver_97").performClick();compose.onNodeWithTag("login").performClick()
        compose.onNodeWithTag("driver_password").performTextInput("wrong");compose.onNodeWithTag("password_visibility").performClick();compose.onNodeWithTag("driver_password").assertTextContains("wrong");compose.onNodeWithContentDescription("Скрыть пароль").assertIsDisplayed();compose.onNodeWithTag("password_visibility").performClick();hideKeyboard()
        compose.onNodeWithTag("confirm_login").performClick();waitTag("invalid_credentials");compose.onNodeWithTag("driver_password").performTextReplacement("demo-only");compose.onNodeWithTag("confirm_login").performClick();waitTag("orders")
        compose.onNodeWithTag("nav_directory").performClick();waitTag("directory_search");compose.onNodeWithTag("directory_category_1").performClick();compose.onNodeWithTag("directory_search").performTextInput("Второй тестовый")
        compose.onNodeWithText("Второй тестовый водитель").assertIsDisplayed();compose.onNodeWithText("Тестовый водитель",substring=false).assertDoesNotExist()
        hideKeyboard();waitTag("nav_home");compose.onNodeWithTag("nav_home").performClick();waitTag("orders")
    }

    @Test fun smallScreenLargeFontKeepsFiveActionsCenteredAndReachable() {
        show(small=true);waitTag("orders")
        val bar=compose.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot
        val center=compose.onNodeWithTag("nav_refresh").fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(bar.center.x,center,2f)
        val fab=compose.onNodeWithTag("refresh_fab",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val surface=compose.onNodeWithTag("nav_surface",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals("Refresh remains a circle",fab.width,fab.height,1f)
        assertTrue("Circle protrudes over panel",fab.top<surface.top)
        assertEquals("Circle is centered",bar.center.x,fab.center.x,2f)
        for(tag in listOf("nav_home","nav_directory","nav_refresh","nav_sort","nav_settings")){
            val node=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(node.left>=bar.left&&node.right<=bar.right)
        }
        shot("09-small-font-1-6")
        compose.onNodeWithTag("nav_settings").performClick();compose.onNodeWithTag("open_work_date").assertIsDisplayed();compose.onNodeWithTag("back").performClick();waitTag("orders")
    }

    @Test fun smallKeyboardLoginCanFinishWithImeAction() {
        show(signed=false,small=true);waitTag("driver_97")
        compose.onNodeWithTag("driver_97").performClick();compose.onNodeWithTag("login").performClick()
        compose.onNodeWithTag("driver_password").performScrollTo().performClick().performTextInput("demo-only")
        compose.onNodeWithTag("driver_password").performImeAction()
        waitTag("orders");waitTag("bottom_navigation")
    }

    @Test fun contextualRefreshUsesTheIncomingEntryWhileItsQueryIsPending() {
        show();waitTag("orders")
        compose.onNodeWithTag("nav_directory").performClick();waitTag("phone_directory")
        val gate=CompletableDeferred<Unit>();fixture.gate=gate
        val before=fixture.reads.get()
        try {
            compose.onNodeWithTag("nav_home").performClick();waitTag("orders_loading")
            compose.onNodeWithTag("nav_refresh").assertIsNotEnabled()
            compose.onNodeWithTag("nav_refresh").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Обновление"))
            assertEquals(before+1,fixture.reads.get())
            gate.complete(Unit);waitTag("orders")
            compose.waitUntil(10000){runCatching{compose.onNodeWithTag("nav_refresh").assertIsEnabled();true}.getOrDefault(false)}
            compose.onNodeWithTag("nav_home").assertIsSelected()
        } finally {gate.complete(Unit);fixture.gate=null}
    }

    @Test fun refreshCannotUndoADragWhilePreferenceCommitIsSuspended() {
        context.getSharedPreferences("ffl_direct_workspace_v1",0).edit().clear().commit()
        val block=java.util.concurrent.atomic.AtomicBoolean(false)
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val delayed=object:android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val original=super.getSharedPreferences(name,mode)
                return object:android.content.SharedPreferences by original {
                    override fun edit():android.content.SharedPreferences.Editor {
                        val editor=original.edit();var orderWrite=false
                        return object:android.content.SharedPreferences.Editor by editor {
                            override fun putString(key:String,value:String?):android.content.SharedPreferences.Editor {
                                orderWrite=orderWrite||key.startsWith("order:");editor.putString(key,value);return this
                            }
                            override fun commit():Boolean {
                                if(orderWrite&&block.compareAndSet(true,false)) {
                                    entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))
                                }
                                return editor.commit()
                            }
                        }
                    }
                }
            }
        }
        val f=DesignFixture();val prefs=WorkspacePreferences(delayed)
        lateinit var model:WorkspaceViewModel
        compose.runOnIdle{model=WorkspaceViewModel(97,"2099-12-31",f.workspaceRepository,prefs);model.refresh()}
        compose.waitUntil(10000){model.state.value is DetailsResult.Success&&!model.refreshing.value}
        try {
            block.set(true);compose.runOnIdle{model.refresh()}
            assertTrue("Delayed storage commit reached",entered.await(10,java.util.concurrent.TimeUnit.SECONDS))
            compose.runOnIdle{model.move(OrderSection.FIRST,OrderId(OrderSource.REYS,1),1)}
            release.countDown()
            compose.waitUntil(10000){!model.refreshing.value&&prefs.savedOrder(97,"2099-12-31",OrderSection.FIRST).firstOrNull()=="reys:2"}
            val rows=(model.state.value as DetailsResult.Success).value.filter{it.section==OrderSection.FIRST}
            assertEquals(listOf("reys:2","reys:1","reys:3"),rows.map{it.id.wire})
        } finally {release.countDown();compose.runOnIdle{model.hide()}}
    }

    @Test fun workspaceViewModelRejectsDuplicateRefreshWithoutClearingGoodData() {
        val f=DesignFixture(session=DriverSession(97,"Тестовый водитель"));lateinit var model:WorkspaceViewModel
        compose.runOnIdle{model=WorkspaceViewModel(97,"2099-12-31",f.workspaceRepository,WorkspacePreferences(context));model.refresh()}
        compose.waitUntil(10000){model.state.value is DetailsResult.Success}
        val before=f.reads.get();val gate=CompletableDeferred<Unit>();f.gate=gate
        compose.runOnIdle{model.refresh();model.refresh();model.refresh();assertTrue(model.state.value is DetailsResult.Success)}
        assertEquals(before+1,f.reads.get())
        compose.runOnIdle{gate.complete(Unit)};compose.waitUntil(10000){!model.refreshing.value}
        f.gate=null;f.failure=DetailsResult.Failed(AuthError.TIMEOUT)
        compose.runOnIdle{model.refresh()};compose.waitUntil(10000){!model.refreshing.value};assertTrue(model.state.value is DetailsResult.Success)
        compose.runOnIdle{model.hide()};assertNull(model.state.value)
    }
}

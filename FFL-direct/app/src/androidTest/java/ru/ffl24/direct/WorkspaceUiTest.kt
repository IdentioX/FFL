package ru.ffl24.direct

import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences
import ru.ffl24.direct.notifications.SecondRouteNotifications
import ru.ffl24.direct.ui.*
import kotlinx.coroutines.runBlocking

class WorkspaceUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun bothRoutesDeliveredAndSourceIdentityAreVisible() {
        var selected: OrderId?=null
        val first=row(OrderSource.REYS,1,"Первый клиент")
        val second=row(OrderSource.REYS_DEN,1,"Второй клиент")
        val delivered=row(OrderSource.REYS_DEN,2,"Сданный клиент",true)
        compose.setContent { MaterialTheme { OrdersContent(DetailsResult.Success(listOf(first,second,delivered)),"2026-05-18",false,null,{},{},{_,_,_->},{ selected=it },{}) } }
        compose.onNodeWithTag("order_reys:1").assertIsDisplayed()
        screenshot("orders-first")
        compose.onNodeWithTag("section_1").performClick()
        compose.onNodeWithTag("order_reys_den:1").performClick()
        screenshot("orders-second")
        compose.runOnIdle { assertEquals(second.id,selected) }
        compose.onNodeWithTag("section_2").performClick()
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys_den:2"))
        compose.onNodeWithTag("order_reys_den:2").assertIsDisplayed()
        screenshot("orders-delivered")
        compose.onNodeWithText("Сдал").assertDoesNotExist()
    }
    @Test fun deliveryRequiresFiveSecondConfirmationAndNeverFiresOnOpening() {
        var delivered=0
        compose.mainClock.autoAdvance=false
        compose.setContent { MaterialTheme { OrdersContent(DetailsResult.Success(listOf(row(OrderSource.REYS,1,"Fixture"))),"2026-05-18",false,null,{},{},{_,_,_->},{},{ delivered++ }) } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("order_reys:1").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("confirm_delivery").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0,delivered) }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithTag("confirm_delivery").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(1,delivered) }
        compose.mainClock.autoAdvance=true
    }
    @Test fun reorderAndDetailsCallbacksKeepFullSourceId() {
        var moved: OrderId?=null
        compose.setContent { MaterialTheme { OrdersContent(DetailsResult.Success(listOf(row(OrderSource.REYS_DEN,3_000_000_000L,"Fixture"))),"2026-05-18",false,null,{},{},{_,id,_-> moved=id },{},{},second=true,sorting=true) } }
        val actions=compose.onNodeWithTag("drag_reys_den:3000000000").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label=="Поднять заказ" }.action()) }
        compose.runOnIdle { assertEquals("reys_den:3000000000",moved?.wire) }
    }
    @Test fun directoryMissingCardRawPhoneCopyAndActiveDriverRows() {
        val directory=PhoneDirectory(null,null,listOf(DirectoryPhone("Проверочный диспетчер"," +70000000000 ")),listOf(DriverPhone(97,"Проверочный водитель","+70000000001")))
        val focused=java.util.concurrent.atomic.AtomicReference<android.view.View>()
        compose.setContent { focused.set(androidx.compose.ui.platform.LocalView.current); MaterialTheme { DirectoryContent(DetailsResult.Success(directory),{}) } }
        compose.waitUntil(10_000) { focused.get()?.hasWindowFocus()==true }
        compose.onNodeWithText("Номер карты не указан").assertIsDisplayed()
        screenshot("directory")
        compose.onNodeWithText(" +70000000000 ").assertExists()
        compose.onNodeWithContentDescription("Копировать номер Проверочный диспетчер").performClick()
        compose.runOnIdle {
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(" +70000000000 ",clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithTag("directory_category_1").performClick()
        compose.onNodeWithText("Проверочный водитель").assertIsDisplayed()
    }
    @Test fun localPreferencesKeepDriverDateSectionOrderAndScrollSeparate() = runBlocking {
        // This isolated test APK/AVD may contain data left by a previous full fixture run.
        context.getSharedPreferences("ffl_direct_workspace_v1",Context.MODE_PRIVATE).edit().clear().commit()
        val p=WorkspacePreferences(context)
        p.order(97,"2026-05-18",OrderSection.FIRST,listOf("reys:3","reys:1"))
        p.scroll(97,"2026-05-18",0,7,12)
        assertEquals(listOf("reys:3","reys:1"),p.savedOrder(97,"2026-05-18",OrderSection.FIRST))
        assertTrue(p.savedOrder(98,"2026-05-18",OrderSection.FIRST).isEmpty())
        assertTrue(p.savedOrder(97,"2026-05-19",OrderSection.FIRST).isEmpty())
        assertTrue(p.savedOrder(97,"2026-05-18",OrderSection.SECOND).isEmpty())
        assertEquals(7 to 12,WorkspacePreferences(context).scroll(97,"2026-05-18",0))
        p.seen("97:2026-05-18",setOf("reys_den:1"));p.seen("98:2026-05-19",setOf("reys_den:2"))
        assertNull(p.seen("97:2026-05-18"));assertEquals(setOf("reys_den:2"),p.seen("98:2026-05-19"))
        p.clearSeen()
    }
    @Test fun notificationChannelPendingIntentAndSchedulerAreRealAndroidObjects() {
        compose.setContent { MaterialTheme { androidx.compose.material3.Text("Проверка уведомлений") } }
        SecondRouteNotifications.post(context,97,2)
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification=manager.activeNotifications.firstOrNull { it.id==1001 }
        assertNotNull(notification)
        assertEquals(context.packageName,notification!!.notification.contentIntent.creatorPackage)
        if(android.os.Build.VERSION.SDK_INT>=26) assertNotNull(manager.getNotificationChannel(SecondRouteNotifications.CHANNEL))
        SecondRouteNotifications.schedule(context,97)
        val work=WorkManager.getInstance(context).getWorkInfosForUniqueWork(SecondRouteNotifications.WORK).get(15,java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(work.isNotEmpty())
        SecondRouteNotifications.cancel(context)
        compose.waitUntil(10_000) { manager.activeNotifications.none { it.id==1001 } }
        assertFalse(manager.activeNotifications.any { it.id==1001 })
    }
    @Test fun pullToRefreshRequestsNewRead() {
        var refreshes=0
        compose.setContent { MaterialTheme { OrdersContent(DetailsResult.Success(listOf(row(OrderSource.REYS,1,"Fixture"))),"2026-05-18",false,null,{ refreshes++ },{},{_,_,_->},{},{}) } }
        compose.onNodeWithTag("order_list").performTouchInput { swipeDown(startY=10f,endY=height-10f,durationMillis=500) }
        compose.runOnIdle { assertTrue(refreshes>0) }
    }
    @Test fun errorAndDisabledNeverRenderPriorOrders() {
        var state by mutableStateOf<DetailsResult<List<DriverOrder>>>(DetailsResult.Success(listOf(row(OrderSource.REYS,1,"Скрываемый клиент"))))
        compose.setContent { MaterialTheme { OrdersContent(state,"2026-05-18",false,null,{},{},{_,_,_->},{},{}) } }
        compose.onNodeWithTag("order_reys:1").assertIsDisplayed()
        compose.runOnIdle { state=DetailsResult.DriverDisabled }
        compose.onNodeWithTag("order_reys:1").assertDoesNotExist()
        compose.onNodeWithText("Нет доступа").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val directory=File(context.filesDir,"completion-ui").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun row(source: OrderSource,id: Long,name: String,delivered: Boolean=false)=DriverOrder(OrderId(source,id),97,null,"2026-05-18","Fixture-$id",name,null,0,2,2,1,delivered,if(delivered) "fixture-time" else null,OrderContacts())
}

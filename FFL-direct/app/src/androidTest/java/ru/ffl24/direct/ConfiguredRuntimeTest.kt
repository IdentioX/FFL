package ru.ffl24.direct

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.domain.*

/** Ordinary MainActivity/DirectApplication/BuildConfig; no injected graph. */
class ConfiguredRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun choose(db: OwnedTestDatabase) {
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("driver_search").fetchSemanticsNodes().isNotEmpty() }
        if (db.restored) compose.onNodeWithTag("driver_search").performTextInput("FFL E2E")
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("driver_${db.driver}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("driver_${db.disabled}").assertDoesNotExist()
        compose.onNodeWithTag("driver_${db.driver}").performClick()
    }

    @Test fun freshLoginOrdersDetailsDeliveryAndDirectoryUseCompiledConfiguration() {
        val db = OwnedTestDatabase()
        choose(db)
        compose.onNodeWithTag("configuration_missing").assertDoesNotExist()
        compose.onNodeWithTag("login").performClick()
        compose.onNodeWithTag("driver_password").performTextInput("wrong-fixture-password")
        compose.onNodeWithTag("confirm_login").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("invalid_credentials").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("driver_password").performTextReplacement(db.password)
        compose.onNodeWithTag("confirm_login").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("orders").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("work_date").performTextReplacement(db.date)
        compose.onNodeWithText("Применить дату").performClick()
        compose.onNodeWithTag("back").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("order_reys:${db.order}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys_den:${db.order}"))
        compose.onNodeWithTag("order_reys_den:${db.order}").assertIsDisplayed()
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys:${db.order + 1}"))
        compose.onNodeWithTag("order_reys:${db.order + 1}").assertIsDisplayed()
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys:${db.order}"))
        compose.onNodeWithTag("order_reys:${db.order}").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithText(db.address).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сдал").performClick()
        compose.onNodeWithTag("confirm_delivery").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithText("Заказ сдан").fetchSemanticsNodes().isNotEmpty() }
        val graph = (compose.activity.application as DirectApplication).graph
        runBlocking {
            val details = (graph.orderDetailsRepository.getDetails(OrderId(OrderSource.REYS, db.order)) as DetailsResult.Success).value
            assertTrue(details.isDelivered); assertNotNull(details.deliveredAt)
        }
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys:${db.order}"))
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("order_reys:${db.order}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_directory").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("phone_directory").fetchSemanticsNodes().isNotEmpty() }
        if (!db.restored) compose.onNodeWithText("Fixture holder").assertIsDisplayed()
        compose.onNodeWithTag("nav_home").performClick()
        compose.waitUntil(45000){compose.onAllNodesWithTag("order_list").fetchSemanticsNodes().isNotEmpty()}
        if(db.restored) {
            // Exercise the same drag -> scoped disk preferences used after a cold process restart.
            compose.onNodeWithTag("nav_sort").performClick()
            compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("drag_reys:${db.order}"))
            compose.onNodeWithTag("drag_reys:${db.order}").performTouchInput {down(center);advanceEventTime(700);moveBy(androidx.compose.ui.geometry.Offset(0f,-100f),900);up()}
            val prefs=ru.ffl24.direct.local.WorkspacePreferences(db.context)
            compose.waitUntil(10000){val ids=prefs.savedOrder(db.driver,db.date,OrderSection.DELIVERED);ids.indexOf("reys:${db.order}")>=0&&ids.indexOf("reys:${db.order}")<ids.indexOf("reys_den:${db.notification}")}
            compose.onNodeWithTag("nav_sort").performClick()
        }
    }

    @Test fun coldProcessRestoresPersistedSessionAndDateThenLogsOut() {
        val db = OwnedTestDatabase()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("orders").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("driver_${db.driver}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Рабочая дата ${db.date}").assertIsDisplayed()
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys:${db.order}"))
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("order_reys:${db.order}").fetchSemanticsNodes().isNotEmpty() }
        if(db.restored) {
            compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("section_2"))
            assertTrue(compose.onNodeWithTag("order_reys:${db.order}").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithTag("order_reys_den:${db.notification}").fetchSemanticsNode().boundsInRoot.top)
        }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("driver_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("orders").assertDoesNotExist()
        runBlocking { assertNull((compose.activity.application as DirectApplication).graph.sessions.current().session) }
    }
    @Test fun centralRefreshReadsActualMysqlAndPreservesDateOrderAndViewport() {
        val db=OwnedTestDatabase()
        assertTrue(db.restored && db.driver==2000000001L && db.notification==2000000004L)
        var previousState:Int=1;var previousTime:String?=null
        fun update(state:Int,time:String?) {
            ru.ffl24.direct.data.mysql.MySqlConnectionFactory().open(db.jdbc,ru.ffl24.direct.data.mysql.ConnectionPolicy()).use { c->
                c.isReadOnly=false
                c.prepareStatement("UPDATE reys_den SET st_vv=?,date_att_log=? WHERE id=? AND id_v=?").use { q->
                    q.setInt(1,state);if(time==null)q.setNull(2,java.sql.Types.VARCHAR)else q.setString(2,time)
                    q.setLong(3,db.notification);q.setLong(4,db.driver);assertEquals(1,q.executeUpdate())
                }
            }
        }
        runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ru.ffl24.direct.data.mysql.MySqlConnectionFactory().open(db.jdbc,ru.ffl24.direct.data.mysql.ConnectionPolicy()).use { c->
                c.prepareStatement("SELECT st_vv,date_att_log FROM reys_den WHERE id=? AND id_v=?").use { q->q.setLong(1,db.notification);q.setLong(2,db.driver);q.executeQuery().use { r->assertTrue(r.next());previousState=r.getInt(1);previousTime=r.getString(2) } }
            }
            update(1,"2099-12-31 10:00:00")
        }}
        try {
            choose(db);compose.onNodeWithTag("login").performClick();compose.onNodeWithTag("driver_password").performTextInput(db.password);compose.onNodeWithTag("confirm_login").performClick()
            compose.waitUntil(60000){compose.onAllNodesWithTag("orders").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("open_settings").performClick();compose.onNodeWithTag("work_date").performTextReplacement(db.date);compose.onNodeWithText("Применить дату").performClick();compose.onNodeWithTag("back").performClick()
            compose.waitUntil(60000){runCatching{compose.onNodeWithTag("order_list").assertIsDisplayed();true}.getOrDefault(false)}
            compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys_den:${db.order}"))
            compose.onNodeWithTag("section_1").assert(hasText("1 заказ",substring=true))
            val anchor=compose.onNodeWithTag("order_reys_den:${db.order}").fetchSemanticsNode().boundsInRoot.top
            val preferences=ru.ffl24.direct.local.WorkspacePreferences(db.context)
            compose.waitUntil(10000){preferences.savedOrder(db.driver,db.date,OrderSection.SECOND).contains("reys_den:${db.order}")}
            val saved=preferences.savedOrder(db.driver,db.date,OrderSection.SECOND)
            val deliveredOrder=preferences.savedOrder(db.driver,db.date,OrderSection.DELIVERED)
            assertTrue(deliveredOrder.indexOf("reys:${db.order + 1}")<deliveredOrder.indexOf("reys:${db.order}"))
            runBlocking {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){update(0,null)}}
            compose.onNodeWithTag("nav_refresh").performClick()
            compose.waitUntil(60000){runCatching{compose.onNodeWithTag("section_1").assert(hasText("2 заказа",substring=true));true}.getOrDefault(false)}
            compose.onNodeWithTag("order_reys_den:${db.notification}").assertExists()
            assertEquals(db.date,preferences.date())
            assertEquals(saved.first(),preferences.savedOrder(db.driver,db.date,OrderSection.SECOND).first())
            assertEquals(deliveredOrder.filterNot{it=="reys_den:${db.notification}"},preferences.savedOrder(db.driver,db.date,OrderSection.DELIVERED))
            assertEquals(anchor,compose.onNodeWithTag("order_reys_den:${db.order}").fetchSemanticsNode().boundsInRoot.top,2f)
            val graph=(compose.activity.application as DirectApplication).graph
            val result=runBlocking {graph.workspaceRepository.orders(db.date)} as DetailsResult.Success
            assertTrue(result.value.any {it.id==OrderId(OrderSource.REYS_DEN,db.notification)&&!it.isDelivered})
        } finally {runBlocking {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){update(previousState,previousTime)}}}
    }

}

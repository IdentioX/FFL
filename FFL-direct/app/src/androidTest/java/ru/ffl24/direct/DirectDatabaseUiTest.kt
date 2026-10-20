package ru.ffl24.direct

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.ui.DirectApp

/** Real screen -> production repositories -> JDBC/TLS -> owned synthetic MySQL fixture. */
class DirectDatabaseUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualScreenLoadsDriverOrdersAndDetailsFromFixtureMySql() {
        val i=InstrumentationRegistry.getInstrumentation()
        val file=java.io.File(i.targetContext.filesDir,"completion-db.json")
        assumeTrue("Disposable MySQL fixture not configured",file.exists())
        val config=JSONObject(file.readText())
        require(config.getString("fixtureMarker")=="ffl-direct-owned-fixture-20261017")
        i.targetContext.getSharedPreferences("ffl_direct_workspace_v1",0).edit().clear().putString("date","2026-05-18").commit()
        val ca=config.optString("caPem").takeIf { it.isNotBlank() }
            ?: i.context.assets.open("fixture-ca.pem").bufferedReader().use { it.readText() }
        val dbSettings=DirectMySqlSettings(config.getString("host"),config.getString("port"),
            config.getString("database"),config.getString("user"),config.getString("password"),"true",ca)
        val graph=AppGraph.create("DIRECT_MYSQL",dbSettings)
        compose.activityRule.scenario.onActivity { it.setContent { DirectApp(graph) } }
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("driver_97").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("driver_97").performClick()
        compose.onNodeWithTag("login").performClick()
        compose.onNodeWithTag("driver_password").performTextInput("driver-fixture-only")
        compose.onNodeWithTag("confirm_login").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("order_reys:1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("order_reys:1").assertIsDisplayed()
        val directory=java.io.File(i.targetContext.filesDir,"completion-ui").apply { mkdirs() }
        java.io.File(directory,"mysql-orders.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithTag("order_reys:1").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithText("Synthetic test address").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Synthetic test address").assertIsDisplayed()
        java.io.File(directory,"mysql-details.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithText("Сдал").performClick()
        compose.onNodeWithTag("confirm_delivery").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithText("Заказ сдан").fetchSemanticsNodes().isNotEmpty() }
        // Only this disposable fixture is reset, using its column-limited account, for the next test.
        val db=(DirectMySqlConfig.parse(dbSettings) as MySqlConfiguration.Ready).config
        MySqlConnectionFactory().open(db,ConnectionPolicy()).use { connection ->
            connection.isReadOnly=false
            try {
                connection.prepareStatement("SELECT st_vv,date_att_log FROM reys WHERE id=1 AND id_v=97").use { statement ->
                    statement.executeQuery().use { rows -> org.junit.Assert.assertTrue(rows.next());org.junit.Assert.assertEquals(1,rows.getInt(1));org.junit.Assert.assertNotNull(rows.getString(2)) }
                }
                java.io.File(directory,"mysql-ui-write.json").writeText("{\"fixture_only\":true,\"screen_to_mysql_delivery_verified\":true}")
            } finally {
                connection.prepareStatement("UPDATE reys SET st_vv=0,date_att_log=NULL WHERE id=1 AND id_v=97").use { org.junit.Assert.assertEquals(1,it.executeUpdate()) }
            }
        }
    }
}

package ru.ffl24.direct

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import ru.ffl24.direct.data.mysql.DirectMySqlSettings
import ru.ffl24.direct.ui.DirectApp

class StartupTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStartHasNoConnectionInputOrAuthNavigationPanel() {
        compose.onNodeWithTag("configuration_missing").assertIsDisplayed()
        compose.onNodeWithText("База данных не настроена").assertIsDisplayed()
        assertNoEditableFields()
        compose.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        compose.onNodeWithTag("open_settings").assertDoesNotExist()
    }

    @Test
    fun recreationPreservesUnconfiguredStateWithoutSetupFlow() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("configuration_missing").assertIsDisplayed()
        compose.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        assertNoEditableFields()
    }

    @Test
    fun refusedConnectionShowsSafeMessageAndRetryWithoutCredentials() {
        val ca = InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-ca.pem").bufferedReader().use { it.readText() }
        val graph = AppGraph.create("DIRECT_MYSQL", DirectMySqlSettings(
            "127.0.0.1", "1", "fixture", "fixture_read", "fixture-only", "true", ca
        ))
        compose.activityRule.scenario.onActivity { it.setContent { DirectApp(graph) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("connection_failed").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("connection_failed").assertIsDisplayed()
        compose.onNodeWithText("fixture_read", substring = true).assertDoesNotExist()
        compose.onNodeWithText("127.0.0.1", substring = true).assertDoesNotExist()
        assertNoEditableFields()
        compose.onNodeWithText("Повторить").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("connection_failed").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        assertNoEditableFields()
    }

    @Test
    fun incompleteConfigHasNoSetupInputs() {
        val graph = AppGraph.create("DIRECT_MYSQL", DirectMySqlSettings(host = "localhost"))
        compose.activityRule.scenario.onActivity { it.setContent { DirectApp(graph) } }
        compose.onNodeWithTag("configuration_invalid").assertIsDisplayed()
        assertNoEditableFields()
    }

    private fun assertNoEditableFields() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).fetchSemanticsNodes()
        val date = compose.onAllNodesWithTag("work_date").fetchSemanticsNodes()
        assertEquals(date.size, nodes.size)
        compose.onNodeWithText("Адрес API").assertDoesNotExist()
        compose.onNodeWithText("Адрес сервера").assertDoesNotExist()
    }
}

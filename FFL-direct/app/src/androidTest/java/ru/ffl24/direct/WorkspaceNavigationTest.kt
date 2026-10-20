package ru.ffl24.direct

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.ui.DirectApp

/** Full Compose flow with test-only repositories. This is not a MySQL E2E claim. */
class WorkspaceNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun loginListDetailsDirectorySettingsAndLogoutUseRealNavigation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("ffl_direct_workspace_v1",0).edit().clear().commit()
        val fixture=NavigationFixture()
        var tap by mutableStateOf<Long?>(null)
        compose.activityRule.scenario.onActivity { it.setContent { DirectApp(fixture,tap,consumeNotification={ tap=null }) } }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("driver_97").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("driver_97").performClick()
        compose.onNodeWithTag("login").performClick()
        compose.onNodeWithTag("driver_password").performTextInput("wrong")
        compose.onNodeWithTag("confirm_login").performClick()
        compose.onNodeWithTag("invalid_credentials").assertIsDisplayed()
        compose.onNodeWithTag("driver_password").performTextReplacement("fixture-only")
        compose.onNodeWithTag("confirm_login").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("order_reys:1").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { tap=97 }
        compose.waitUntil(20000){runCatching{compose.onNodeWithTag("orders").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle,"Второй рейс"));true}.getOrDefault(false)}
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("section_1"))
        compose.onNodeWithTag("section_1").assert(hasText("Второй рейс",substring=true))
        compose.runOnIdle { tap=98 }
        compose.runOnIdle { assertNull(tap) }
        compose.onNodeWithTag("nav_home").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("order_reys:1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("order_reys:1").performClick()
        compose.onNodeWithTag("order_details").assertIsDisplayed()
        compose.onNodeWithText("Fixture client").assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        // Back triggers a lifecycle refresh and navigation transition. Existence
        // alone can refer to the outgoing zero-size node before placement.
        compose.waitUntil(20_000) {
            runCatching { compose.onNodeWithTag("order_reys:1").assertIsDisplayed();true }.getOrDefault(false)
        }
        compose.onNodeWithTag("nav_directory").performClick()
        compose.onNodeWithTag("phone_directory").assertIsDisplayed()
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("driver_97").fetchSemanticsNodes().isNotEmpty() }
        assertNull(fixture.session)
        compose.onNodeWithTag("order_reys:1").assertDoesNotExist()
    }
}
private class NavigationFixture: DirectRuntime {
    var session: DriverSession?=null
    private val id=OrderId(OrderSource.REYS,1)
    override val startupRepository=object: StartupRepository { override fun readiness()=RuntimeReadiness.ConnectionNotChecked }
    override val authRepository=object: AuthRepository {
        override suspend fun getDrivers()=AuthResult.Success(listOf(Driver(97,"Fixture driver",null,null,null)))
        override suspend fun restore()=AuthResult.Success(session)
        override suspend fun checkAccess()=AuthResult.Success(session)
        override suspend fun logout(): AuthResult<Unit> { session=null;return AuthResult.Success(Unit) }
        override suspend fun login(driverId: Long,password: CharArray): AuthResult<DriverSession> =
            if(password.concatToString()=="fixture-only") AuthResult.Success(DriverSession(97,"Fixture driver").also { session=it }) else AuthResult.InvalidCredentials
    }
    override val orderDetailsRepository=object: OrderDetailsRepository {
        override suspend fun validateRouteOpened(id: OrderId)=DetailsResult.Success(Unit)
        override suspend fun getDetails(id: OrderId)=DetailsResult.Success(OrderDetails(id,0,"Fixture-1","Fixture client","Fixture client",null,null,null,null,null,2,2,1,false,null,OrderContacts(),emptyList()))
    }
    override val workspaceRepository=object: DriverWorkspaceRepository {
        override suspend fun orders(date: String)=DetailsResult.Success(listOf(DriverOrder(id,97,"Fixture driver",date,"Fixture-1","Fixture client",null,0,2,2,1,false,null,OrderContacts())))
        override suspend fun directory()=DetailsResult.Success(PhoneDirectory(null,null,emptyList(),emptyList()))
        override suspend fun summary(date: String)=DetailsResult.Success(NotificationSummary(1,0,0,emptyList()))
        override suspend fun deliver(id: OrderId)=DetailsResult.Success(DeliveryReceipt(DeliveryStatus.DELIVERED,"fixture-time"))
    }
}

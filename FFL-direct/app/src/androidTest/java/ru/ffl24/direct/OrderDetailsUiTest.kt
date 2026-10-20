package ru.ffl24.direct

import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ffl24.direct.data.mysql.AddressCandidate
import ru.ffl24.direct.data.mysql.OrderContactResolver
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.navigation.*
import ru.ffl24.direct.presentation.OrderDetailsUiState
import ru.ffl24.direct.ui.OrderDetailsContent

/** Controlled local data. No driver login or live SQL is claimed by these UI checks. */
@RunWith(AndroidJUnit4::class)
class OrderDetailsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ambiguousAddressShowsManualRouteWithoutCandidateContacts() {
        val contacts = OrderContactResolver.resolve(listOf(
            AddressCandidate(1, address = "Ложный адрес", clientPhone = "+70000000000"),
            AddressCandidate(2, address = "Ложный адрес", clientPhone = "+70000000001")
        ), "Ложный адрес")
        compose.setContent { MaterialTheme { OrderDetailsContent(OrderDetailsUiState.Content(details(contacts).copy(rawDeliveryNote = "Комментарий +70000000099")), {}, {}) } }
        compose.onNodeWithTag("ambiguous_address").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ложный адрес", substring = false).assertDoesNotExist()
        compose.onNodeWithText("+70000000000").assertDoesNotExist()
        compose.onNodeWithContentDescription("Номера из примечания").performScrollTo().performClick()
        compose.onNodeWithText("+70000000099").assertIsDisplayed()
        compose.onNodeWithText("Копировать номера").performClick()
        compose.onNodeWithTag("route").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Ручной маршрут").assertExists()
    }

    @Test fun manualDialogOffersBothYandexAppsAndCopyWithoutInventingDestination() {
        val selected = AtomicReference<YandexApp>()
        compose.setContent { MaterialTheme { OrderDetailsContent(OrderDetailsUiState.Content(details()), {}, selected::set) } }
        compose.onNodeWithTag("route").performScrollTo().performClick()
        compose.onNodeWithText("Скопировать исходный текст").assertIsDisplayed()
        compose.onNodeWithText("Яндекс Карты").assertIsDisplayed()
        compose.onNodeWithText("Яндекс Навигатор").performClick()
        compose.runOnIdle { assertEquals(YandexApp.NAVIGATOR, selected.get()) }
    }

    @Test fun secondTableAndHistoryAndOriginalCommentAreRendered() {
        val d = details().copy(previousDrivers = listOf(PreviousDriver(98, "Исторический водитель", null, null,
            "2026-05-17", OrderId(OrderSource.REYS_DEN, 11), "Предыдущий заказ")))
        compose.setContent { MaterialTheme { OrderDetailsContent(OrderDetailsUiState.Content(d), {}, {}) } }
        compose.onNodeWithText("Второй рейс").assertExists()
        compose.onNodeWithText("Комментарий +70000000000").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Исторический водитель").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("17.05.2026").assertExists()
        compose.onNodeWithText("Исторический водитель").performClick()
        compose.onNodeWithText("Предыдущий заказ").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun yandexSpecificLinkDoesNotOfferIncompatibleApplication() {
        val contacts = OrderContacts(routeUrl = "yandexmaps://maps.yandex.ru/route")
        compose.setContent { MaterialTheme { OrderDetailsContent(OrderDetailsUiState.Content(details(contacts)), {}, {}) } }
        compose.onNodeWithTag("route").performScrollTo().performClick()
        compose.onNodeWithText("Яндекс Карты").assertIsDisplayed()
        compose.onNodeWithText("Яндекс Навигатор").assertDoesNotExist()
    }

    @Test fun actualAndroidIntentsKeepPackageSchemesAndUseDialRatherThanCall() {
        val route = OrderExternalActions.routeIntent("HTTPS://example.invalid/route", YandexApp.MAPS)!!
        assertEquals(Intent.ACTION_VIEW, route.action)
        assertEquals("https", route.data!!.scheme)
        assertEquals("ru.yandex.yandexmaps", route.`package`)
        assertNull(OrderExternalActions.routeIntent("yandexnavi://route", YandexApp.MAPS))
        val manual = OrderExternalActions.routeIntent(null, YandexApp.NAVIGATOR)!!
        assertEquals(Intent.ACTION_MAIN, manual.action)
        assertNull(manual.data)
        val dial = OrderExternalActions.dialIntent("+7 (000) 000-00-00")!!
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("+70000000000", dial.data!!.schemeSpecificPart)
        assertNull(OrderExternalActions.dialIntent("*100#"))
    }

    @Test fun unicodeTimeParsingRunsOnArt() {
        val text = OrderTextParsing.parse("Клиент Москва", "С\u00a012\u0085ч, комментарий")
        assertEquals("С 12 ч", text.deliveryTime)
        assertEquals("комментарий", text.comment)
        assertEquals("11ч", OrderTextParsing.parse("Клиент", "11ч\u0301").deliveryTime)
        assertEquals("11ч", OrderTextParsing.parse("Клиент", "11ч\u203f").deliveryTime)
    }

    private fun details(contacts: OrderContacts = OrderContacts()) = OrderDetails(
        OrderId(OrderSource.REYS_DEN, 10), 0, "Тестовый заказ", "Клиент", "Клиент Москва", "Клиент Москва",
        "11:30 Комментарий +70000000000", "11:30 Комментарий +70000000000", "11:30", "Комментарий +70000000000",
        3, 2, 1, false, null, contacts, emptyList())
}

package ru.ffl24.direct

import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.navigation.*

class OrderIdentityAndActionsTest {
    @Test fun preservesSourceAndLongIdsAndRejectsInjectionAndOverflow() {
        assertNotEquals(OrderId.parse("reys:1"), OrderId.parse("reys_den:1"))
        assertEquals("reys_den:9223372036854775807", OrderId.parse("reys_den:${Long.MAX_VALUE}")!!.wire)
        listOf("", "reys", "reys:0", "reys:-1", "reys:9223372036854775808", "reys:1 OR 1=1",
            "reys;DROP:1", "other:1", "reys:1:2", "REYS:1", "reys:1.0").forEach { assertNull(it, OrderId.parse(it)) }
    }
    @Test fun routesOnlyDispatchSafeAllowlistedUrisAndCompatibleYandexApp() {
        listOf("https://example.invalid/x", "HTTP://example.invalid/x", "yandexmaps://maps.yandex.ru/x", "yandexnavi://route?x=1")
            .forEach { assertEquals(it, RouteLinks.safeUrl(it)) }
        listOf("javascript:alert(1)", "intent://route", "file:///etc/hosts", "https://", "https://user:pass@example.invalid", "https://a b/x", "https://example.invalid/\n")
            .forEach { assertNull(it, RouteLinks.safeUrl(it)) }
        assertFalse(RouteLinks.supports("yandexmaps://maps.yandex.ru/x", YandexApp.NAVIGATOR))
        assertFalse(RouteLinks.supports("yandexnavi://route", YandexApp.MAPS))
        assertTrue(RouteLinks.supports("https://example.invalid/x", YandexApp.NAVIGATOR))
    }
    @Test fun doesNotGuessAPhoneFromMultipleNumbersOrExecuteServiceCodes() {
        assertEquals("+70000000000", PhoneActions.dialNumber("+7 (000) 000-00-00"))
        listOf("+70000000000, +70000000001", "*100#", "tel:+70000000000", "Клиент: +70000000000", "1234", "", "+70000000000;123")
            .forEach { assertNull(it, PhoneActions.dialNumber(it)) }
    }
}

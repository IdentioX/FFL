package ru.ffl24.direct
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.ContactParsing
class ContactParsingTest {
    @Test fun rawNumbersPreservedAndDatesTimesExcluded() {
        assertEquals(listOf("+7 (000) 000-00-00"),ContactParsing.phones("Позвонить +7 (000) 000-00-00 утром"))
        assertTrue(ContactParsing.phones("2026-05-18 в 11:30").isEmpty())
        assertTrue(ContactParsing.phones(null).isEmpty())
        assertEquals(listOf("+70000000000","+70000000001"),ContactParsing.phones("+70000000000\n+70000000001"))
    }
}

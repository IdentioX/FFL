package ru.ffl24.direct.data.mysql

import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*

class OrderResolverContextTest {
    @Test fun sameOrderNumberCandidatesAreRescoredForEachClientText() {
        val candidates = listOf(AddressCandidate(1, address = "Тверская 1"), AddressCandidate(2, address = "Лесная 2"))
        assertEquals("Тверская 1", OrderContactResolver.resolve(candidates, "Клиент Тверская 1").address)
        assertEquals("Лесная 2", OrderContactResolver.resolve(candidates, "Клиент Лесная 2").address)
        assertEquals(AddressSource.AMBIGUOUS, OrderContactResolver.resolve(candidates, "Неизвестно").addressSource)
    }
    @Test fun closePositiveScoresRemainAmbiguous() {
        // Full one-token match 170 versus three distinct token matches 150: gap 20 < 40.
        val candidates = listOf(AddressCandidate(1, address = "Тверская"),
            AddressCandidate(99, address = "альфа бета гамма отсутствует"))
        val client = "Тверская альфа бета гамма"
        assertEquals(170, OrderContactResolver.score(candidates[0], client).first)
        assertEquals(150, OrderContactResolver.score(candidates[1], client).first)
        assertEquals(AddressSource.AMBIGUOUS, OrderContactResolver.resolve(candidates, client).addressSource)
    }
}

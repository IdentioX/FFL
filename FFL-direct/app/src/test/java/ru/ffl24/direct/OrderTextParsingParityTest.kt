package ru.ffl24.direct

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import ru.ffl24.direct.domain.OrderTextParsing

@RunWith(Parameterized::class)
class OrderTextParsingParityTest(private val index: Int) {
    @Test fun matchesBackendTitleTimeAndComment() {
        val p = "text.$index"
        val parsed = OrderTextParsing.parse(P5Golden.value("$p.client"), P5Golden.value("$p.note"))
        assertEquals(P5Golden.value("$p.expected.title"), parsed.title)
        assertEquals(P5Golden.value("$p.expected.deliveryTime"), parsed.deliveryTime)
        assertEquals(P5Golden.value("$p.expected.comment"), parsed.comment)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "backend text {0}")
        fun cases() = (0 until P5Golden.count("text.count")).map { arrayOf<Any>(it) }
    }
}

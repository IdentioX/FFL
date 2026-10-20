package ru.ffl24.direct.data.mysql

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import ru.ffl24.direct.P5Golden as G
import ru.ffl24.direct.domain.AddressSource

@RunWith(Parameterized::class)
class OrderContactResolverParityTest(private val index: Int, private val name: String) {
    @Test fun matchesUnchangedBackendPureFunction() {
        val p = "resolver.$index"
        val candidates = List(G.count("$p.count")) { i ->
            val q = "$p.candidate.$i"
            AddressCandidate(G.value("$q.id")!!.toLong(), G.value("$q.ooo"), G.value("$q.adress"), G.value("$q.link"),
                G.value("$q.tell_klient"), G.value("$q.tell_gruzchik"), G.value("$q.manager_name"),
                G.value("$q.manager_phone"), G.value("$q.manager_short_phone"))
        }
        val actual = OrderContactResolver.resolve(candidates, G.value("$p.client"))
        val fields = mapOf("address" to actual.address, "routeText" to actual.routeText, "routeUrl" to actual.routeUrl,
            "routeMode" to actual.routeMode, "routeButtonLabel" to actual.routeButtonLabel,
            "addressSource" to when (actual.addressSource) {
                AddressSource.MENEGER_CONTR_FFL -> "meneger_contr_ffl"
                AddressSource.NOT_FOUND -> "not_found"
                AddressSource.AMBIGUOUS -> "ambiguous"
            }, "addressMatchConfidence" to actual.confidence.name.lowercase(Locale.ROOT),
            "clientPhone" to actual.clientPhone, "loaderPhone" to actual.loaderPhone,
            "managerName" to actual.managerName, "managerPhone" to actual.managerPhone)
        fields.forEach { (field, value) -> assertEquals("$name $field", G.value("$p.expected.$field"), value) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}: {1}")
        fun cases() = (0 until G.count("resolver.count")).map { arrayOf<Any>(it, G.value("resolver.$it.name")!!) }
    }
}

package ru.ffl24.direct.data.mysql

import java.util.Locale
import ru.ffl24.direct.domain.AddressSource
import ru.ffl24.direct.domain.MatchConfidence
import ru.ffl24.direct.domain.OrderContacts
import ru.ffl24.direct.domain.normalizeSpaces

internal data class AddressCandidate(
    val id: Long, val organization: String? = null, val address: String? = null, val link: String? = null,
    val clientPhone: String? = null, val loaderPhone: String? = null,
    val managerName: String? = null, val managerPhone: String? = null, val managerShortPhone: String? = null
) {
    override fun toString() = "AddressCandidate(redacted)"
}

internal object OrderContactResolver {
    private val punctuation = Regex("[\"'`«»“”„.,;:()\\[\\]{}]+")
    private val separators = Regex("[-/\\\\]+")
    private val stopwords = setOf("г", "город", "москва", "обл", "область", "ул", "улица", "д", "дом",
        "корп", "корпус", "стр", "строение", "пр", "проспект", "ш", "шоссе", "наб", "набережная", "пер", "переулок", "офис")

    fun normalize(value: String?): String = normalizeSpaces(separators.replace(
        punctuation.replace(normalizeSpaces(value).orEmpty().lowercase(Locale.ROOT).replace('ё', 'е').replace('№', ' '), " "), " ")
    ).orEmpty()

    fun score(candidate: AddressCandidate, client: String?): Pair<Int, MatchConfidence> {
        val order = normalize(client)
        if (order.isEmpty()) return 0 to MatchConfidence.NONE
        val address = normalize(candidate.address)
        val combined = normalize("${candidate.organization.orEmpty()} ${candidate.address.orEmpty()}")
        var score = 0
        var confidence = MatchConfidence.NONE
        if (address.isNotEmpty() && address in order) { score = 120; confidence = MatchConfidence.HIGH }
        else if (combined.isNotEmpty() && combined in order) { score = 100; confidence = MatchConfidence.HIGH }
        val tokens = address.split(' ').toSet().filter {
            it.codePointCount(0, it.length) >= 4 && !pythonDigits(it) && it !in stopwords
        }
        val matches = tokens.count { it in order } // includes exact tokens and substring matches, as backend
        if (matches > 0) {
            score += minOf(matches, 3) * 50
            if (confidence == MatchConfidence.NONE) confidence = MatchConfidence.MEDIUM
        }
        return score to confidence
    }

    fun resolve(candidates: List<AddressCandidate>, client: String?): OrderContacts {
        if (candidates.isEmpty()) return OrderContacts()
        if (candidates.size == 1) return selected(candidates.single(), MatchConfidence.HIGH)
        val scored = candidates.map { val (score, confidence) = score(it, client); Triple(it, score, confidence) }
            .sortedWith(compareByDescending<Triple<AddressCandidate, Int, MatchConfidence>> { it.second }.thenByDescending { it.first.id })
        val top = scored.first()
        val second = scored[1].second
        if (top.second <= 0 || scored.count { it.second == top.second } > 1 ||
            (second > 0 && top.second - second < 40) || top.third == MatchConfidence.NONE) {
            return OrderContacts(addressSource = AddressSource.AMBIGUOUS)
        }
        return selected(top.first, top.third)
    }

    private fun selected(c: AddressCandidate, confidence: MatchConfidence): OrderContacts {
        val address = normalizeSpaces(c.address)
        // Preserve backend prefix contract here; stricter URI checks belong at Intent dispatch.
        val url = normalizeSpaces(c.link)?.takeIf { value ->
            listOf("http://", "https://", "yandexmaps://", "yandexnavi://").any { value.startsWith(it, ignoreCase = true) }
        }
        return OrderContacts(address, if (url == null) null else address, url, AddressSource.MENEGER_CONTR_FFL,
            confidence, normalizeSpaces(c.clientPhone), normalizeSpaces(c.loaderPhone), normalizeSpaces(c.managerName),
            normalizeSpaces(c.managerPhone) ?: normalizeSpaces(c.managerShortPhone))
    }

    private fun pythonDigits(value: String): Boolean = value.codePoints().toArray().all {
        Character.isDigit(it) || Character.getType(it) == Character.OTHER_NUMBER.toInt() && Character.getNumericValue(it) in 0..9
    }
}

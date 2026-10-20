package ru.ffl24.direct.domain

import java.util.Locale
import java.util.regex.Pattern

/** Python str.split whitespace semantics, including NBSP and NEL. */
internal fun legacyWhitespace(c: Char) = c.isWhitespace() || c == '\u0085'
internal fun normalizeSpaces(value: String?): String? {
    if (value == null) return null
    val result = StringBuilder()
    var separator = false
    value.forEach { c ->
        if (legacyWhitespace(c)) separator = result.isNotEmpty()
        else {
            if (separator) result.append(' ')
            result.append(c)
            separator = false
        }
    }
    return result.toString().takeIf { it.isNotEmpty() }
}
internal fun orderNumber(value: String?) = value?.trim(::legacyWhitespace)?.takeIf { it.isNotEmpty() } ?: "Без номера"
internal fun firstCodePoints(value: String, count: Int): String =
    value.substring(0, value.offsetByCodePoints(0, minOf(count, value.codePointCount(0, value.length))))

data class ParsedOrderText(val title: String, val deliveryTime: String?, val comment: String?) {
    override fun toString() = "ParsedOrderText(redacted)"
}

object OrderTextParsing {
    private val markers = listOf("Москва", "г.Москва", "г. Москва", "Московская обл", "МО,", "ул.",
        "улица", "пр-т", "проспект", "шоссе", "ш.", "наб.", "набережная", "пер.", "переулок", "дом",
        "д.", "стр.", "строение", "корп.", "корпус")
    private val time = Pattern.compile(
        // Python \w is letters/numbers/underscore, unlike Java's Unicode \w (which includes marks).
        // Spaces are already normalized; explicit Nd and boundaries also avoid Android's unsupported flag256.
        "(?<![\\p{L}\\p{N}_])(?:с\\s*)?\\p{Nd}{1,2}(?::\\p{Nd}{2}|(?:\\s*-\\s*\\p{Nd}{1,2})?\\s*ч)(?![\\p{L}\\p{N}_])",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    fun parse(client: String?, note: String?): ParsedOrderText {
        val text = normalizeSpaces(client).orEmpty()
        val planned = normalizeSpaces(note).orEmpty()
        val lower = text.lowercase(Locale.ROOT)
        val first = markers.map { lower.indexOf(it.lowercase(Locale.ROOT)) }.filter { it >= 0 }.minOrNull()
        // Count code points when converting Python lower-string indices back to the original text.
        val prefix = first?.let { index -> firstCodePoints(text, lower.codePointCount(0, index)).trim(::legacyWhitespace) }
        val title = prefix?.takeIf { it.isNotEmpty() } ?: firstCodePoints(text, 80)
        val match = time.matcher(planned)
        return if (!match.find()) ParsedOrderText(title, null, planned.takeIf { it.isNotEmpty() }) else {
            val comment = normalizeSpaces(planned.substring(0, match.start()) + " " + planned.substring(match.end()))
                ?.trim(' ', ',', '.', ';', ':', '-')?.takeIf { it.isNotEmpty() }
            ParsedOrderText(title, match.group().trim(::legacyWhitespace), comment)
        }
    }
}

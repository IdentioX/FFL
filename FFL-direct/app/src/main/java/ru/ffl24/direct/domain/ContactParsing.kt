package ru.ffl24.direct.domain

/** Suggestions only: raw comment is retained and the user explicitly chooses copy/dial. */
object ContactParsing {
    private val candidate = Regex("(?<![\\p{L}\\p{N}])\\+?[0-9][0-9() \\u00a0-]{7,48}[0-9](?![\\p{L}\\p{N}])")
    fun phones(text: String?): List<String> = candidate.findAll(text.orEmpty()).map { it.value.trim() }
        .filter { it.count(Char::isDigit) in 10..15 && !it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) }
        .distinct().take(10).toList()
}

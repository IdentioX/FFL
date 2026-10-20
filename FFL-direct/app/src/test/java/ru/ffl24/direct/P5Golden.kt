package ru.ffl24.direct

import java.util.Base64
import java.util.Properties

object P5Golden {
    private val data = Properties().apply {
        P5Golden::class.java.getResourceAsStream("/p5-golden.properties")!!.use { load(it) }
    }
    fun value(key: String): String? = data.getProperty(key)?.let {
        if (it == "~") null else String(Base64.getDecoder().decode(it), Charsets.UTF_8)
    }
    fun count(key: String) = value(key)!!.toInt()
}

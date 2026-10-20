package ru.ffl24.direct.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.util.Locale
import ru.ffl24.direct.domain.normalizeSpaces

enum class YandexApp(val packageName: String) {
    MAPS("ru.yandex.yandexmaps"), NAVIGATOR("ru.yandex.yandexnavi")
}

/** A prefix accepted by the legacy resolver is not sufficient for dispatching an Intent. */
object RouteLinks {
    fun safeUrl(value: String?): String? {
        if (value == null || value.any { it.isISOControl() || it.isWhitespace() }) return null
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (uri.rawUserInfo != null || uri.rawAuthority.isNullOrBlank()) null
            else when (scheme) {
                "http", "https" -> value.takeIf { !uri.host.isNullOrBlank() }
                "yandexmaps", "yandexnavi" -> value
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun supports(url: String, app: YandexApp): Boolean = when (URI(url).scheme.lowercase(Locale.ROOT)) {
        "yandexmaps" -> app == YandexApp.MAPS
        "yandexnavi" -> app == YandexApp.NAVIGATOR
        "http", "https" -> true
        else -> false
    }
}

object PhoneActions {
    /** One conservative dial target. Multiple/labelled strings remain displayable and copyable. */
    fun dialNumber(raw: String?): String? {
        val normalized = normalizeSpaces(raw) ?: return null
        if (!normalized.matches(Regex("\\+?[0-9][0-9 ()-]*"))) return null
        val digits = normalized.filter { it in '0'..'9' }
        if (digits.length !in 5..15) return null
        return (if (normalized.startsWith('+')) "+" else "") + digits
    }
}

object OrderExternalActions {
    fun openRoute(context: Context, url: String?, app: YandexApp): Boolean {
        val intent = routeIntent(url, app) ?: return false
        return launch(context, intent)
    }

    internal fun routeIntent(url: String?, app: YandexApp): Intent? {
        val safe = RouteLinks.safeUrl(url)
        val intent = if (safe == null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            else {
                if (!RouteLinks.supports(safe, app)) return null
                Intent(Intent.ACTION_VIEW, Uri.parse(safe).normalizeScheme())
            }
        return intent.setPackage(app.packageName)
    }

    fun dial(context: Context, raw: String): Boolean {
        val intent = dialIntent(raw) ?: return false
        return launch(context, intent)
    }

    internal fun dialIntent(raw: String): Intent? {
        val number = PhoneActions.dialNumber(raw) ?: return null
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
}

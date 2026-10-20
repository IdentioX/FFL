package ru.ffl24.direct.config

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ffl24.direct.*
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.session.DataStoreDriverSessionStore
import ru.ffl24.direct.notifications.SecondRouteNotifications
import java.security.MessageDigest

/** Sole production owner of configuration. UI and Worker observe the same graph. */
class RuntimeProfiles(private val context: Context) {
    private val store = ConnectionProfileStore(context)
    private val edits = Mutex()
    private var settings: DirectMySqlSettings
    val graph: MutableStateFlow<AppGraph>
    init {
        // A corrupt encrypted profile fails closed; never silently fall back to another server.
        settings = try { store.read() ?: AppGraph.buildSettings() } catch (_: Exception) { DirectMySqlSettings() }
        graph = MutableStateFlow(create(settings))
    }
    fun hasSavedProfile() = store.exists()
    fun currentSettings(): DirectMySqlSettings = settings
    private fun create(value: DirectMySqlSettings) = AppGraph.create(BuildConfig.DATA_SOURCE_MODE,value,DataStoreDriverSessionStore(context,scope(value))).also { it.profileScope = scope(value) }
    suspend fun check(value: DirectMySqlSettings): ConnectionProbeResult = MySqlConnectionProbe(DirectMySqlConfig.parse(value)).check()
    suspend fun apply(value: DirectMySqlSettings): String? = edits.withLock {
        val checked = check(value)
        if (checked is ConnectionProbeResult.Failed) return@withLock checked.error.safeMessage()
        withContext(Dispatchers.IO + NonCancellable) {
            try { store.save(value) } catch (_: Exception) { return@withContext "Не удалось безопасно сохранить настройки. Действующий профиль сохранён." }
            val old = graph.value
            old.sessions.invalidate()
            SecondRouteNotifications.cancel(context)
            old.retire()
            // Wait for prior session publication/cleanup before exposing the next graph.
            old.authRepository.logout()
            settings = value
            graph.value = create(value)
            null
        }
    }
    companion object {
        internal fun scope(value: DirectMySqlSettings): String {
            if (DirectMySqlConfig.parse(value) !is MySqlConfiguration.Ready) return ""
            // Password rotation keeps local ordering; endpoint/account/CA changes isolate it.
            val identity = listOf(value.host.lowercase(),value.port,value.database,value.user,value.caPem.trim().replace("\r\n","\n")).joinToString("\u0000")
            return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString(""){"%02x".format(it)}
        }
    }
}

fun DirectDbError.safeMessage(): String = when(this) {
    DirectDbError.CONFIGURATION_MISSING,DirectDbError.CONFIGURATION_INVALID -> "Проверьте адрес, порт, базу, пользователя, пароль и CA-сертификат. TLS обязателен; root запрещён."
    DirectDbError.ACCOUNT_REJECTED -> "Сервер отклонил логин или пароль."
    DirectDbError.DATABASE_NOT_FOUND -> "База данных не существует или недоступна пользователю."
    DirectDbError.TLS_VERIFICATION -> "Не удалось проверить TLS-сертификат сервера. Проверьте CA и имя сервера."
    DirectDbError.TIMEOUT -> "Сервер не ответил вовремя. Проверьте сеть и доступность сервера."
    DirectDbError.UNAVAILABLE -> "Сервер недоступен. Проверьте сеть, адрес и порт."
    else -> "Не удалось выполнить проверку базы данных. Обратитесь к администратору."
}

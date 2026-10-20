package ru.ffl24.direct.local

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ffl24.direct.domain.OrderSection
import ru.ffl24.direct.domain.OrderId
import ru.ffl24.direct.domain.PollingChanges

/** Independent Direct storage. No passwords, tokens, addresses or phone directory are stored here. */
class WorkspacePreferences(context: Context, scope: String = (context.applicationContext as? ru.ffl24.direct.DirectApplication)?.graph?.profileScope.orEmpty()) {
    // UI and Worker construct different instances for the same preference file.
    private companion object { val edits = Mutex() }
    private val prefs = context.getSharedPreferences("ffl_direct_workspace_v1" + if(scope.isEmpty()) "" else "_$scope", Context.MODE_PRIVATE)
    fun date(): String = prefs.getString("date", null)?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: LocalDate.now().toString()
    fun dark() = prefs.getBoolean("dark", false)
    fun notifications() = prefs.getBoolean("notifications", false)
    fun savedOrder(driver: Long, date: String, section: OrderSection): List<String> =
        prefs.getString("order:$driver:$date:${section.name}", "").orEmpty().split('|').filter { it.isNotBlank() }
    suspend fun order(driver: Long, date: String, section: OrderSection, ids: List<String>) = edit {
        putString("order:$driver:$date:${section.name}", ids.joinToString("|"))
    }
    suspend fun setDate(date: String?) = edit {
        if (date == null) remove("date") else putString("date", LocalDate.parse(date).toString())
    }
    suspend fun setDark(dark: Boolean) = edit { putBoolean("dark", dark) }
    suspend fun setNotifications(enabled: Boolean) = edit { putBoolean("notifications", enabled) }
    fun scroll(driver: Long, date: String, section: Int): Pair<Int,Int> {
        val key="scroll:$driver:$date:$section"
        return prefs.getInt("$key:index",0) to prefs.getInt("$key:offset",0)
    }
    suspend fun scroll(driver: Long,date: String,section: Int,index: Int,offset: Int) = edit {
        val key="scroll:$driver:$date:$section";putInt("$key:index",index);putInt("$key:offset",offset)
    }
    fun seen(scope: String): Set<String>? = prefs.getStringSet("seen:$scope", null)?.toSet()
    suspend fun observeSecondRoute(scope: String, ids: List<OrderId>): List<OrderId> = edits.withLock {
        withContext(Dispatchers.IO) {
            val previous=seen(scope)
            val fresh=PollingChanges.newIds(previous,ids)
            val next=(previous.orEmpty()+ids.map { it.wire }).toSet()
            check(prefs.edit().apply {
                prefs.all.keys.filter { it.startsWith("seen:") && it!="seen:$scope" }.forEach { remove(it) }
                putStringSet("seen:$scope",next)
            }.commit()) { "Local storage unavailable" }
            fresh
        }
    }
    suspend fun seen(scope: String, ids: Set<String>) = edit {
        // Retain one driver/date baseline, keeping storage bounded across date/driver changes.
        prefs.all.keys.filter { it.startsWith("seen:") && it != "seen:$scope" }.forEach { remove(it) }
        putStringSet("seen:$scope", ids)
    }
    suspend fun clearSeen() = edit { prefs.all.keys.filter { it.startsWith("seen:") }.forEach { remove(it) } }
    private suspend fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) = edits.withLock { withContext(Dispatchers.IO) {
        check(prefs.edit().apply(block).commit()) { "Local storage unavailable" }
    } }
}

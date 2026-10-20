package ru.ffl24.direct

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import ru.ffl24.direct.data.mysql.*

/** Private config and reserved fictional records, never a production endpoint. */
internal class OwnedTestDatabase {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val config: JSONObject
    val restored: Boolean
    init {
        val file = java.io.File(context.filesDir, "completion-db.json")
        assumeTrue("Owned local database configuration is required", file.exists())
        config = JSONObject(file.readText())
        val marker = config.optString("fixtureMarker")
        restored = marker == "ffl-direct-private-restored-dump"
        assumeTrue("Only owned local profiles are permitted", restored || marker == "ffl-direct-owned-fixture-20261017")
        val active = (context.applicationContext as DirectApplication).profiles.currentSettings()
        // Both legacy private BuildConfig fixtures and runtime profiles use the same production owner.
        // This is an assertion, not a skip when the intended profile is missing or wrong.
        assertTrue(BuildConfig.DATA_SOURCE_MODE == "DIRECT_MYSQL")
        assertTrue(active.host == "localhost" && config.getString("host") == "localhost")
        assertTrue(active.port == config.getString("port"))
        assertTrue(active.database == config.getString("database"))
        assertTrue(active.user == config.getString("user"))
        assertTrue(active.password == config.getString("password"))
        assertTrue(active.caPem.trim().replace("\r\n","\n") == config.getString("caPem").trim().replace("\r\n","\n"))
        if (restored) assertTrue(config.getJSONObject("testScope").getBoolean("restored_schema"))
    }
    private val scope get() = config.optJSONObject("testScope")
    val driver get() = scope?.getLong("driver") ?: 97L
    val disabled get() = scope?.getLong("disabled") ?: 98L
    val order get() = scope?.getLong("order") ?: 1L
    val foreign get() = scope?.getLong("foreign") ?: 3L
    val notification get() = scope?.getLong("notification") ?: 4L
    val paired get() = scope?.getLong("paired") ?: 2000000020L
    val date get() = scope?.getString("date") ?: "2026-05-18"
    val password get() = scope?.getString("password") ?: "driver-fixture-only"
    val address get() = if (restored) "FFL controlled address" else "Synthetic test address"
    val settings get() = DirectMySqlSettings(config.getString("host"), config.getString("port"), config.getString("database"), config.getString("user"), config.getString("password"), "true", config.getString("caPem"))
    val jdbc get() = (DirectMySqlConfig.parse(settings) as MySqlConfiguration.Ready).config
    // Verification-only reader: injected privately at test time, never BuildConfig.
    val verificationJdbc get(): DirectMySqlConfig {
        val audit=config.optJSONObject("verification") ?: return jdbc
        assertTrue(audit.getString("purpose")=="integrity-audit")
        assertTrue(audit.getString("host")=="localhost" && audit.getString("port")==config.getString("port"))
        assertTrue(audit.getString("database")==config.getString("database"))
        return (DirectMySqlConfig.parse(DirectMySqlSettings(audit.getString("host"),audit.getString("port"),
            audit.getString("database"),audit.getString("user"),audit.getString("password"),"true",audit.getString("caPem"))) as MySqlConfiguration.Ready).config
    }
    override fun toString() = "OwnedTestDatabase(redacted)"
}

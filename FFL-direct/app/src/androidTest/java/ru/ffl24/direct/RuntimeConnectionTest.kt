package ru.ffl24.direct

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.config.*
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences
import java.io.File

/** Private, runtime-injected local fixtures. No credentials are compiled into either APK. */
class RuntimeConnectionTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun config(index:Int):DirectMySqlSettings {
        val json=JSONObject(File(context.filesDir,"runtime-test-$index.json").readText())
        check(json.getString("host")=="localhost"&&json.getString("fixtureMarker").startsWith("ffl-direct-"))
        return DirectMySqlSettings("localhost",json.getString("port"),json.getString("database"),json.getString("user"),json.getString("password"),"true",json.getString("caPem"))
    }
    private fun change(c:DirectMySqlSettings,host:String=c.host,port:String=c.port,database:String=c.database,user:String=c.user,password:String=c.password,ca:String=c.caPem)=DirectMySqlSettings(host,port,database,user,password,"true",ca)
    @Test fun actualMysqlSelectAndSafeFailureCategories()=runBlocking {
        val c=config(1);val manager=RuntimeProfiles(context)
        assertEquals(ConnectionProbeResult.Reachable,manager.check(c))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.CONFIGURATION_INVALID),manager.check(change(c,port="0")))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE),manager.check(change(c,port="1")))
        // DNS can return NXDOMAIN or time out when the emulator resolver has no upstream.
        val dnsFailure=manager.check(change(c,host="ffl-unresolvable.invalid"))
        assertTrue(dnsFailure==ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE)||dnsFailure==ConnectionProbeResult.Failed(DirectDbError.TIMEOUT))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.ACCOUNT_REJECTED),manager.check(change(c,user="ffl_nonexistent_test_user")))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.ACCOUNT_REJECTED),manager.check(change(c,password="intentionally-invalid-test-password")))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.DATABASE_NOT_FOUND),manager.check(change(c,database="ffl_nonexistent_test_database")))
        val wrongCA=InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-ca.pem").bufferedReader().use{it.readText()}
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TLS_VERIFICATION),manager.check(change(c,ca=wrongCA)))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TLS_VERIFICATION),manager.check(change(c,host="127.0.0.1")))
        val unreachable=manager.check(change(c,host="192.0.2.1"))
        assertTrue(unreachable==ConnectionProbeResult.Failed(DirectDbError.TIMEOUT)||unreachable==ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE))
        manager.graph.value.retire()
    }
    @Test fun switchingProfilesInvalidatesSessionClosesOldGraphAndIsolatesOrder()=runBlocking {
        val manager=RuntimeProfiles(context);val one=config(1);val two=config(2)
        assertNull(manager.apply(one));val old=manager.graph.value
        assertTrue(old.authRepository.login(97,"driver-fixture-only".toCharArray()) is AuthResult.Success)
        val prefs=WorkspacePreferences(context,old.profileScope)
        prefs.setDate("2026-05-18");prefs.order(97,"2026-05-18",OrderSection.FIRST,listOf("reys:1"))
        assertNull(manager.apply(two));val current=manager.graph.value
        assertNotSame(old,current);assertTrue(old.profileScope!=current.profileScope)
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE),old.connectionProbe.check())
        assertTrue(old.sessions.current().session==null)
        assertEquals(AuthResult.Success<DriverSession?>(null),current.authRepository.restore())
        assertTrue(WorkspacePreferences(context,current.profileScope).savedOrder(97,"2026-05-18",OrderSection.FIRST).isEmpty())
        assertTrue(current.authRepository.login(2000000001,"ffl-e2e-only-42".toCharArray()) is AuthResult.Success)
        assertTrue(current.workspaceRepository.orders("2099-12-31") is DetailsResult.Success)
        assertNull(manager.apply(one))
        assertEquals(listOf("reys:1"),WorkspacePreferences(context,manager.graph.value.profileScope).savedOrder(97,"2026-05-18",OrderSection.FIRST))
        assertEquals(AuthResult.Success<DriverSession?>(null),manager.graph.value.authRepository.restore())
        manager.graph.value.retire()
    }
    @Test fun failedSaveKeepsWorkingGraphAndEncryptedProfile()=runBlocking {
        val manager=RuntimeProfiles(context);val c=config(1);assertNull(manager.apply(c));val active=manager.graph.value
        assertNotNull(manager.apply(change(c,password="invalid-runtime-test-password")))
        assertSame(active,manager.graph.value);assertEquals(ConnectionProbeResult.Reachable,active.connectionProbe.check())
        val loaded=requireNotNull(ConnectionProfileStore(context).read())
        assertTrue(loaded.password==c.password&&loaded.port==c.port)
        val bytes=File(context.noBackupFilesDir,"direct-profile-v1.enc").readBytes()
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains(c.password))
        active.retire()
    }
    @Test fun encryptedProfileSurvivesFreshManagerAndDriverMustReauthenticate()=runBlocking {
        val manager=RuntimeProfiles(context);val c=config(2);assertNull(manager.apply(c))
        val restarted=RuntimeProfiles(context)
        assertEquals(RuntimeProfiles.scope(c),restarted.graph.value.profileScope)
        assertEquals(ConnectionProbeResult.Reachable,restarted.graph.value.connectionProbe.check())
        assertEquals(AuthResult.Success<DriverSession?>(null),restarted.graph.value.authRepository.restore())
        manager.graph.value.retire();restarted.graph.value.retire()
    }
    @Test fun actualProcessRestartUsesSavedRuntimeProfile()=runBlocking {
        val app=context.applicationContext as DirectApplication
        assertTrue(BuildConfig.DIRECT_MYSQL_HOST.isEmpty())
        assertTrue(app.profiles.hasSavedProfile())
        assertTrue(DirectMySqlConfig.parse(app.profiles.currentSettings()) is MySqlConfiguration.Ready)
        assertEquals(ConnectionProbeResult.Reachable,app.graph.connectionProbe.check())
    }
    @Test fun cancelledProbeDoesNotPublishOrReplaceConfiguration()=runBlocking {
        val manager=RuntimeProfiles(context);val current=manager.graph.value
        val ca=InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-ca.pem").bufferedReader().use{it.readText()}
        val job=launch{manager.check(DirectMySqlSettings("localhost","19064","fixture","fixture_read","fixture-only","true",ca));fail("Cancelled probe completed")}
        delay(200);job.cancelAndJoin();assertTrue(job.isCancelled);assertSame(current,manager.graph.value)
        current.retire()
    }
}

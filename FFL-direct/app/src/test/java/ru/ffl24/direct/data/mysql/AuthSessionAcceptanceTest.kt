package ru.ffl24.direct.data.mysql

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.data.repository.DirectAuthRepository
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.session.*

class AuthSessionAcceptanceTest {
    @Test fun supersededLoginReturnsASafeFailureInsteadOfStrandingTheUi() = runBlocking {
        val sessions=AuthSessionManager(InMemoryDriverSessionStore())
        val jdbc=ScriptJdbc(query={sql,_->
            if("SELECT posTypeId AS driverId, access" in sql) listOf(mapOf("driverId" to 97L,"access" to "1"))
            else { sessions.invalidate();listOf(mapOf("driverId" to 97L,"driverName" to "Fixture")) }
        })
        val repo=DirectAuthRepository(DirectMySqlDriverDataSource(MySqlReadExecutor(fixtureReady(),JdbcConnector { _,_->jdbc.connection })),sessions)
        assertEquals(AuthResult.Failed(AuthError.DATA_UNAVAILABLE),repo.login(97,"fixture-only".toCharArray()))
        assertNull(sessions.current().session)
    }
    @Test fun activeListNeverProjectsPasswordAndLoginBindsItOnlyAsParameter() = runBlocking {
        val jdbc=ScriptJdbc(query={sql,params -> when {
            "SELECT posTypeId AS driverId, access" in sql -> listOf(mapOf("driverId" to 97L,"access" to "1"))
            "BINARY parol = BINARY ?" in sql -> if(params[2]=="fixture-only") listOf(mapOf("driverId" to 97L,"driverName" to "Fixture")) else emptyList()
            else -> { assertFalse(sql.contains("parol"));assertTrue(sql.contains("access = 1"));listOf(mapOf("driverId" to 97L,"driverName" to "Fixture")) }
        } })
        val data=DirectMySqlDriverDataSource(MySqlReadExecutor(fixtureReady(),JdbcConnector { _,_->jdbc.connection }))
        val repo=DirectAuthRepository(data,AuthSessionManager(InMemoryDriverSessionStore()))
        assertEquals(1,(repo.getDrivers() as AuthResult.Success).value.size)
        val wrong="wrong".toCharArray();assertEquals(AuthResult.InvalidCredentials,repo.login(97,wrong));assertTrue(wrong.all { it=='\u0000' })
        val password="fixture-only".toCharArray();assertTrue(repo.login(97,password) is AuthResult.Success)
        assertTrue(password.all { it=='\u0000' });assertEquals(97L,(repo.checkAccess() as AuthResult.Success).value!!.driverId)
        assertTrue(jdbc.calls.all { !it.sql.contains("__hr_typepos_bf") && it.sql.trim().startsWith("SELECT") })
    }
    @Test fun revokedSessionIsClearedAndCannotBeRestoredOfflineAsAuthenticated() = runBlocking {
        var enabled=true
        val jdbc=ScriptJdbc(query={sql,_ -> if("access FROM" in sql) listOf(mapOf("driverId" to 97L,"access" to if(enabled) "1" else "0"))
            else listOf(mapOf("driverId" to 97L,"driverName" to "Fixture")) })
        val store=InMemoryDriverSessionStore();val sessions=AuthSessionManager(store)
        val repo=DirectAuthRepository(DirectMySqlDriverDataSource(MySqlReadExecutor(fixtureReady(),JdbcConnector { _,_->jdbc.connection })),sessions)
        assertTrue(repo.login(97,"fixture-only".toCharArray()) is AuthResult.Success)
        enabled=false;assertEquals(AuthResult.DriverDisabled,repo.checkAccess())
        assertNull(store.read());assertNull(sessions.current().session)
        assertEquals(AuthResult.Success<DriverSession?>(null),repo.restore())
    }
    @Test fun logoutRemovesLocalSessionAndProtectedAccess() = runBlocking {
        val jdbc=ScriptJdbc(query={sql,_ -> if("access FROM" in sql) listOf(mapOf("driverId" to 97L,"access" to "1")) else listOf(mapOf("driverId" to 97L,"driverName" to "Fixture")) })
        val store=InMemoryDriverSessionStore();val sessions=AuthSessionManager(store)
        val repo=DirectAuthRepository(DirectMySqlDriverDataSource(MySqlReadExecutor(fixtureReady(),JdbcConnector { _,_->jdbc.connection })),sessions)
        assertTrue(repo.login(97,"fixture-only".toCharArray()) is AuthResult.Success)
        assertEquals(AuthResult.Success(Unit),repo.logout());assertNull(store.read());assertNull(sessions.current().session)
    }
}

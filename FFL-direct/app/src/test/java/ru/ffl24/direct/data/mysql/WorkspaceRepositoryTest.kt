package ru.ffl24.direct.data.mysql

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.data.repository.DirectWorkspaceRepository
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.session.*

class WorkspaceRepositoryTest {
    @Test fun concurrentDeliveryIsSerializedAndSecondCallDoesNotUpdateAgain() = runBlocking {
        var updated=false;var writes=0
        val jdbc=ScriptJdbc({sql,_-> if("SELECT access" in sql) listOf(mapOf("access" to "1"))
            else listOf(mapOf("id" to 1L,"st_vv" to if(updated) 1L else 0L,"date_att_log" to "time")) },{_,_->writes++;updated=true;1})
        val sessions=AuthSessionManager(InMemoryDriverSessionStore())
        sessions.publish(DriverSession(97,"Fixture"),sessions.current().revision,false)
        val repo=repository(jdbc,sessions)
        val results=awaitAll(async { repo.deliver(OrderId(OrderSource.REYS,1)) },async { repo.deliver(OrderId(OrderSource.REYS,1)) })
        assertEquals(listOf(DeliveryStatus.DELIVERED,DeliveryStatus.ALREADY_DELIVERED),results.map { (it as DetailsResult.Success).value.status })
        assertEquals(1,writes);assertEquals(2,jdbc.commits)
    }
    @Test fun invalidDateAndMissingSessionCannotOpenConnection() = runBlocking {
        val jdbc=ScriptJdbc(query={_,_->error("No query allowed")})
        val repo=repository(jdbc,AuthSessionManager(InMemoryDriverSessionStore()))
        assertEquals(DetailsResult.Failed(AuthError.DATA_UNAVAILABLE),repo.orders("2026-05-18' OR 1=1"))
        assertEquals(DetailsResult.SessionRequired,repo.directory())
        assertEquals(DetailsResult.SessionRequired,repo.summary("2026-05-18"))
        assertTrue(jdbc.calls.isEmpty());assertFalse(jdbc.closed)
    }
    @Test fun sessionChangedDuringReadDoesNotPublishRows() = runBlocking {
        val sessions=AuthSessionManager(InMemoryDriverSessionStore())
        sessions.publish(DriverSession(97,"Fixture"),sessions.current().revision,false)
        val jdbc=ScriptJdbc(query={sql,_->if("SELECT access" in sql) listOf(mapOf("access" to "1")) else { sessions.invalidate();emptyList() } })
        assertEquals(DetailsResult.SessionRequired,repository(jdbc,sessions).orders("2026-05-18"))
    }
    private fun repository(jdbc: ScriptJdbc,sessions: AuthSessionManager): DirectWorkspaceRepository {
        val connector=JdbcConnector { _,_->jdbc.connection }
        val auth=object: AuthRepository {
            override suspend fun checkAccess(): AuthResult<DriverSession?> = AuthResult.Success(sessions.current().session)
            override suspend fun getDrivers(): AuthResult<List<Driver>> = error("unused")
            override suspend fun login(driverId: Long,password: CharArray): AuthResult<DriverSession> = error("unused")
            override suspend fun restore(): AuthResult<DriverSession?> = error("unused")
            override suspend fun logout(): AuthResult<Unit> = error("unused")
        }
        return DirectWorkspaceRepository(DirectWorkspaceDataSource(MySqlReadExecutor(fixtureReady(),connector)),DirectDeliveryDataSource(fixtureReady(),connector),auth,sessions)
    }
}

package ru.ffl24.direct

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.data.repository.DirectOrderDetailsRepository
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.session.*

class OrderDetailsRepositoryTest {
    private val id = OrderId(OrderSource.REYS_DEN, 123)
    private val identity = DriverSession(97, "Тест")

    @Test fun noSessionCannotReadAnOrderOrRoute() = runBlocking {
        val data = Data()
        val auth = Auth()
        val repo = DirectOrderDetailsRepository(data, auth, AuthSessionManager(InMemoryDriverSessionStore()))
        assertEquals(DetailsResult.SessionRequired, repo.getDetails(id))
        assertEquals(DetailsResult.SessionRequired, repo.validateRouteOpened(id))
        assertEquals(0, auth.calls)
        assertEquals(0, data.calls)
    }

    @Test fun disabledAndNetworkAuthFailuresNeverReadData() = runBlocking {
        for (result in listOf(AuthResult.DriverDisabled, AuthResult.Failed(AuthError.CONNECTION_UNAVAILABLE))) {
            val sessions = active()
            val data = Data()
            val auth = Auth(result)
            val repo = DirectOrderDetailsRepository(data, auth, sessions)
            val actual = repo.getDetails(id)
            assertEquals(if (result == AuthResult.DriverDisabled) DetailsResult.DriverDisabled
                else DetailsResult.Failed(AuthError.CONNECTION_UNAVAILABLE), actual)
            assertEquals(0, data.calls)
        }
    }

    @Test fun loadGetsOnlyCurrentDriverAndPreservesSourceIdentity() = runBlocking {
        val data = Data()
        val auth = Auth()
        val repo = DirectOrderDetailsRepository(data, auth, active())
        val result = repo.getDetails(id) as DetailsResult.Success
        assertEquals(id, result.value.id)
        assertEquals(listOf(97L), data.drivers)
        assertEquals(1, auth.calls)
    }

    @Test fun logoutAndEvenSameDriverReloginDiscardLateRead() = runBlocking {
        for (relogin in listOf(false, true)) {
            val sessions = active()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val data = Data {
                entered.complete(Unit)
                release.await()
                DbResult.Success(DetailsLookup.Found(details()))
            }
            val repo = DirectOrderDetailsRepository(data, Auth(), sessions)
            val pending = async { repo.getDetails(id) }
            entered.await()
            val revision = sessions.invalidate()
            sessions.clear(revision)
            if (relogin) sessions.publish(identity, revision, persist = false)
            release.complete(Unit)
            assertEquals(DetailsResult.SessionRequired, pending.await())
        }
    }

    @Test fun revokedAccessClearsMemoryAndLocalHint() = runBlocking {
        val store = InMemoryDriverSessionStore()
        val sessions = active(store)
        val data = Data { DbResult.Success(DetailsLookup.Disabled) }
        val repo = DirectOrderDetailsRepository(data, Auth(), sessions)
        assertEquals(DetailsResult.DriverDisabled, repo.getDetails(id))
        assertNull(sessions.current().session)
        assertNull(store.read())
    }

    @Test fun storageFailureDuringRevocationDoesNotClaimSuccessfulCleanup() = runBlocking {
        val store = object : DriverSessionStore {
            override suspend fun read() = identity
            override suspend fun save(session: DriverSession) = Unit
            override suspend fun clear() { throw IOException("PRIVATE_STORAGE") }
        }
        val sessions = AuthSessionManager(store)
        sessions.publish(identity, sessions.current().revision, persist = false)
        val repo = DirectOrderDetailsRepository(Data { DbResult.Success(DetailsLookup.Disabled) }, Auth(), sessions)
        assertEquals(DetailsResult.Failed(AuthError.LOCAL_STORAGE_UNAVAILABLE), repo.getDetails(id))
        assertNull(sessions.current().session)
    }

    @Test fun routeValidationUsesOwnerReadAndAuthWithoutLoadingContacts() = runBlocking {
        val data = Data()
        val auth = Auth()
        val repo = DirectOrderDetailsRepository(data, auth, active())
        assertEquals(DetailsResult.Success(Unit), repo.validateRouteOpened(id))
        assertEquals(0, data.calls)
        assertEquals(1, data.ownerCalls)
        assertEquals(1, auth.calls)
    }

    @Test fun jdbcErrorIsSafeAndCallerCancellationPropagates() = runBlocking {
        val repo = DirectOrderDetailsRepository(Data { DbResult.Failed(DirectDbError.TLS_VERIFICATION) }, Auth(), active())
        assertEquals(DetailsResult.Failed(AuthError.TLS_REJECTED), repo.getDetails(id))
        val cancelled = DirectOrderDetailsRepository(Data { throw CancellationException("Cancelled") }, Auth(), active())
        try { cancelled.getDetails(id); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }

    private suspend fun active(store: DriverSessionStore = InMemoryDriverSessionStore()) = AuthSessionManager(store).also {
        it.publish(identity, it.current().revision, persist = true)
    }
    private inner class Auth(private val result: AuthResult<DriverSession?> = AuthResult.Success(identity)) : AuthRepository {
        var calls = 0
        override suspend fun checkAccess(): AuthResult<DriverSession?> { calls++; return result }
        override suspend fun getDrivers(): AuthResult<List<Driver>> = error("Unexpected list")
        override suspend fun login(driverId: Long, password: CharArray): AuthResult<DriverSession> = error("Unexpected login")
        override suspend fun restore(): AuthResult<DriverSession?> = error("Unexpected restore")
        override suspend fun logout(): AuthResult<Unit> = error("Unexpected logout")
    }
    private inner class Data(
        val loadResult: suspend () -> DbResult<DetailsLookup<OrderDetails>> = { DbResult.Success(DetailsLookup.Found(details())) }
    ) : OrderDetailsDataSource {
        var calls = 0
        var ownerCalls = 0
        val drivers = mutableListOf<Long>()
        override suspend fun load(id: OrderId, driverId: Long): DbResult<DetailsLookup<OrderDetails>> {
            calls++; drivers.add(driverId); return loadResult()
        }
        override suspend fun validateOwner(id: OrderId, driverId: Long): DbResult<DetailsLookup<Unit>> {
            ownerCalls++; drivers.add(driverId); return DbResult.Success(DetailsLookup.Found(Unit))
        }
    }
    private fun details() = OrderDetails(id, 0, "Тест", "Тест", "Тест", "Тест", null, null, null, null,
        0, 0, 0, false, null, OrderContacts(), emptyList())
}

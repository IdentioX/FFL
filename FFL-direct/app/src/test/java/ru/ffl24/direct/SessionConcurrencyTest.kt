package ru.ffl24.direct

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.DriverSession
import ru.ffl24.direct.session.*

class SessionConcurrencyTest {
    private val driver=DriverSession(97,"Fictional driver")
    @Test fun twoRestoresDoNotInvalidateEachOther() = runBlocking {
        val store=InMemoryDriverSessionStore().also { it.save(driver) }
        val sessions=AuthSessionManager(store)
        val ui=sessions.saved();val worker=sessions.saved()
        assertTrue(sessions.publish(driver,ui.revision,false))
        assertTrue(sessions.publish(driver,worker.revision,false))
    }
    @Test fun restoreCannotResurrectSessionBetweenLogoutInvalidationAndDiskClear() = runBlocking {
        val store=InMemoryDriverSessionStore().also { it.save(driver) }
        val sessions=AuthSessionManager(store)
        val logout=sessions.invalidate()
        val restore=sessions.saved()
        sessions.clear(logout)
        assertNull(restore.session)
        assertNull(store.read())
    }
    @Test fun backgroundRestoreDoesNotCancelAnInflightLogin() = runBlocking {
        val sessions=AuthSessionManager(InMemoryDriverSessionStore())
        val login=sessions.invalidate();sessions.clear(login)
        assertNull(sessions.saved().session)
        assertTrue(sessions.publish(driver,login,true))
    }
    @Test fun failedLogoutDiskClearDoesNotReenableTheStoredHintInTheSameProcess() = runBlocking {
        val store=object: DriverSessionStore {
            override suspend fun read()=driver
            override suspend fun save(session: DriverSession) { }
            override suspend fun clear() { throw IOException("Owned storage failure") }
        }
        val sessions=AuthSessionManager(store);val logout=sessions.invalidate()
        try { sessions.clear(logout);fail("Expected failure") } catch(_: IOException) { }
        assertNull(sessions.saved().session)
    }
    @Test fun offlineFailureCanRetainAHintForAnExplicitRevalidatedRetry() = runBlocking {
        val store=InMemoryDriverSessionStore().also { it.save(driver) }
        val sessions=AuthSessionManager(store);val first=sessions.saved()
        assertTrue(sessions.publish(driver,first.revision,false))
        sessions.invalidateIfCurrent(first.revision,preserveSavedIdentity=true)
        assertNull(sessions.current().session)
        assertEquals(driver,sessions.saved().session)
    }
}

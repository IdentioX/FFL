package ru.ffl24.direct

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences

class AuditPreferencesTest {
    @Test fun independentWorkerInstancesObserveEachNewIdOnlyOnce() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val a=WorkspacePreferences(context);val b=WorkspacePreferences(context)
        a.clearSeen()
        val scope="97:2099-12-31"
        val first=OrderId(OrderSource.REYS_DEN,1);val next=OrderId(OrderSource.REYS_DEN,2)
        assertTrue(a.observeSecondRoute(scope,listOf(first)).isEmpty())
        val results=listOf(a,b).map { prefs -> async(Dispatchers.IO) { prefs.observeSecondRoute(scope,listOf(first,next)) } }.awaitAll()
        assertEquals(listOf(next),results.flatten())
        assertEquals(setOf(first.wire,next.wire),a.seen(scope))
        assertTrue(b.observeSecondRoute(scope,listOf(next)).isEmpty())
        assertTrue(a.observeSecondRoute(scope,listOf(first,next)).isEmpty())
        a.clearSeen()
        Unit
    }
}

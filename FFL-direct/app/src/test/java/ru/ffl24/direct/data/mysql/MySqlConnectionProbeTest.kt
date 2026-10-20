package ru.ffl24.direct.data.mysql

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.Assert.*
import org.junit.Test

class MySqlConnectionProbeTest {
    @Test fun missingAndInvalidNeverOpenConnection() = runBlocking {
        val connector = JdbcConnector { _, _ -> error("Must not connect") }
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.CONFIGURATION_MISSING),
            MySqlConnectionProbe(MySqlConfiguration.Missing, connector).check())
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.CONFIGURATION_INVALID),
            MySqlConnectionProbe(MySqlConfiguration.Invalid(ConfigProblem.TLS_REQUIRED), connector).check())
    }

    @Test fun actualJdbcScopeRunsOnIoAndClosesInReverseOrder() = runBlocking {
        val callingThread = Thread.currentThread()
        val fake = FakeJdbc()
        val probe = MySqlConnectionProbe(fixtureReady(), JdbcConnector { _, policy ->
            assertNotSame(callingThread, Thread.currentThread())
            assertEquals(5000, policy.socketTimeoutMs)
            fake.connection
        })
        assertEquals(ConnectionProbeResult.Reachable, probe.check())
        assertEquals(listOf("rows.close", "statement.close", "connection.close"), fake.events.takeLast(3))
        assertTrue(fake.events.contains("queryTimeout:3"))
        assertTrue(fake.events.contains("readOnly:true"))
        assertTrue(fake.events.contains("sql:SELECT 1"))
    }

    @Test fun queryFailureClosesStatementAndConnection() = runBlocking {
        val fake = FakeJdbc(fail = "executeQuery")
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.QUERY_FAILED), probe(fake).check())
        assertEquals(listOf("statement.close", "connection.close"), fake.events.takeLast(2))
    }

    @Test fun mapperFailureAlsoClosesResultSet() = runBlocking {
        val fake = FakeJdbc(fail = "getInt")
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.QUERY_FAILED), probe(fake).check())
        assertEquals(listOf("rows.close", "statement.close", "connection.close"), fake.events.takeLast(3))
    }

    @Test fun preparationFailureClosesConnection() = runBlocking {
        val fake = FakeJdbc(fail = "prepareStatement")
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.QUERY_FAILED), probe(fake).check())
        assertEquals("connection.close", fake.events.last())
    }

    @Test fun closeFailureStillClosesOuterResourcesAndDoesNotReportSuccess() = runBlocking {
        val fake = FakeJdbc(fail = "rows.close")
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.QUERY_FAILED), probe(fake).check())
        assertEquals(listOf("rows.close", "statement.close", "connection.close"), fake.events.takeLast(3))
    }

    @Test fun noAutomaticRetryAfterConnectionFailure() = runBlocking {
        val calls = AtomicInteger()
        val probe = MySqlConnectionProbe(fixtureReady(), JdbcConnector { _, _ ->
            calls.incrementAndGet()
            throw SQLException("PRIVATE_FIXTURE", "08000")
        })
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.UNAVAILABLE), probe.check())
        assertEquals(1, calls.get())
    }

    @Test fun operationTimeoutInterruptsCooperativeJdbcAndClosesResources() = runBlocking {
        val fake = FakeJdbc(execute = { Thread.sleep(10_000) })
        val result = MySqlConnectionProbe(fixtureReady(), JdbcConnector { _, _ -> fake.connection },
            ConnectionPolicy(operationTimeoutMs = 100)).check()
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TIMEOUT), result)
        assertEquals(listOf("statement.close", "connection.close"), fake.events.takeLast(2))
    }

    @Test fun callerCancellationIsNotConvertedToUserErrorAndResourcesClose() = runBlocking {
        val started = CountDownLatch(1)
        val fake = FakeJdbc(execute = { started.countDown(); Thread.sleep(10_000) })
        val job = launch { probe(fake).check(); fail("Cancellation swallowed") }
        withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf("statement.close", "connection.close"), fake.events.takeLast(2))
    }

    @Test fun cancellationAfterUninterruptibleOpenClosesLateConnection() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fake = FakeJdbc()
        val probe = MySqlConnectionProbe(fixtureReady(), JdbcConnector { _, _ ->
            started.countDown()
            while (true) {
                try { release.await(); break } catch (_: InterruptedException) { /* emulate native blocking IO */ }
            }
            fake.connection
        })
        val job = launch { probe.check(); fail("Cancellation swallowed") }
        withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
        job.cancel()
        release.countDown()
        job.join()
        assertEquals(listOf("connection.close"), fake.events)
    }

    @Test fun concurrentProbesAreBoundedAndNeverShareConnection() = runBlocking {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val fakes = Collections.synchronizedList(mutableListOf<FakeJdbc>())
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val connector = JdbcConnector { _, _ ->
            val count = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, count) }
            val fake = FakeJdbc(execute = {
                started.countDown()
                release.await(3, TimeUnit.SECONDS)
                active.decrementAndGet()
            })
            fakes.add(fake)
            fake.connection
        }
        val slots = Semaphore(2)
        val probe = MySqlConnectionProbe(fixtureReady(), connector, permits = slots)
        val jobs = List(6) { async { probe.check() } }
        withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
        assertEquals(2, fakes.size)
        release.countDown()
        assertTrue(jobs.awaitAll().all { it == ConnectionProbeResult.Reachable })
        assertEquals(2, maxActive.get())
        assertEquals(6, fakes.size)
        assertTrue(fakes.all { it.events.count { e -> e == "connection.close" } == 1 })
    }

    @Test fun malformedCaFailsSafelyBeforeOpeningAnySocket() = runBlocking {
        val ready = DirectMySqlConfig.parse(fixtureSettings(ca = "-----BEGIN CERTIFICATE-----\ninvalid\n-----END CERTIFICATE-----"))
        assertEquals(ConnectionProbeResult.Failed(DirectDbError.TLS_VERIFICATION), MySqlConnectionProbe(ready).check())
    }

    private fun probe(fake: FakeJdbc) = MySqlConnectionProbe(fixtureReady(), JdbcConnector { _, _ -> fake.connection })
}

private class FakeJdbc(private val fail: String? = null, private val execute: () -> Unit = {}) {
    val events = Collections.synchronizedList(mutableListOf<String>())
    private val rows: ResultSet = proxy(ResultSet::class.java) { name, _ ->
        if (name == "close") event("rows.close") else {
            maybeFail(name)
            when (name) { "next" -> true; "getInt" -> 1; else -> error("Unexpected ResultSet.$name") }
        }
    }
    private val statement: PreparedStatement = proxy(PreparedStatement::class.java) { name, args ->
        if (name == "close") event("statement.close") else {
            maybeFail(name)
            when (name) {
                "setQueryTimeout" -> event("queryTimeout:${args!![0]}")
                "setMaxRows" -> null
                "executeQuery" -> { execute(); rows }
                else -> error("Unexpected Statement.$name")
            }
        }
    }
    val connection: Connection = proxy(Connection::class.java) { name, args ->
        if (name == "close") event("connection.close") else {
            maybeFail(name)
            when (name) {
                "getCatalog" -> fixtureSettings().database
                "setNetworkTimeout" -> event("networkTimeout:${args!![1]}")
                "setReadOnly" -> event("readOnly:${args!![0]}")
                "prepareStatement" -> { event("sql:${args!![0]}"); statement }
                else -> error("Unexpected Connection.$name")
            }
        }
    }
    private fun event(name: String): Any? { events.add(name); maybeFail(name); return null }
    private fun maybeFail(name: String) { if (fail == name) throw SQLException("PRIVATE_FIXTURE", "HY000") }
}

private fun <T : Any> proxy(type: Class<T>, call: (String, Array<out Any?>?) -> Any?): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> call(method.name, args) }
)

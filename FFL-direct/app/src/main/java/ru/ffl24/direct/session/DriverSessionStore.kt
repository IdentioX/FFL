package ru.ffl24.direct.session

import ru.ffl24.direct.domain.DriverSession

interface DriverSessionStore {
    suspend fun read(): DriverSession?
    suspend fun save(session: DriverSession)
    suspend fun clear()
}

/** Explicit non-persistent wiring for graphs constructed without an Android Context. */
class InMemoryDriverSessionStore : DriverSessionStore {
    private var session: DriverSession? = null
    override suspend fun read() = session
    override suspend fun save(session: DriverSession) { this.session = session }
    override suspend fun clear() { session = null }
}

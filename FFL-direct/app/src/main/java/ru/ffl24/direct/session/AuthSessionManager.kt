package ru.ffl24.direct.session

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ffl24.direct.domain.DriverSession

internal data class SessionSnapshot(val revision: Long, val session: DriverSession?)

/** Serialize disk publication and invalidate obsolete login/restore after logout/new attempts. */
class AuthSessionManager(private val store: DriverSessionStore) {
    private var revision = 0L
    private val disk = Mutex()
    private var active: DriverSession? = null
    private var restoreAllowed = true

    @Synchronized internal fun invalidate(preserveSavedIdentity: Boolean = false): Long {
        val next = ++revision
        active = null
        restoreAllowed = preserveSavedIdentity
        return next
    }

    internal suspend fun saved(): SessionSnapshot = disk.withLock {
        val snapshot=current()
        // Restoration is an observation, not a new identity intent. Two foreground/
        // worker restores may validate the same revision. Login/logout explicitly
        // block disk restoration before waiting for storage, so an old hint cannot
        // resurrect a logout or supersede an in-flight password check.
        val mayRead=synchronized(this) { revision==snapshot.revision && restoreAllowed }
        if(snapshot.session!=null || !mayRead) snapshot
        else SessionSnapshot(snapshot.revision,store.read())
    }

    @Synchronized internal fun current() = SessionSnapshot(revision, active)

    @Synchronized internal fun invalidateIfCurrent(expected: Long, preserveSavedIdentity: Boolean = false): Long? =
        if (revision == expected) invalidate(preserveSavedIdentity) else null

    internal suspend fun clear(expected: Long) = disk.withLock {
        if (current().revision == expected) {
            synchronized(this) { if (revision == expected) { active = null; restoreAllowed = false } }
            store.clear()
        }
    }

    internal suspend fun publish(session: DriverSession, expected: Long, persist: Boolean): Boolean = disk.withLock {
        if (current().revision != expected) return@withLock false
        try {
            if (persist) store.save(session)
            currentCoroutineContext().ensureActive()
            val accepted = synchronized(this) {
                if (revision == expected) { active = session; restoreAllowed = true; true } else false
            }
            if (!accepted) {
                if (persist) store.clear()
                false
            } else {
                true
            }
        } catch (failure: CancellationException) {
            synchronized(this) { if (revision == expected) { active = null; restoreAllowed = false } }
            // An atomic DataStore edit may have committed before cancellation was delivered.
            if (persist) withContext(NonCancellable) {
                try { store.clear() } catch (_: IOException) {
                    failure.addSuppressed(IOException("Session cleanup failed"))
                }
            }
            throw failure
        }
    }
}

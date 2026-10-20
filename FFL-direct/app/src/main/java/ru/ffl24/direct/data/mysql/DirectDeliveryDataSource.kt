package ru.ffl24.direct.data.mysql

import java.sql.Connection
import java.sql.SQLException
import java.util.ServiceConfigurationError
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import ru.ffl24.direct.domain.*

/** Dedicated write scope: no automatic retries, only the two delivery columns. */
internal class DirectDeliveryDataSource(
    private val configuration: MySqlConfiguration,
    private val connector: JdbcConnector = MySqlConnectionFactory(),
    private val policy: ConnectionPolicy = ConnectionPolicy()
) {
    suspend fun deliver(id: OrderId, driver: Long, stillCurrent: () -> Boolean): DbResult<DetailsLookup<DeliveryReceipt>> {
        val config = when (val c = configuration) {
            is MySqlConfiguration.Ready -> c.config
            MySqlConfiguration.Missing -> return DbResult.Failed(DirectDbError.CONFIGURATION_MISSING)
            is MySqlConfiguration.Invalid -> return DbResult.Failed(DirectDbError.CONFIGURATION_INVALID)
        }
        return try {
            withTimeout(policy.operationTimeoutMs) {
                ConnectionSlots.permits.withPermit {
                    val context = currentCoroutineContext()
                    runInterruptible(Dispatchers.IO) {
                        connector.open(config, policy).use { c ->
                            context.ensureActive()
                            c.isReadOnly = false
                            DbResult.Success(deliveryTransaction(c, id, driver, policy.queryTimeoutSeconds) {
                                context.ensureActive(); stillCurrent()
                            })
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            DbResult.Success(DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.UNKNOWN, null)))
        } catch (e: CancellationException) { throw e }
        catch (_: LinkageError) { DbResult.Failed(DirectDbError.DRIVER_UNAVAILABLE) }
        catch (_: ServiceConfigurationError) { DbResult.Failed(DirectDbError.DRIVER_UNAVAILABLE) }
        catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            // The transaction itself returns UNKNOWN if commit/rollback is uncertain.
            // A rejected TLS handshake or a confirmed rollback is a definite failure.
            DbResult.Failed(DirectDbError.from(failure))
        }
    }
}

/** Testable transaction protocol; connection is owned/closed by the outer IO scope. */
internal fun deliveryTransaction(c: Connection, id: OrderId, driver: Long, timeout: Int, stillCurrent: () -> Boolean): DetailsLookup<DeliveryReceipt> {
    var changed = false
    var committing = false
    c.autoCommit = false
    try {
        if (!stillCurrent()) { c.rollback(); return DetailsLookup.DriverMissing }
        // A plain SELECT under MariaDB REPEATABLE READ would keep seeing the old
        // access snapshot after revocation. Hold a current shared lock until commit:
        // a revocation committed first blocks delivery; an in-flight delivery finishes
        // before the operator's revocation can commit. This is not server row isolation.
        access(c, driver, timeout, lock = true)?.let { c.rollback(); return it }
        val row = c.prepareStatement("SELECT id,st_vv,date_att_log FROM ${id.source.wire} WHERE id=? AND id_v=? FOR UPDATE").use { s ->
            s.queryTimeout = timeout; s.setLong(1, id.legacyId); s.setLong(2, driver)
            s.executeQuery().use { r -> if (!r.next()) null else {
                if (positive(r, "id") != id.legacyId) throw SQLException("Invalid identity")
                (r.getLong("st_vv") == 1L) to r.getString("date_att_log")
            } }
        } ?: run { c.rollback(); return DetailsLookup.NotFound }
        if (row.first) {
            committing = true; c.commit()
            return DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.ALREADY_DELIVERED, row.second))
        }
        if (!stillCurrent()) { c.rollback(); return DetailsLookup.DriverMissing }
        access(c, driver, timeout)?.let { c.rollback(); return it }
        c.prepareStatement("UPDATE ${id.source.wire} SET st_vv=1,date_att_log=NOW() WHERE id=? AND id_v=? AND COALESCE(st_vv,0)<>1").use { s ->
            s.queryTimeout = timeout; s.setLong(1, id.legacyId); s.setLong(2, driver)
            changed = true // failed execute can still have reached the server
            if (s.executeUpdate() != 1) throw SQLException("Unexpected affected rows")
        }
        val at = c.prepareStatement("SELECT id,st_vv,date_att_log FROM ${id.source.wire} WHERE id=? AND id_v=?").use { s ->
            s.queryTimeout = timeout; s.setLong(1, id.legacyId); s.setLong(2, driver)
            s.executeQuery().use { r ->
                if (!r.next() || positive(r, "id") != id.legacyId || r.getLong("st_vv") != 1L) throw SQLException("Invalid postcondition")
                r.getString("date_att_log") ?: throw SQLException("Missing timestamp")
            }
        }
        if (!stillCurrent()) throw SQLException("Session changed")
        access(c, driver, timeout)?.let { c.rollback(); return it }
        committing = true; c.commit()
        return DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.DELIVERED, at))
    } catch (failure: Exception) {
        var rolledBack = false
        try { c.rollback(); rolledBack = true } catch (_: Exception) { }
        if (committing || (changed && !rolledBack)) return DetailsLookup.Found(DeliveryReceipt(DeliveryStatus.UNKNOWN, null))
        throw failure
    }
}

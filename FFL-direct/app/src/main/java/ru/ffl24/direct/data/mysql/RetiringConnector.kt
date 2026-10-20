package ru.ffl24.direct.data.mysql

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException

/** A retired graph cannot open another socket. All its scoped connections are closed on IO. */
internal class RetiringConnector(private val delegate: JdbcConnector = MySqlConnectionFactory()) : JdbcConnector {
    private var retired = false
    private val connections = mutableSetOf<Connection>()
    @Synchronized override fun open(config: DirectMySqlConfig, policy: ConnectionPolicy): Connection {
        if (retired) throw SQLException("Connection profile retired", "08003")
        val connection = delegate.open(config, policy)
        connections.add(connection)
        return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            try { method.invoke(connection, *(args ?: emptyArray())) }
            catch (e: InvocationTargetException) { throw e.targetException }
            finally { if (method.name == "close") synchronized(this) { connections.remove(connection) } }
        } as Connection
    }
    @Synchronized fun retire() {
        retired = true
        connections.toList().forEach { runCatching { it.close() } }
        connections.clear()
    }
}

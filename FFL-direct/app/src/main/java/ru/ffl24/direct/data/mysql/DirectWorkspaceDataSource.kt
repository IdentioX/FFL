package ru.ffl24.direct.data.mysql

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import ru.ffl24.direct.domain.*

internal class DirectWorkspaceDataSource(private val reader: MySqlReadExecutor) {
    suspend fun orders(date: String, driver: Long) = reader.read { c ->
        checked(c, driver) {
            val pending = c.prepareStatement(ORDERS_SQL).use { s ->
                s.queryTimeout = reader.queryTimeoutSeconds
                s.setString(1, date); s.setLong(2, driver); s.setString(3, date); s.setLong(4, driver)
                s.executeQuery().use { rows -> buildList {
                    while (rows.next()) {
                        val source = OrderSource.entries.firstOrNull { it.wire == rows.getString("source_table") }
                            ?: throw SQLException("Invalid source")
                        val id = OrderId(source, positive(rows, "id"))
                        val owner = positive(rows, "id_v")
                        if (owner != driver) throw SQLException("Invalid owner")
                        val rawName = rows.getString("name_c")
                        val number = normalizeSpaces(rows.getString("nomer_z"))
                        add(Triple(DriverOrder(id, owner, rows.getString("driver_name"),
                            rows.getString("date") ?: throw SQLException("Invalid date"), orderNumber(rows.getString("nomer_z")),
                            normalizeSpaces(rawName).orEmpty(), normalizeSpaces(rows.getString("vremya")),
                            rows.getLong("sort_r"), rows.getLong("col"), rows.getLong("status"),
                            rows.getLong("col_s"), rows.getLong("st_vv") == 1L, rows.getString("date_att_log"), OrderContacts()), number, rawName))
                    }
                } }
            }
            // Close the order ResultSet before more statements; one round trip per
            // bounded batch instead of one per order number. Resolve per row, not per number.
            val candidates = contacts(c, pending.mapNotNull { it.second }.distinct())
            pending.map { (row, number, rawName) -> row.copy(contacts =
                OrderContactResolver.resolve(candidates[number].orEmpty(), rawName)) }
        }
    }

    suspend fun directory(driver: Long) = reader.read { c -> checked(c, driver) {
        val card = c.prepareStatement(CARD_SQL).use { s ->
            s.queryTimeout = reader.queryTimeoutSeconds
            s.executeQuery().use { r -> if (r.next()) r.getString("cardNumber") to r.getString("cardHolder") else null to null }
        }
        val phones = c.prepareStatement(PHONES_SQL).use { s ->
            s.queryTimeout = reader.queryTimeoutSeconds
            s.executeQuery().use { r -> buildList {
                while (r.next()) r.getString("phone")?.takeIf { it.isNotBlank() }?.let { add(DirectoryPhone(r.getString("name"), it)) }
            } }
        }
        val drivers = c.prepareStatement(DRIVERS_SQL).use { s ->
            s.queryTimeout = reader.queryTimeoutSeconds
            s.executeQuery().use { r -> buildList {
                while (r.next()) {
                    val phone = directoryNumber(r.getString("mobile"), r.getString("whatsapp")) ?: continue
                    add(DriverPhone(positive(r, "driverId"), r.getString("name") ?: throw SQLException("Invalid driver"), phone))
                }
            } }
        }
        PhoneDirectory(card.first, card.second, phones, drivers)
    } }

    suspend fun summary(date: String, driver: Long) = reader.read { c -> checked(c, driver) {
        val counts = c.prepareStatement(SUMMARY_SQL).use { s ->
            s.queryTimeout = reader.queryTimeoutSeconds
            s.setString(1, date); s.setLong(2, driver); s.setString(3, date); s.setLong(4, driver)
            s.executeQuery().use { r ->
                if (!r.next()) throw SQLException("Missing summary")
                Triple(r.getLong("first_count"), r.getLong("second_count"), r.getLong("delivered_count"))
            }
        }
        val ids = c.prepareStatement(SECOND_IDS_SQL).use { s ->
            s.queryTimeout = reader.queryTimeoutSeconds; s.setString(1, date); s.setLong(2, driver)
            s.executeQuery().use { r -> buildList { while (r.next()) add(OrderId(OrderSource.REYS_DEN, positive(r, "id"))) } }
        }
        NotificationSummary(counts.first, counts.second, counts.third, ids)
    } }

    private fun <T> checked(c: Connection, driver: Long, block: () -> T): DetailsLookup<T> {
        access(c, driver, reader.queryTimeoutSeconds)?.let { return it }
        val value = block()
        access(c, driver, reader.queryTimeoutSeconds)?.let { return it }
        return DetailsLookup.Found(value)
    }
    private fun contacts(c: Connection, numbers: List<String>): Map<String, List<AddressCandidate>> {
        val result = mutableMapOf<String, MutableList<AddressCandidate>>()
        for (batch in numbers.chunked(64)) c.prepareStatement(contactsSql(batch.size)).use { s ->
            s.queryTimeout = reader.queryTimeoutSeconds
            batch.forEachIndexed { i, number -> s.setString(i + 1, number) }
            s.executeQuery().use { r -> while (r.next()) {
                val requested = r.getString("requested_number") ?: throw SQLException("Invalid contact key")
                result.getOrPut(requested) { mutableListOf() }.add(AddressCandidate(r.getLong("id"),
                    r.getString("ooo"), r.getString("adress"), r.getString("link"), r.getString("tell_klient"),
                    r.getString("tell_gruzchik"), r.getString("manager_name"), r.getString("manager_phone"), r.getString("manager_short_phone")))
            } }
        }
        return result
    }
    companion object {
        internal fun contactsSql(count: Int): String {
            require(count in 1..64)
            // Join requested values on the server to preserve its charset/collation/TRIM
            // equivalence. Kotlin equality must not replace MySQL's candidate matching.
            val requested = List(count) { "SELECT ? AS requested_number" }.joinToString(" UNION ALL ")
            return """SELECT requested.requested_number, c.id,c.ooo,c.adress,c.link,c.tell_klient,c.tell_gruzchik,
                m.meneger AS manager_name,m.tel AS manager_phone,m.short_tel AS manager_short_phone
                FROM ($requested) requested
                JOIN meneger_contr_ffl c ON TRIM(c.nomer_z)=TRIM(requested.requested_number)
                LEFT JOIN meneger_ffl m ON m.id=c.id_m
                ORDER BY requested.requested_number,c.id ASC"""
        }
        internal const val ORDERS_SQL = """
            SELECT orders.*, name_v(orders.id_v) AS driver_name, COALESCE(orders.st_vv,0) AS is_delivered
            FROM (
                SELECT 'reys' AS source_table, 1 AS section_order, r.id,r.nomer_z,r.id_v,r.name_c,r.col,
                    r.vremya,r.date,r.status,r.col_s,r.sort_r,r.st_vv,r.date_att_log
                FROM reys r WHERE r.date = ? AND r.id_v = ?
                UNION ALL
                SELECT 'reys_den' AS source_table, 2 AS section_order, d.id,d.nomer_z,d.id_v,d.name_c,d.col,
                    d.vremya,d.date,d.status,d.col_s,d.sort_r,d.st_vv,d.date_att_log
                FROM reys_den d WHERE d.date = ? AND d.id_v = ?
            ) orders
            ORDER BY is_delivered ASC,orders.section_order ASC,
                CASE WHEN orders.sort_r IS NULL OR orders.sort_r = 0 THEN 1 ELSE 0 END ASC,
                orders.sort_r ASC,orders.id ASC
        """
        internal const val CARD_SQL = """SELECT MAX(CASE WHEN variable = 'karta' THEN value END) AS cardNumber,
            MAX(CASE WHEN variable = 'imya_karta' THEN value END) AS cardHolder
            FROM nastroiki WHERE variable IN ('karta','imya_karta')"""
        internal const val PHONES_SQL = "SELECT name,nomer_full AS phone FROM phone WHERE nomer_full IS NOT NULL AND TRIM(nomer_full) <> '' ORDER BY name"
        internal const val DRIVERS_SQL = """SELECT posTypeId AS driverId,posTypeName AS name,mobile,whatsapp FROM `__hr_typepos`
            WHERE posTypeId IS NOT NULL AND posTypeName IS NOT NULL AND posTypeName <> '' AND access = 1
            AND ((mobile IS NOT NULL AND TRIM(mobile) <> '') OR (whatsapp IS NOT NULL AND TRIM(whatsapp) <> '')) ORDER BY name"""
        internal const val SUMMARY_SQL = """SELECT
            COALESCE(SUM(CASE WHEN source_table='reys' AND delivered=0 THEN 1 ELSE 0 END),0) AS first_count,
            COALESCE(SUM(CASE WHEN source_table='reys_den' AND delivered=0 THEN 1 ELSE 0 END),0) AS second_count,
            COALESCE(SUM(CASE WHEN delivered=1 THEN 1 ELSE 0 END),0) AS delivered_count FROM (
                SELECT 'reys' AS source_table, CASE WHEN COALESCE(st_vv,0)=1 THEN 1 ELSE 0 END AS delivered FROM reys WHERE date=? AND id_v=?
                UNION ALL
                SELECT 'reys_den' AS source_table, CASE WHEN COALESCE(st_vv,0)=1 THEN 1 ELSE 0 END AS delivered FROM reys_den WHERE date=? AND id_v=?
            ) orders"""
        internal const val SECOND_IDS_SQL = """SELECT id FROM reys_den WHERE date=? AND id_v=? AND COALESCE(st_vv,0)<>1
            ORDER BY CASE WHEN sort_r IS NULL OR sort_r=0 THEN 1 ELSE 0 END ASC,sort_r ASC,id ASC"""
    }
}

internal fun directoryNumber(mobile: String?, whatsapp: String?): String? = mobile?.takeIf { it.isNotBlank() } ?: whatsapp?.takeIf { it.isNotBlank() }
internal fun positive(r: ResultSet, name: String): Long = r.getLong(name).also {
    if (r.wasNull() || it <= 0) throw SQLException("Invalid identity")
}
internal fun access(c: Connection, driver: Long, timeout: Int, lock: Boolean = false): DetailsLookup<Nothing>? =
    c.prepareStatement("SELECT access FROM `__hr_typepos` WHERE posTypeId = ? LIMIT 1" + if (lock) " LOCK IN SHARE MODE" else "").use { s ->
        s.queryTimeout = timeout; s.setLong(1, driver)
        s.executeQuery().use { r ->
            if (!r.next()) DetailsLookup.DriverMissing
            else if (r.getString("access")?.trim()?.toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ONE) == 0) null
            else DetailsLookup.Disabled
        }
    }

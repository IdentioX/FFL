package ru.ffl24.direct.data.mysql

/** One attempt per explicit probe; no automatic retries and no connection pool. */
data class ConnectionPolicy(
    val connectTimeoutMs: Int = 3_000,
    val socketTimeoutMs: Int = 5_000,
    val queryTimeoutSeconds: Int = 3,
    val operationTimeoutMs: Long = 15_000
) {
    init {
        require(connectTimeoutMs > 0 && socketTimeoutMs > 0 && queryTimeoutSeconds > 0 && operationTimeoutMs > 0)
    }
}

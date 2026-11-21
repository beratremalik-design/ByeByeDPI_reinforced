package io.github.romanvht.byedpi.data

data class TestSettings(
    val sites: List<String>,
    val commands: List<String>,
    val host: String,
    val port: Int,
    val delaySec: Int,
    val requestsCount: Int,
    val requestTimeout: Long,
    val requestLimit: Int,
    /** Откуда взяты стратегии: asset / pack / user (см. TestService.SOURCE_*). */
    val source: String = "asset",
)

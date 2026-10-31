package com.nuvio.app.core.network

import co.touchlab.kermit.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

data class NetworkSpeedSnapshot(
    val downloadMbps: Double,
    val measuredAtMs: Long,
)

/**
 * Lightweight download probe used by Smart Play. The result is cached and never
 * blocks playback: callers use whatever snapshot is available and let the next
 * playback benefit from a fresh measurement.
 */
object NetworkSpeedRepository {
    private const val SPEED_TEST_URL = "https://speed.cloudflare.com/__down?bytes=5000000"
    private const val SPEED_TEST_FALLBACK_URL = "https://speed.cloudflare.com/__down?bytes=2000000"
    private const val CACHE_TTL_MS = 15 * 60_000L
    private const val PROBE_TIMEOUT_MS = 3_500L
    private const val MAX_PROBE_BYTES = 4_000_000L
    private const val MIN_PROBE_BYTES = 256_000L
    private const val MIN_PROBE_DURATION_MS = 250L

    private val log = Logger.withTag("NetworkSpeed")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client by lazy { createApiHttpClient() }

    @Volatile
    private var snapshot: NetworkSpeedSnapshot? = null

    @Volatile
    private var refreshInFlight = false

    fun snapshot(): NetworkSpeedSnapshot? = snapshot

    fun refreshIfStale(ttlMs: Long = CACHE_TTL_MS) {
        if (refreshInFlight) return
        val current = snapshot
        val now = nowMs()
        if (current != null && now - current.measuredAtMs < ttlMs) return

        refreshInFlight = true
        scope.launch {
            try {
                val measured = measureDownload()
                if (measured != null) {
                    log.d { "Download probe measured $measured Mbps" }
                    snapshot = NetworkSpeedSnapshot(
                        downloadMbps = measured,
                        measuredAtMs = nowMs(),
                    )
                } else {
                    log.d { "Download probe unavailable; Smart Play will use the display cap" }
                }
            } finally {
                refreshInFlight = false
            }
        }
    }

    private suspend fun measureDownload(): Double? =
        measureUrl(SPEED_TEST_URL) ?: measureUrl(SPEED_TEST_FALLBACK_URL)

    private suspend fun measureUrl(url: String): Double? =
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            runCatching {
                val response = client.get(url) {
                    header(HttpHeaders.CacheControl, "no-cache")
                    header(HttpHeaders.Accept, "*/*")
                }
                if (response.status.value !in 200..299) return@runCatching null

                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                val started = TimeSource.Monotonic.markNow()
                var totalBytes = 0L
                var firstByteMs = -1L
                var lastByteMs = 0L

                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue

                    val elapsedMs = started.elapsedNow().inWholeMilliseconds
                    if (firstByteMs < 0) firstByteMs = elapsedMs
                    lastByteMs = elapsedMs
                    totalBytes += read
                    if (totalBytes >= MAX_PROBE_BYTES) break
                }

                val durationMs = lastByteMs - firstByteMs
                if (totalBytes < MIN_PROBE_BYTES || durationMs < MIN_PROBE_DURATION_MS) {
                    return@runCatching null
                }
                downloadMbps(totalBytes, durationMs)
            }.getOrNull()
        }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}

internal fun downloadMbps(bytes: Long, elapsedMs: Long): Double? {
    if (bytes <= 0L || elapsedMs <= 0L) return null
    return (bytes * 8.0) / (elapsedMs / 1_000.0) / 1_000_000.0
}

package com.nuvio.app.features.streams

import com.nuvio.app.features.player.PlayerSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SoftIgnoredStreamEntry(
    val key: String,
    val contentKey: String,
    val label: String = "",
    val addonName: String? = null,
    val failedAtMs: Long = 0L,
)

/**
 * Sources that recently failed to play.
 *
 * Unlike [IgnoredStreamsRepository] this is not a user decision: it only stops the automatic source
 * failover from retrying a dead link, and it expires after [TTL_MS] so a link that was temporarily
 * unavailable (debrid still fetching the file, CDN hiccup) becomes eligible again. The manual source
 * list still shows these streams - the user can always pick one by hand.
 */
object SoftIgnoredStreamsRepository {
    private const val TTL_MS = 24L * 60L * 60L * 1000L
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<SoftIgnoredStreamEntry>>(emptyList())
    val entries: StateFlow<List<SoftIgnoredStreamEntry>> = _entries.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = runCatching { PlayerSettingsStorage.loadSoftIgnoredStreams() }.getOrNull()
        val now = nowMs()
        _entries.value = raw
            ?.let { stored -> runCatching { json.decodeFromString<List<SoftIgnoredStreamEntry>>(stored) }.getOrNull() }
            .orEmpty()
            .filter { now - it.failedAtMs < TTL_MS }
    }

    fun isSoftIgnored(stream: StreamItem, videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        ensureLoaded()
        val key = IgnoredStreamsRepository.keyFor(stream, videoId)
        val now = nowMs()
        return _entries.value.any {
            it.key == key && it.contentKey == videoId && now - it.failedAtMs < TTL_MS
        }
    }

    fun markFailed(stream: StreamItem, videoId: String?, label: String = "") {
        if (videoId.isNullOrBlank()) return
        ensureLoaded()
        val key = IgnoredStreamsRepository.keyFor(stream, videoId)
        val now = nowMs()
        val kept = _entries.value.filter { now - it.failedAtMs < TTL_MS && it.key != key }
        _entries.value = kept + SoftIgnoredStreamEntry(
            key = key,
            contentKey = videoId,
            label = label,
            addonName = stream.addonName,
            failedAtMs = now,
        )
        persist()
    }

    /** Sources skipped for the given episode/movie, most recent first. */
    fun entriesForContent(videoId: String?): List<SoftIgnoredStreamEntry> {
        if (videoId.isNullOrBlank()) return emptyList()
        ensureLoaded()
        val now = nowMs()
        return _entries.value
            .filter { it.contentKey == videoId && now - it.failedAtMs < TTL_MS }
            .sortedByDescending { it.failedAtMs }
    }

    fun clearForContent(videoId: String?) {
        if (videoId.isNullOrBlank()) return
        ensureLoaded()
        val updated = _entries.value.filterNot { it.contentKey == videoId }
        if (updated.size == _entries.value.size) return
        _entries.value = updated
        persist()
    }

    private fun persist() {
        runCatching { PlayerSettingsStorage.saveSoftIgnoredStreams(json.encodeToString(_entries.value)) }
    }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}

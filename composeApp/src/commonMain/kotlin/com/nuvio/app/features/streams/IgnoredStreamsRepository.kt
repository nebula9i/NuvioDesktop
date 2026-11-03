package com.nuvio.app.features.streams

import com.nuvio.app.features.player.PlayerSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class IgnoredStreamEntry(
    val key: String,
    val videoId: String,
    val label: String,
    val addonName: String? = null,
    val contentTitle: String? = null,
    val ignoredAtMs: Long = 0L,
)

/**
 * Files the viewer explicitly rejected. The key includes the requested video id, so
 * ignoring a bad S4E1 file does not hide the same release for another episode.
 */
object IgnoredStreamsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<IgnoredStreamEntry>>(emptyList())
    val entries: StateFlow<List<IgnoredStreamEntry>> = _entries.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = runCatching { PlayerSettingsStorage.loadIgnoredStreams() }.getOrNull()
        _entries.value = raw
            ?.let { stored -> runCatching { json.decodeFromString<List<IgnoredStreamEntry>>(stored) }.getOrNull() }
            .orEmpty()
    }

    fun isIgnored(stream: StreamItem, videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        ensureLoaded()
        val key = keyFor(stream, videoId)
        return _entries.value.any { it.key == key }
    }

    // The file identity is what matters; the requested video id is only stored for display.
    // This still works when an episode is reached through a different addon-specific video id.
    fun keyFor(stream: StreamItem, videoId: String? = null): String = stream.identityKey()

    fun ignore(stream: StreamItem, videoId: String, contentTitle: String?): IgnoredStreamEntry? {
        if (videoId.isBlank()) return null
        ensureLoaded()
        val key = keyFor(stream, videoId)
        if (_entries.value.any { it.key == key }) return null

        val entry = IgnoredStreamEntry(
            key = key,
            videoId = videoId,
            label = stream.ignoredLabel(),
            addonName = stream.addonName,
            contentTitle = contentTitle,
            ignoredAtMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        )
        _entries.value = _entries.value + entry
        persist()
        return entry
    }

    fun restore(entry: IgnoredStreamEntry) = restore(entry.key)

    fun restore(key: String) {
        ensureLoaded()
        val updated = _entries.value.filterNot { it.key == key }
        if (updated.size == _entries.value.size) return
        _entries.value = updated
        persist()
    }

    private fun persist() {
        runCatching { PlayerSettingsStorage.saveIgnoredStreams(json.encodeToString(_entries.value)) }
    }

    private fun StreamItem.identityKey(): String {
        val hash = infoHash?.trim()?.lowercase()
            ?: p2pInfoHash?.lowercase()
        if (!hash.isNullOrBlank()) {
            return "torrent:$hash:${fileIdx ?: p2pFileIdx ?: -1}"
        }

        val filename = behaviorHints.filename
            ?: clientResolve?.filename
            ?: clientResolve?.stream?.raw?.filename
            ?: clientResolve?.stream?.raw?.torrentName
        if (!filename.isNullOrBlank()) {
            return "file:${filename.trim().lowercase()}"
        }

        val url = playableDirectUrl ?: url ?: externalUrl
        if (!url.isNullOrBlank()) {
            val identity = listOfNotNull(
                behaviorHints.filename?.takeIf { it.isNotBlank() },
                name?.takeIf { it.isNotBlank() },
                title?.takeIf { it.isNotBlank() },
                description?.takeIf { it.isNotBlank() },
            ).joinToString("|").lowercase()
            return "url:${url.trim().lowercase()}:$identity"
        }
        return "name:${addonId}:${name.orEmpty()}"
    }

    private fun StreamItem.ignoredLabel(): String = listOfNotNull(
        behaviorHints.filename,
        clientResolve?.filename,
        clientResolve?.stream?.raw?.filename,
        name?.takeIf { it.isNotBlank() },
        title?.takeIf { it.isNotBlank() },
    ).firstOrNull()
        ?.trim()
        .orEmpty()
        .ifBlank { "Stream" }
}

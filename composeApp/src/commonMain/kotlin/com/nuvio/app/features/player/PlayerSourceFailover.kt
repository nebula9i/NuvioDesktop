package com.nuvio.app.features.player

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.details.parseRuntimeMinutes
import com.nuvio.app.features.streams.IgnoredStreamsRepository
import com.nuvio.app.features.streams.SmartPlayPolicy
import com.nuvio.app.features.streams.SoftIgnoredStreamsRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.currentSmartPlayContext
import com.nuvio.app.features.streams.isSelectableForPlayback
import com.nuvio.app.features.streams.mismatchesRequestedContent
import io.ktor.client.request.head
import io.ktor.client.request.header
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_no_working_source
import nuvio.composeapp.generated.resources.player_source_switching_after_timeout
import org.jetbrains.compose.resources.getString

/** Upper bound on automatic source switches for one playback session. */
private const val MAX_AUTO_FAILOVER_ATTEMPTS = 8
private const val FAILOVER_SOURCE_WAIT_MS = 10_000L
private const val FAILOVER_PROBE_TIMEOUT_MS = 4_000L
private const val FAILOVER_PROBE_CANDIDATES = 4

private val failoverProbeClient by lazy { createApiHttpClient() }

/**
 * Marks the source that just failed as temporarily unusable (24h soft ignore) and switches to the
 * next best one. Returns true when a switch was started, false when there is nothing left to try.
 *
 * This is the single entry point for "this source is not watchable": the load watchdog and the
 * playback error handler both call it, so a dead link is always replaced automatically instead of
 * asking the viewer to pick another source by hand.
 */
internal fun PlayerScreenRuntime.failOverToNextSource(): Boolean {
    if (isSourceSwitchInProgress || failoverInProgress) return false
    if (failoverAttempts >= MAX_AUTO_FAILOVER_ATTEMPTS) return false
    val videoId = activeVideoId ?: return false

    activeStream?.let { failed ->
        SoftIgnoredStreamsRepository.markFailed(failed, videoId, failed.streamLabel)
    }
    // An ended/errored source leaves the player paused; the replacement must start playing.
    shouldPlay = true
    failoverAttempts += 1
    failoverInProgress = true
    scope.launch {
        try {
            val candidates = loadFailoverCandidates(videoId)
            val next = pickNextCandidate(candidates)
            if (next == null) {
                showNoWorkingSourceError()
                return@launch
            }
            NuvioToastController.show(getString(Res.string.player_source_switching_after_timeout))
            switchToSource(next)
        } finally {
            failoverInProgress = false
        }
    }
    return true
}

internal fun PlayerScreenRuntime.showNoWorkingSourceError() {
    scope.launch {
        if (errorMessage == null) {
            errorMessage = getString(Res.string.player_no_working_source)
        }
    }
}

private suspend fun PlayerScreenRuntime.loadFailoverCandidates(videoId: String): List<StreamItem> {
    val state = PlayerStreamsRepository.sourceState.value
    if (state.groups.isEmpty() || state.isAnyLoading) {
        PlayerStreamsRepository.loadSources(
            type = contentType ?: parentMetaType,
            videoId = videoId,
            season = activeSeasonNumber,
            episode = activeEpisodeNumber,
        )
        withTimeoutOrNull(FAILOVER_SOURCE_WAIT_MS) {
            PlayerStreamsRepository.sourceState.first { current ->
                current.groups.isNotEmpty() &&
                    !current.isAnyLoading &&
                    current.groups.none { it.isLoading }
            }
        }
    }
    return PlayerStreamsRepository.sourceState.value.groups.flatMap { it.streams }
}

/**
 * Ranks the remaining sources and probes the best few in parallel, so a dead link is skipped
 * without waiting for a full playback timeout on each one.
 */
private suspend fun PlayerScreenRuntime.pickNextCandidate(streams: List<StreamItem>): StreamItem? {
    val ranked = rankFailoverCandidates(streams)
    if (ranked.isEmpty()) return null
    val probed = coroutineScope {
        ranked.take(FAILOVER_PROBE_CANDIDATES)
            .map { stream -> async { stream to probeStream(stream) } }
            .awaitAll()
    }
    // If nothing answers the probe, still try the best ranked source: HEAD is not supported
    // everywhere, so a failed probe is not proof that the link is dead.
    return probed.firstOrNull { it.second }?.first ?: ranked.first()
}

private fun PlayerScreenRuntime.rankFailoverCandidates(streams: List<StreamItem>): List<StreamItem> {
    val debridEnabled = DebridSettingsRepository.snapshot().canResolvePlayableLinks
    val requestedType = contentType ?: parentMetaType
    val expectedRuntimeMinutes = if (requestedType.equals("movie", ignoreCase = true)) {
        (metaUiState.meta?.runtime ?: playerMeta?.runtime)?.let(::parseRuntimeMinutes)
    } else {
        null
    }
    val usable = streams
        .filterNot { IgnoredStreamsRepository.isIgnored(it, activeVideoId) }
        .filterNot { SoftIgnoredStreamsRepository.isSoftIgnored(it, activeVideoId) }
        .filter { it.isSelectableForPlayback(debridEnabled) && !it.isActiveSource(this) }
    // Prefer sources that look like the requested content, but a wrong runtime guess must not leave
    // the viewer without any candidate - fall back to everything that can play.
    val matching = usable.filterNot {
        it.mismatchesRequestedContent(
            contentType = requestedType,
            season = activeSeasonNumber,
            episode = activeEpisodeNumber,
            expectedRuntimeMinutes = expectedRuntimeMinutes,
        )
    }
    val pool = matching.ifEmpty { usable }
    return SmartPlayPolicy.rankSmart(
        streams = pool,
        context = currentSmartPlayContext(playerSettingsUiState.smartPlayHdrPreference),
        debridEnabled = debridEnabled,
    )
}

/**
 * Cheap liveness check: a HEAD request with the stream's own headers. Streams without a resolved
 * URL cannot be probed and are treated as reachable so the switch still happens.
 */
private suspend fun probeStream(stream: StreamItem): Boolean {
    val url = stream.playableDirectUrl ?: return true
    val headers = stream.behaviorHints.proxyHeaders?.request.orEmpty()
    val status = withTimeoutOrNull(FAILOVER_PROBE_TIMEOUT_MS) {
        runCatching {
            failoverProbeClient.head(url) {
                headers.forEach { (name, value) -> header(name, value) }
            }.status.value
        }.getOrNull()
    } ?: return false
    return status in 200..399 || status == 405
}

package com.nuvio.app.features.player

import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.details.parseRuntimeMinutes
import com.nuvio.app.features.streams.IgnoredStreamsRepository
import com.nuvio.app.features.streams.SmartPlayPolicy
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.currentSmartPlayContext
import com.nuvio.app.features.streams.isSelectableForPlayback
import com.nuvio.app.features.streams.mismatchesRequestedContent
import com.nuvio.app.features.streams.streamQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun PlayerScreenRuntime.requestLowerQuality() {
    val targetHeight = stallDowngradeTargetHeight ?: return
    if (stallDowngradeInProgress) return

    val videoId = activeVideoId ?: return
    val type = contentType ?: parentMetaType

    stallDowngradeInProgress = true
    showStallDowngradeOffer = false

    PlayerStreamsRepository.loadSources(
        type = type,
        videoId = videoId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )

    scope.launch {
        try {
            val state = withTimeoutOrNull(SOURCE_WAIT_MS) {
                PlayerStreamsRepository.sourceState.first { state ->
                    state.groups.isNotEmpty() &&
                        !state.isAnyLoading &&
                        state.groups.none { it.isLoading }
                }
            } ?: return@launch

            val stream = selectLowerQualityStream(
                streams = state.groups.flatMap { it.streams },
                targetHeight = targetHeight,
            ) ?: return@launch
            switchToSource(stream)
        } finally {
            stallDowngradeInProgress = false
        }
    }
}

private fun PlayerScreenRuntime.selectLowerQualityStream(
    streams: List<StreamItem>,
    targetHeight: Int,
): StreamItem? {
    val debridEnabled = DebridSettingsRepository.snapshot().canResolvePlayableLinks
    val context = currentSmartPlayContext(playerSettingsUiState.smartPlayHdrPreference)
        .copy(maxHeightOverride = targetHeight)
    val requestedType = contentType ?: parentMetaType
    val expectedRuntimeMinutes = if (requestedType.equals("movie", ignoreCase = true)) {
        (metaUiState.meta?.runtime ?: playerMeta?.runtime)?.let(::parseRuntimeMinutes)
    } else {
        null
    }
    val eligible = streams
        .filterNot {
            it.mismatchesRequestedContent(
                contentType = requestedType,
                season = activeSeasonNumber,
                episode = activeEpisodeNumber,
                expectedRuntimeMinutes = expectedRuntimeMinutes,
            )
        }
        .filterNot { IgnoredStreamsRepository.isIgnored(it, activeVideoId) }
    return SmartPlayPolicy.rankSmart(
        streams = eligible,
        context = context,
        debridEnabled = debridEnabled,
    )
        .firstOrNull { stream ->
            val height = stream.streamQuality().height
            height != null &&
                height <= targetHeight &&
                stream.isSelectableForPlayback(debridEnabled) &&
                !stream.isActiveSource(this)
        }
}

internal fun StreamItem.isActiveSource(runtime: PlayerScreenRuntime): Boolean =
    playableDirectUrl?.let { it == runtime.activeSourceUrl } == true ||
        infoHash?.equals(runtime.activeTorrentInfoHash, ignoreCase = true) == true

private const val SOURCE_WAIT_MS = 8_000L

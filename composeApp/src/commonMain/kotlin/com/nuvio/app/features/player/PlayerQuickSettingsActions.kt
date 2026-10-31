package com.nuvio.app.features.player

import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.details.parseRuntimeMinutes
import com.nuvio.app.features.streams.IgnoredStreamsRepository
import com.nuvio.app.features.streams.SmartPlayPolicy
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.streams.currentSmartPlayContext
import com.nuvio.app.features.streams.isSelectableForPlayback
import com.nuvio.app.features.streams.mismatchesRequestedContent
import com.nuvio.app.features.streams.streamQuality

internal fun PlayerScreenRuntime.openQuickSettings() {
    showQuickSettingsPanel = true
    showIgnoredFilesPanel = false
    showSourcesPanel = false
    showEpisodesPanel = false
    showChaptersPanel = false
    controlsVisible = true
    quickSettingsQualityOverride = activeStream?.streamQuality()?.height
    loadQuickSettingsSources()
}

internal fun PlayerScreenRuntime.loadQuickSettingsSources() {
    IgnoredStreamsRepository.ensureLoaded()
    val videoId = activeVideoId ?: return
    PlayerStreamsRepository.loadSources(
        type = contentType ?: parentMetaType,
        videoId = videoId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
}

internal fun PlayerScreenRuntime.openIgnoredFiles() {
    showIgnoredFilesPanel = true
    showQuickSettingsPanel = false
}

internal fun PlayerScreenRuntime.closeIgnoredFiles() {
    showIgnoredFilesPanel = false
    showQuickSettingsPanel = true
}

internal fun PlayerScreenRuntime.selectQuality(height: Int?) {
    quickSettingsQualityOverride = height

    val requestedType = contentType ?: parentMetaType
    val expectedRuntimeMinutes = if (requestedType.equals("movie", ignoreCase = true)) {
        (metaUiState.meta?.runtime ?: playerMeta?.runtime)?.let(::parseRuntimeMinutes)
    } else {
        null
    }
    val candidates = PlayerStreamsRepository.sourceState.value.groups.flatMap { it.streams }
        .filterNot {
            it.mismatchesRequestedContent(
                contentType = requestedType,
                season = activeSeasonNumber,
                episode = activeEpisodeNumber,
                expectedRuntimeMinutes = expectedRuntimeMinutes,
            )
        }
        .filterNot { IgnoredStreamsRepository.isIgnored(it, activeVideoId) }
    if (candidates.isEmpty()) return

    val debridEnabled = DebridSettingsRepository.snapshot().canResolvePlayableLinks
    val context = currentSmartPlayContext(playerSettingsUiState.smartPlayHdrPreference)
        .copy(maxHeightOverride = height ?: 0)
    val fallbackRanked = SmartPlayPolicy.rankSmart(
        streams = candidates,
        context = context,
        debridEnabled = debridEnabled,
    )
    val exactRanked = if (height != null) {
        val exactHeight = candidates.filter { it.streamQuality().height == height }
        if (exactHeight.isNotEmpty()) {
            SmartPlayPolicy.rankSmart(
                streams = exactHeight,
                context = context.copy(maxHeightOverride = 0),
                debridEnabled = debridEnabled,
            )
        } else {
            emptyList()
        }
    } else {
        emptyList()
    }
    val ordered = (exactRanked + fallbackRanked)
        .distinctBy {
            it.playerSourceIdentityKey()
                ?: "${it.addonId}:${it.name.orEmpty()}:${it.behaviorHints.filename.orEmpty()}"
        }
        .filter { it.isSelectableForPlayback(debridEnabled) && !it.isActiveSource(this) }
    val stream = ordered.firstOrNull() ?: return

    val resumePositionMs = lastStablePositionMs.coerceAtLeast(0L)
        .takeIf { it > 0L }
        ?: playbackSnapshot.positionMs.coerceAtLeast(0L)
    pendingQualityCandidates = ordered.drop(1)
    pendingQualityPositionMs = resumePositionMs
    pendingQualityPreviousStream = activeStream
    isSourceSwitchInProgress = true
    activeInitialPositionMs = resumePositionMs
    showQuickSettingsPanel = false
    qualitySwitchGeneration += 1
    switchToSource(stream, saveForReuse = false)
}

internal fun PlayerScreenRuntime.ignoreActiveFile(): Boolean {
    val stream = activeStream ?: return false
    val videoId = activeVideoId ?: return false
    IgnoredStreamsRepository.ignore(
        stream = stream,
        videoId = videoId,
        contentTitle = activeEpisodeTitle?.takeIf { it.isNotBlank() } ?: title,
    ) ?: return false

    PlayerStreamsRepository.filterIgnored(videoId)
    StreamsRepository.filterIgnored(videoId)
    showQuickSettingsPanel = false
    return true
}

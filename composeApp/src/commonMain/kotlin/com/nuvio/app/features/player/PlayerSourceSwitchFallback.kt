package com.nuvio.app.features.player

import com.nuvio.app.features.streams.streamQuality

internal fun PlayerScreenRuntime.clearQualitySwitchState() {
    qualitySwitchGeneration += 1
    isSourceSwitchInProgress = false
    pendingQualityCandidates = emptyList()
    pendingQualityPreviousStream = null
}

internal fun PlayerScreenRuntime.completeSourceSwitchIfStarted(snapshot: PlayerPlaybackSnapshot) {
    if (!isSourceSwitchInProgress) return
    if (snapshot.isLoading) return
    if (!snapshot.isPlaying && snapshot.positionMs <= 0L) return

    isSourceSwitchInProgress = false
    pendingQualityCandidates = emptyList()
    pendingQualityPreviousStream = null
    if (quickSettingsQualityOverride != null) {
        activeStream?.streamQuality()?.height
            ?.takeIf { it > 0 }
            ?.let { quickSettingsQualityOverride = it }
    }
}

internal fun PlayerScreenRuntime.tryHandleQualitySwitchFailure(): Boolean {
    if (!isSourceSwitchInProgress) return false

    val next = pendingQualityCandidates.firstOrNull()
    if (next != null) {
        pendingQualityCandidates = pendingQualityCandidates.drop(1)
        errorMessage = null
        activeInitialPositionMs = pendingQualityPositionMs
        lastStablePositionMs = pendingQualityPositionMs
        isSourceSwitchInProgress = true
        switchToSource(next, saveForReuse = false)
        return true
    }

    val previous = pendingQualityPreviousStream
    pendingQualityPreviousStream = null
    if (previous != null) {
        errorMessage = null
        activeInitialPositionMs = pendingQualityPositionMs
        lastStablePositionMs = pendingQualityPositionMs
        isSourceSwitchInProgress = true
        switchToSource(previous, saveForReuse = false)
        return true
    }

    isSourceSwitchInProgress = false
    pendingQualityCandidates = emptyList()
    return false
}

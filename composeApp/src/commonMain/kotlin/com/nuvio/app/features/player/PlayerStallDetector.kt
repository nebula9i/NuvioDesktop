package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.streamQuality

internal class PlayerStallDetector(
    private val windowMs: Long = 60_000L,
    private val threshold: Int = 3,
    private val minStallMs: Long = 1_000L,
    private val seekGraceMs: Long = 2_000L,
) {
    private val stallTimesMs = ArrayDeque<Long>()
    private var loadingStartedAtMs: Long? = null
    private var wasLoading = false
    private var ignoreUntilMs = 0L
    private var ready = false
    private var triggered = false

    fun onReady() {
        ready = true
    }

    fun onSeek(nowMs: Long) {
        ignoreUntilMs = nowMs + seekGraceMs
    }

    fun onLoadingChanged(isLoading: Boolean, nowMs: Long): Boolean {
        val previous = wasLoading
        wasLoading = isLoading
        if (!ready || previous == isLoading) return false

        if (isLoading) {
            loadingStartedAtMs = nowMs
            return false
        }

        val startedAt = loadingStartedAtMs ?: return false
        loadingStartedAtMs = null
        if (nowMs < ignoreUntilMs) return false
        if (nowMs - startedAt < minStallMs) return false

        stallTimesMs.addLast(nowMs)
        while (stallTimesMs.isNotEmpty() && stallTimesMs.first() < nowMs - windowMs) {
            stallTimesMs.removeFirst()
        }
        if (triggered || stallTimesMs.size < threshold) return false
        triggered = true
        return true
    }

    fun reset() {
        stallTimesMs.clear()
        loadingStartedAtMs = null
        wasLoading = false
        ignoreUntilMs = 0L
        ready = false
        triggered = false
    }
}

internal fun nextLowerQualityHeight(currentHeight: Int): Int? =
    QUALITY_HEIGHTS.firstOrNull { it < currentHeight }

internal fun PlayerScreenRuntime.trackStallDowngrade(snapshot: PlayerPlaybackSnapshot) {
    if (initialLoadCompleted) stallDetector.onReady()

    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    if (isScrubbingTimeline || scrubbingPositionMs != null) stallDetector.onSeek(nowMs)

    val triggered = stallDetector.onLoadingChanged(
        isLoading = snapshot.isLoading && shouldPlay,
        nowMs = nowMs,
    )
    if (!triggered) return
    if (playerSettingsUiState.streamAutoPlayMode != StreamAutoPlayMode.SMART_PLAY) return
    if (showStallDowngradeOffer || stallDowngradeInProgress) return

    val currentHeight = snapshot.videoHeight.takeIf { it > 0 }
        ?: streamMediaInfo.videoHeight
        ?: activeStream?.streamQuality()?.height
        ?: return
    val targetHeight = nextLowerQualityHeight(currentHeight) ?: return

    stallDowngradeTargetHeight = targetHeight
    showStallDowngradeOffer = true
}

internal fun PlayerScreenRuntime.resetStallDetection() {
    stallDetector.reset()
    // The new source starts with its own initial buffer; ignore it before counting stalls.
    stallDetector.onSeek(kotlin.time.Clock.System.now().toEpochMilliseconds() + SWITCH_GRACE_MS)
    showStallDowngradeOffer = false
    stallDowngradeTargetHeight = null
}

private val QUALITY_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
private const val SWITCH_GRACE_MS = 8_000L

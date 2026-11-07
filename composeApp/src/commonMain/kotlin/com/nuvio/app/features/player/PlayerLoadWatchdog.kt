package com.nuvio.app.features.player

import kotlin.time.Clock

/** How long a source may sit in the loading state without any progress before we react. */
private const val LOAD_WATCHDOG_TIMEOUT_MS = 25_000L

/**
 * Watches for a source that never starts delivering data.
 *
 * The stall detector only reacts to repeated buffering *after* playback started, so a source that
 * never loads at all (dead link, blocked CDN, unsupported container) used to leave the player on
 * the buffering screen forever with no download speed and no way out. Progress is any of: position
 * moving, the buffer growing, or bytes arriving.
 */
internal fun PlayerScreenRuntime.trackLoadWatchdog(snapshot: PlayerPlaybackSnapshot) {
    // Only watch once an engine is attached to the current source: resolving a debrid link or
    // fetching torrent metadata can legitimately take a while and has its own progress UI.
    val watching = errorMessage == null &&
        shouldPlay &&
        snapshot.isLoading &&
        playerController != null &&
        activeTorrentInfoHash == null
    if (!watching) {
        resetLoadWatchdog()
        return
    }
    val now = Clock.System.now().toEpochMilliseconds()
    val progressed = snapshot.positionMs != loadWatchdogPositionMs ||
        snapshot.bufferedPositionMs != loadWatchdogBufferedMs ||
        snapshot.networkSpeedBytesPerSecond > 0L
    if (progressed) {
        loadWatchdogPositionMs = snapshot.positionMs
        loadWatchdogBufferedMs = snapshot.bufferedPositionMs
        loadWatchdogStartedAtMs = now
        return
    }
    val startedAt = loadWatchdogStartedAtMs
    if (startedAt == null) {
        loadWatchdogStartedAtMs = now
        return
    }
    if (now - startedAt < LOAD_WATCHDOG_TIMEOUT_MS) return
    // Restart the window so a slow recovery does not fire the watchdog again immediately.
    loadWatchdogStartedAtMs = now
    onSourceLoadTimeout()
}

/** A file that ends this much before its reported duration is treated as broken. */
private const val EARLY_END_TOLERANCE_MS = 120_000L

/**
 * Catches a source that opens, plays for a while and then dies - typically a file that was removed
 * from the debrid server or is still being fetched there. mpv reports it as an early end of file,
 * so instead of leaving the viewer on a frozen frame the source is replaced automatically.
 */
internal fun PlayerScreenRuntime.trackEarlyPlaybackEnd(snapshot: PlayerPlaybackSnapshot) {
    if (!snapshot.isEnded || errorMessage != null) return
    val durationMs = snapshot.durationMs
    if (durationMs <= 0L) return
    if (snapshot.positionMs >= durationMs - EARLY_END_TOLERANCE_MS) return
    if (isSourceSwitchInProgress || failoverInProgress) return
    failOverToNextSource()
}

internal fun PlayerScreenRuntime.resetLoadWatchdog() {
    loadWatchdogStartedAtMs = null
    loadWatchdogPositionMs = -1L
    loadWatchdogBufferedMs = -1L
}

/**
 * A source that never starts delivering data is replaced automatically. Only when every candidate
 * has been tried does the viewer get an actionable error.
 */
internal fun PlayerScreenRuntime.onSourceLoadTimeout() {
    if (errorMessage != null || isSourceSwitchInProgress) return
    if (failOverToNextSource()) return
    showNoWorkingSourceError()
}

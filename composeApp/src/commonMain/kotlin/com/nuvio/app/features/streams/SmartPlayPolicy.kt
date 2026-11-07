package com.nuvio.app.features.streams

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.display.currentDisplayCapability
import com.nuvio.app.core.network.NetworkSpeedRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository

data class SmartPlayContext(
    val displayMaxHeightPx: Int,
    val downloadMbps: Double?,
    val maxHeightOverride: Int = 0,
    val hdrPreference: SmartPlayHdrPreference = SmartPlayHdrPreference.AUTO,
)

internal fun currentSmartPlayContext(
    hdrPreference: SmartPlayHdrPreference = SmartPlayHdrPreference.AUTO,
): SmartPlayContext {
    NetworkSpeedRepository.refreshIfStale()
    return SmartPlayContext(
        displayMaxHeightPx = currentDisplayCapability().maxHeightPx,
        downloadMbps = NetworkSpeedRepository.snapshot()?.downloadMbps,
        hdrPreference = hdrPreference,
    )
}

internal object SmartPlayPolicy {

    fun bandwidthCapHeight(mbps: Double?): Int = when {
        mbps == null || mbps <= 0.0 -> 0
        mbps < 3.0 -> 480
        mbps < 8.0 -> 720
        mbps < 15.0 -> 1080
        mbps < 30.0 -> 1440
        else -> 2160
    }

    fun targetHeight(context: SmartPlayContext): Int =
        listOf(
            context.displayMaxHeightPx,
            bandwidthCapHeight(context.downloadMbps),
            context.maxHeightOverride,
        )
            .filter { it > 0 }
            .minOrNull() ?: 0

    /**
     * Keeps the source order already presented in the stream list and only filters by the
     * display/bandwidth target plus each source's estimated bitrate. Direct and debrid streams
     * stay ahead of raw P2P streams. Within one source, the highest fitting resolution wins,
     * then the lighter file at the same resolution; the remaining fitting streams come next,
     * then the smallest streams above the target as a last resort.
     */
    fun rankSmart(
        streams: List<StreamItem>,
        context: SmartPlayContext,
        preferredBingeGroup: String? = null,
        debridEnabled: Boolean = true,
    ): List<StreamItem> {
        if (streams.isEmpty()) return emptyList()
        val target = targetHeight(context)
        val preferredGroup = preferredBingeGroup?.trim().orEmpty()

        val sourceOrder = mutableMapOf<String, Int>()
        val ranked = streams.mapIndexed { index, stream ->
            if (stream.addonId !in sourceOrder) {
                sourceOrder[stream.addonId] = index
            }
            val quality = stream.streamQuality()
            RankedStream(
                stream = stream,
                quality = quality,
                requiredMbps = requiredMbps(quality),
                readiness = readiness(stream, debridEnabled),
                sourceOrder = sourceOrder.getValue(stream.addonId),
                originalIndex = index,
            )
        }

        val withoutCam = ranked.filterNot { it.quality.isCam }
        val pool = withoutCam.ifEmpty { ranked }

        val groups = pool
            .groupBy { it.readiness to it.stream.addonId }
            .values
            .sortedWith(compareBy({ it.first().readiness }, { it.first().sourceOrder }))

        val bandwidthLimit = context.downloadMbps?.takeIf { it > 0.0 }?.times(0.9) ?: 0.0
        val fitting = mutableListOf<StreamItem>()
        val heightFitting = mutableListOf<RankedStream>()
        val heavyFallback = mutableListOf<StreamItem>()
        val overTargetFallback = mutableListOf<StreamItem>()
        groups.forEach { group ->
            val (fitsHeight, overTarget) = group.partition { it.fitsHeight(target) }
            val (fitsBandwidth, heavy) = fitsHeight.partition { it.fitsBandwidth(bandwidthLimit) }
            heightFitting += fitsHeight
            fitsBandwidth.sortedWith(fitsComparator(preferredGroup, context.hdrPreference))
                .forEach { fitting += it.stream }
            heavy.sortedWith(overTargetComparator(preferredGroup, context.hdrPreference))
                .forEach { heavyFallback += it.stream }
            overTarget.sortedWith(overTargetComparator(preferredGroup, context.hdrPreference))
                .forEach { overTargetFallback += it.stream }
        }
        if (fitting.isNotEmpty()) {
            return fitting + heavyFallback + overTargetFallback
        }
        if (heightFitting.isNotEmpty()) {
            return heightFitting
                .sortedWith(lightestComparator(preferredGroup, context.hdrPreference))
                .map { it.stream } + overTargetFallback
        }
        return overTargetFallback
    }

    private fun readiness(stream: StreamItem, debridEnabled: Boolean): Int = when {
        // Already stored on the debrid provider: starts instantly, no download, no dead link.
        debridEnabled && stream.isCachedDebridTorrentStream -> CACHED
        stream.serverTarget != null || stream.playableDirectUrl != null -> READY
        debridEnabled && stream.isDirectDebridStream -> READY
        debridEnabled && stream.isAddonDebridCandidate -> DEBRID_CANDIDATE
        P2pSettingsRepository.isPlaybackEnabled &&
            stream.needsLocalDebridResolve &&
            stream.p2pInfoHash != null -> P2P
        else -> OTHER
    }

    private fun RankedStream.fitsHeight(target: Int): Boolean {
        val height = quality.height
        return height == null || target <= 0 || height <= target
    }

    private fun RankedStream.fitsBandwidth(limit: Double): Boolean =
        limit <= 0.0 || requiredMbps == null || requiredMbps <= limit

    private fun fitsComparator(
        preferredGroup: String,
        hdrPreference: SmartPlayHdrPreference,
    ): Comparator<RankedStream> =
        compareByDescending<RankedStream> { it.quality.height ?: -1 }
            .thenByDescending { it.hdrScore(hdrPreference) }
            .thenByDescending { it.bingeMatch(preferredGroup) }
            .thenBy { it.requiredMbps ?: Double.MAX_VALUE }
            .thenBy { it.originalIndex }

    private fun overTargetComparator(
        preferredGroup: String,
        hdrPreference: SmartPlayHdrPreference,
    ): Comparator<RankedStream> =
        compareBy<RankedStream> { it.quality.height ?: Int.MAX_VALUE }
            .thenByDescending { it.hdrScore(hdrPreference) }
            .thenByDescending { it.bingeMatch(preferredGroup) }
            .thenBy { it.requiredMbps ?: Double.MAX_VALUE }
            .thenBy { it.originalIndex }

    private fun lightestComparator(
        preferredGroup: String,
        hdrPreference: SmartPlayHdrPreference,
    ): Comparator<RankedStream> =
        compareBy<RankedStream> { it.requiredMbps ?: Double.MAX_VALUE }
            .thenByDescending { it.quality.height ?: -1 }
            .thenByDescending { it.hdrScore(hdrPreference) }
            .thenByDescending { it.bingeMatch(preferredGroup) }
            .thenBy { it.originalIndex }

    private fun requiredMbps(quality: StreamQuality): Double? {
        val sizeBytes = quality.sizeBytes ?: return null
        val runtimeMinutes = quality.runtimeMinutes ?: return null
        if (sizeBytes <= 0L || runtimeMinutes <= 0) return null
        return sizeBytes * 8.0 / (runtimeMinutes * 60.0) / 1_000_000.0
    }

    private fun RankedStream.bingeMatch(preferredGroup: String): Boolean =
        preferredGroup.isNotEmpty() && stream.behaviorHints.bingeGroup == preferredGroup

    private fun RankedStream.hdrScore(preference: SmartPlayHdrPreference): Int = when (preference) {
        SmartPlayHdrPreference.AUTO -> 0
        SmartPlayHdrPreference.PREFER_HDR -> if (quality.isHdr || quality.isDolbyVision) 1 else 0
        SmartPlayHdrPreference.PREFER_SDR -> if (!quality.isHdr && !quality.isDolbyVision) 1 else 0
    }

    private data class RankedStream(
        val stream: StreamItem,
        val quality: StreamQuality,
        val requiredMbps: Double?,
        val readiness: Int,
        val sourceOrder: Int,
        val originalIndex: Int,
    )

    private const val CACHED = 0
    private const val READY = 1
    private const val DEBRID_CANDIDATE = 2
    private const val P2P = 3
    private const val OTHER = 4
}

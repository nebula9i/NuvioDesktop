package com.nuvio.app.features.streams

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.display.currentDisplayCapability
import com.nuvio.app.core.network.NetworkSpeedRepository

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
     * display/bandwidth target. Direct and debrid streams stay ahead of raw P2P streams.
     * Within one source, the highest fitting resolution wins; the remaining fitting streams
     * come next, then the smallest streams above the target as a last resort.
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
            RankedStream(
                stream = stream,
                quality = stream.streamQuality(),
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

        val fitting = mutableListOf<StreamItem>()
        val fallback = mutableListOf<StreamItem>()
        groups.forEach { group ->
            val (fits, overTarget) = group.partition { it.fits(target) }
            fits.sortedWith(fitsComparator(preferredGroup, context.hdrPreference)).forEach { fitting += it.stream }
            overTarget.sortedWith(overTargetComparator(preferredGroup, context.hdrPreference))
                .forEach { fallback += it.stream }
        }
        return fitting + fallback
    }

    private fun readiness(stream: StreamItem, debridEnabled: Boolean): Int = when {
        stream.serverTarget != null || stream.playableDirectUrl != null -> READY
        debridEnabled && (stream.isDirectDebridStream || stream.isCachedDebridTorrentStream) -> READY
        debridEnabled && stream.isAddonDebridCandidate -> DEBRID_CANDIDATE
        AppFeaturePolicy.p2pEnabled &&
            stream.needsLocalDebridResolve &&
            stream.p2pInfoHash != null -> P2P
        else -> OTHER
    }

    private fun RankedStream.fits(target: Int): Boolean {
        val height = quality.height
        return height == null || target <= 0 || height <= target
    }

    private fun fitsComparator(
        preferredGroup: String,
        hdrPreference: SmartPlayHdrPreference,
    ): Comparator<RankedStream> =
        compareByDescending<RankedStream> { it.quality.height ?: -1 }
            .thenByDescending { it.hdrScore(hdrPreference) }
            .thenByDescending { it.bingeMatch(preferredGroup) }
            .thenBy { it.originalIndex }

    private fun overTargetComparator(
        preferredGroup: String,
        hdrPreference: SmartPlayHdrPreference,
    ): Comparator<RankedStream> =
        compareBy<RankedStream> { it.quality.height ?: Int.MAX_VALUE }
            .thenByDescending { it.hdrScore(hdrPreference) }
            .thenByDescending { it.bingeMatch(preferredGroup) }
            .thenBy { it.originalIndex }

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
        val readiness: Int,
        val sourceOrder: Int,
        val originalIndex: Int,
    )

    private const val READY = 0
    private const val DEBRID_CANDIDATE = 1
    private const val P2P = 2
    private const val OTHER = 3
}

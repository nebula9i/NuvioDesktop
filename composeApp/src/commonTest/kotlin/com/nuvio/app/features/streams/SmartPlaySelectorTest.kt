package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartPlaySelectorTest {

    @Test
    fun `smart play picks the best fit and keeps a ranked fallback list`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)
        val hd = stream("720p", "https://example.com/720.mp4", 720)

        val evaluation = evaluate(
            streams = listOf(uhd, hd, fhd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(fhd, evaluation.stream)
        assertEquals(listOf(fhd, hd, uhd), evaluation.readyStreams)
    }

    @Test
    fun `smart play prefers 720p on a slow connection`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)
        val hd = stream("720p", "https://example.com/720.mp4", 720)

        val evaluation = evaluate(
            streams = listOf(uhd, fhd, hd),
            context = SmartPlayContext(displayMaxHeightPx = 2160, downloadMbps = 5.0),
        )

        assertEquals(hd, evaluation.stream)
    }

    @Test
    fun `smart play does not let a binge group break the display cap`() {
        val uhdPreferred = stream("4K", "https://example.com/4k.mp4", 2160, bingeGroup = "show")
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)

        val evaluation = evaluate(
            streams = listOf(uhdPreferred, fhd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
            preferredBingeGroup = "show",
            preferBingeGroupInSelection = true,
        )

        assertEquals(fhd, evaluation.stream)
    }

    @Test
    fun `smart play keeps the binge group within the same tier`() {
        val other = stream("1080p", "https://example.com/other.mp4", 1080, bingeGroup = "other")
        val preferred = stream("1080p", "https://example.com/preferred.mp4", 1080, bingeGroup = "show")

        val evaluation = evaluate(
            streams = listOf(other, preferred),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
            preferredBingeGroup = "show",
            preferBingeGroupInSelection = true,
        )

        assertEquals(preferred, evaluation.stream)
    }

    @Test
    fun `smart play works without a context by ranking known resolutions`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)

        val evaluation = evaluate(streams = listOf(fhd, uhd), context = null)

        assertEquals(uhd, evaluation.stream)
        assertEquals(listOf(uhd, fhd), evaluation.readyStreams)
    }

    @Test
    fun `smart play falls back to a cam only when nothing else is playable`() {
        val cam = stream("Movie.HDCAM", "https://example.com/cam.mp4", 1080)
        val hd = stream("Movie.720p", "https://example.com/720.mp4", 720)

        val evaluation = evaluate(
            streams = listOf(cam, hd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(hd, evaluation.stream)
        assertEquals(listOf(hd), evaluation.readyStreams)
    }

    @Test
    fun `existing modes are unchanged when no smart context is supplied`() {
        val first = stream("1080p", "https://example.com/first.mp4", 1080)
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)

        val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
            streams = listOf(first, uhd),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
        )

        assertEquals(first, evaluation.stream)
    }

    @Test
    fun `manual mode still returns no stream`() {
        val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
            streams = listOf(stream("1080p", "https://example.com/1080.mp4", 1080)),
            mode = StreamAutoPlayMode.MANUAL,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            smartPlayContext = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertNull(evaluation.stream)
    }

    private fun evaluate(
        streams: List<StreamItem>,
        context: SmartPlayContext?,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
    ): StreamAutoPlayEvaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
        streams = streams,
        mode = StreamAutoPlayMode.SMART_PLAY,
        regexPattern = "",
        source = StreamAutoPlaySource.ALL_SOURCES,
        installedAddonNames = setOf("Addon"),
        selectedAddons = emptySet(),
        selectedPlugins = emptySet(),
        preferredBingeGroup = preferredBingeGroup,
        preferBingeGroupInSelection = preferBingeGroupInSelection,
        smartPlayContext = context,
    )

    private fun stream(
        name: String,
        url: String,
        height: Int?,
        bingeGroup: String? = null,
    ): StreamItem = StreamItem(
        name = name,
        url = url,
        addonName = "Addon",
        addonId = "addon:Addon",
        behaviorHints = StreamBehaviorHints(bingeGroup = bingeGroup),
        clientResolve = height?.let { resolvedHeight ->
            StreamClientResolve(
                type = "debrid",
                service = "torbox",
                isCached = true,
                stream = StreamClientResolveStream(
                    raw = StreamClientResolveRaw(
                        parsed = StreamClientResolveParsed(resolution = "${resolvedHeight}p"),
                    ),
                ),
            )
        },
    )
}

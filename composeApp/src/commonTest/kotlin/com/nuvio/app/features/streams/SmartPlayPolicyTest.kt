package com.nuvio.app.features.streams

import com.nuvio.app.core.network.downloadMbps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartPlayPolicyTest {

    @Test
    fun `bandwidth tiers map to conservative heights`() {
        assertEquals(0, SmartPlayPolicy.bandwidthCapHeight(null))
        assertEquals(0, SmartPlayPolicy.bandwidthCapHeight(0.0))
        assertEquals(480, SmartPlayPolicy.bandwidthCapHeight(2.9))
        assertEquals(720, SmartPlayPolicy.bandwidthCapHeight(7.9))
        assertEquals(1080, SmartPlayPolicy.bandwidthCapHeight(14.9))
        assertEquals(1440, SmartPlayPolicy.bandwidthCapHeight(29.9))
        assertEquals(2160, SmartPlayPolicy.bandwidthCapHeight(30.0))
    }

    @Test
    fun `target height is the smallest of display bandwidth and override`() {
        val display4k = SmartPlayContext(displayMaxHeightPx = 2160, downloadMbps = 10.0)
        val override = display4k.copy(maxHeightOverride = 720)
        val unknownSpeed = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = null)

        assertEquals(1080, SmartPlayPolicy.targetHeight(display4k))
        assertEquals(720, SmartPlayPolicy.targetHeight(override))
        assertEquals(1080, SmartPlayPolicy.targetHeight(unknownSpeed))
        assertEquals(0, SmartPlayPolicy.targetHeight(SmartPlayContext(0, null)))
    }

    @Test
    fun `1080 display picks 1080 over 4K even when all streams are ready`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)
        val hd = stream("720p", "https://example.com/720.mp4", 720)

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(uhd, hd, fhd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(fhd, hd, uhd), ranked)
    }

    @Test
    fun `slow connection caps a 4K display at 720p`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)
        val hd = stream("720p", "https://example.com/720.mp4", 720)
        val sd = stream("480p", "https://example.com/480.mp4", 480)

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(uhd, fhd, sd, hd),
            context = SmartPlayContext(displayMaxHeightPx = 2160, downloadMbps = 5.0),
        )

        assertEquals(listOf(hd, sd, fhd, uhd), ranked)
    }

    @Test
    fun `when nothing fits the smallest overshoot is preferred`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val qhd = stream("1440p", "https://example.com/1440.mp4", 1440)

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(uhd, qhd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 10.0),
        )

        assertEquals(listOf(qhd, uhd), ranked)
    }

    @Test
    fun `unknown resolution ranks before streams above the target`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val unknown = stream("Direct", "https://example.com/direct.mp4", null)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(uhd, unknown, fhd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(fhd, unknown, uhd), ranked)
    }

    @Test
    fun `binge group breaks ties within the same tier`() {
        val other = stream("1080p", "https://example.com/other.mp4", 1080, bingeGroup = "other")
        val preferred = stream("1080p", "https://example.com/preferred.mp4", 1080, bingeGroup = "show")

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(other, preferred),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
            preferredBingeGroup = "show",
        )

        assertEquals(listOf(preferred, other), ranked)
    }

    @Test
    fun `cams are dropped unless they are the only option`() {
        val cam = stream("Movie.HDCAM", "https://example.com/cam.mp4", 1080)
        val hd = stream("Movie.720p", "https://example.com/720.mp4", 720)

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(cam, hd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )
        val onlyCams = SmartPlayPolicy.rankSmart(
            streams = listOf(cam),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(hd), ranked)
        assertEquals(listOf(cam), onlyCams)
    }

    @Test
    fun `no target keeps the highest quality first`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160)
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080)

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(fhd, uhd),
            context = SmartPlayContext(displayMaxHeightPx = 0, downloadMbps = null),
        )

        assertEquals(listOf(uhd, fhd), ranked)
    }

    @Test
    fun `preferred source order wins over a higher resolution from a later source`() {
        val preferred = stream(
            "Preferred 720p",
            "https://example.com/preferred.mp4",
            720,
            addonName = "Preferred",
        )
        val later = stream(
            "Later 1080p",
            "https://example.com/later.mp4",
            1080,
            addonName = "Later",
        )

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(preferred, later),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(preferred, later), ranked)
    }

    @Test
    fun `a fitting later source beats an above-target preferred source`() {
        val preferred4k = stream(
            "Preferred 4K",
            "https://example.com/preferred-4k.mp4",
            2160,
            addonName = "Preferred",
        )
        val later1080 = stream(
            "Later 1080p",
            "https://example.com/later-1080.mp4",
            1080,
            addonName = "Later",
        )

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(preferred4k, later1080),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(later1080, preferred4k), ranked)
    }

    @Test
    fun `within the preferred source the highest fitting resolution wins`() {
        val uhd = stream("4K", "https://example.com/4k.mp4", 2160, addonName = "Preferred")
        val fhd = stream("1080p", "https://example.com/1080.mp4", 1080, addonName = "Preferred")
        val hd = stream("720p", "https://example.com/720.mp4", 720, addonName = "Preferred")

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(uhd, hd, fhd),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(fhd, hd, uhd), ranked)
    }

    @Test
    fun `direct and debrid streams stay ahead of p2p`() {
        val p2p = stream(
            name = "Torrent 1080p",
            url = null,
            height = 1080,
            addonName = "P2P",
            infoHash = "hash",
            debridResolve = false,
        )
        val debrid = stream(
            name = "Debrid 1080p",
            url = "https://example.com/debrid.mp4",
            height = 1080,
            addonName = "Debrid",
        )

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(p2p, debrid),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
            debridEnabled = false,
        )

        assertEquals(listOf(debrid, p2p), ranked)
    }

    @Test
    fun `a lighter file wins when the heavier one exceeds the measured bandwidth`() {
        val heavy = stream(
            "1080p Heavy",
            "https://example.com/heavy.mp4",
            1080,
            sizeBytes = 20_000_000_000L,
            durationSeconds = 100 * 60L,
        )
        val light = stream(
            "1080p Light",
            "https://example.com/light.mp4",
            1080,
            sizeBytes = 5_000_000_000L,
            durationSeconds = 100 * 60L,
        )

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(heavy, light),
            context = SmartPlayContext(displayMaxHeightPx = 1080, downloadMbps = 20.0),
        )

        assertEquals(listOf(light, heavy), ranked)
    }

    @Test
    fun `a lighter 4K source can beat a heavier 4K remux`() {
        val heavy = stream(
            "4K Remux",
            "https://example.com/remux.mp4",
            2160,
            sizeBytes = 50_000_000_000L,
            durationSeconds = 100 * 60L,
        )
        val light = stream(
            "4K Light",
            "https://example.com/light-4k.mp4",
            2160,
            sizeBytes = 10_000_000_000L,
            durationSeconds = 100 * 60L,
        )

        val ranked = SmartPlayPolicy.rankSmart(
            streams = listOf(heavy, light),
            context = SmartPlayContext(displayMaxHeightPx = 2160, downloadMbps = 35.0),
        )

        assertEquals(listOf(light, heavy), ranked)
    }

    @Test
    fun `downloadMbps computes sustained throughput`() {
        assertTrue(downloadMbps(1_000_000, 1_000)!!.let { it > 7.9 && it < 8.1 })
        assertEquals(null, downloadMbps(0, 1_000))
        assertEquals(null, downloadMbps(1_000, 0))
    }

    private fun stream(
        name: String,
        url: String?,
        height: Int?,
        bingeGroup: String? = null,
        addonName: String = "Addon",
        infoHash: String? = null,
        debridResolve: Boolean = true,
        sizeBytes: Long? = null,
        durationSeconds: Long? = null,
    ): StreamItem = StreamItem(
        name = name,
        url = url,
        infoHash = infoHash,
        addonName = addonName,
        addonId = "addon:$addonName",
        behaviorHints = StreamBehaviorHints(
            bingeGroup = bingeGroup,
            videoSize = sizeBytes,
        ),
        clientResolve = if (debridResolve && (height != null || durationSeconds != null)) {
            StreamClientResolve(
                type = "debrid",
                service = "torbox",
                isCached = true,
                stream = StreamClientResolveStream(
                    raw = StreamClientResolveRaw(
                        parsed = StreamClientResolveParsed(
                            resolution = height?.let { "${it}p" },
                            duration = durationSeconds,
                        ),
                    ),
                ),
            )
        } else {
            null
        },
    )
}

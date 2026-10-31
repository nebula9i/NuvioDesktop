package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamQualityTest {

    @Test
    fun `parsed metadata takes precedence over the file name`() {
        val stream = stream(
            name = "Movie.2024.2160p.WEB-DL.mkv",
            parsedResolution = "1080p",
        )

        assertEquals(1080, stream.streamQuality().height)
    }

    @Test
    fun `resolution is parsed from common release names`() {
        assertEquals(2160, "Movie.2024.4K.HDR.mkv".toHeight())
        assertEquals(2160, "Movie.2160p.WEB.mkv".toHeight())
        assertEquals(1440, "Movie.1440p.mkv".toHeight())
        assertEquals(1080, "Movie.1080p.WEB.x264.mkv".toHeight())
        assertEquals(1080, "Movie.FHD.mkv".toHeight())
        assertEquals(1080, "1920x1080".toHeight())
        assertEquals(720, "Movie.720p.HDTV.mkv".toHeight())
        assertEquals(720, "Movie.HD.mkv".toHeight())
        assertEquals(480, "Movie.480p.DVDRip.mkv".toHeight())
        assertEquals(480, "Movie.SD.mkv".toHeight())
        assertEquals(360, "Movie.360p.mkv".toHeight())
        assertNull("Movie.mkv".toHeight())
    }

    @Test
    fun `cam releases are detected but dts audio is not a telesync`() {
        val cam = stream(name = "Movie.2024.HDCAM.x264")
        val rip = stream(name = "Movie.2024.DTS.1080p.BluRay")

        assertTrue(cam.streamQuality().isCam)
        assertFalse(rip.streamQuality().isCam)
    }

    @Test
    fun `hdr and dolby vision are detected from parsed metadata and text`() {
        val parsed = stream(name = "Movie.2160p", parsedHdr = listOf("HDR10", "Dolby Vision"))
        val text = stream(name = "Movie.2160p.DV.HDR10.mkv")

        assertTrue(parsed.streamQuality().isHdr)
        assertTrue(parsed.streamQuality().isDolbyVision)
        assertTrue(text.streamQuality().isHdr)
        assertTrue(text.streamQuality().isDolbyVision)
    }

    @Test
    fun `size falls back from behavior hints to parsed torrent size`() {
        val hinted = stream(name = "Movie.1080p", videoSize = 4_000_000_000L)
        val resolved = stream(name = "Movie.1080p", parsedSize = 8_000_000_000L)

        assertEquals(4_000_000_000L, hinted.streamQuality().sizeBytes)
        assertEquals(8_000_000_000L, resolved.streamQuality().sizeBytes)
    }

    private fun stream(
        name: String?,
        parsedResolution: String? = null,
        parsedHdr: List<String> = emptyList(),
        parsedSize: Long? = null,
        videoSize: Long? = null,
    ): StreamItem = StreamItem(
        name = name,
        url = "https://example.com/video.mp4",
        addonName = "Addon",
        addonId = "addon:Addon",
        behaviorHints = StreamBehaviorHints(videoSize = videoSize),
        clientResolve = if (parsedResolution != null || parsedHdr.isNotEmpty() || parsedSize != null) {
            StreamClientResolve(
                type = "debrid",
                service = "torbox",
                isCached = true,
                stream = StreamClientResolveStream(
                    raw = StreamClientResolveRaw(
                        size = parsedSize,
                        parsed = StreamClientResolveParsed(
                            resolution = parsedResolution,
                            hdr = parsedHdr,
                        ),
                    ),
                ),
            )
        } else {
            null
        },
    )
}

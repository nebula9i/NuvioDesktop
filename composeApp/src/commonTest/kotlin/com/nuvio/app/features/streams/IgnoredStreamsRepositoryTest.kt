package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals

class IgnoredStreamsRepositoryTest {

    @Test
    fun `torrent identity includes hash and file index`() {
        val stream = StreamItem(
            infoHash = "ABC123",
            fileIdx = 2,
            addonName = "Addon",
            addonId = "addon:Addon",
        )

        assertEquals(
            "torrent:abc123:2",
            IgnoredStreamsRepository.keyFor(stream, "episode-1"),
        )
    }

    @Test
    fun `same file from another addon shares the identity`() {
        val first = stream(filename = "Show.S04E01.1080p.mkv", addonName = "A")
        val second = stream(filename = "show.s04e01.1080p.mkv", addonName = "B")

        assertEquals(
            IgnoredStreamsRepository.keyFor(first, "episode-1"),
            IgnoredStreamsRepository.keyFor(second, "episode-1"),
        )
    }

    @Test
    fun `identity survives a different episode id`() {
        val stream = stream(filename = "Show.S04E01.1080p.mkv")

        assertEquals(
            IgnoredStreamsRepository.keyFor(stream, "episode-1"),
            IgnoredStreamsRepository.keyFor(stream, "episode-2"),
        )
    }

    private fun stream(filename: String, addonName: String = "Addon"): StreamItem = StreamItem(
        name = filename,
        addonName = addonName,
        addonId = "addon:$addonName",
        behaviorHints = StreamBehaviorHints(filename = filename),
    )
}

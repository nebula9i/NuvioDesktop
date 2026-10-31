package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamEpisodeMatcherTest {

    @Test
    fun `clear filename mismatch is rejected`() {
        val wrongEpisode = stream(name = "Show.S04E02.1080p.WEB.mkv")
        val wrongSeason = stream(name = "Show.S01E01.1080p.WEB.mkv")
        val correct = stream(name = "Show.S04E01.1080p.WEB.mkv")

        assertTrue(wrongEpisode.mismatchesEpisode(4, 1))
        assertTrue(wrongSeason.mismatchesEpisode(4, 1))
        assertFalse(correct.mismatchesEpisode(4, 1))
    }

    @Test
    fun `files without season or episode markers are left alone`() {
        val absolute = stream(name = "One Piece - 1075 [1080p].mkv")
        val plain = stream(name = "Some Episode.mkv")

        assertFalse(absolute.mismatchesEpisode(4, 1))
        assertFalse(plain.mismatchesEpisode(4, 1))
    }

    @Test
    fun `parsed metadata wins over a conflicting filename`() {
        val parsed = stream(
            name = "Show.S01E01.1080p.mkv",
            parsedSeasons = listOf(4),
            parsedEpisodes = listOf(1),
        )

        assertFalse(parsed.mismatchesEpisode(4, 1))
        assertTrue(parsed.mismatchesEpisode(1, 1))
    }

    @Test
    fun `season packs match the selected episode`() {
        val pack = stream(
            name = "Show.S04.1080p.WEB.mkv",
            parsedSeasons = listOf(4),
        )

        assertFalse(pack.mismatchesEpisode(4, 7))
        assertTrue(pack.mismatchesEpisode(5, 7))
    }

    @Test
    fun `parsed episode ranges match any included episode`() {
        val range = stream(
            parsedSeasons = listOf(4),
            parsedEpisodes = listOf(1, 2, 3),
        )

        assertFalse(range.mismatchesEpisode(4, 2))
        assertTrue(range.mismatchesEpisode(4, 4))
    }

    @Test
    fun `movies and missing episode context are never filtered`() {
        val stream = stream(name = "Movie.2024.1080p.mkv")

        assertFalse(stream.mismatchesEpisode(null, null))
        assertFalse(stream.mismatchesEpisode(4, null))
        assertFalse(stream.mismatchesEpisode(null, 1))
    }

    @Test
    fun `movie streams with series markers are rejected`() {
        val episodeFile = stream(name = "Solo.Leveling.S01E01.1080p.mkv")
        val seasonPack = stream(name = "Solo Leveling Season 1 Complete 1080p")
        val plain = stream(name = "Solo Leveling -ReAwakening- 1080p")

        assertTrue(episodeFile.mismatchesRequestedContent("movie", null, null, 116))
        assertTrue(seasonPack.mismatchesRequestedContent("movie", null, null, 116))
        assertFalse(plain.mismatchesRequestedContent("movie", null, null, 116))
    }

    @Test
    fun `movie streams with parsed series metadata are rejected`() {
        val parsedSeries = stream(
            name = "Movie.2024.1080p",
            parsedSeasons = listOf(1),
            parsedEpisodes = listOf(1),
        )

        assertTrue(parsedSeries.mismatchesRequestedContent("movie", null, null, 116))
    }

    @Test
    fun `movie runtime mismatch is rejected but a matching runtime is allowed`() {
        val matching = stream(name = "Movie.2024.1080p", parsedDuration = 6_973)
        val tooLong = stream(name = "Movie.2024.1080p", parsedDuration = 18_000)
        val tooShort = stream(name = "Movie.2024.1080p", parsedDuration = 1_800)

        assertFalse(matching.mismatchesRequestedContent("movie", null, null, 116))
        assertTrue(tooLong.mismatchesRequestedContent("movie", null, null, 116))
        assertTrue(tooShort.mismatchesRequestedContent("movie", null, null, 116))
    }

    @Test
    fun `movie streams without runtime metadata are allowed`() {
        val unknown = stream(name = "Movie.2024.1080p")

        assertFalse(unknown.mismatchesRequestedContent("movie", null, null, 116))
        assertFalse(unknown.mismatchesRequestedContent("movie", null, null, null))
    }

    @Test
    fun `series requests keep using the episode guard`() {
        val wrong = stream(name = "Show.S01E01.1080p")
        val correct = stream(name = "Show.S04E01.1080p")

        assertTrue(wrong.mismatchesRequestedContent("series", 4, 1, null))
        assertFalse(correct.mismatchesRequestedContent("series", 4, 1, null))
    }

    private fun stream(
        name: String? = null,
        parsedSeasons: List<Int> = emptyList(),
        parsedEpisodes: List<Int> = emptyList(),
        parsedDuration: Long? = null,
    ): StreamItem = StreamItem(
        name = name,
        addonName = "Addon",
        addonId = "addon:Addon",
        behaviorHints = StreamBehaviorHints(filename = name),
        clientResolve = if (
            parsedSeasons.isNotEmpty() ||
            parsedEpisodes.isNotEmpty() ||
            parsedDuration != null
        ) {
            StreamClientResolve(
                type = "debrid",
                service = "torbox",
                isCached = true,
                stream = StreamClientResolveStream(
                    raw = StreamClientResolveRaw(
                        parsed = StreamClientResolveParsed(
                            seasons = parsedSeasons,
                            episodes = parsedEpisodes,
                            duration = parsedDuration,
                        ),
                    ),
                ),
            )
        } else {
            null
        },
    )
}

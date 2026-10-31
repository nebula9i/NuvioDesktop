package com.nuvio.app.features.streams

/**
 * Returns true only when the stream clearly points at a different episode than the one
 * being requested. Files with no season/episode markers are left alone so absolute
 * numbering and season packs still work.
 */
internal fun StreamItem.mismatchesRequestedContent(
    contentType: String?,
    season: Int?,
    episode: Int?,
    expectedRuntimeMinutes: Int?,
): Boolean {
    if (season != null && episode != null) {
        return mismatchesEpisode(season, episode)
    }
    if (!contentType.equals("movie", ignoreCase = true)) return false
    return mismatchesMovie(expectedRuntimeMinutes)
}

private fun StreamItem.mismatchesMovie(expectedRuntimeMinutes: Int?): Boolean {
    val parsed = clientResolve?.stream?.raw?.parsed
    if (parsed != null) {
        if (parsed.seasons.any { it > 0 } || parsed.episodes.any { it > 0 }) return true
        val actualMinutes = parsed.duration?.let(::durationMinutes)
        if (actualMinutes != null &&
            expectedRuntimeMinutes != null &&
            runtimeMismatch(actualMinutes, expectedRuntimeMinutes)
        ) {
            return true
        }
    }

    val filename = episodeFilename() ?: return false
    return looksLikeSeriesFile(filename)
}

internal fun StreamItem.mismatchesEpisode(season: Int?, episode: Int?): Boolean {
    if (season == null || episode == null) return false

    val parsed = clientResolve?.stream?.raw?.parsed
    if (parsed != null) {
        val seasons = parsed.seasons.filter { it > 0 }
        val episodes = parsed.episodes.filter { it > 0 }
        if (seasons.isNotEmpty() && season !in seasons) return true
        if (episodes.isNotEmpty() && episode !in episodes) return true
        if (seasons.isNotEmpty() || episodes.isNotEmpty()) return false
    }

    val candidate = episodeFilename() ?: return false
    val (fileSeason, fileEpisode) = FilenameEpisodeMatcher.extractSeasonEpisode(candidate) ?: return false
    if (fileSeason != null && fileSeason != season) return true
    return fileEpisode != episode
}

private fun StreamItem.episodeFilename(): String? = listOfNotNull(
    behaviorHints.filename,
    clientResolve?.filename,
    clientResolve?.stream?.raw?.filename,
    clientResolve?.stream?.raw?.torrentName,
    name,
    title,
).map { it.trim() }
    .firstOrNull { it.isNotBlank() }

private fun durationMinutes(value: Long): Int? = when {
    value <= 0L -> null
    value <= 600L -> value.toInt()
    value <= 86_400L -> (value / 60L).toInt()
    else -> (value / 60_000L).toInt()
}

private fun runtimeMismatch(actualMinutes: Int, expectedMinutes: Int): Boolean {
    if (expectedMinutes < 30 || actualMinutes <= 0) return false
    return actualMinutes > expectedMinutes * 1.5 + 20 ||
        actualMinutes < expectedMinutes * 0.6
}

private val seriesFileRegex = Regex(
    """(?i)\b(s\d{1,3}\s*e\d{1,4}|s\d{1,3}\b|season\s*\d+|complete\s+series|e\d{1,4}\s*-\s*e?\d{1,4})\b""",
)

private fun looksLikeSeriesFile(filename: String): Boolean =
    FilenameEpisodeMatcher.extractSeasonEpisode(filename) != null ||
        seriesFileRegex.containsMatchIn(filename)

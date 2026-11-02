package com.nuvio.app.features.streams

data class StreamQuality(
    val height: Int?,
    val isCam: Boolean,
    val isHdr: Boolean,
    val isDolbyVision: Boolean,
    val sizeBytes: Long?,
    val runtimeMinutes: Int?,
)

internal fun StreamItem.streamQuality(): StreamQuality {
    val raw = clientResolve?.stream?.raw
    val parsed = raw?.parsed
    val candidates = qualityTexts()

    val height = parsed?.resolution?.toHeight()
        ?: parsed?.quality?.toHeight()
        ?: candidates.firstNotNullOfOrNull { it.toHeight() }

    val text = candidates.joinToString(" ")
    val isCam = CAM_REGEX.containsMatchIn(text)
    val isDolbyVision = parsed?.hdr.orEmpty().any { it.contains("dolby", ignoreCase = true) || it.equals("dv", ignoreCase = true) } ||
        DOLBY_VISION_REGEX.containsMatchIn(text)
    val isHdr = parsed?.hdr.orEmpty().isNotEmpty() ||
        HDR_REGEX.containsMatchIn(text)

    return StreamQuality(
        height = height,
        isCam = isCam,
        isHdr = isHdr,
        isDolbyVision = isDolbyVision,
        sizeBytes = behaviorHints.videoSize
            ?: raw?.size
            ?: debridCacheStatus?.cachedSize
            ?: parseSizeBytes(text),
        runtimeMinutes = parsed?.duration?.toRuntimeMinutes()
            ?: parseRuntimeMinutes(text),
    )
}

private fun Long.toRuntimeMinutes(): Int? = when {
    this <= 0 -> null
    this >= 600 -> (this / 60).toInt().takeIf { it > 0 }
    else -> toInt()
}

private fun parseRuntimeMinutes(text: String): Int? {
    RUNTIME_HOURS_REGEX.find(text)?.let { match ->
        val hours = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val total = (hours * 60) + minutes
        if (total > 0) return total
    }
    RUNTIME_CLOCK_REGEX.find(text)?.let { match ->
        val first = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
        val second = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@let
        val total = if (match.groupValues.getOrNull(3).isNullOrEmpty()) {
            first
        } else {
            (first * 60) + second
        }
        if (total > 0) return total
    }
    RUNTIME_MINUTES_REGEX.find(text)?.let { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
    }
    return null
}

private fun parseSizeBytes(text: String): Long? {
    val match = SIZE_REGEX.find(text) ?: return null
    val value = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues.getOrNull(2)?.lowercase()) {
        "tib", "tb" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        "gib", "gb" -> 1024.0 * 1024.0 * 1024.0
        else -> 1024.0 * 1024.0
    }
    return (value * multiplier).toLong()
}

private fun StreamItem.qualityTexts(): List<String> = listOfNotNull(
    clientResolve?.stream?.raw?.filename,
    clientResolve?.filename,
    behaviorHints.filename,
    debridCacheStatus?.cachedName,
    clientResolve?.stream?.raw?.torrentName,
    clientResolve?.torrentName,
    name,
    title,
    description,
    clientResolve?.stream?.raw?.parsed?.rawTitle,
    clientResolve?.stream?.raw?.parsed?.parsedTitle,
    clientResolve?.stream?.raw?.parsed?.quality,
).map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

internal fun String.toHeight(): Int? {
    val value = trim()
    if (value.isEmpty()) return null
    return when {
        UHD_REGEX.containsMatchIn(value) || value.contains("2160") -> 2160
        QHD_REGEX.containsMatchIn(value) || value.contains("1440") -> 1440
        FHD_REGEX.containsMatchIn(value) || value.contains("1080") -> 1080
        HD_REGEX.containsMatchIn(value) || value.contains("720") -> 720
        value.contains("576") -> 576
        SD_REGEX.containsMatchIn(value) || value.contains("480") -> 480
        value.contains("360") -> 360
        else -> null
    }
}

private val RUNTIME_HOURS_REGEX = Regex(
    """\b(\d{1,2})\s*h(?:ours?)?(?:\s*:?\s*(\d{1,2})\s*m(?:in)?)?\b""",
    RegexOption.IGNORE_CASE,
)
private val RUNTIME_MINUTES_REGEX = Regex("""\b(\d{1,3})\s*min(?:ute)?s?\b""", RegexOption.IGNORE_CASE)
private val RUNTIME_CLOCK_REGEX = Regex("""\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b""")
private val SIZE_REGEX = Regex(
    """\b(\d+(?:\.\d+)?)\s*(TiB|TB|GiB|GB|MiB|MB)\b""",
    RegexOption.IGNORE_CASE,
)
private val CAM_REGEX = Regex(
    """\b(cam|hdcam|camrip|hdts|ts|telesync|tc|telecine|scr|screener)\b""",
    RegexOption.IGNORE_CASE,
)
private val DOLBY_VISION_REGEX = Regex("""dolby[ ._-]?vision|\b(dv|dovi)\b""", RegexOption.IGNORE_CASE)
private val HDR_REGEX = Regex("""\bhdr(?:10\+?)?\b|\bhlg\b""", RegexOption.IGNORE_CASE)
private val UHD_REGEX = Regex("""\b(4k|uhd|2160p?)\b""", RegexOption.IGNORE_CASE)
private val QHD_REGEX = Regex("""\b(2k|qhd|1440p?)\b""", RegexOption.IGNORE_CASE)
private val FHD_REGEX = Regex("""\b(fhd|fullhd|full[ ._-]?hd|1080p?)\b""", RegexOption.IGNORE_CASE)
private val HD_REGEX = Regex("""\b(hd|720p?)\b""", RegexOption.IGNORE_CASE)
private val SD_REGEX = Regex("""\b(sd|480p?)\b""", RegexOption.IGNORE_CASE)

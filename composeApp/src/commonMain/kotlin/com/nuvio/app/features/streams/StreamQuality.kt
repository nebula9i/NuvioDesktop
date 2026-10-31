package com.nuvio.app.features.streams

data class StreamQuality(
    val height: Int?,
    val isCam: Boolean,
    val isHdr: Boolean,
    val isDolbyVision: Boolean,
    val sizeBytes: Long?,
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
            ?: debridCacheStatus?.cachedSize,
    )
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

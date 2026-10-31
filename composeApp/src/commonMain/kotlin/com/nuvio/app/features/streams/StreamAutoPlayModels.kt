package com.nuvio.app.features.streams

enum class StreamAutoPlayMode {
    MANUAL,
    FIRST_STREAM,
    REGEX_MATCH,
    SMART_PLAY,
}

enum class StreamAutoPlaySource {
    ALL_SOURCES,
    INSTALLED_ADDONS_ONLY,
    ENABLED_PLUGINS_ONLY,
}

enum class SmartPlayHdrPreference {
    AUTO,
    PREFER_HDR,
    PREFER_SDR,
}

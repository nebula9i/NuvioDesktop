package com.nuvio.app.core.display

/**
 * Physical display constraints used by Smart Play source selection.
 * A zero value means the capability is unknown and must not cap quality.
 */
data class DisplayCapability(
    val maxHeightPx: Int,
    val widthPx: Int,
)

internal expect fun currentDisplayCapability(): DisplayCapability

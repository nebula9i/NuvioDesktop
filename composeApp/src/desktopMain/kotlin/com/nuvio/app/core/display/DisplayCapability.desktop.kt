package com.nuvio.app.core.display

import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import kotlin.math.roundToInt

internal actual fun currentDisplayCapability(): DisplayCapability {
    val configuration = runCatching {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .activeWindow
            ?.graphicsConfiguration
    }.getOrNull()
        ?: runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
        }.getOrNull()
        ?: return DisplayCapability(maxHeightPx = 0, widthPx = 0)

    return configuration.toDisplayCapability()
}

private fun GraphicsConfiguration.toDisplayCapability(): DisplayCapability {
    val bounds = runCatching { bounds }.getOrNull() ?: return DisplayCapability(0, 0)
    val scaleX = runCatching { defaultTransform.scaleX }.getOrDefault(1.0).takeIf { it > 0.0 } ?: 1.0
    val scaleY = runCatching { defaultTransform.scaleY }.getOrDefault(1.0).takeIf { it > 0.0 } ?: 1.0
    return DisplayCapability(
        maxHeightPx = (bounds.height * scaleY).roundToInt().coerceAtLeast(0),
        widthPx = (bounds.width * scaleX).roundToInt().coerceAtLeast(0),
    )
}

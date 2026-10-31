package com.nuvio.app.core.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

private var applicationContext: Context? = null

internal fun initializeDisplayCapability(context: Context) {
    applicationContext = context.applicationContext
}

internal actual fun currentDisplayCapability(): DisplayCapability {
    val context = applicationContext ?: return DisplayCapability(0, 0)
    val display = runCatching {
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    }.getOrNull() ?: return DisplayCapability(0, 0)

    val modes = runCatching { display.supportedModes }.getOrDefault(emptyArray())
    if (modes.isEmpty()) {
        return runCatching {
            DisplayCapability(
                maxHeightPx = minOf(display.mode.physicalWidth, display.mode.physicalHeight),
                widthPx = maxOf(display.mode.physicalWidth, display.mode.physicalHeight),
            )
        }.getOrDefault(DisplayCapability(0, 0))
    }

    return DisplayCapability(
        maxHeightPx = modes.maxOf { minOf(it.physicalWidth, it.physicalHeight) },
        widthPx = modes.maxOf { maxOf(it.physicalWidth, it.physicalHeight) },
    )
}

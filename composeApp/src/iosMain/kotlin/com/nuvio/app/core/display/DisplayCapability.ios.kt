package com.nuvio.app.core.display

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentDisplayCapability(): DisplayCapability {
    val bounds = UIScreen.mainScreen.nativeBounds
    val shortSide = bounds.useContents { minOf(size.width, size.height).roundToInt() }
    val longSide = bounds.useContents { maxOf(size.width, size.height).roundToInt() }
    return DisplayCapability(
        maxHeightPx = shortSide.coerceAtLeast(0),
        widthPx = longSide.coerceAtLeast(0),
    )
}

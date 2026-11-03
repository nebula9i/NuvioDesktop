@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.mouseBackNavigation(enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.button == PointerButton.Back) {
                    event.changes.forEach { it.consume() }
                    onBack()
                }
            }
        }
    }
}

actual fun Modifier.consumeMouseBackForwardNavigation(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press &&
                (event.button == PointerButton.Back || event.button == PointerButton.Forward)
            ) {
                event.changes.forEach { it.consume() }
            }
        }
    }
}

actual fun Modifier.dismissOnPointerScroll(onScroll: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Scroll) { onScroll() }

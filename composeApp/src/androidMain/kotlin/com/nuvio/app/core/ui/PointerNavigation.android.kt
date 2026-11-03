package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

actual fun Modifier.mouseBackNavigation(enabled: Boolean, onBack: () -> Unit): Modifier = this

actual fun Modifier.consumeMouseBackForwardNavigation(): Modifier = this

actual fun Modifier.dismissOnPointerScroll(onScroll: () -> Unit): Modifier = this

package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

expect fun Modifier.mouseBackNavigation(enabled: Boolean, onBack: () -> Unit): Modifier

expect fun Modifier.consumeMouseBackForwardNavigation(): Modifier

expect fun Modifier.dismissOnPointerScroll(onScroll: () -> Unit): Modifier

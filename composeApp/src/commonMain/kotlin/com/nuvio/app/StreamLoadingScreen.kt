package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.OpeningOverlay
import com.nuvio.app.features.player.OpeningOverlayActions
import com.nuvio.app.features.player.subtitleLoadingStatusMessage
import com.nuvio.app.features.streams.StreamLaunch
import com.nuvio.app.features.streams.StreamsUiState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.debrid_resolving_stream
import nuvio.composeapp.generated.resources.player_loading_preparing
import nuvio.composeapp.generated.resources.streams_finding_source
import nuvio.composeapp.generated.resources.streams_loading_subtitles
import nuvio.composeapp.generated.resources.youtube_resolving_stream
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StreamLoadingScreen(
    launch: StreamLaunch,
    state: StreamsUiState,
    showStatus: Boolean,
    resolvingDebridStream: Boolean,
    resolvingYouTubeStream: Boolean = false,
    onBack: () -> Unit,
    preparingPlayback: Boolean = false,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
) {
    val message = when {
        !showStatus -> null
        resolvingDebridStream -> stringResource(Res.string.debrid_resolving_stream)
        resolvingYouTubeStream -> stringResource(Res.string.youtube_resolving_stream)
        preparingPlayback -> stringResource(Res.string.player_loading_preparing)
        state.overlayMessage == stringResource(Res.string.streams_loading_subtitles) -> subtitleLoadingStatusMessage()
        state.overlayMessage != null -> state.overlayMessage
        state.autoPlayStream != null -> stringResource(Res.string.player_loading_preparing)
        else -> stringResource(Res.string.streams_finding_source)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        OpeningOverlay(
            artwork = launch.background ?: launch.poster,
            logo = launch.logo,
            title = launch.title,
            onBack = onBack,
            horizontalSafePadding = 0.dp,
            message = message,
        )
        // Same quick actions as the buffering page: without them the viewer was stuck on this
        // screen until the automatic source search finished on its own.
        if (onSourcesClick != null || onEpisodesClick != null) {
            OpeningOverlayActions(
                onSourcesClick = onSourcesClick,
                onEpisodesClick = onEpisodesClick,
            )
        }
    }
}

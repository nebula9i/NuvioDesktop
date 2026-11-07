package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.features.p2p.P2pLoadingStatus
import com.nuvio.app.features.player.skip.NextEpisodeCard
import com.nuvio.app.features.player.skip.NextEpisodeInfo
import com.nuvio.app.features.player.skip.SkipIntroButton
import com.nuvio.app.features.player.skip.SkipInterval
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_episodes
import nuvio.composeapp.generated.resources.compose_player_sources
import nuvio.composeapp.generated.resources.player_error_try_another_source
import nuvio.composeapp.generated.resources.player_quick_settings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BoxScope.PlayerPlaybackOverlays(
    playerControlsLocked: Boolean,
    useLegacyLayout: Boolean,
    lockedOverlayVisible: Boolean,
    showRemainingTime: Boolean = false,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: Dp,
    onUnlock: () -> Unit,
    showOpeningOverlay: Boolean,
    backdropArtwork: String?,
    logo: String?,
    title: String,
    onBackWithProgress: () -> Unit,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onQuickSettingsClick: (() -> Unit)? = null,
    openingLoadingMessage: String?,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
    currentGestureFeedback: GestureFeedbackState?,
    renderedGestureFeedback: GestureFeedbackState?,
    initialLoadCompleted: Boolean,
    pausedOverlayVisible: Boolean,
    activeSkipInterval: SkipInterval?,
    skipsToPostCredits: Boolean,
    skipIntervalDismissed: Boolean,
    controlsVisible: Boolean,
    onSkipInterval: (SkipInterval) -> Unit,
    onDismissSkipInterval: () -> Unit,
    sliderEdgePadding: Dp,
    overlayBottomPadding: Dp,
    isSeries: Boolean,
    nextEpisodeInfo: NextEpisodeInfo?,
    showNextEpisodeCard: Boolean,
    nextEpisodeAutoPlaySearching: Boolean,
    nextEpisodeAutoPlaySourceName: String?,
    nextEpisodeAutoPlayCountdown: Int?,
    blurUnwatchedEpisodes: Boolean,
    onPlayNextEpisode: () -> Unit,
    onDismissNextEpisode: () -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    AnimatedVisibility(
        visible = playerControlsLocked && lockedOverlayVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LockedPlayerOverlay(
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            horizontalSafePadding = horizontalSafePadding,
            onUnlock = onUnlock,
            useLegacyLayout = useLegacyLayout,
            showRemainingTime = showRemainingTime,
            modifier = Modifier.fillMaxSize(),
        )
    }

    AnimatedVisibility(
        visible = showOpeningOverlay,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        OpeningOverlay(
            artwork = backdropArtwork,
            logo = logo,
            title = title,
            onBack = onBackWithProgress,
            horizontalSafePadding = horizontalSafePadding,
            modifier = Modifier.fillMaxSize(),
            message = openingLoadingMessage,
            progress = p2pInitialLoadingProgress,
        )
    }

    if (showOpeningOverlay) {
        OpeningOverlayActions(
            horizontalSafePadding = horizontalSafePadding,
            onSourcesClick = onSourcesClick,
            onEpisodesClick = onEpisodesClick,
            onQuickSettingsClick = onQuickSettingsClick,
        )
    }
    P2pLoadingStatus(
        visible = showP2pRebufferStats && errorMessage == null,
        message = p2pRebufferMessage,
        progress = p2pRebufferProgress,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(top = 58.dp),
    )

    PlayerGestureOverlay(
        currentFeedback = currentGestureFeedback,
        renderedFeedback = renderedGestureFeedback,
        useLegacyLayout = useLegacyLayout,
        horizontalSafePadding = horizontalSafePadding,
        horizontalPadding = metrics.horizontalPadding,
    )

    if (!playerControlsLocked) {
        SkipIntroButton(
            interval = if (!initialLoadCompleted || pausedOverlayVisible) null else activeSkipInterval,
            skipsToPostCredits = skipsToPostCredits,
            dismissed = skipIntervalDismissed,
            controlsVisible = controlsVisible,
            onSkip = {
                activeSkipInterval?.let(onSkipInterval)
            },
            onDismiss = onDismissSkipInterval,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sliderEdgePadding, bottom = overlayBottomPadding),
        )
    }

    if (isSeries && !playerControlsLocked) {
        NextEpisodeCard(
            nextEpisode = nextEpisodeInfo,
            visible = showNextEpisodeCard || nextEpisodeAutoPlaySearching || nextEpisodeAutoPlayCountdown != null,
            isAutoPlaySearching = nextEpisodeAutoPlaySearching,
            autoPlaySourceName = nextEpisodeAutoPlaySourceName,
            autoPlayCountdownSec = nextEpisodeAutoPlayCountdown,
            blurred = blurUnwatchedEpisodes && nextEpisodeInfo?.isWatched == false,
            onPlayNext = onPlayNextEpisode,
            onDismiss = onDismissNextEpisode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = sliderEdgePadding, bottom = overlayBottomPadding),
        )
    }

    if (errorMessage != null) {
        ErrorModal(
            message = errorMessage,
            onDismiss = onDismissError,
            secondaryLabel = if (onSourcesClick != null) {
                stringResource(Res.string.player_error_try_another_source)
            } else {
                null
            },
            onSecondary = onSourcesClick,
        )
    }
}

/**
 * Bottom pill with the quick actions shown over the opening/loading overlay: open the source
 * list, switch episode, quick settings. Shared by the player and by the "searching for sources"
 * screen, which used to show no way out of the loading state.
 */
@Composable
internal fun BoxScope.OpeningOverlayActions(
    onSourcesClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onQuickSettingsClick: (() -> Unit)? = null,
    horizontalSafePadding: Dp = 0.dp,
) {
    val sourcePainter = appIconPainter(AppIconResource.PlayerSource)
    val episodesPainter = appIconPainter(AppIconResource.PlayerEpisodes)
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Bottom))
            .padding(horizontal = horizontalSafePadding + 12.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onSourcesClick?.let { onClick ->
                    PlayerActionPillButton(
                        label = stringResource(Res.string.compose_player_sources),
                        painter = sourcePainter,
                        onClick = onClick,
                    )
                }
                onEpisodesClick?.let { onClick ->
                    PlayerActionPillButton(
                        label = stringResource(Res.string.compose_player_episodes),
                        painter = episodesPainter,
                        onClick = onClick,
                    )
                }
                onQuickSettingsClick?.let { onClick ->
                    PlayerActionPillButton(
                        label = stringResource(Res.string.player_quick_settings),
                        icon = Icons.Rounded.Settings,
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

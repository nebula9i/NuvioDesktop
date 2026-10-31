package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.IgnoredStreamEntry
import com.nuvio.app.features.streams.IgnoredStreamsRepository
import com.nuvio.app.features.streams.SmartPlayHdrPreference
import com.nuvio.app.features.streams.streamQuality
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.compose_player_audio
import nuvio.composeapp.generated.resources.player_audio_auto
import nuvio.composeapp.generated.resources.player_audio_change
import nuvio.composeapp.generated.resources.player_file
import nuvio.composeapp.generated.resources.player_file_ignored
import nuvio.composeapp.generated.resources.player_file_restored
import nuvio.composeapp.generated.resources.player_hdr
import nuvio.composeapp.generated.resources.player_hdr_auto
import nuvio.composeapp.generated.resources.player_hdr_prefer
import nuvio.composeapp.generated.resources.player_hdr_sdr
import nuvio.composeapp.generated.resources.player_ignore_file
import nuvio.composeapp.generated.resources.player_ignore_file_description
import nuvio.composeapp.generated.resources.player_ignored_files
import nuvio.composeapp.generated.resources.player_ignored_files_count
import nuvio.composeapp.generated.resources.player_ignored_files_description
import nuvio.composeapp.generated.resources.player_ignored_files_empty
import nuvio.composeapp.generated.resources.player_quality
import nuvio.composeapp.generated.resources.player_quality_auto
import nuvio.composeapp.generated.resources.player_quality_current
import nuvio.composeapp.generated.resources.player_quality_unavailable
import nuvio.composeapp.generated.resources.player_quick_settings
import nuvio.composeapp.generated.resources.player_restore
import nuvio.composeapp.generated.resources.player_sources_loading
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerScreenRuntime.PlayerQuickSettingsPanel() {
    val ignoredEntries by IgnoredStreamsRepository.entries.collectAsStateWithLifecycle()
    val ignoredMessage = stringResource(Res.string.player_file_ignored)
    val heights = availableQualityHeights()
    val currentHeight = currentPlaybackHeight()
    val currentAudio = currentAudioLabel()
    val sourceLoading = sourceStreamsState.isAnyLoading || sourceStreamsState.groups.any { it.isLoading }
    val hdrPreference = playerSettingsUiState.smartPlayHdrPreference

    PlayerSidePanel(
        visible = showQuickSettingsPanel,
        onDismiss = { showQuickSettingsPanel = false },
        width = 420.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(title = stringResource(Res.string.player_quick_settings)) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = { showQuickSettingsPanel = false },
                )
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                QuickSection(title = stringResource(Res.string.player_quality)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickChip(
                            label = stringResource(Res.string.player_quality_auto),
                            selected = quickSettingsQualityOverride == null,
                            onClick = { selectQuality(null) },
                        )
                        heights.forEach { height ->
                            QuickChip(
                                label = qualityLabel(height),
                                selected = quickSettingsQualityOverride == height,
                                onClick = { selectQuality(height) },
                            )
                        }
                    }
                    when {
                        sourceLoading -> Text(
                            text = stringResource(Res.string.player_sources_loading),
                            color = MaterialTheme.nuvio.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        heights.isEmpty() -> Text(
                            text = stringResource(Res.string.player_quality_unavailable),
                            color = MaterialTheme.nuvio.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        currentHeight != null -> Text(
                            text = stringResource(Res.string.player_quality_current, qualityLabel(currentHeight)),
                            color = MaterialTheme.nuvio.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                QuickSection(title = stringResource(Res.string.compose_player_audio)) {
                    QuickRow(
                        title = currentAudio ?: stringResource(Res.string.player_audio_auto),
                        subtitle = stringResource(Res.string.player_audio_change),
                        onClick = {
                            refreshTracks()
                            showAudioModal = true
                            showQuickSettingsPanel = false
                        },
                    )
                }

                QuickSection(title = stringResource(Res.string.player_hdr)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmartPlayHdrPreference.entries.forEach { preference ->
                            QuickChip(
                                label = hdrLabel(preference),
                                selected = hdrPreference == preference,
                                onClick = { PlayerSettingsRepository.setSmartPlayHdrPreference(preference) },
                            )
                        }
                    }
                }

                QuickSection(title = stringResource(Res.string.player_file)) {
                    QuickRow(
                        title = stringResource(Res.string.player_ignore_file),
                        subtitle = stringResource(Res.string.player_ignore_file_description),
                        enabled = activeStream != null && activeVideoId != null,
                        onClick = {
                            if (ignoreActiveFile()) {
                                playerNotificationMessage = ignoredMessage
                                playerNotificationToken += 1
                            }
                        },
                    )
                    QuickRow(
                        title = stringResource(Res.string.player_ignored_files_count, ignoredEntries.size),
                        subtitle = stringResource(Res.string.player_ignored_files_description),
                        onClick = { openIgnoredFiles() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayerScreenRuntime.PlayerIgnoredFilesPanel() {
    val entries by IgnoredStreamsRepository.entries.collectAsStateWithLifecycle()
    val restoredMessage = stringResource(Res.string.player_file_restored)
    val tokens = MaterialTheme.nuvio

    PlayerSidePanel(
        visible = showIgnoredFilesPanel,
        onDismiss = { showIgnoredFilesPanel = false },
        width = 420.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(title = stringResource(Res.string.player_ignored_files)) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_back),
                    onClick = { closeIgnoredFiles() },
                )
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = { showIgnoredFilesPanel = false },
                )
            }
            Spacer(Modifier.height(16.dp))

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(Res.string.player_ignored_files_empty),
                    color = tokens.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        IgnoredFileRow(
                            entry = entry,
                            onRestore = {
                                IgnoredStreamsRepository.restore(entry)
                                activeVideoId?.let { videoId ->
                                    PlayerStreamsRepository.loadSources(
                                        type = contentType ?: parentMetaType,
                                        videoId = videoId,
                                        season = activeSeasonNumber,
                                        episode = activeEpisodeNumber,
                                        forceRefresh = true,
                                    )
                                }
                                playerNotificationMessage = restoredMessage
                                playerNotificationToken += 1
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = tokens.colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tokens.colors.surfaceCard else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) tokens.colors.textPrimary else tokens.colors.textSecondary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) tokens.colors.textPrimary else tokens.colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun QuickRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else tokens.opacity.disabled)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.colors.surfaceCard)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            color = tokens.colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IgnoredFileRow(entry: IgnoredStreamEntry, onRestore: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val subtitle = listOfNotNull(
        entry.addonName?.takeIf { it.isNotBlank() },
        entry.contentTitle?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.colors.surfaceCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = entry.label,
            color = tokens.colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        PlayerDialogButton(
            label = stringResource(Res.string.player_restore),
            onClick = onRestore,
        )
    }
}

@Composable
private fun hdrLabel(preference: SmartPlayHdrPreference): String = when (preference) {
    SmartPlayHdrPreference.AUTO -> stringResource(Res.string.player_hdr_auto)
    SmartPlayHdrPreference.PREFER_HDR -> stringResource(Res.string.player_hdr_prefer)
    SmartPlayHdrPreference.PREFER_SDR -> stringResource(Res.string.player_hdr_sdr)
}

private fun qualityLabel(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "1440p"
    else -> "${height}p"
}

private fun PlayerScreenRuntime.currentPlaybackHeight(): Int? =
    playbackSnapshot.videoHeight.takeIf { it > 0 }
        ?: activeStream?.streamQuality()?.height

private fun PlayerScreenRuntime.availableQualityHeights(): List<Int> {
    val known = sourceStreamsState.groups
        .flatMap { it.streams }
        .mapNotNull { it.streamQuality().height }
        .filter { it > 0 }
    return (known + listOfNotNull(currentPlaybackHeight()))
        .distinct()
        .sortedDescending()
}

private fun PlayerScreenRuntime.currentAudioLabel(): String? =
    serverAudioTracks.ifEmpty { audioTracks }
        .firstOrNull { it.isSelected || it.index == selectedAudioIndex }
        ?.label

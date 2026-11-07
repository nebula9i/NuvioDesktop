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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.features.p2p.formatP2pSpeed
import com.nuvio.app.features.streams.IgnoredStreamEntry
import com.nuvio.app.features.streams.IgnoredStreamsRepository
import com.nuvio.app.features.streams.SmartPlayHdrPreference
import com.nuvio.app.features.streams.SoftIgnoredStreamEntry
import com.nuvio.app.features.streams.SoftIgnoredStreamsRepository
import com.nuvio.app.features.streams.streamQuality
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.compose_player_audio
import nuvio.composeapp.generated.resources.player_audio_auto
import nuvio.composeapp.generated.resources.player_audio_change
import nuvio.composeapp.generated.resources.player_buffered
import nuvio.composeapp.generated.resources.player_current_file
import nuvio.composeapp.generated.resources.player_download_speed
import nuvio.composeapp.generated.resources.player_downloaded
import nuvio.composeapp.generated.resources.player_file
import nuvio.composeapp.generated.resources.player_file_size
import nuvio.composeapp.generated.resources.player_file_status
import nuvio.composeapp.generated.resources.player_peers
import nuvio.composeapp.generated.resources.player_provider
import nuvio.composeapp.generated.resources.player_status_connecting
import nuvio.composeapp.generated.resources.player_status_playing
import nuvio.composeapp.generated.resources.player_status_streaming
import nuvio.composeapp.generated.resources.player_stream_details
import nuvio.composeapp.generated.resources.player_file_ignored
import nuvio.composeapp.generated.resources.player_file_restored
import nuvio.composeapp.generated.resources.player_hdr
import nuvio.composeapp.generated.resources.player_hdr_auto
import nuvio.composeapp.generated.resources.player_hdr_prefer
import nuvio.composeapp.generated.resources.player_hdr_sdr
import nuvio.composeapp.generated.resources.player_ignore_file
import nuvio.composeapp.generated.resources.player_ignore_file_description
import nuvio.composeapp.generated.resources.player_ignored_added_at
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
import nuvio.composeapp.generated.resources.player_soft_ignored_clear
import nuvio.composeapp.generated.resources.player_soft_ignored_description
import nuvio.composeapp.generated.resources.player_soft_ignored_failed_at
import nuvio.composeapp.generated.resources.player_soft_ignored_title
import nuvio.composeapp.generated.resources.player_sources_loading
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerScreenRuntime.PlayerQuickSettingsPanel() {
    val ignoredEntries by IgnoredStreamsRepository.entries.collectAsStateWithLifecycle()
    val ignoredCountForContent = remember(ignoredEntries, activeVideoId) {
        ignoredEntries.count { it.contentKey == activeVideoId }
    }
    val ignoredMessage = stringResource(Res.string.player_file_ignored)
    val panelVisible = showQuickSettingsPanel
    val heights = if (panelVisible) availableQualityHeights() else emptyList()
    val currentHeight = if (panelVisible) currentPlaybackHeight() else null
    val currentAudio = currentAudioLabel()
    val sourceLoading = sourceStreamsState.isAnyLoading || sourceStreamsState.groups.any { it.isLoading }
    val hdrPreference = playerSettingsUiState.smartPlayHdrPreference
    val p2pState by P2pStreamingEngine.state.collectAsStateWithLifecycle()
    val fileSizeBytes = activeStream?.behaviorHints?.videoSize ?: 0L
    val p2pStreaming = p2pState as? P2pStreamingState.Streaming
    val downloadedBytes = p2pStreaming?.downloadedBytes ?: 0L
    val downloadProgress = p2pStreaming?.totalProgress?.takeIf { it > 0f }?.let { (it * 100).toInt() }
    val bufferedText = if (panelVisible) {
        val durationMs = playbackSnapshot.durationMs
        val bufferedProgress = durationMs.takeIf { it > 0L }?.let {
            (playbackSnapshot.bufferedPositionMs.toFloat() / it * 100f).toInt().takeIf { percent -> percent > 0 }
        }
        if (bufferedProgress != null) {
            "${formatDurationMs(playbackSnapshot.bufferedPositionMs)} / " +
                "${formatDurationMs(durationMs)} ($bufferedProgress%)"
        } else {
            null
        }
    } else {
        null
    }
    val downloadSpeed = if (panelVisible) {
        p2pStreaming?.downloadSpeed?.takeIf { it > 0L }
            ?: playbackSnapshot.networkSpeedBytesPerSecond
    } else {
        0L
    }
    val streamName = activeStream?.behaviorHints?.filename
        ?: activeStream?.streamLabel
        ?: activeStreamTitle.takeIf { it.isNotBlank() }
    val providerName = activeStream?.addonName ?: activeProviderName.takeIf { it.isNotBlank() }
    val playbackStatus = when {
        p2pState is P2pStreamingState.Connecting -> stringResource(Res.string.player_status_connecting)
        p2pStreaming != null -> stringResource(Res.string.player_status_streaming)
        else -> stringResource(Res.string.player_status_playing)
    }

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
                QuickSection(title = stringResource(Res.string.player_stream_details)) {
                    QuickDetailRow(stringResource(Res.string.player_current_file), streamName)
                    QuickDetailRow(stringResource(Res.string.player_provider), providerName)
                    QuickDetailRow(
                        stringResource(Res.string.player_quality),
                        currentHeight?.let(::qualityLabel),
                    )
                    if (fileSizeBytes > 0L) {
                        QuickDetailRow(stringResource(Res.string.player_file_size), formatFileSize(fileSizeBytes))
                    }
                    QuickDetailRow(stringResource(Res.string.player_file_status), playbackStatus)
                    val downloadedText = when {
                        fileSizeBytes > 0L && downloadProgress != null ->
                            "${formatFileSize(downloadedBytes)} / ${formatFileSize(fileSizeBytes)} ($downloadProgress%)"
                        else -> bufferedText
                    }
                    QuickDetailRow(
                        stringResource(
                            if (downloadProgress != null) Res.string.player_downloaded else Res.string.player_buffered,
                        ),
                        downloadedText,
                    )
                    QuickDetailRow(
                        stringResource(Res.string.player_download_speed),
                        if (downloadSpeed > 0L) formatP2pSpeed(downloadSpeed) else "—",
                    )
                    if (p2pStreaming != null && p2pStreaming.peers > 0) {
                        QuickDetailRow(stringResource(Res.string.player_peers), p2pStreaming.peers.toString())
                    }
                }

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
                        title = stringResource(Res.string.player_ignored_files_count, ignoredCountForContent),
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
    val allEntries by IgnoredStreamsRepository.entries.collectAsStateWithLifecycle()
    // Only the files ignored for the episode/movie that is playing right now.
    val entries = remember(allEntries, activeVideoId) {
        allEntries.filter { it.contentKey == activeVideoId }
    }
    val allSoftEntries by SoftIgnoredStreamsRepository.entries.collectAsStateWithLifecycle()
    // Sources the automatic failover skipped after they failed to play (24h, not a user decision).
    val softEntries = remember(allSoftEntries, activeVideoId) {
        allSoftEntries
            .filter { it.contentKey == activeVideoId }
            .sortedByDescending { it.failedAtMs }
    }
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

            if (entries.isEmpty() && softEntries.isEmpty()) {
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
                    if (softEntries.isNotEmpty()) {
                        item(key = "soft-ignored-header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.player_soft_ignored_title),
                                        color = tokens.colors.textPrimary,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    PlayerDialogButton(
                                        label = stringResource(Res.string.player_soft_ignored_clear),
                                        onClick = {
                                            SoftIgnoredStreamsRepository.clearForContent(activeVideoId)
                                        },
                                    )
                                }
                                Text(
                                    text = stringResource(Res.string.player_soft_ignored_description),
                                    color = tokens.colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        items(softEntries, key = { "soft-${it.key}" }) { entry ->
                            SoftIgnoredFileRow(entry)
                        }
                    }
                    items(entries, key = { it.key }) { entry ->
                        IgnoredFileRow(
                            entry = entry,
                            addedAtLabel = if (entry.ignoredAtMs > 0L) {
                                stringResource(
                                    Res.string.player_ignored_added_at,
                                    formatLocalDateTime(entry.ignoredAtMs),
                                )
                            } else {
                                ""
                            },
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
                                scope.launch { NuvioToastController.show(restoredMessage) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            color = tokens.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            color = tokens.colors.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

internal fun formatFileSize(bytes: Long): String {
    val mib = 1024.0 * 1024.0
    return when {
        bytes >= 1024L * 1024L * 1024L -> "${formatOneDecimal(bytes / (mib * 1024.0))} GB"
        bytes >= 1024L * 1024L -> "${formatOneDecimal(bytes / mib)} MB"
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }
}

internal fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return rounded.toString()
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
private fun IgnoredFileRow(
    entry: IgnoredStreamEntry,
    addedAtLabel: String,
    onRestore: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val subtitle = listOfNotNull(
        entry.addonName?.takeIf { it.isNotBlank() },
        entry.contentLabel.takeIf { it.isNotBlank() }
            ?: entry.contentTitle?.takeIf { it.isNotBlank() },
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
        if (addedAtLabel.isNotBlank()) {
            Text(
                text = addedAtLabel,
                color = tokens.colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
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
private fun SoftIgnoredFileRow(entry: SoftIgnoredStreamEntry) {
    val tokens = MaterialTheme.nuvio
    val subtitle = listOfNotNull(
        entry.addonName?.takeIf { it.isNotBlank() },
        if (entry.failedAtMs > 0L) {
            stringResource(
                Res.string.player_soft_ignored_failed_at,
                formatLocalDateTime(entry.failedAtMs),
            )
        } else {
            null
        },
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
            text = entry.label.ifBlank { "Stream" },
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
    activeStream?.streamQuality()?.height
        ?: playbackSnapshot.videoHeight.takeIf { it > 0 }

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

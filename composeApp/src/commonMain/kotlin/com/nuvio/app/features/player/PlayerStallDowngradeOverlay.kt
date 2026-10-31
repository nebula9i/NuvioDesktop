package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_stall_keep_stream
import nuvio.composeapp.generated.resources.player_stall_keeps_buffering
import nuvio.composeapp.generated.resources.player_stall_switch_to
import nuvio.composeapp.generated.resources.player_stall_switching
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerStallDowngradeOverlay(
    targetHeight: Int,
    switching: Boolean,
    onSwitch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(switching) {
        if (!switching) {
            delay(20_000L)
            onDismiss()
        }
    }

    Surface(
        modifier = modifier.widthIn(min = 220.dp, max = 340.dp),
        color = Color.Black.copy(alpha = 0.82f),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.player_stall_keeps_buffering),
                style = MaterialTheme.nuvioTypeScale.titleLg,
            )
            Text(
                text = stringResource(Res.string.player_stall_switch_to, "${targetHeight}p"),
                style = MaterialTheme.nuvioTypeScale.bodyMd,
                color = Color.White.copy(alpha = 0.72f),
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !switching) {
                    Text(stringResource(Res.string.player_stall_keep_stream))
                }
                TextButton(onClick = onSwitch, enabled = !switching) {
                    Text(
                        text = if (switching) {
                            stringResource(Res.string.player_stall_switching)
                        } else {
                            stringResource(Res.string.player_stall_switch_to, "${targetHeight}p")
                        },
                    )
                }
            }
        }
    }
}

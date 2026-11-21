package app.clearcall.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clearcall.core.AppPermissions
import app.clearcall.core.Format
import app.clearcall.core.openAppSettings
import app.clearcall.data.CaptureMode
import app.clearcall.data.RecordMode
import app.clearcall.recording.RecorderStatus
import app.clearcall.recording.RecordingService
import app.clearcall.ui.components.AppCard
import app.clearcall.ui.components.EmptyState
import app.clearcall.ui.components.RecordingRow
import app.clearcall.ui.components.SectionHeader
import app.clearcall.ui.theme.Caution
import app.clearcall.ui.theme.RecordRed
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    padding: PaddingValues,
    onOpenRecording: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDeviceCheck: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("ClearCall", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Private, on-device call recordings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            StatusCard(
                state = state,
                onGrant = {
                    // If the user has permanently denied a permission the system dialog is skipped.
                    permissionLauncher.launch(AppPermissions.all())
                },
                onOpenSettings = { context.openAppSettings() },
                onDeviceCheck = onOpenDeviceCheck,
            )
        }

        item {
            ModeSelector(selected = state.settings.recordMode, onSelect = vm::setRecordMode)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Recordings", state.totalCount.toString(), Modifier.weight(1f))
                StatTile("Total time", Format.totalDuration(state.totalDurationMs), Modifier.weight(1f))
                StatTile("Storage", Format.size(state.totalBytes), Modifier.weight(1f))
            }
        }

        item {
            SectionHeader(
                title = "Recent",
                action = if (state.recent.isNotEmpty()) "See all" else null,
                onAction = onOpenLibrary,
            )
        }

        if (state.loaded && state.recent.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.GraphicEq,
                    title = "No recordings yet",
                    body = "Recordings appear here after a call. Choose Ask or Auto above to get started.",
                )
            }
        } else {
            items(state.recent, key = { it.id }) { recording ->
                RecordingRow(item = recording, onClick = { onOpenRecording(recording.id) }, showDate = true)
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: HomeState,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeviceCheck: () -> Unit,
) {
    val status = state.status
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                status is RecorderStatus.Recording -> RecordingNow(status)

                state.missingCore.isNotEmpty() -> {
                    StatusHeader(color = Caution, title = "Permissions needed")
                    Text(
                        "ClearCall needs microphone and phone access to detect and record calls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGrant) { Text("Grant access") }
                        TextButton(onClick = onOpenSettings) { Text("Open settings") }
                    }
                }

                state.captureMode == null -> {
                    StatusHeader(color = Caution, title = "Can't record on this device")
                    Text(
                        "Android doesn't allow apps to capture call audio here, and microphone mode is off.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onDeviceCheck) { Text("Review options") }
                }

                else -> {
                    val title = when (state.settings.recordMode) {
                        RecordMode.OFF -> "Recording is off"
                        RecordMode.ASK -> "Ready · asks before recording"
                        RecordMode.AUTO -> "Ready · records automatically"
                    }
                    StatusHeader(
                        color = if (state.settings.recordMode == RecordMode.OFF) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        title = title,
                    )
                    Text(
                        if (state.captureMode == CaptureMode.CALL_AUDIO) {
                            "Captures both sides of the call."
                        } else {
                            "Microphone mode: records your voice. The other person is only audible on speakerphone."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status is RecorderStatus.Failed) {
                        Text(
                            "Last attempt failed: ${status.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Caution,
                        )
                        TextButton(onClick = onDeviceCheck) { Text("Check device compatibility") }
                    }
                }
            }
            if (state.notificationsBlocked && status !is RecorderStatus.Recording) {
                Text(
                    "Notifications are off, so you won't see the recording indicator or the Record button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Caution,
                )
            }
        }
    }
}

@Composable
private fun StatusHeader(color: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RecordingNow(status: RecorderStatus.Recording) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val elapsed by produceState(0L, status.startedAt) {
        while (true) {
            value = System.currentTimeMillis() - status.startedAt
            delay(1_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).alpha(alpha).clip(CircleShape).background(RecordRed))
        Spacer(Modifier.width(10.dp))
        Text("Recording · ${Format.duration(elapsed)}", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        if (status.mode == CaptureMode.CALL_AUDIO) {
            "Capturing call audio (both sides)."
        } else {
            "Capturing through the microphone (your voice)."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { RecordingService.requestStop() },
        colors = ButtonDefaults.buttonColors(containerColor = RecordRed, contentColor = Color.White),
    ) {
        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Stop recording")
    }
}

@Composable
private fun ModeSelector(selected: RecordMode, onSelect: (RecordMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Text(
            when (selected) {
                RecordMode.OFF -> "ClearCall will not record or prompt."
                RecordMode.ASK -> "A notification offers to record each call. Nothing is captured until you tap Record."
                RecordMode.AUTO -> "Recording starts by itself when Android allows it; otherwise you get the Record button."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

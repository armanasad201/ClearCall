package app.clearcall.ui.player

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.clearcall.core.Format
import app.clearcall.data.CaptureMode
import app.clearcall.data.RecordingEntity
import app.clearcall.data.displayTitle
import app.clearcall.data.mimeType
import app.clearcall.ui.components.AppCard
import app.clearcall.ui.components.DirectionAvatar
import app.clearcall.ui.components.Pill
import app.clearcall.ui.components.label
import app.clearcall.ui.theme.Caution
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun PlayerScreen(
    vm: PlayerViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (val current = ui) {
        PlayerUi.Loading -> Box(Modifier.fillMaxSize())
        PlayerUi.Missing -> {
            // Deleted (or never existed): leave the screen.
            LaunchedEffect(Unit) { onBack() }
            Box(Modifier.fillMaxSize())
        }
        is PlayerUi.Ready -> PlayerContent(vm, current.recording, snackbar, onBack)
    }
}

@Composable
private fun PlayerContent(
    vm: PlayerViewModel,
    recording: RecordingEntity,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(recording.id) { vm.file(recording) }

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(recording.mimeType),
    ) { uri: Uri? ->
        if (uri != null) {
            vm.export(recording, uri) { ok ->
                scope.launch { snackbar.showSnackbar(if (ok) "Exported" else "Export failed") }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.toggleStar(recording) }) {
                Icon(
                    if (recording.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (recording.starred) "Remove star" else "Star",
                    tint = if (recording.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            shareRecording(context, vm.shareUri(recording), recording.mimeType)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export to…") },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch(suggestedFileName(recording))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; confirmingDelete = true },
                    )
                }
            }
        }

        // Header
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DirectionAvatar(recording.direction, Modifier.size(64.dp))
            Text(
                recording.displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "${recording.direction.label()} · ${Format.dateTime(recording.startedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                recording.silent -> Pill("No audio detected", Caution, Icons.Rounded.Warning)
                recording.captureMode == CaptureMode.MICROPHONE ->
                    Pill("Microphone only", MaterialTheme.colorScheme.primary)
                recording.captureMode == CaptureMode.CALL_AUDIO ->
                    Pill("Both sides", MaterialTheme.colorScheme.primary)
            }
        }

        if (recording.silent) {
            AppCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
                Text(
                    "Android did not deliver any audio for this call, which is common on phones that block " +
                        "recording during calls. The file is kept so you can see the attempt, but it is silent.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!file.exists()) {
            AppCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
                Text(
                    "The audio file for this recording is missing from storage.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            PlayerControls(file = file, durationMs = recording.durationMs, modifier = Modifier.padding(horizontal = 8.dp))
        }

        Details(recording, file.length(), modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.size(24.dp))
    }

    if (renaming) {
        RenameDialog(
            initial = recording.customTitle.orEmpty(),
            hint = recording.contactName ?: recording.phoneNumber ?: "Unknown caller",
            onConfirm = {
                vm.rename(recording, it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete recording?") },
            text = { Text("This permanently removes the audio from your device. It can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        vm.delete(recording)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlayerControls(file: java.io.File, durationMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                it.prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    var position by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            isPlaying = player.isPlaying
            ended = player.playbackState == Player.STATE_ENDED
            delay(200)
        }
    }

    val total = maxOf(durationMs, 1L)

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Slider(
                value = (if (seeking) seekValue else position.toFloat()).coerceIn(0f, total.toFloat()),
                onValueChange = {
                    seeking = true
                    seekValue = it
                },
                onValueChangeFinished = {
                    player.seekTo(seekValue.toLong())
                    position = seekValue.toLong()
                    seeking = false
                },
                valueRange = 0f..total.toFloat(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    Format.duration(if (seeking) seekValue.toLong() else position),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    Format.duration(total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                FilledIconButton(
                    onClick = {
                        when {
                            isPlaying -> player.pause()
                            ended -> {
                                player.seekTo(0)
                                player.play()
                            }
                            else -> player.play()
                        }
                    },
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(
                        when {
                            isPlaying -> Icons.Rounded.Pause
                            ended -> Icons.Rounded.Replay
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                IconButton(
                    onClick = {
                        val target = player.currentPosition + 10_000
                        player.seekTo(if (player.duration > 0) minOf(target, player.duration) else target)
                    },
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds", modifier = Modifier.size(32.dp))
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SPEEDS) { option ->
                    FilterChip(
                        selected = speed == option,
                        onClick = {
                            speed = option
                            player.setPlaybackSpeed(option)
                        },
                        label = { Text(speedLabel(option)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Details(recording: RecordingEntity, fileBytes: Long, modifier: Modifier = Modifier) {
    val captured = when (recording.captureMode) {
        CaptureMode.CALL_AUDIO -> "Call audio (both sides)"
        CaptureMode.MICROPHONE -> "Microphone"
        CaptureMode.UNKNOWN -> "Unknown"
    }
    val quality = buildString {
        append(if (recording.format == "wav") "WAV (lossless)" else "AAC (.m4a)")
        if (recording.sampleRate > 0) append(" · ${recording.sampleRate / 1000.0} kHz")
        if (recording.format != "wav" && recording.bitrate > 0) append(" · ${recording.bitrate / 1000} kbps")
    }
    val rows = listOf(
        "Date" to Format.dateTime(recording.startedAt),
        "Duration" to Format.duration(recording.durationMs),
        "Number" to (recording.phoneNumber ?: "Not available"),
        "Captured from" to captured,
        "Format" to quality,
        "Size" to Format.size(fileBytes.takeIf { it > 0 } ?: recording.sizeBytes),
    )
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename recording") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                singleLine = true,
                placeholder = { Text(hint) },
                supportingText = { Text("Leave empty to use the contact or number.") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

private fun suggestedFileName(recording: RecordingEntity): String {
    val base = recording.displayTitle.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "recording" }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date(recording.startedAt))
    return "$base $stamp.${recording.format}"
}

private fun shareRecording(context: android.content.Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Recording", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share recording"))
}

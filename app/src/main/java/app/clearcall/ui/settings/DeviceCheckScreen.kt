package app.clearcall.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clearcall.ui.components.AppCard
import app.clearcall.ui.theme.Caution

private enum class Level { GOOD, NOTE, BAD }

private data class Check(val level: Level, val title: String, val detail: String)

@Composable
fun DeviceCheckScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    // Permissions can change in Android settings while this screen is open.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val caps = remember(tick) { vm.capabilities() }
    val micMode = settings?.allowMicFallback ?: true

    val verdict = when {
        !caps.hasTelephony -> "This device can't make calls"
        caps.callAudioCapture -> "Full call recording is available"
        micMode -> "Microphone recording only"
        else -> "Recording is unavailable"
    }
    val verdictLevel = when {
        !caps.hasTelephony -> Level.BAD
        caps.callAudioCapture -> Level.GOOD
        micMode -> Level.NOTE
        else -> Level.BAD
    }

    val checks = listOf(
        Check(Level.GOOD, "Device", "${caps.deviceName} · Android ${caps.release} (API ${caps.sdkInt})"),
        if (caps.hasTelephony) {
            Check(Level.GOOD, "Phone calls", "This device can make and receive calls.")
        } else {
            Check(Level.BAD, "Phone calls", "No telephony hardware, so there are no calls to record.")
        },
        if (caps.microphonePermission) {
            Check(Level.GOOD, "Microphone permission", "Granted.")
        } else {
            Check(Level.BAD, "Microphone permission", "Not granted. Allow it in the app's Android settings.")
        },
        if (caps.callAudioCapture) {
            Check(Level.GOOD, "Call audio capture", "Available. Both sides of the call can be recorded.")
        } else {
            Check(Level.NOTE, "Call audio capture", "Not available to regular apps on this device.")
        },
        if (micMode) {
            Check(Level.NOTE, "Microphone mode", "On. Records your voice, and the other person only on speakerphone.")
        } else {
            Check(Level.NOTE, "Microphone mode", "Off.")
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Device compatibility", style = MaterialTheme.typography.titleLarge)
        }

        AppCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelIcon(verdictLevel)
                    Spacer(Modifier.width(10.dp))
                    Text(verdict, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    caps.captureSummary(micMode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AppCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                checks.forEach { check ->
                    Row(verticalAlignment = Alignment.Top) {
                        LevelIcon(check.level)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(check.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                check.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        AppCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use microphone mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lets ClearCall record when call audio is blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = micMode, onCheckedChange = { vm.setAllowMicFallback(it) })
            }
        }

        AppCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Why can't ClearCall record both sides?", style = MaterialTheme.typography.titleMedium)
                Explain(
                    "Since Android 10, the system stops regular apps from capturing the audio of a phone call. " +
                        "Only software installed by the phone maker, with a special privileged permission, can do it.",
                )
                Explain(
                    "In microphone mode, many phones also send silence to the microphone during a call to protect " +
                        "privacy. ClearCall checks the signal and marks a recording \"No audio detected\" instead of " +
                        "pretending it worked.",
                )
                Explain(
                    "ClearCall does not use workarounds such as rooting or accessibility tricks. They break with " +
                        "updates and weaken your phone's security. If you need both sides reliably, check whether " +
                        "your phone's own Phone app includes a call recorder.",
                )
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LevelIcon(level: Level) {
    val icon: ImageVector
    val tint: Color
    val description: String
    when (level) {
        Level.GOOD -> {
            icon = Icons.Rounded.CheckCircle
            tint = MaterialTheme.colorScheme.primary
            description = "OK"
        }
        Level.NOTE -> {
            icon = Icons.Rounded.Info
            tint = Caution
            description = "Note"
        }
        Level.BAD -> {
            icon = Icons.Rounded.Cancel
            tint = MaterialTheme.colorScheme.error
            description = "Problem"
        }
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
}

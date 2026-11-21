package app.clearcall.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.clearcall.core.findActivity
import app.clearcall.core.openAppSettings
import app.clearcall.data.RecordMode
import app.clearcall.data.RecordingQuality
import app.clearcall.data.ThemeMode
import app.clearcall.security.BiometricAuth
import app.clearcall.ui.components.AppCard
import app.clearcall.ui.onboarding.ConsentCopy
import app.clearcall.ui.onboarding.ConsentPoints
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RETENTION_OPTIONS = listOf(0 to "Keep all", 30 to "30 days", 90 to "90 days", 365 to "1 year")
private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    padding: PaddingValues,
    snackbar: SnackbarHostState,
    onOpenDeviceCheck: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recordingCount by vm.recordingCount.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    var busy by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            vm.exportBackup(uri) { message ->
                busy = false
                say(message)
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            vm.restoreBackup(uri) { message ->
                busy = false
                say(message)
            }
        }
    }

    val s = settings ?: return

    fun setLock(enable: Boolean) {
        val host = activity ?: return
        val canAuthenticate = BiometricAuth.canAuthenticate(host)
        when {
            // Nothing to verify against any more (screen lock removed): let the user switch it off.
            !enable && !canAuthenticate -> vm.setAppLock(false)
            !canAuthenticate -> say("Set up a screen lock or fingerprint in Android settings first.")
            else -> BiometricAuth.prompt(
                activity = host,
                title = if (enable) "Turn on app lock" else "Turn off app lock",
            ) { ok -> if (ok) vm.setAppLock(enable) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Group("Recording") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("When a call starts", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.recordMode == mode,
                            onClick = { vm.setRecordMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
            RowDivider()
            ToggleRow("Incoming calls", null, s.recordIncoming) { vm.setRecordIncoming(it) }
            RowDivider()
            ToggleRow("Outgoing calls", null, s.recordOutgoing) { vm.setRecordOutgoing(it) }
            RowDivider()
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Audio quality",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                RecordingQuality.entries.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setQuality(quality) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.quality == quality, onClick = { vm.setQuality(quality) })
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(quality.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                quality.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            RowDivider()
            ToggleRow(
                title = "Microphone mode",
                subtitle = "If Android blocks call audio, record through the microphone instead. " +
                    "Captures your voice, and the other person only on speakerphone.",
                checked = s.allowMicFallback,
                onCheckedChange = { vm.setAllowMicFallback(it) },
            )
            RowDivider()
            NavRow("Device compatibility", "What this phone allows ClearCall to record", onOpenDeviceCheck)
        }

        Group("Appearance") {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = s.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        Group("Privacy & security") {
            ToggleRow(
                title = "App lock",
                subtitle = "Ask for your fingerprint, face or screen lock to open ClearCall. " +
                    "Also hides the app from screenshots and the recent-apps view.",
                checked = s.appLockEnabled,
                onCheckedChange = { setLock(it) },
            )
            RowDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Auto-delete old recordings", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Starred recordings are never deleted automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RETENTION_OPTIONS.forEach { (days, label) ->
                        FilterChip(
                            selected = s.retentionDays == days,
                            onClick = { vm.setRetentionDays(days) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            RowDivider()
            NavRow("Recording and the law", "Read the notice you accepted", { showConsent = true })
            RowDivider()
            NavRow("App permissions", "Microphone, phone, contacts, notifications", { context.openAppSettings() })
            RowDivider()
            Text(
                "ClearCall has no internet permission. Recordings are kept in private app storage and are " +
                    "only shared when you choose to share, export or back them up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        Group("Storage & backup") {
            Text(
                if (recordingCount == 1) "1 recording stored on this device" else "$recordingCount recordings stored on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            NavRow(
                title = "Back up recordings",
                subtitle = "Save a ZIP file to a place you choose. The file is not encrypted.",
                onClick = { if (!busy) backupLauncher.launch(backupFileName()) },
            )
            RowDivider()
            NavRow(
                title = "Restore from backup",
                subtitle = "Add recordings from a ClearCall backup ZIP",
                onClick = { if (!busy) restoreLauncher.launch(BACKUP_MIME_TYPES) },
            )
            RowDivider()
            NavRow(
                title = "Delete all recordings",
                subtitle = "Permanently removes every recording from this device",
                onClick = { if (!busy) confirmDeleteAll = true },
                titleColor = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            "ClearCall ${appVersion(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(ConsentCopy.TITLE) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { ConsentPoints() }
            },
            confirmButton = { TextButton(onClick = { showConsent = false }) { Text("Close") } },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all recordings?") },
            text = { Text("Every recording will be permanently deleted from this device. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        busy = true
                        vm.deleteAllRecordings {
                            busy = false
                            say("All recordings deleted")
                        }
                    },
                ) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        AppCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun backupFileName(): String =
    "ClearCall-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"

@Suppress("DEPRECATION")
private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull()
        .orEmpty()

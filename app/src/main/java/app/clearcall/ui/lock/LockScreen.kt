package app.clearcall.ui.lock

import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.clearcall.core.findActivity
import app.clearcall.security.BiometricAuth

@Composable
fun LockScreen(onUnlocked: () -> Unit, onTurnOffLock: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    var status by remember { mutableIntStateOf(BiometricAuth.status(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    fun authenticate() {
        val host = activity ?: return
        message = null
        BiometricAuth.prompt(
            activity = host,
            title = "Unlock ClearCall",
            subtitle = "Use your fingerprint, face or screen lock",
        ) { ok ->
            if (ok) onUnlocked() else message = "Not unlocked. Tap Unlock to try again."
        }
    }

    // The user may set up (or remove) a screen lock while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = BiometricAuth.status(context) }

    LaunchedEffect(Unit) {
        if (BiometricAuth.canAuthenticate(context)) authenticate()
    }

    val notSetUp = BiometricAuth.isNotSetUp(status)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("ClearCall is locked", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)

            if (notSetUp) {
                Text(
                    "This device has no screen lock or fingerprint set up, so ClearCall can't verify it's you. " +
                        "Set one up in Android settings, or turn off app lock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onTurnOffLock) { Text("Turn off app lock") }
            } else {
                Text(
                    "Your recordings are protected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { authenticate() },
                    enabled = status == BiometricManager.BIOMETRIC_SUCCESS,
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock")
                }
            }
        }
    }
}

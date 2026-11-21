package app.clearcall.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.clearcall.appContainer
import app.clearcall.core.AppPermissions
import app.clearcall.ui.components.AppCard
import app.clearcall.ui.theme.Caution
import kotlinx.coroutines.launch

private const val LAST_STEP = 2

private data class PermissionRow(
    val permission: String,
    val title: String,
    val why: String,
    val required: Boolean,
)

private fun permissionRows(): List<PermissionRow> = buildList {
    add(PermissionRow(Manifest.permission.RECORD_AUDIO, "Microphone", "Captures audio during a call.", true))
    add(PermissionRow(Manifest.permission.READ_PHONE_STATE, "Phone state", "Detects when a call starts and ends.", true))
    add(PermissionRow(Manifest.permission.READ_CALL_LOG, "Call log (optional)", "Shows who a call was with and whether it was incoming or outgoing.", false))
    add(PermissionRow(Manifest.permission.READ_CONTACTS, "Contacts (optional)", "Shows contact names instead of phone numbers.", false))
    if (Build.VERSION.SDK_INT >= 33) {
        add(PermissionRow(Manifest.permission.POST_NOTIFICATIONS, "Notifications (optional)", "Shows the recording indicator and the Record button.", false))
    }
}

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(0) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
    }
    val coreGranted = remember(permissionTick) { AppPermissions.hasCore(context) }

    fun finish() {
        scope.launch { container.settings.completeOnboarding() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        StepDots(current = step, total = LAST_STEP + 1)
        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it / 5 } + fadeIn() togetherWith slideOutHorizontally { -it / 5 } + fadeOut()
                } else {
                    slideInHorizontally { -it / 5 } + fadeIn() togetherWith slideOutHorizontally { it / 5 } + fadeOut()
                }
            },
            label = "onboarding",
        ) { page ->
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> ConsentPage(accepted = accepted, onAcceptedChange = { accepted = it })
                    else -> PermissionsPage(permissionTick)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (step) {
                0 -> Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
                1 -> Button(
                    onClick = { step = 2 },
                    enabled = accepted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Continue") }
                else -> {
                    Button(
                        onClick = {
                            if (coreGranted) finish() else launcher.launch(AppPermissions.all())
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (coreGranted) "Start using ClearCall" else "Allow access") }
                    if (!coreGranted) {
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
                    }
                }
            }
            if (step > 0) {
                TextButton(onClick = { step -= 1 }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            val width by animateDpAsState(if (index == current) 28.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomePage() {
    val context = LocalContext.current
    val caps = remember { context.appContainer.capabilities.evaluate() }

    PageHeader(
        icon = Icons.Rounded.GraphicEq,
        title = "Welcome to ClearCall",
        subtitle = "Private call recordings that stay on your phone.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Feature(Icons.Rounded.Lock, "Local only", "No account, no cloud, no ads. ClearCall can't even reach the internet.")
        Feature(Icons.Rounded.VerifiedUser, "Honest about your phone", "ClearCall only records what Android actually allows, and tells you which it is.")
    }
    Spacer(Modifier.height(24.dp))
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("On this device (${caps.deviceName}, Android ${caps.release})", style = MaterialTheme.typography.labelLarge)
            Text(
                caps.captureSummary(micFallbackEnabled = true),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!caps.hasTelephony) {
                Text(
                    "This device has no phone hardware, so there are no calls to record.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Caution,
                )
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConsentPage(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    PageHeader(
        icon = Icons.Rounded.VerifiedUser,
        title = ConsentCopy.TITLE,
        subtitle = "Please read this before you record anyone.",
    )
    AppCard(modifier = Modifier.fillMaxWidth()) {
        ConsentPoints(modifier = Modifier.padding(16.dp))
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAcceptedChange(!accepted) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
        Text(
            ConsentCopy.CHECKBOX,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, end = 8.dp),
        )
    }
}

@Composable
private fun PermissionsPage(tick: Int) {
    val context = LocalContext.current
    val rows = remember { permissionRows() }
    val status = remember(tick) { rows.associate { it.permission to AppPermissions.has(context, it.permission) } }

    PageHeader(
        icon = Icons.Rounded.Lock,
        title = "Permissions",
        subtitle = "ClearCall asks only for what it needs. You can change any of these later in Android settings.",
    )
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { row ->
                val granted = status[row.permission] == true
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (granted) "Granted" else "Not granted",
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            row.why,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (rows.filter { it.required }.any { status[it.permission] != true } && tick > 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Without microphone and phone access ClearCall can't record. You can grant them later from the dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = Caution,
        )
    }
}

package app.clearcall

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.clearcall.data.AppSettings
import app.clearcall.data.ThemeMode
import app.clearcall.ui.lock.LockScreen
import app.clearcall.ui.navigation.AppNavigation
import app.clearcall.ui.onboarding.OnboardingScreen
import app.clearcall.ui.theme.ClearCallTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs a fragment host. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // With app lock on, block screenshots, screen recording and the recent-apps thumbnail.
        lifecycleScope.launch {
            appContainer.settings.settings
                .map { it.appLockEnabled }
                .distinctUntilChanged()
                .collect { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent { ClearCallRoot() }
    }

    override fun onStart() {
        super.onStart()
        appContainer.appLock.onForegrounded()
    }

    override fun onStop() {
        appContainer.appLock.onBackgrounded()
        super.onStop()
    }
}

@Composable
private fun ClearCallRoot() {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val unlocked by container.appLock.unlocked.collectAsStateWithLifecycle()
    val current = settings

    ClearCallTheme(themeMode = current?.themeMode ?: ThemeMode.SYSTEM) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                // Settings are still loading from disk: show nothing rather than flash the wrong screen.
                current == null -> Unit

                !current.onboardingDone -> OnboardingScreen()

                // Content is not composed at all while locked, so nothing is drawn or played behind the lock.
                current.appLockEnabled && !unlocked -> LockScreen(
                    onUnlocked = { container.appLock.markUnlocked() },
                    onTurnOffLock = { scope.launch { container.settings.setAppLock(false) } },
                )

                else -> AppNavigation()
            }
        }
    }
}

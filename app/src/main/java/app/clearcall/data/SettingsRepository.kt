package app.clearcall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** What ClearCall does when a call is answered or placed. */
enum class RecordMode(val label: String) {
    OFF("Off"),

    /** Posts a "Record" notification; nothing is captured until the user taps it. */
    ASK("Ask"),

    /** Starts automatically (where Android allows it) and falls back to ASK when blocked. */
    AUTO("Auto"),
}

enum class RecordingQuality(
    val title: String,
    val detail: String,
    val extension: String,
    val sampleRate: Int,
    /** Encoder bitrate in bits/s; 0 for lossless PCM. */
    val bitrate: Int,
) {
    STANDARD("Standard", "AAC 128 kbps · 44.1 kHz · about 58 MB per hour", "m4a", 44_100, 128_000),
    HIGH("High", "AAC 256 kbps · 48 kHz · about 115 MB per hour", "m4a", 48_000, 256_000),
    LOSSLESS("Lossless", "WAV 16-bit PCM · 48 kHz · about 345 MB per hour", "wav", 48_000, 0);

    val isLossless: Boolean get() = extension == "wav"
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val consentAccepted: Boolean = false,
    val recordMode: RecordMode = RecordMode.ASK,
    val recordIncoming: Boolean = true,
    val recordOutgoing: Boolean = true,
    val quality: RecordingQuality = RecordingQuality.HIGH,
    /** Allow microphone capture when the device does not expose call audio. */
    val allowMicFallback: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    /** Auto-delete unstarred recordings older than this many days; 0 = keep forever. */
    val retentionDays: Int = 0,
)

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val consentAccepted = booleanPreferencesKey("consent_accepted")
        val recordMode = stringPreferencesKey("record_mode")
        val recordIncoming = booleanPreferencesKey("record_incoming")
        val recordOutgoing = booleanPreferencesKey("record_outgoing")
        val quality = stringPreferencesKey("quality")
        val allowMicFallback = booleanPreferencesKey("allow_mic_fallback")
        val themeMode = stringPreferencesKey("theme_mode")
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val retentionDays = intPreferencesKey("retention_days")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            onboardingDone = p[Keys.onboardingDone] ?: defaults.onboardingDone,
            consentAccepted = p[Keys.consentAccepted] ?: defaults.consentAccepted,
            recordMode = enumOrDefault(p[Keys.recordMode], defaults.recordMode),
            recordIncoming = p[Keys.recordIncoming] ?: defaults.recordIncoming,
            recordOutgoing = p[Keys.recordOutgoing] ?: defaults.recordOutgoing,
            quality = enumOrDefault(p[Keys.quality], defaults.quality),
            allowMicFallback = p[Keys.allowMicFallback] ?: defaults.allowMicFallback,
            themeMode = enumOrDefault(p[Keys.themeMode], defaults.themeMode),
            appLockEnabled = p[Keys.appLockEnabled] ?: defaults.appLockEnabled,
            retentionDays = p[Keys.retentionDays] ?: defaults.retentionDays,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun completeOnboarding() {
        store.edit {
            it[Keys.consentAccepted] = true
            it[Keys.onboardingDone] = true
        }
    }

    suspend fun setRecordMode(value: RecordMode) = store.edit { it[Keys.recordMode] = value.name }.let { }
    suspend fun setRecordIncoming(value: Boolean) = store.edit { it[Keys.recordIncoming] = value }.let { }
    suspend fun setRecordOutgoing(value: Boolean) = store.edit { it[Keys.recordOutgoing] = value }.let { }
    suspend fun setQuality(value: RecordingQuality) = store.edit { it[Keys.quality] = value.name }.let { }
    suspend fun setAllowMicFallback(value: Boolean) = store.edit { it[Keys.allowMicFallback] = value }.let { }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.themeMode] = value.name }.let { }
    suspend fun setAppLock(value: Boolean) = store.edit { it[Keys.appLockEnabled] = value }.let { }
    suspend fun setRetentionDays(value: Int) = store.edit { it[Keys.retentionDays] = value }.let { }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

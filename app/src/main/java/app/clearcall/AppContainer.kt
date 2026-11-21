package app.clearcall

import android.content.Context
import app.clearcall.data.AppDatabase
import app.clearcall.data.BackupManager
import app.clearcall.data.RecordingRepository
import app.clearcall.data.SettingsRepository
import app.clearcall.recording.CaptureCapabilities
import app.clearcall.recording.RecordingStateHolder
import app.clearcall.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container: one instance per process, reachable from receivers, services and UI. */
class AppContainer(context: Context) {
    val context: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(this.context)

    private val database: AppDatabase by lazy { AppDatabase.build(this.context) }

    val recordings: RecordingRepository by lazy {
        RecordingRepository(this.context, database.recordingDao())
    }

    val backup: BackupManager by lazy { BackupManager(this.context, recordings) }

    val recordingState = RecordingStateHolder()

    val appLock = AppLockManager()

    val capabilities = CaptureCapabilities(this.context)
}

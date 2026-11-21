package app.clearcall

import android.app.Application
import android.content.Context
import app.clearcall.recording.NotificationHelper
import kotlinx.coroutines.launch

class ClearCallApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        container.appScope.launch {
            // Housekeeping: rescue recordings left behind by a killed process, then apply retention.
            container.recordings.recoverOrphans()
            container.recordings.applyRetention(container.settings.current().retentionDays)
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ClearCallApp).container

package tw.dietbook

import android.app.Application

class DietApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        try {
            DataUpdater.schedulePeriodic(this)
        } catch (_: Exception) {
            // Robolectric／無 WorkManager 初始化時略過
        }
    }
}

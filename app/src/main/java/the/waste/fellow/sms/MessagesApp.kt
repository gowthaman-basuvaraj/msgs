package the.waste.fellow.sms

import android.app.Application
import com.google.android.material.color.DynamicColors
import the.waste.fellow.sms.sync.HttpSmsSyncRepository
import the.waste.fellow.sms.sync.SmsSync
import the.waste.fellow.sms.utils.AppSettings

/**
 * Applies Material 3 dynamic color (wallpaper-derived palette) on Android 12+ where
 * available, and installs the server-sync repository so received messages are forwarded to
 * the sms_web_api server.
 */
class MessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Route the SmsSync seam to the HTTP implementation; it internally no-ops when sync
        // is disabled in settings, so this is always safe to install.
        SmsSync.repository = HttpSmsSyncRepository(this)

        // Flush anything queued from a previous run (e.g. was offline / token had expired).
        if (AppSettings(this).syncConfigured) {
            HttpSmsSyncRepository.scheduleSync(this)
        }
    }
}

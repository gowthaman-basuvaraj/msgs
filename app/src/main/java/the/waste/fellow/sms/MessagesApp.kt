package the.waste.fellow.sms

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.android.material.color.DynamicColors
import the.waste.fellow.sms.constants.Constants
import the.waste.fellow.sms.sync.HttpSmsSyncRepository
import the.waste.fellow.sms.sync.SmsSync
import the.waste.fellow.sms.utils.AppSettings

/**
 * Applies Material 3 dynamic color (wallpaper-derived palette) on Android 12+ where
 * available, installs the server-sync repository, and creates the notification channels.
 */
class MessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        createNotificationChannels()

        // Route the SmsSync seam to the HTTP implementation; it internally no-ops when sync
        // is disabled in settings, so this is always safe to install.
        SmsSync.repository = HttpSmsSyncRepository(this)

        // Flush anything queued from a previous run (e.g. was offline / token had expired).
        if (AppSettings(this).syncConfigured) {
            HttpSmsSyncRepository.scheduleSync(this)
        }
    }

    // App-wide channels; whether a notification is posted is decided per-sender in
    // SmsReceiver (see SenderNotifyPrefs), so these are always displayable.
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(Constants.CHANNEL_OTP, "OTP", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}

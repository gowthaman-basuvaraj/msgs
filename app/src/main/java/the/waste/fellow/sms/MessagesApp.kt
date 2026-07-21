package the.waste.fellow.sms

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Applies Material 3 dynamic color (wallpaper-derived palette) on Android 12+ where
 * available. On older devices the static brand colours from the theme are used.
 */
class MessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

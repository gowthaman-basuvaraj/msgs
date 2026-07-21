package the.waste.fellow.sms.activities

import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * Transparent, no-UI trampoline that copies an OTP to the clipboard from the notification's
 * "Copy" action. Clipboard writes from a background BroadcastReceiver are silently ignored
 * on Android 10+ (that was the original copy bug); doing it from a briefly-foregrounded
 * Activity works reliably.
 */
class OtpCopyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val otp = intent?.getStringExtra(EXTRA_OTP)
        if (!otp.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP", otp))

            // Android 13+ shows its own copy confirmation; avoid a duplicate toast there.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "OTP copied", Toast.LENGTH_SHORT).show()
            }
        }

        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
        if (notificationId != -1) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notificationId)
        }

        finish()
    }

    companion object {
        const val EXTRA_OTP = "otp"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

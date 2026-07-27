package the.waste.fellow.sms.notify

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Single-choice dialog to set a sender's notification state and persist it:
 *   Notify all messages / OTP only (default) / Mute (silence all).
 * [onChanged] runs after a selection so callers can refresh their UI (e.g. the bell icon).
 */
fun showNotifyChooser(context: Context, sender: String, onChanged: (() -> Unit)? = null) {
    val prefs = SenderNotifyPrefs(context)
    val states = arrayOf(NotifyState.UNMUTED, NotifyState.DEFAULT, NotifyState.MUTED)
    val labels = arrayOf(
        "Notify all messages",
        "OTP only (default)",
        "Mute (silence all)",
    )
    val current = states.indexOf(prefs.state(sender)).coerceAtLeast(0)
    MaterialAlertDialogBuilder(context)
        .setTitle(sender)
        .setSingleChoiceItems(labels, current) { dialog, which ->
            prefs.set(sender, states[which])
            dialog.dismiss()
            onChanged?.invoke()
        }
        .setNegativeButton("Cancel", null)
        .show()
}
